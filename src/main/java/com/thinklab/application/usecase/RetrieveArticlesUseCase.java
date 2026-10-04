package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.ArticleResponse;
import com.thinklab.application.mapper.ArticleMapper;
import com.thinklab.domain.model.Article.ArticleStatus;
import com.thinklab.domain.model.Article.Visibility;
import com.thinklab.domain.repository.ArticleRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Use Case for searching the tenant's Articles (BIAN Behavior Qualifier: {@code retrieve}, collection). A REQUESTER is forced to
 * {@code PUBLISHED} and {@code PUBLIC} whatever they ask for, and so cannot see the people or the links (ADR-032).
 */
@Singleton
public class RetrieveArticlesUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveArticlesUseCase.class);

    private final ArticleRepository articleRepository;

    public RetrieveArticlesUseCase(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    public Flux<ArticleResponse> execute(UUID organisationId, ArticleRepository.Filter filter, String role) {
        log.info("[USE CASE] Searching Articles for organisation: {} role: {}", organisationId, role);

        boolean requester = ArticleWorkflow.isRequester(role);
        ArticleRepository.Filter effective = requester
                ? new ArticleRepository.Filter(filter.text(), ArticleStatus.PUBLISHED, filter.category(), Visibility.PUBLIC, filter.keyword(), filter.problemId(),
                        filter.incidentId(), null)
                : filter;
        return articleRepository.findAll(organisationId, effective).map(article -> ArticleMapper.toResponse(article, requester));
    }
}
