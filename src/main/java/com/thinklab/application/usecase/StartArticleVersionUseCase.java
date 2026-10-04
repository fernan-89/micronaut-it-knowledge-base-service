package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.ArticleResponse;
import com.thinklab.application.mapper.ArticleMapper;
import com.thinklab.domain.exception.ArticleNotFoundException;
import com.thinklab.domain.model.Article;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.ArticleRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case for starting a new version of a PUBLISHED Article (BIAN Behavior Qualifier: {@code version/initiate}). It creates a new draft
 * document with the same key and {@code version + 1}; the published one keeps serving. Two people starting the same version at once: the
 * unique {@code (articleKey, version)} index lets one win and refuses the other (409).
 */
@Singleton
public class StartArticleVersionUseCase {

    private static final Logger log = LoggerFactory.getLogger(StartArticleVersionUseCase.class);

    private final HashServicePort hashServicePort;
    private final ArticleRepository articleRepository;

    public StartArticleVersionUseCase(HashServicePort hashServicePort, ArticleRepository articleRepository) {
        this.hashServicePort = hashServicePort;
        this.articleRepository = articleRepository;
    }

    public Mono<ArticleResponse> execute(UUID id, UUID organisationId, String executor, String role) {
        log.info("[USE CASE] Starting a new version of Article ID: {}", id);

        return Mono.fromRunnable(() -> ArticleWorkflow.requireStaff(role, "start a new version of an article"))
                .then(Mono.defer(() -> articleRepository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new ArticleNotFoundException(id)))
                .flatMap(published -> hashServicePort.generateSovereignId("article-version-creation")
                        .map(newId -> Article.nextVersionOf(published, newId, executor)))
                .flatMap(articleRepository::create)
                .map(article -> ArticleMapper.toResponse(article, false));
    }
}
