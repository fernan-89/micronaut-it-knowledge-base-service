package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateArticleRequest;
import com.thinklab.application.dto.response.ArticleResponse;
import com.thinklab.application.mapper.ArticleMapper;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.ArticleRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Use Case for drafting an Article (BIAN Behavior Qualifier: {@code initiate}). Staff only; the author is the caller; the id is a sovereign UUID. */
@Singleton
public class InitiateArticleUseCase {

    private static final Logger log = LoggerFactory.getLogger(InitiateArticleUseCase.class);

    private final HashServicePort hashServicePort;
    private final ArticleRepository articleRepository;

    public InitiateArticleUseCase(HashServicePort hashServicePort, ArticleRepository articleRepository) {
        this.hashServicePort = hashServicePort;
        this.articleRepository = articleRepository;
    }

    public Mono<ArticleResponse> execute(UUID organisationId, InitiateArticleRequest request, String executor, String role) {
        log.info("[USE CASE] Drafting an Article for organisation: {}", organisationId);

        return Mono.fromRunnable(() -> ArticleWorkflow.requireStaff(role, "write an article"))
                .then(Mono.defer(() -> hashServicePort.generateSovereignId("article-creation")))
                .map(sovereignId -> ArticleMapper.toDomain(request, sovereignId, organisationId, executor))
                .flatMap(articleRepository::create)
                .map(article -> ArticleMapper.toResponse(article, false));
    }
}
