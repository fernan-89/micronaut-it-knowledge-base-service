package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.FindPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.thinklab.domain.exception.DuplicateArticleException;
import com.thinklab.domain.exception.InvalidArticleStatusException;
import com.thinklab.domain.model.Article;
import com.thinklab.domain.model.Article.ArticleStatus;
import com.thinklab.domain.model.Article.Visibility;
import com.thinklab.domain.repository.ArticleRepository.Filter;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ArticleDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ArticleDocument.ArticlePersistenceMapper;
import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ArticleMongoRepositoryAdapterTest {

    @Mock private MongoClient mongoClient;
    @Mock private MongoDatabase mongoDatabase;
    @Mock private MongoCollection<ArticleDocument> mongoCollection;

    private ArticleMongoRepositoryAdapter adapter;
    private UUID organisationId;
    private Article article;

    @BeforeEach
    void setUp() {
        lenient().when(mongoClient.getDatabase("thinklab_it_knowledge_base_db")).thenReturn(mongoDatabase);
        lenient().when(mongoDatabase.getCollection("articles", ArticleDocument.class)).thenReturn(mongoCollection);
        lenient().when(mongoCollection.withCodecRegistry(any())).thenReturn(mongoCollection);
        adapter = new ArticleMongoRepositoryAdapter(mongoClient, "mongodb://localhost:27017/thinklab_it_knowledge_base_db");
        organisationId = UUID.randomUUID();
        article = Article.createNew(UUID.randomUUID(), organisationId, "Switch drops packets", "Reboot it", "NETWORK", Set.of("switch"), Visibility.PUBLIC, Set.of(), Set.of(), "author-1");
    }

    private FindPublisher<ArticleDocument> finds(Article... found) {
        FindPublisher<ArticleDocument> publisher = mock(FindPublisher.class);
        when(mongoCollection.find(any(Bson.class))).thenReturn(publisher);
        lenient().when(publisher.first()).thenReturn(publisher);
        lenient().when(publisher.sort(any())).thenReturn(publisher);
        doAnswer(invocation -> {
            org.reactivestreams.Subscriber<ArticleDocument> subscriber = invocation.getArgument(0);
            Flux.fromArray(found).map(ArticlePersistenceMapper::toDocument).subscribe(subscriber);
            return null;
        }).when(publisher).subscribe(any());
        return publisher;
    }

    @Test
    @DisplayName("the database falls back to the service default when the URI has none")
    void databaseFallback() {
        ArticleMongoRepositoryAdapter fallback = new ArticleMongoRepositoryAdapter(mongoClient, "mongodb://localhost:27017");
        when(mongoCollection.insertOne(any(ArticleDocument.class))).thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))));

        StepVerifier.create(fallback.create(article)).expectNext(article).verifyComplete();
    }

    @Test
    @DisplayName("create inserts the whole aggregate; a duplicate-key error on (key, version) becomes DuplicateArticleException; other errors pass through")
    void create() {
        when(mongoCollection.insertOne(any(ArticleDocument.class)))
                .thenReturn(Mono.just(InsertOneResult.acknowledged(new BsonObjectId(new ObjectId()))))
                .thenReturn(Mono.error(new MongoWriteException(new WriteError(11000, "E11000 duplicate key", new BsonDocument()), new ServerAddress())))
                .thenReturn(Mono.error(new MongoWriteException(new WriteError(121, "validation", new BsonDocument()), new ServerAddress())))
                .thenReturn(Mono.error(new IllegalStateException("boom")));

        StepVerifier.create(adapter.create(article)).expectNext(article).verifyComplete();
        StepVerifier.create(adapter.create(article)).expectError(DuplicateArticleException.class).verify();
        StepVerifier.create(adapter.create(article)).expectError(MongoWriteException.class).verify();
        StepVerifier.create(adapter.create(article)).expectError(IllegalStateException.class).verify();
    }

    @Test
    @DisplayName("isDuplicateKey recognises only a duplicate-key write error")
    void isDuplicateKey() {
        assertTrue(ArticleMongoRepositoryAdapter.isDuplicateKey(new MongoWriteException(new WriteError(11000, "dup", new BsonDocument()), new ServerAddress())));
        assertFalse(ArticleMongoRepositoryAdapter.isDuplicateKey(new MongoWriteException(new WriteError(121, "other", new BsonDocument()), new ServerAddress())));
        assertFalse(ArticleMongoRepositoryAdapter.isDuplicateKey(new RuntimeException()));
    }

    @Test
    @DisplayName("findById and findByKey are scoped to the organisation; the versions come oldest first")
    void finders() {
        FindPublisher<ArticleDocument> publisher = finds(article);

        StepVerifier.create(adapter.findById(article.getId(), organisationId)).assertNext(found -> assertEquals(article.getId(), found.getId())).verifyComplete();
        StepVerifier.create(adapter.findByKey(article.getArticleKey(), organisationId)).expectNextCount(1).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(mongoCollection, times(2)).find(filter.capture());
        assertTrue(filter.getAllValues().get(0).toString().contains("organisationId"));
        assertTrue(filter.getAllValues().get(1).toString().contains("articleKey"));
        verify(publisher).sort(any());
    }

    @Test
    @DisplayName("findAll applies every optional filter, including the text search and the lower-cased keyword")
    void findAll() {
        finds(article);
        UUID anyId = UUID.randomUUID();

        StepVerifier.create(adapter.findAll(organisationId, new Filter("firmware", ArticleStatus.PUBLISHED, "NETWORK", Visibility.PUBLIC, " Switch ", anyId, anyId, "author-1")))
                .expectNextCount(1).verifyComplete();
        StepVerifier.create(adapter.findAll(organisationId, new Filter(null, null, null, null, null, null, null, null))).expectNextCount(1).verifyComplete();

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(mongoCollection, times(2)).find(filter.capture());
        String full = filter.getAllValues().get(0).toString();
        assertTrue(full.contains("search") && full.contains("firmware") && full.contains("PUBLISHED") && full.contains("category") && full.contains("visibility")
                && full.contains("keywords") && full.contains("switch") && full.contains("relatedProblemIds") && full.contains("relatedIncidentIds") && full.contains("authorId"));
        String bare = filter.getAllValues().get(1).toString();
        assertTrue(bare.contains("organisationId") && !bare.contains("search") && !bare.contains("keywords"));
    }

    @Test
    @DisplayName("save is a guarded write on the organisation and the status loaded; a lost race is a 409-style conflict")
    void save() {
        when(mongoCollection.updateOne(any(Bson.class), any(Bson.class)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(1, 1L, null)))
                .thenReturn(Mono.just(UpdateResult.acknowledged(0, 0L, null)));
        var entry = article.submit("author-1");

        StepVerifier.create(adapter.save(article, ArticleStatus.DRAFT, entry)).verifyComplete();
        StepVerifier.create(adapter.save(article, ArticleStatus.DRAFT, entry)).expectError(InvalidArticleStatusException.class).verify();

        ArgumentCaptor<Bson> guard = ArgumentCaptor.forClass(Bson.class);
        verify(mongoCollection, times(2)).updateOne(guard.capture(), any(Bson.class));
        assertTrue(guard.getAllValues().get(0).toString().contains("organisationId") && guard.getAllValues().get(0).toString().contains("DRAFT"));
    }
}
