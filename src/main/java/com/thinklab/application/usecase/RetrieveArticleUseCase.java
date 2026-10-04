package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.ArticleResponse;
import com.thinklab.application.mapper.ArticleMapper;
import com.thinklab.domain.exception.ArticleNotFoundException;
import com.thinklab.domain.repository.ArticleRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case for reading one Article of the tenant (BIAN Behavior Qualifier: {@code retrieve}). Staff read any; a REQUESTER reads only a
 * published public article (anything else is a 404, so a draft or an internal article is not even confirmed to exist) and sees it without
 * the people and links (ADR-032).
 */
@Singleton
public class RetrieveArticleUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveArticleUseCase.class);

    private final ArticleRepository articleRepository;

    public RetrieveArticleUseCase(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    public Mono<ArticleResponse> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving Article by ID: {}", id);

        boolean requester = ArticleWorkflow.isRequester(role);
        return articleRepository.findById(id, organisationId)
                .filter(article -> !requester || article.isReadableByRequesters())
                .switchIfEmpty(Mono.error(new ArticleNotFoundException(id)))
                .map(article -> ArticleMapper.toResponse(article, requester));
    }
}
