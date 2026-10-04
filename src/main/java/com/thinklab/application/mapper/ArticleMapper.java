package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateArticleRequest;
import com.thinklab.application.dto.response.ArticleResponse;
import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.domain.model.Article;
import com.thinklab.domain.model.Article.ArticleAuditEntry;

import java.util.UUID;

/** Maps between the Article aggregate and its DTOs. Static, stateless. */
public final class ArticleMapper {

    private ArticleMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static Article toDomain(InitiateArticleRequest request, UUID sovereignId, UUID organisationId, String author) {
        return Article.createNew(sovereignId, organisationId, request.title(), request.body(), request.category(), request.keywords(), request.visibility(),
                request.relatedProblemIds(), request.relatedIncidentIds(), author);
    }

    public static ArticleResponse toResponse(Article article, boolean forRequester) {
        return new ArticleResponse(article.getId(), article.getOrganisationId(), article.getArticleKey(), article.getVersion(), article.getTitle(),
                article.getBody(), article.getCategory(), article.getKeywords(), article.getVisibility().name(), article.getStatus().name(),
                forRequester ? null : article.getAuthorId(), forRequester ? null : article.getReviewerId(), forRequester ? null : article.getReviewComment(),
                article.getPublishedAt(), forRequester ? null : article.getRelatedProblemIds(), forRequester ? null : article.getRelatedIncidentIds(),
                article.getCreatedAt(), article.getUpdatedAt());
    }

    public static AuditEntryResponse toResponse(ArticleAuditEntry entry) {
        return new AuditEntryResponse(entry.occurredAt(), entry.action(), entry.executor(),
                entry.fromStatus() != null ? entry.fromStatus().name() : null, entry.toStatus().name(), entry.detail());
    }
}
