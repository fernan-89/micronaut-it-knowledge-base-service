package com.thinklab.application.usecase;

import com.thinklab.domain.exception.ArticleAccessDeniedException;
import com.thinklab.domain.exception.ArticleNotFoundException;
import com.thinklab.domain.model.Article;
import com.thinklab.domain.model.Article.ArticleAuditEntry;
import com.thinklab.domain.repository.ArticleRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.function.Function;

/**
 * The shared shape of every staff action on an article: refuse a REQUESTER (ADR-032), load the article inside the tenant, apply the domain
 * behavior and save the state it reached with its audit entry as a guarded write (ADR-033), so a lost race is a 409 and never a lost update.
 */
@Singleton
public class ArticleWorkflow {

    static final String REQUESTER_ROLE = "REQUESTER";

    private final ArticleRepository articleRepository;

    public ArticleWorkflow(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    /** Writing, reviewing and publishing are the work of IT staff: a REQUESTER is refused. */
    static void requireStaff(String role, String operation) {
        if (REQUESTER_ROLE.equals(role)) {
            throw new ArticleAccessDeniedException(operation);
        }
    }

    static boolean isRequester(String role) {
        return REQUESTER_ROLE.equals(role);
    }

    public Mono<Void> apply(UUID id, UUID organisationId, String role, String operation, Function<Article, ArticleAuditEntry> action) {
        return Mono.fromRunnable(() -> requireStaff(role, operation))
                .then(Mono.defer(() -> articleRepository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new ArticleNotFoundException(id)))
                .flatMap(article -> {
                    var statusBefore = article.getStatus();
                    ArticleAuditEntry entry = action.apply(article);
                    return articleRepository.save(article, statusBefore, entry);
                });
    }
}
