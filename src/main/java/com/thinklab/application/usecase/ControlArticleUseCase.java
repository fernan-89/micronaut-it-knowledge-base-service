package com.thinklab.application.usecase;

import com.thinklab.domain.model.Article;
import com.thinklab.domain.model.Article.ArticleAuditEntry;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for the lifecycle moves that need no payload (BIAN Behavior Qualifier: {@code control/*}). */
@Singleton
public class ControlArticleUseCase {

    private static final Logger log = LoggerFactory.getLogger(ControlArticleUseCase.class);

    /** What a person can ask for; each constant says how the aggregate performs it. */
    public enum Action {
        SUBMIT {
            @Override ArticleAuditEntry apply(Article article, String executor) { return article.submit(executor); }
        },
        RETIRE {
            @Override ArticleAuditEntry apply(Article article, String executor) { return article.retire(executor); }
        };

        abstract ArticleAuditEntry apply(Article article, String executor);
    }

    private final ArticleWorkflow workflow;

    public ControlArticleUseCase(ArticleWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, Action action, String executor, String role) {
        log.info("[USE CASE] {} on Article ID: {}", action, id);

        return workflow.apply(id, organisationId, role, "work an article", article -> action.apply(article, executor));
    }
}
