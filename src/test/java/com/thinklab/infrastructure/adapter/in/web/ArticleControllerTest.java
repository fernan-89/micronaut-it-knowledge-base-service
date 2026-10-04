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
import com.thinklab.domain.repository.ArticleRepository.Filter;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The controller only reads headers and delegates: tenant and role always travel to the use case. */
@ExtendWith(MockitoExtension.class)
class ArticleControllerTest {

    private static final String EXECUTOR = "op-1";
    private final UUID tenant = UUID.randomUUID();
    private final String tenantHeader = tenant.toString();
    private final UUID id = UUID.randomUUID();

    @Mock private InitiateArticleUseCase initiateArticleUseCase;
    @Mock private RetrieveArticleUseCase retrieveArticleUseCase;
    @Mock private RetrieveArticlesUseCase retrieveArticlesUseCase;
    @Mock private RetrieveArticleVersionsUseCase retrieveArticleVersionsUseCase;
    @Mock private UpdateArticleUseCase updateArticleUseCase;
    @Mock private ControlArticleUseCase controlArticleUseCase;
    @Mock private PublishArticleUseCase publishArticleUseCase;
    @Mock private ReturnArticleUseCase returnArticleUseCase;
    @Mock private StartArticleVersionUseCase startArticleVersionUseCase;
    @Mock private RetrieveArticleAuditLogUseCase retrieveArticleAuditLogUseCase;

    private ArticleController controller;

    @BeforeEach
    void setUp() {
        controller = new ArticleController(initiateArticleUseCase, retrieveArticleUseCase, retrieveArticlesUseCase, retrieveArticleVersionsUseCase,
                updateArticleUseCase, controlArticleUseCase, publishArticleUseCase, returnArticleUseCase, startArticleVersionUseCase, retrieveArticleAuditLogUseCase);
    }

    private ArticleResponse sample() {
        return new ArticleResponse(id, tenant, id, 1, "t", "b", null, Set.of(), "PUBLIC", "DRAFT", "op", null, null, null, Set.of(), Set.of(), Instant.now(), Instant.now());
    }

    @Test
    @DisplayName("initiate and a new version answer 201 with the drafted article")
    void creating() {
        var body = new InitiateArticleRequest("t", "b", null, null, Visibility.PUBLIC, null, null);
        when(initiateArticleUseCase.execute(tenant, body, EXECUTOR, "ADMIN")).thenReturn(Mono.just(sample()));
        when(startArticleVersionUseCase.execute(id, tenant, EXECUTOR, null)).thenReturn(Mono.just(sample()));

        StepVerifier.create(controller.initiate(tenantHeader, EXECUTOR, "ADMIN", body)).assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus())).verifyComplete();
        StepVerifier.create(controller.initiateVersion(id, tenantHeader, EXECUTOR, null)).assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus())).verifyComplete();
    }

    @Test
    @DisplayName("retrieve (one, search, versions) carries tenant and role; a blank search text is no search")
    void retrieve() {
        UUID anyId = UUID.randomUUID();
        when(retrieveArticleUseCase.execute(id, tenant, "REQUESTER")).thenReturn(Mono.just(sample()));
        when(retrieveArticlesUseCase.execute(eq(tenant), any(Filter.class), eq(null))).thenReturn(Flux.just(sample(), sample()));
        when(retrieveArticleVersionsUseCase.execute(id, tenant, null)).thenReturn(Mono.just(List.of(sample())));

        StepVerifier.create(controller.retrieveById(id, tenantHeader, "REQUESTER")).assertNext(response -> assertEquals(HttpStatus.OK, response.getStatus())).verifyComplete();
        StepVerifier.create(controller.retrieveAll(tenantHeader, null, " firmware ", ArticleStatus.PUBLISHED, "NETWORK", Visibility.PUBLIC, "switch", anyId, anyId, "op"))
                .assertNext(list -> assertEquals(2, list.size())).verifyComplete();
        StepVerifier.create(controller.retrieveAll(tenantHeader, null, "  ", null, null, null, null, null, null, null)).assertNext(list -> assertEquals(2, list.size())).verifyComplete();
        StepVerifier.create(controller.retrieveAll(tenantHeader, null, null, null, null, null, null, null, null, null)).assertNext(list -> assertEquals(2, list.size())).verifyComplete();
        StepVerifier.create(controller.retrieveVersions(id, tenantHeader, null)).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
        verify(retrieveArticlesUseCase).execute(tenant, new Filter("firmware", ArticleStatus.PUBLISHED, "NETWORK", Visibility.PUBLIC, "switch", anyId, anyId, "op"), null);
        verify(retrieveArticlesUseCase, org.mockito.Mockito.times(2)).execute(tenant, new Filter(null, null, null, null, null, null, null, null), null);
    }

    @Test
    @DisplayName("update, submit, publish, return and retire answer 204 with the right action")
    void commands() {
        var update = new UpdateArticleRequest("t", "b", null, null, Visibility.PUBLIC, null, null);
        var ret = new ReturnArticleRequest("fix");
        when(updateArticleUseCase.execute(id, tenant, update, EXECUTOR, null)).thenReturn(Mono.empty());
        when(returnArticleUseCase.execute(id, tenant, ret, EXECUTOR, null)).thenReturn(Mono.empty());
        when(publishArticleUseCase.execute(id, tenant, EXECUTOR, null)).thenReturn(Mono.empty());
        when(controlArticleUseCase.execute(eq(id), eq(tenant), any(ControlArticleUseCase.Action.class), eq(EXECUTOR), eq(null))).thenReturn(Mono.empty());

        StepVerifier.create(controller.update(id, tenantHeader, EXECUTOR, null, update)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller.controlSubmit(id, tenantHeader, EXECUTOR, null)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller.controlPublish(id, tenantHeader, EXECUTOR, null)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller.controlReturn(id, tenantHeader, EXECUTOR, null, ret)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        StepVerifier.create(controller.controlRetire(id, tenantHeader, EXECUTOR, null)).assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus())).verifyComplete();
        verify(controlArticleUseCase).execute(id, tenant, ControlArticleUseCase.Action.SUBMIT, EXECUTOR, null);
        verify(controlArticleUseCase).execute(id, tenant, ControlArticleUseCase.Action.RETIRE, EXECUTOR, null);
    }

    @Test
    @DisplayName("the audit log is returned as one list")
    void auditLog() {
        when(retrieveArticleAuditLogUseCase.execute(id, tenant, "ADMIN")).thenReturn(Mono.just(List.of(new AuditEntryResponse(Instant.now(), "INITIATED", "op", null, "DRAFT", "d"))));

        StepVerifier.create(controller.retrieveAuditLog(id, tenantHeader, "ADMIN")).assertNext(list -> assertEquals(1, list.size())).verifyComplete();
    }
}
