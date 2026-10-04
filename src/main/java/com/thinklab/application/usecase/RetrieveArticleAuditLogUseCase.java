package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.mapper.ArticleMapper;
import com.thinklab.domain.exception.ArticleNotFoundException;
import com.thinklab.domain.repository.ArticleRepository;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Use Case for the forensic ledger of an Article (BIAN Behavior Qualifier: {@code audit-log/retrieve}). Staff only. */
@Singleton
public class RetrieveArticleAuditLogUseCase {

    private static final Logger log = LoggerFactory.getLogger(RetrieveArticleAuditLogUseCase.class);

    private final ArticleRepository articleRepository;

    public RetrieveArticleAuditLogUseCase(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    public Mono<List<AuditEntryResponse>> execute(UUID id, UUID organisationId, String role) {
        log.info("[USE CASE] Retrieving the audit log of Article ID: {}", id);

        return Mono.fromRunnable(() -> ArticleWorkflow.requireStaff(role, "read the audit trail of an article"))
                .then(Mono.defer(() -> articleRepository.findById(id, organisationId)))
                .switchIfEmpty(Mono.error(new ArticleNotFoundException(id)))
                .map(article -> article.getAuditTrail().stream().map(ArticleMapper::toResponse).collect(Collectors.toList()));
    }
}
