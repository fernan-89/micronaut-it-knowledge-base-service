package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.Article;
import com.thinklab.domain.model.Article.ArticleAuditEntry;
import com.thinklab.domain.model.Article.ArticleStatus;
import com.thinklab.domain.model.Article.Visibility;
import io.micronaut.core.annotation.Introspected;
import org.bson.codecs.pojo.annotations.BsonId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Infrastructure-specific representation of the Article Aggregate for MongoDB. */
@Introspected
public class ArticleDocument {

    @BsonId
    private UUID id;

    private UUID organisationId;
    private UUID articleKey;
    private int version;
    private String title;
    private String body;
    private String category;
    private List<String> keywords = new ArrayList<>();
    private String visibility;
    private String status;
    private String authorId;
    private String reviewerId;
    private String reviewComment;
    private Instant publishedAt;
    private List<UUID> relatedProblemIds = new ArrayList<>();
    private List<UUID> relatedIncidentIds = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;
    private List<AuditEntryDocument> auditTrail = new ArrayList<>();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganisationId() { return organisationId; }
    public void setOrganisationId(UUID organisationId) { this.organisationId = organisationId; }
    public UUID getArticleKey() { return articleKey; }
    public void setArticleKey(UUID articleKey) { this.articleKey = articleKey; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public String getReviewerId() { return reviewerId; }
    public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public List<UUID> getRelatedProblemIds() { return relatedProblemIds; }
    public void setRelatedProblemIds(List<UUID> relatedProblemIds) { this.relatedProblemIds = relatedProblemIds; }
    public List<UUID> getRelatedIncidentIds() { return relatedIncidentIds; }
    public void setRelatedIncidentIds(List<UUID> relatedIncidentIds) { this.relatedIncidentIds = relatedIncidentIds; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<AuditEntryDocument> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<AuditEntryDocument> auditTrail) { this.auditTrail = auditTrail; }

    @Introspected
    public record AuditEntryDocument(Instant occurredAt, String action, String executor, String fromStatus, String toStatus, String detail) {

        public static AuditEntryDocument fromDomain(ArticleAuditEntry entry) {
            return new AuditEntryDocument(entry.occurredAt(), entry.action(), entry.executor(),
                    entry.fromStatus() != null ? entry.fromStatus().name() : null, entry.toStatus().name(), entry.detail());
        }

        // toStatus has no null branch: fromDomain always writes entry.toStatus().name().
        ArticleAuditEntry toDomain() {
            return new ArticleAuditEntry(occurredAt, action, executor, fromStatus != null ? ArticleStatus.valueOf(fromStatus) : null,
                    ArticleStatus.valueOf(toStatus), detail);
        }
    }

    public static final class ArticlePersistenceMapper {

        private ArticlePersistenceMapper() { throw new UnsupportedOperationException(); }

        public static ArticleDocument toDocument(Article article) {
            ArticleDocument doc = new ArticleDocument();
            doc.setId(article.getId());
            doc.setOrganisationId(article.getOrganisationId());
            doc.setArticleKey(article.getArticleKey());
            doc.setVersion(article.getVersion());
            doc.setTitle(article.getTitle());
            doc.setBody(article.getBody());
            doc.setCategory(article.getCategory());
            doc.setKeywords(new ArrayList<>(article.getKeywords()));
            doc.setVisibility(article.getVisibility().name());
            doc.setStatus(article.getStatus().name());
            doc.setAuthorId(article.getAuthorId());
            doc.setReviewerId(article.getReviewerId());
            doc.setReviewComment(article.getReviewComment());
            doc.setPublishedAt(article.getPublishedAt());
            doc.setRelatedProblemIds(new ArrayList<>(article.getRelatedProblemIds()));
            doc.setRelatedIncidentIds(new ArrayList<>(article.getRelatedIncidentIds()));
            doc.setCreatedAt(article.getCreatedAt());
            doc.setUpdatedAt(article.getUpdatedAt());
            doc.setAuditTrail(article.getAuditTrail().stream().map(AuditEntryDocument::fromDomain).collect(Collectors.toCollection(ArrayList::new)));
            return doc;
        }

        public static Article toDomain(ArticleDocument doc) {
            return Article.reconstitute(doc.getId(), doc.getOrganisationId(), doc.getArticleKey(), doc.getVersion(), doc.getTitle(), doc.getBody(),
                    doc.getCategory(), new LinkedHashSet<>(doc.getKeywords()), Visibility.valueOf(doc.getVisibility()), ArticleStatus.valueOf(doc.getStatus()),
                    doc.getAuthorId(), doc.getReviewerId(), doc.getReviewComment(), doc.getPublishedAt(), asSet(doc.getRelatedProblemIds()),
                    asSet(doc.getRelatedIncidentIds()), doc.getCreatedAt(), doc.getUpdatedAt(),
                    doc.getAuditTrail().stream().map(AuditEntryDocument::toDomain).collect(Collectors.toList()));
        }

        private static Set<UUID> asSet(List<UUID> ids) {
            return new LinkedHashSet<>(ids);
        }
    }
}
