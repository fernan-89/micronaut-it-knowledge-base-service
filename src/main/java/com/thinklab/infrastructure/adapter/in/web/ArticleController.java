package com.thinklab.infrastructure.adapter.in.web;

import com.thinklab.application.dto.request.InitiateArticleRequest;
import com.thinklab.application.dto.request.ReturnArticleRequest;
import com.thinklab.application.dto.request.UpdateArticleRequest;
import com.thinklab.application.dto.response.ArticleResponse;
import com.thinklab.application.dto.response.AuditEntryResponse;
import com.thinklab.application.usecase.ControlArticleUseCase;
import com.thinklab.application.usecase.InitiateArticleUseCase;
import com.thinklab.application.usecase.PublishArticleUseCase;
import com.thinklab.application.usecase.RetrieveArticleAuditLogUseCase;
import com.thinklab.application.usecase.RetrieveArticleUseCase;
import com.thinklab.application.usecase.RetrieveArticleVersionsUseCase;
import com.thinklab.application.usecase.RetrieveArticlesUseCase;
import com.thinklab.application.usecase.ReturnArticleUseCase;
import com.thinklab.application.usecase.StartArticleVersionUseCase;
import com.thinklab.application.usecase.UpdateArticleUseCase;
import com.thinklab.domain.model.Article.ArticleStatus;
import com.thinklab.domain.model.Article.Visibility;
import com.thinklab.domain.repository.ArticleRepository;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Inbound Web Adapter for the {@code it-knowledge-base} Service Domain.
 *
 * <p><b>BIAN-Aligned Resource Model (ADR-013):</b> {@link com.thinklab.domain.model.Article} is the Control Record. Every route follows
 * {@code /it-knowledge-base/v1/{control-record-id}/{behavior-qualifier}}. There is no {@code DELETE}: {@code control/retire} is a terminal,
 * soft status transition.
 *
 * <p><b>Tenant on every route, two audiences (ADR-032):</b> {@code X-Tenant-Id} is mandatory everywhere and scopes every lookup.
 * {@code X-Role}, set by the kit's {@code SecurityFilter} from the verified token when security is on, narrows a {@code REQUESTER} to
 * reading published public articles; every other route refuses them with 403 {@code ERR-KNB-00403}.
 */
@Controller("/it-knowledge-base/v1")
public class ArticleController {

    private static final Logger log = LoggerFactory.getLogger(ArticleController.class);
    static final String TENANT_HEADER = "X-Tenant-Id";
    static final String EXECUTOR_HEADER = "X-Executor";
    static final String ROLE_HEADER = "X-Role";

    private final InitiateArticleUseCase initiateArticleUseCase;
    private final RetrieveArticleUseCase retrieveArticleUseCase;
    private final RetrieveArticlesUseCase retrieveArticlesUseCase;
    private final RetrieveArticleVersionsUseCase retrieveArticleVersionsUseCase;
    private final UpdateArticleUseCase updateArticleUseCase;
    private final ControlArticleUseCase controlArticleUseCase;
    private final PublishArticleUseCase publishArticleUseCase;
    private final ReturnArticleUseCase returnArticleUseCase;
    private final StartArticleVersionUseCase startArticleVersionUseCase;
    private final RetrieveArticleAuditLogUseCase retrieveArticleAuditLogUseCase;

    public ArticleController(
            InitiateArticleUseCase initiateArticleUseCase,
            RetrieveArticleUseCase retrieveArticleUseCase,
            RetrieveArticlesUseCase retrieveArticlesUseCase,
            RetrieveArticleVersionsUseCase retrieveArticleVersionsUseCase,
            UpdateArticleUseCase updateArticleUseCase,
            ControlArticleUseCase controlArticleUseCase,
            PublishArticleUseCase publishArticleUseCase,
            ReturnArticleUseCase returnArticleUseCase,
            StartArticleVersionUseCase startArticleVersionUseCase,
            RetrieveArticleAuditLogUseCase retrieveArticleAuditLogUseCase
    ) {
        this.initiateArticleUseCase = initiateArticleUseCase;
        this.retrieveArticleUseCase = retrieveArticleUseCase;
        this.retrieveArticlesUseCase = retrieveArticlesUseCase;
        this.retrieveArticleVersionsUseCase = retrieveArticleVersionsUseCase;
        this.updateArticleUseCase = updateArticleUseCase;
        this.controlArticleUseCase = controlArticleUseCase;
        this.publishArticleUseCase = publishArticleUseCase;
        this.returnArticleUseCase = returnArticleUseCase;
        this.startArticleVersionUseCase = startArticleVersionUseCase;
        this.retrieveArticleAuditLogUseCase = retrieveArticleAuditLogUseCase;
    }

    /** Behavior Qualifier: {@code initiate}. Drafts a new Article; the author is the caller. */
    @Post("/initiate")
    public Mono<HttpResponse<ArticleResponse>> initiate(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role,
            @Body @Valid InitiateArticleRequest request
    ) {
        log.info("[ACTION: INITIATE_ARTICLE] [EXECUTOR: {}] Received request for organisation: {}", executor, tenantId);

        return initiateArticleUseCase.execute(UUID.fromString(tenantId), request, executor, role).map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code retrieve}. A REQUESTER reads only a published public article. */
    @Get("/{id}/retrieve")
    public Mono<HttpResponse<ArticleResponse>> retrieveById(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(ROLE_HEADER) @Nullable String role
    ) {
        log.info("[ACTION: RETRIEVE_ARTICLE] Received request to get Article by ID: {}", id);

        return Mono.defer(() -> retrieveArticleUseCase.execute(id, UUID.fromString(tenantId), role)).map(HttpResponse::ok);
    }

