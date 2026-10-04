package com.thinklab.application.usecase;

import com.thinklab.domain.exception.ArticleNotFoundException;
import com.thinklab.domain.model.Article;
import com.thinklab.domain.model.Article.ArticleStatus;
import com.thinklab.domain.repository.ArticleRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Use Case for publishing an Article in review (BIAN Behavior Qualifier: {@code control/publish}). The reviewer (the caller) cannot be the
 * author. Publishing a newer version then retires the older published versions of the same article (SUPERSEDED): that second step is
 * best effort (ADR-030), so a failure never undoes the publication, it is logged and the older version can be retired by hand.
 */
@Singleton
public class PublishArticleUseCase {

    private static final Logger log = LoggerFactory.getLogger(PublishArticleUseCase.class);

    private final ArticleWorkflow workflow;
    private final ArticleRepository articleRepository;

    public PublishArticleUseCase(ArticleWorkflow workflow, ArticleRepository articleRepository) {
        this.workflow = workflow;
        this.articleRepository = articleRepository;
    }

    public Mono<Void> execute(UUID id, UUID organisationId, String executor, String role) {
        log.info("[USE CASE] Publishing Article ID: {}", id);

        return workflow.apply(id, organisationId, role, "publish an article", article -> article.publish(executor))
                .then(Mono.defer(() -> supersedeOlderVersions(id, organisationId, executor)));
    }

    private Mono<Void> supersedeOlderVersions(UUID id, UUID organisationId, String executor) {
        return articleRepository.findById(id, organisationId)
                .switchIfEmpty(Mono.error(new ArticleNotFoundException(id)))
                .flatMapMany(published -> articleRepository.findByKey(published.getArticleKey(), organisationId)
                        .filter(other -> other.getVersion() < published.getVersion() && other.getStatus() == ArticleStatus.PUBLISHED))
                .concatMap(older -> retire(older, executor))
                .onErrorResume(failure -> {
                    log.warn("[USE CASE] Article {} was published but its older versions could not be retired: {}", id, failure.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> retire(Article older, String executor) {
        var entry = older.supersede(executor);
        return articleRepository.save(older, ArticleStatus.PUBLISHED, entry);
    }
}
