package com.thinklab.infrastructure.adapter.out.persistence.repository;

import com.mongodb.ConnectionString;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.thinklab.domain.exception.DuplicateArticleException;
import com.thinklab.domain.exception.InvalidArticleStatusException;
import com.thinklab.domain.model.Article;
import com.thinklab.domain.model.Article.ArticleAuditEntry;
import com.thinklab.domain.model.Article.ArticleStatus;
import com.thinklab.domain.repository.ArticleRepository;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ArticleDocument;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ArticleDocument.ArticlePersistenceMapper;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ArticleDocument.AuditEntryDocument;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * MongoDB Reactive Repository Adapter for the Article aggregate, raw reactive-streams driver. Every change is a single atomic
 * {@code $set}/{@code $push} that also appends the forensic audit entry, and every filter carries the organisation: another tenant's
 * article is simply not found. The unique {@code (organisationId, articleKey, version)} index is the atomic backstop of "one document per
 * version" (ADR-033).
 */
@Singleton
public class ArticleMongoRepositoryAdapter implements ArticleRepository {

    private static final Logger log = LoggerFactory.getLogger(ArticleMongoRepositoryAdapter.class);
    static final String DEFAULT_DATABASE = "thinklab_it_knowledge_base_db";
    static final String COLLECTION_NAME = "articles";
    private static final String FIELD_ID = "_id";
    private static final String FIELD_ORGANISATION = "organisationId";
    private static final String FIELD_STATUS = "status";

    private static final CodecRegistry POJO_CODEC_REGISTRY = CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    private final MongoClient mongoClient;
    private final String database;

    public ArticleMongoRepositoryAdapter(MongoClient mongoClient, @Property(name = "mongodb.uri") String mongoUri) {
        this.mongoClient = mongoClient;
        String configured = new ConnectionString(Objects.requireNonNull(mongoUri, "mongodb.uri cannot be null.")).getDatabase();
        this.database = configured != null ? configured : DEFAULT_DATABASE;
    }

    private MongoCollection<ArticleDocument> getCollection() {
        return mongoClient.getDatabase(database)
                .getCollection(COLLECTION_NAME, ArticleDocument.class)
                .withCodecRegistry(POJO_CODEC_REGISTRY);
    }

    @Override
    public Mono<Article> create(Article article) {
        log.debug("[PERSISTENCE] Monolithic create for Article Aggregate: {}", article.getId());

        return Mono.from(getCollection().insertOne(ArticlePersistenceMapper.toDocument(article)))
                .map(result -> article)
                .onErrorMap(ArticleMongoRepositoryAdapter::isDuplicateKey,
                        e -> new DuplicateArticleException("Version " + article.getVersion() + " of this article already exists; read it again and work on that one."));
    }

    static boolean isDuplicateKey(Throwable error) {
        return error instanceof MongoWriteException write && write.getError().getCategory() == ErrorCategory.DUPLICATE_KEY;
    }

    @Override
    public Mono<Article> findById(UUID id, UUID organisationId) {
        return Mono.from(getCollection().find(Filters.and(Filters.eq(FIELD_ID, id), Filters.eq(FIELD_ORGANISATION, organisationId))).first())
                .map(ArticlePersistenceMapper::toDomain);
    }

    @Override
    public Flux<Article> findByKey(UUID articleKey, UUID organisationId) {
        return Flux.from(getCollection().find(Filters.and(Filters.eq("articleKey", articleKey), Filters.eq(FIELD_ORGANISATION, organisationId)))
                        .sort(Sorts.ascending("version")))
                .map(ArticlePersistenceMapper::toDomain);
    }

    @Override
    public Flux<Article> findAll(UUID organisationId, Filter filter) {
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq(FIELD_ORGANISATION, organisationId));
        if (filter.text() != null) {
            filters.add(Filters.text(filter.text()));
        }
        if (filter.status() != null) {
            filters.add(Filters.eq(FIELD_STATUS, filter.status().name()));
        }
        if (filter.category() != null) {
            filters.add(Filters.eq("category", filter.category()));
        }
        if (filter.visibility() != null) {
            filters.add(Filters.eq("visibility", filter.visibility().name()));
        }
        if (filter.keyword() != null) {
            filters.add(Filters.eq("keywords", filter.keyword().trim().toLowerCase(java.util.Locale.ROOT)));
        }
        if (filter.problemId() != null) {
            filters.add(Filters.eq("relatedProblemIds", filter.problemId()));
        }
        if (filter.incidentId() != null) {
            filters.add(Filters.eq("relatedIncidentIds", filter.incidentId()));
        }
        if (filter.authorId() != null) {
            filters.add(Filters.eq("authorId", filter.authorId()));
        }

        return Flux.from(getCollection().find(Filters.and(filters))).map(ArticlePersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> save(Article article, ArticleStatus expectedStatus, ArticleAuditEntry auditEntry) {
        // Guarded write: it only applies while the article still has the status it had when it was loaded.
        Bson guard = Filters.and(Filters.eq(FIELD_ID, article.getId()), Filters.eq(FIELD_ORGANISATION, article.getOrganisationId()),
                Filters.eq(FIELD_STATUS, expectedStatus.name()));
        Bson update = Updates.combine(
                Updates.set("title", article.getTitle()),
                Updates.set("body", article.getBody()),
                Updates.set("category", article.getCategory()),
                Updates.set("keywords", new ArrayList<>(article.getKeywords())),
                Updates.set("visibility", article.getVisibility().name()),
                Updates.set(FIELD_STATUS, article.getStatus().name()),
                Updates.set("reviewerId", article.getReviewerId()),
                Updates.set("reviewComment", article.getReviewComment()),
                Updates.set("publishedAt", article.getPublishedAt()),
                Updates.set("relatedProblemIds", new ArrayList<>(article.getRelatedProblemIds())),
                Updates.set("relatedIncidentIds", new ArrayList<>(article.getRelatedIncidentIds())),
                Updates.set("updatedAt", Instant.now()),
                Updates.push("auditTrail", AuditEntryDocument.fromDomain(auditEntry))
        );
        return Mono.from(getCollection().updateOne(guard, update))
                .flatMap(result -> result.getMatchedCount() == 0
                        ? Mono.error(new InvalidArticleStatusException("Article was changed by someone else while this change was being recorded; read it again and retry."))
                        : Mono.<Void>empty());
    }
}
