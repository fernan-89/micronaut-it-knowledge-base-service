package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.ArticleResponse;
import com.thinklab.application.mapper.ArticleMapper;
import com.thinklab.domain.exception.ArticleNotFoundException;
import com.thinklab.domain.repository.ArticleRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/** Use Case for the history of an Article: every version that shares its key, oldest first (BIAN Behavior Qualifier: {@code versions/retrieve}). Staff only. */
@Singleton
public class RetrieveArticleVersionsUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveArticleVersionsUseCase.class);

    private final ArticleRepository articleRepository;

    public RetrieveArticleVersionsUseCase(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    public Mono<List<ArticleResponse>> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving the versions of Article ID: {}", id);

        return Mono.fromRunnable(() -> ArticleWorkflow.requireStaff(role, "read the versions of an article"))
                .then(Mono.defer(() -> articleRepository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new ArticleNotFoundException(id)))
                .flatMap(article -> articleRepository.findByKey(article.getArticleKey(), organisationId)
                        .map(version -> ArticleMapper.toResponse(version, false))
                        .collectList());
    }
}