    /** Behavior Qualifier: {@code retrieve} (collection). {@code q} is a free-text search; a REQUESTER is forced to published public articles. */
    @Get("/retrieve")
    public Mono<List<ArticleResponse>> retrieveAll(
            @Header(TENANT_HEADER) @NotBlank String tenantId,
            @Header(ROLE_HEADER) @Nullable String role,
            @QueryValue @Nullable String q,
            @QueryValue @Nullable ArticleStatus status,
            @QueryValue @Nullable String category,
            @QueryValue @Nullable Visibility visibility,
            @QueryValue @Nullable String keyword,
            @QueryValue @Nullable UUID problemId,
            @QueryValue @Nullable UUID incidentId,
            @QueryValue @Nullable String authorId
    ) {
        log.info("[ACTION: RETRIEVE_ARTICLES] Received request to search Articles for organisation: {} q: {} status: {}", tenantId, q, status);

        String text = q == null || q.isBlank() ? null : q.trim();
        var filter = new ArticleRepository.Filter(text, status, category, visibility, keyword, problemId, incidentId, authorId);
        return Mono.defer(() -> retrieveArticlesUseCase.execute(UUID.fromString(tenantId), filter, role).collectList());
    }

    /** Behavior Qualifier: {@code versions/retrieve}. Every version of the article, oldest first (staff). */
    @Get("/{id}/versions/retrieve")
    public Mono<List<ArticleResponse>> retrieveVersions(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                        @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> retrieveArticleVersionsUseCase.execute(id, UUID.fromString(tenantId), role));
    }

    /** Behavior Qualifier: {@code update}. Content and links, only while the article is a DRAFT. */
    @Put("/{id}/update")
    public Mono<HttpResponse<Void>> update(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid UpdateArticleRequest request
    ) {
        return Mono.defer(() -> updateArticleUseCase.execute(id, UUID.fromString(tenantId), request, executor, role)).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/submit}. DRAFT -&gt; IN_REVIEW. */
    @Put("/{id}/control/submit")
    public Mono<HttpResponse<Void>> controlSubmit(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                  @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlArticleUseCase.Action.SUBMIT, executor, role);
    }

    /** Behavior Qualifier: {@code control/publish}. IN_REVIEW -&gt; PUBLISHED, by somebody other than the author. */
    @Put("/{id}/control/publish")
    public Mono<HttpResponse<Void>> controlPublish(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                   @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        log.info("[ACTION: PUBLISH_ARTICLE] [EXECUTOR: {}] for ID: {}", executor, id);

        return Mono.defer(() -> publishArticleUseCase.execute(id, UUID.fromString(tenantId), executor, role)).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/return}. IN_REVIEW -&gt; DRAFT with a comment, by somebody other than the author. */
    @Put("/{id}/control/return")
    public Mono<HttpResponse<Void>> controlReturn(
            @PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId, @Header(EXECUTOR_HEADER) @NotBlank String executor,
            @Header(ROLE_HEADER) @Nullable String role, @Body @Valid ReturnArticleRequest request
    ) {
        return Mono.defer(() -> returnArticleUseCase.execute(id, UUID.fromString(tenantId), request, executor, role)).thenReturn(HttpResponse.noContent());
    }

    /** Behavior Qualifier: {@code control/retire}. Terminal, replaces DELETE. */
    @Put("/{id}/control/retire")
    public Mono<HttpResponse<Void>> controlRetire(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                  @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        return control(id, tenantId, ControlArticleUseCase.Action.RETIRE, executor, role);
    }

    /** Behavior Qualifier: {@code version/initiate}. Starts a new draft version of a PUBLISHED article. */
    @Post(value = "/{id}/version/initiate", consumes = MediaType.ALL)
    public Mono<HttpResponse<ArticleResponse>> initiateVersion(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                               @Header(EXECUTOR_HEADER) @NotBlank String executor, @Header(ROLE_HEADER) @Nullable String role) {
        log.info("[ACTION: START_ARTICLE_VERSION] [EXECUTOR: {}] for ID: {}", executor, id);

        return Mono.defer(() -> startArticleVersionUseCase.execute(id, UUID.fromString(tenantId), executor, role)).map(HttpResponse::created);
    }

    /** Behavior Qualifier: {@code audit-log/retrieve}. Immutable forensic ledger of the Article (staff). */
    @Get("/{id}/audit-log/retrieve")
    public Mono<List<AuditEntryResponse>> retrieveAuditLog(@PathVariable UUID id, @Header(TENANT_HEADER) @NotBlank String tenantId,
                                                           @Header(ROLE_HEADER) @Nullable String role) {
        return Mono.defer(() -> retrieveArticleAuditLogUseCase.execute(id, UUID.fromString(tenantId), role));
    }

    private Mono<HttpResponse<Void>> control(UUID id, String tenantId, ControlArticleUseCase.Action action, String executor, String role) {
        log.info("[ACTION: CONTROL_ARTICLE] [EXECUTOR: {}] {} for ID: {}", executor, action, id);

        return Mono.defer(() -> controlArticleUseCase.execute(id, UUID.fromString(tenantId), action, executor, role)).thenReturn(HttpResponse.noContent());
    }
}
