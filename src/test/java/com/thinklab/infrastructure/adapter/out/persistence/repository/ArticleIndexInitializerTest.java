package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoTimeoutException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import io.micronaut.context.event.StartupEvent;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class ArticleIndexInitializerTest {

    private final StartupEvent startup = mock(StartupEvent.class);

    private MongoCollection<Document> collectionIn(MongoClient client, String database) {
        MongoDatabase mongoDatabase = mock(MongoDatabase.class);
        MongoCollection<Document> collection = mock(MongoCollection.class);
        when(client.getDatabase(database)).thenReturn(mongoDatabase);
        when(mongoDatabase.getCollection("articles")).thenReturn(collection);
        return collection;
    }

    @Test
    @DisplayName("startup creates the unique version index, the listing, problem, incident and keyword indexes and the weighted text index behind the organisation")
    void createsIndexes() {
        MongoClient client = mock(MongoClient.class);
        MongoCollection<Document> collection = collectionIn(client, "tenant_knb");
        when(collection.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));

        new ArticleIndexInitializer(client, "mongodb://mongo:27017/tenant_knb").onApplicationEvent(startup);

        ArgumentCaptor<Document> keys = ArgumentCaptor.forClass(Document.class);
        ArgumentCaptor<IndexOptions> options = ArgumentCaptor.forClass(IndexOptions.class);
        verify(collection, times(6)).createIndex(keys.capture(), options.capture());
        assertEquals(List.of(
                new Document("organisationId", 1).append("articleKey", 1).append("version", 1),
                new Document("organisationId", 1).append("status", 1),
                new Document("organisationId", 1).append("relatedProblemIds", 1),
                new Document("organisationId", 1).append("relatedIncidentIds", 1),
                new Document("organisationId", 1).append("keywords", 1),
                new Document("organisationId", 1).append("title", "text").append("body", "text").append("keywords", "text")), keys.getAllValues());
        assertEquals(List.of(ArticleIndexInitializer.VERSION_INDEX, ArticleIndexInitializer.STATUS_INDEX, ArticleIndexInitializer.PROBLEM_INDEX,
                ArticleIndexInitializer.INCIDENT_INDEX, ArticleIndexInitializer.KEYWORD_INDEX, ArticleIndexInitializer.TEXT_INDEX),
                options.getAllValues().stream().map(IndexOptions::getName).toList());
        assertTrue(options.getAllValues().get(0).isUnique());
        assertEquals("none", options.getAllValues().get(5).getDefaultLanguage());
        assertEquals(new Document("title", 10).append("keywords", 5).append("body", 1), options.getAllValues().get(5).getWeights());
    }

    @Test
    @DisplayName("a URI without a database uses the service default")
    void defaultDatabase() {
        MongoClient client = mock(MongoClient.class);
        MongoCollection<Document> collection = collectionIn(client, ArticleMongoRepositoryAdapter.DEFAULT_DATABASE);
        when(collection.createIndex(any(), any(IndexOptions.class))).thenReturn(Mono.just("ok"));

        new ArticleIndexInitializer(client, "mongodb://mongo:27017").onApplicationEvent(startup);

        verify(collection, times(6)).createIndex(any(), any(IndexOptions.class));
    }

    @Test
    @DisplayName("fail-open: an unreachable server or a rejected index is logged, never propagated")
    void failOpen() {
        MongoClient client = mock(MongoClient.class);
        MongoCollection<Document> collection = collectionIn(client, "knb_db");
        when(collection.createIndex(any(), any(IndexOptions.class)))
                .thenReturn(Mono.error(new MongoTimeoutException("no server")))
                .thenReturn(Mono.error(new IllegalStateException("rejected")))
                .thenReturn(Mono.just("ok"));

        assertDoesNotThrow(() -> new ArticleIndexInitializer(client, "mongodb://mongo:27017/knb_db", Duration.ofSeconds(1)).onApplicationEvent(startup));
        verify(collection, times(6)).createIndex(any(), any(IndexOptions.class));
    }

    @Test
    @DisplayName("collaborators, mongodb.uri and the startup event are null-checked")
    void guards() {
        MongoClient client = mock(MongoClient.class);
        assertThrows(NullPointerException.class, () -> new ArticleIndexInitializer(null, "mongodb://mongo:27017/a"));
        assertThrows(NullPointerException.class, () -> new ArticleIndexInitializer(client, null));
        assertThrows(NullPointerException.class, () -> new ArticleIndexInitializer(client, "mongodb://mongo:27017/a").onApplicationEvent(null));
    }
}
