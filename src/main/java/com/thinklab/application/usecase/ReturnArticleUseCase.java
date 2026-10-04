package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.ReturnArticleRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for sending an Article in review back to its author with a comment (BIAN Behavior Qualifier: {@code control/return}). The reviewer cannot be the author. */
@Singleton
public class ReturnArticleUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReturnArticleUseCase.class);

    private final ArticleWorkflow workflow;

    public ReturnArticleUseCase(ArticleWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, ReturnArticleRequest request, String executor, String role) {
        log.info("[USE CASE] Returning Article ID: {}", id);

        return workflow.apply(id, organisationId, role, "review an article", article -> article.returnForChanges(request.comment(), executor));
    }
}
