package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.UpdateArticleRequest;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for editing a DRAFT Article (BIAN Behavior Qualifier: {@code update}). */
@Singleton
public class UpdateArticleUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateArticleUseCase.class);

    private final ArticleWorkflow workflow;

    public UpdateArticleUseCase(ArticleWorkflow workflow) {
        this.workflow = workflow;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, UpdateArticleRequest request, String executor, String role) {
        log.info("[USE CASE] Updating Article ID: {}", id);

        return workflow.apply(id, organisationId, role, "edit an article",
                article -> article.updateContent(request.title(), request.body(), request.category(), request.keywords(), request.visibility(),
                        request.relatedProblemIds(), request.relatedIncidentIds(), executor));
    }
}
