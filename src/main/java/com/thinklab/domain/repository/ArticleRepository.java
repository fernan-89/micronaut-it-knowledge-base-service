package com.thinklab.domain.repository;

import com.thinklab.domain.model.Article;
import com.thinklab.domain.model.Article.ArticleAuditEntry;
import com.thinklab.domain.model.Article.ArticleStatus;
import com.thinklab.domain.model.Article.Visibility;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound Port for Article persistence (IT Knowledge Base Service Domain).
 *
 * <p>ARCHITECTURAL RULE: Partial State Mutations (ADR-002). {@link #create} is the only whole-document write; {@link #save} persists the
 * state a domain operation reached together with its audit entry in one atomic update, and only while the article is still in the status
 * it had when it was loaded (ADR-033). There is no {@code deleteById}. Every lookup is tenant-scoped: an article of another organisation
 * is simply not found.
 */
public interface ArticleRepository {

    /**
     * Inserts the article. A second document for the same {@code (articleKey, version)} (two people starting the same new version at once)
     * is {@link com.thinklab.domain.exception.DuplicateArticleException}.
     */
    Mono<Article> create(Article article);

    Mono<Article> findById(UUID id, UUID organisationId);

    /** Every version of one article, oldest first. */
    Flux<Article> findByKey(UUID articleKey, UUID organisationId);

    Flux<Article> findAll(UUID organisationId, Filter filter);

    /** Persists the state the article reached with its audit entry; a lost race is {@link com.thinklab.domain.exception.InvalidArticleStatusException} (409, retry). */
    Mono<Void> save(Article article, ArticleStatus expectedStatus, ArticleAuditEntry auditEntry);

    /**
     * Optional filters of the collection. {@code text} is a free-text search over title, body and keywords; {@code keyword} an exact
     * keyword; {@code problemId} and {@code incidentId} answer "what do we know about this problem / incident".
     */
    record Filter(String text, ArticleStatus status, String category, Visibility visibility, String keyword, UUID problemId, UUID incidentId, String authorId) {
    }
}
