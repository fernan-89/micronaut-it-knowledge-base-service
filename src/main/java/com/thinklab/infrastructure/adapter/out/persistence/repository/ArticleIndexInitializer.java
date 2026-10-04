package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.reactivestreams.client.MongoClient;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Creates the indexes of the {@code articles} collection at startup, each matching a query the adapter really runs: the UNIQUE
 * {@code (organisationId, articleKey, version)} (the atomic backstop of "one document per version", ADR-033), the listing by
 * {@code (organisationId, status)}, "what do we know about this problem / incident" ({@code relatedProblemIds}, {@code relatedIncidentIds},
 * multikey), "articles with this keyword" ({@code keywords}, multikey) and the free-text search: a text index over title, body and
 * keywords (weighted 10, 1 and 5) behind the organisation as an equality prefix, so every search is tenant-scoped by the index itself. The
 * adapter uses the driver directly, so the kit's generic {@code MongoIndexInitializer} does not see it.
 *
 * <p>The text index uses {@code default_language: none}: articles are written in whatever language the team uses, so no stemming or stop
 * words are assumed. Fail-open: {@code createIndex} is idempotent; a failure is logged and the application still starts. Turn it off with
 * {@code thinklab.mongo.create-indexes=false}.
 */
@Singleton
@Requires(property = "thinklab.mongo.create-indexes", notEquals = "false")
public class ArticleIndexInitializer implements ApplicationEventListener<StartupEvent> {

    static final String VERSION_INDEX = "organisationId_1_articleKey_1_version_1";
    static final String STATUS_INDEX = "organisationId_1_status_1";
    static final String PROBLEM_INDEX = "organisationId_1_relatedProblemIds_1";
    static final String INCIDENT_INDEX = "organisationId_1_relatedIncidentIds_1";
    static final String KEYWORD_INDEX = "organisationId_1_keywords_1";
    static final String TEXT_INDEX = "organisationId_1_article_text";

    private static final Logger log = LoggerFactory.getLogger(ArticleIndexInitializer.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final MongoClient mongoClient;
    private final String database;
    private final Duration timeout;

    @Inject
    public ArticleIndexInitializer(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this(mongoClient, mongoUri, TIMEOUT);
    }

    ArticleIndexInitializer(MongoClient mongoClient, String mongoUri, Duration timeout) {
        this.mongoClient = Objects.requireNonNull(mongoClient, "Infrastructure constraint violated: MongoClient cannot be null.");
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : ArticleMongoRepositoryAdapter.DEFAULT_DATABASE;
        this.timeout = timeout;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        Objects.requireNonNull(event, "Application constraint violated: StartupEvent cannot be null.");
        ensureIndex(new Document("organisationId", 1).append("articleKey", 1).append("version", 1), new IndexOptions().name(VERSION_INDEX).unique(true));
        ensureIndex(new Document("organisationId", 1).append("status", 1), new IndexOptions().name(STATUS_INDEX));
        ensureIndex(new Document("organisationId", 1).append("relatedProblemIds", 1), new IndexOptions().name(PROBLEM_INDEX));
        ensureIndex(new Document("organisationId", 1).append("relatedIncidentIds", 1), new IndexOptions().name(INCIDENT_INDEX));
        ensureIndex(new Document("organisationId", 1).append("keywords", 1), new IndexOptions().name(KEYWORD_INDEX));
        ensureIndex(new Document("organisationId", 1).append("title", "text").append("body", "text").append("keywords", "text"),
                new IndexOptions().name(TEXT_INDEX).defaultLanguage("none").weights(new Document("title", 10).append("keywords", 5).append("body", 1)));
    }

    private void ensureIndex(Document keys, IndexOptions options) {
        try {
            Mono.from(mongoClient.getDatabase(database).getCollection(ArticleMongoRepositoryAdapter.COLLECTION_NAME).createIndex(keys, options)).block(timeout);
            log.info("[MONGO_INDEXES] Ensured index [{}] on [{}.{}]", options.getName(), database, ArticleMongoRepositoryAdapter.COLLECTION_NAME);
        } catch (MongoTimeoutException e) {
            log.error("[MONGO_INDEXES] MongoDB unreachable; index [{}] was not created. Reason: {}", options.getName(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("[MONGO_INDEXES] Could not create index [{}] on [{}.{}]: {}", options.getName(), database, ArticleMongoRepositoryAdapter.COLLECTION_NAME, e.getMessage());
        }
    }
}
