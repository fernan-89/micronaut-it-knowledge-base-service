package com.thinklab.application.usecase;

import com.thinklab.application.dto.request.InitiateArticleRequest;
import com.thinklab.application.dto.request.ReturnArticleRequest;
import com.thinklab.application.dto.request.UpdateArticleRequest;
import com.thinklab.domain.exception.ArticleAccessDeniedException;
import com.thinklab.domain.exception.ArticleNotFoundException;
import com.thinklab.domain.exception.DuplicateArticleException;
import com.thinklab.domain.exception.InvalidArticleStatusException;
import com.thinklab.domain.model.Article;
import com.thinklab.domain.model.Article.ArticleStatus;
import com.thinklab.domain.model.Article.Visibility;
import com.thinklab.domain.port.HashServicePort;
import com.thinklab.domain.repository.ArticleRepository;
import com.thinklab.domain.repository.ArticleRepository.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleUseCaseTest {

    private static final String REQUESTER = "REQUESTER";

    @Mock private ArticleRepository repository;
    @Mock private HashServicePort hashService;

    private final UUID org = UUID.randomUUID();
    private ArticleWorkflow workflow;

    @BeforeEach
    void setUp() {
        workflow = new ArticleWorkflow(repository);
    }

    private Article draft(Visibility visibility) {
        return Article.createNew(UUID.randomUUID(), org, "Switch drops packets", "Reboot it", "NETWORK", Set.of("switch"), visibility, Set.of(UUID.randomUUID()), Set.of(), "author-1");
    }

    private Article published(Visibility visibility) {
        Article article = draft(visibility);
        article.submit("author-1");
        article.publish("reviewer-1");
        return article;
    }

    private Article found(Article article) {
        when(repository.findById(article.getId(), org)).thenReturn(Mono.just(article));
        return article;
    }

    private UUID missing() {
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown, org)).thenReturn(Mono.empty());
        return unknown;
    }

    @Test
    @DisplayName("initiate drafts an article authored by the caller under a sovereign id; a REQUESTER is refused before anything is spent")
    void initiate() {
        UUID id = UUID.randomUUID();
        when(hashService.generateSovereignId("article-creation")).thenReturn(Mono.just(id));
        when(repository.create(any(Article.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        var request = new InitiateArticleRequest("Switch drops packets", "Reboot it", null, Set.of("switch"), Visibility.PUBLIC, null, null);
        InitiateArticleUseCase useCase = new InitiateArticleUseCase(hashService, repository);

        StepVerifier.create(useCase.execute(org, request, "author-1", null)).assertNext(created -> {
            assertEquals(id, created.id());
            assertEquals("DRAFT", created.status());
            assertEquals("author-1", created.authorId());
        }).verifyComplete();
        StepVerifier.create(useCase.execute(org, request, "author-1", REQUESTER)).expectError(ArticleAccessDeniedException.class).verify();
    }

    @Test
    @DisplayName("retrieve: staff read any article; a REQUESTER reads only a published public one, without the people and links; anything else is a 404")
    void retrieve() {
        Article draft = found(draft(Visibility.PUBLIC));
        Article internal = found(published(Visibility.INTERNAL));
        Article open = found(published(Visibility.PUBLIC));
        UUID unknownId = missing();
        RetrieveArticleUseCase useCase = new RetrieveArticleUseCase(repository);

        StepVerifier.create(useCase.execute(draft.getId(), org, null)).assertNext(response -> assertEquals("author-1", response.authorId())).verifyComplete();
        StepVerifier.create(useCase.execute(open.getId(), org, REQUESTER)).assertNext(response -> {
            assertNull(response.authorId());
            assertNull(response.relatedProblemIds());
        }).verifyComplete();
        StepVerifier.create(useCase.execute(draft.getId(), org, REQUESTER)).expectError(ArticleNotFoundException.class).verify();
        StepVerifier.create(useCase.execute(internal.getId(), org, REQUESTER)).expectError(ArticleNotFoundException.class).verify();
        StepVerifier.create(useCase.execute(unknownId, org, null)).expectError(ArticleNotFoundException.class).verify();
    }

    @Test
    @DisplayName("search: staff filters pass through; a REQUESTER is forced to PUBLISHED and PUBLIC and cannot filter by author")
    void search() {
        when(repository.findAll(eq(org), any(Filter.class))).thenReturn(Flux.just(published(Visibility.PUBLIC)));
        UUID problem = UUID.randomUUID();
        var filter = new Filter("firmware", ArticleStatus.DRAFT, "NETWORK", Visibility.INTERNAL, "switch", problem, null, "author-1");
        RetrieveArticlesUseCase useCase = new RetrieveArticlesUseCase(repository);

        StepVerifier.create(useCase.execute(org, filter, null)).expectNextCount(1).verifyComplete();
        StepVerifier.create(useCase.execute(org, filter, REQUESTER)).assertNext(response -> assertNull(response.authorId())).verifyComplete();

        ArgumentCaptor<Filter> captured = ArgumentCaptor.forClass(Filter.class);
        verify(repository, times(2)).findAll(eq(org), captured.capture());
        assertEquals(filter, captured.getAllValues().get(0));
        assertEquals(new Filter("firmware", ArticleStatus.PUBLISHED, "NETWORK", Visibility.PUBLIC, "switch", problem, null, null), captured.getAllValues().get(1));
    }

    @Test
    @DisplayName("versions lists every version sharing the key, for staff; an unknown article is a 404")
    void versions() {
        Article first = found(published(Visibility.PUBLIC));
        UUID unknownId = missing();
        when(repository.findByKey(first.getArticleKey(), org)).thenReturn(Flux.just(first, Article.nextVersionOf(first, UUID.randomUUID(), "author-2")));
        RetrieveArticleVersionsUseCase useCase = new RetrieveArticleVersionsUseCase(repository);

        StepVerifier.create(useCase.execute(first.getId(), org, null)).assertNext(list -> assertEquals(2, list.size())).verifyComplete();
        StepVerifier.create(useCase.execute(first.getId(), org, REQUESTER)).expectError(ArticleAccessDeniedException.class).verify();
        StepVerifier.create(useCase.execute(unknownId, org, null)).expectError(ArticleNotFoundException.class).verify();
    }

    @Test
    @DisplayName("update, submit and retire go through a guarded save with the status the article was loaded in")
    void editing() {
        Article article = found(draft(Visibility.PUBLIC));
        when(repository.save(any(), any(), any())).thenReturn(Mono.empty());
        ControlArticleUseCase control = new ControlArticleUseCase(workflow);

        StepVerifier.create(new UpdateArticleUseCase(workflow).execute(article.getId(), org,
                new UpdateArticleRequest("New title", "New body", null, Set.of("a"), Visibility.INTERNAL, Set.of(), Set.of()), "author-1", null)).verifyComplete();
        assertEquals("New title", article.getTitle());
        StepVerifier.create(control.execute(article.getId(), org, ControlArticleUseCase.Action.SUBMIT, "author-1", null)).verifyComplete();
        verify(repository, times(2)).save(eq(article), eq(ArticleStatus.DRAFT), any());
        StepVerifier.create(control.execute(article.getId(), org, ControlArticleUseCase.Action.RETIRE, "author-1", null)).verifyComplete();
        verify(repository).save(eq(article), eq(ArticleStatus.IN_REVIEW), any());
        assertEquals(ArticleStatus.RETIRED, article.getStatus());
    }

    @Test
    @DisplayName("staff actions refuse a REQUESTER, an unknown article and an illegal transition, and save nothing")
    void refusals() {
        Article article = found(draft(Visibility.PUBLIC));
        UUID unknownId = missing();
        ControlArticleUseCase control = new ControlArticleUseCase(workflow);

        StepVerifier.create(control.execute(article.getId(), org, ControlArticleUseCase.Action.SUBMIT, "a", REQUESTER)).expectError(ArticleAccessDeniedException.class).verify();
        StepVerifier.create(control.execute(unknownId, org, ControlArticleUseCase.Action.SUBMIT, "a", null)).expectError(ArticleNotFoundException.class).verify();
        article.submit("a");
        StepVerifier.create(control.execute(article.getId(), org, ControlArticleUseCase.Action.SUBMIT, "a", null)).expectError(InvalidArticleStatusException.class).verify();
        verify(repository, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("a reviewer other than the author publishes; the older published versions of the same article are then retired")
    void publishSupersedesOlderVersions() {
        Article older = published(Visibility.PUBLIC);
        Article newer = Article.nextVersionOf(older, UUID.randomUUID(), "author-2");
        newer.submit("author-2");
        found(newer);
        when(repository.save(any(), any(), any())).thenReturn(Mono.empty());
        Article alreadyRetired = published(Visibility.PUBLIC);
        alreadyRetired.retire("op-1");
        when(repository.findByKey(newer.getArticleKey(), org)).thenReturn(Flux.just(alreadyRetired, older, newer));
        PublishArticleUseCase useCase = new PublishArticleUseCase(workflow, repository);

        StepVerifier.create(useCase.execute(newer.getId(), org, "reviewer-1", null)).verifyComplete();
        verify(repository, never()).save(eq(alreadyRetired), any(), any());

        assertEquals(ArticleStatus.PUBLISHED, newer.getStatus());
        assertEquals(ArticleStatus.RETIRED, older.getStatus());
        verify(repository).save(eq(newer), eq(ArticleStatus.IN_REVIEW), any());
        verify(repository).save(eq(older), eq(ArticleStatus.PUBLISHED), any());
    }

    @Test
    @DisplayName("publishing a first version retires nothing; the author, a REQUESTER and an unknown article are refused")
    void publishRefusals() {
        Article first = draft(Visibility.PUBLIC);
        first.submit("author-1");
        found(first);
        UUID unknownId = missing();
        when(repository.save(any(), any(), any())).thenReturn(Mono.empty());
        when(repository.findByKey(first.getArticleKey(), org)).thenReturn(Flux.just(first));
        PublishArticleUseCase useCase = new PublishArticleUseCase(workflow, repository);

        StepVerifier.create(useCase.execute(first.getId(), org, "author-1", null)).expectError(InvalidArticleStatusException.class).verify();
        StepVerifier.create(useCase.execute(first.getId(), org, "reviewer-1", REQUESTER)).expectError(ArticleAccessDeniedException.class).verify();
        StepVerifier.create(useCase.execute(unknownId, org, "reviewer-1", null)).expectError(ArticleNotFoundException.class).verify();
        StepVerifier.create(useCase.execute(first.getId(), org, "reviewer-1", null)).verifyComplete();
        verify(repository, times(1)).save(any(), any(), any());
    }

    @Test
    @DisplayName("if an older version cannot be retired the publication still stands")
    void supersedeIsBestEffort() {
        Article older = published(Visibility.PUBLIC);
        Article newer = Article.nextVersionOf(older, UUID.randomUUID(), "author-2");
        newer.submit("author-2");
        found(newer);
        when(repository.save(eq(newer), any(), any())).thenReturn(Mono.empty());
        when(repository.save(eq(older), any(), any())).thenReturn(Mono.error(new InvalidArticleStatusException("lost the race")));
        when(repository.findByKey(newer.getArticleKey(), org)).thenReturn(Flux.just(older, newer));

        StepVerifier.create(new PublishArticleUseCase(workflow, repository).execute(newer.getId(), org, "reviewer-1", null)).verifyComplete();
        assertEquals(ArticleStatus.PUBLISHED, newer.getStatus());
    }

    @Test
    @DisplayName("a reviewer returns an article with a comment; the author cannot return their own, and the comment is mandatory")
    void returnForChanges() {
        Article article = draft(Visibility.PUBLIC);
        article.submit("author-1");
        found(article);
        when(repository.save(any(), any(), any())).thenReturn(Mono.empty());
        ReturnArticleUseCase useCase = new ReturnArticleUseCase(workflow);

        StepVerifier.create(useCase.execute(article.getId(), org, new ReturnArticleRequest("fix"), "author-1", null)).expectError(InvalidArticleStatusException.class).verify();
        StepVerifier.create(useCase.execute(article.getId(), org, new ReturnArticleRequest(" "), "reviewer-1", null)).expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(useCase.execute(article.getId(), org, new ReturnArticleRequest("Say which firmware"), "reviewer-1", null)).verifyComplete();
        assertEquals(ArticleStatus.DRAFT, article.getStatus());
        assertEquals("Say which firmware", article.getReviewComment());
        verify(repository, times(1)).save(any(), eq(ArticleStatus.IN_REVIEW), any());
    }

    @Test
    @DisplayName("a new version of a published article is created as a draft under a new sovereign id; the repository refuses a duplicate version")
    void startVersion() {
        Article first = found(published(Visibility.PUBLIC));
        UUID unknownId = missing();
        when(hashService.generateSovereignId("article-version-creation")).thenReturn(Mono.just(UUID.randomUUID()));
        when(repository.create(any(Article.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)))
                .thenReturn(Mono.error(new DuplicateArticleException("Version 2 of this article already exists")));
        StartArticleVersionUseCase useCase = new StartArticleVersionUseCase(hashService, repository);

        StepVerifier.create(useCase.execute(first.getId(), org, "author-2", null)).assertNext(created -> {
            assertEquals(2, created.version());
            assertEquals("DRAFT", created.status());
            assertEquals(first.getArticleKey(), created.articleKey());
            assertEquals("author-2", created.authorId());
        }).verifyComplete();
        StepVerifier.create(useCase.execute(first.getId(), org, "author-3", null)).expectError(DuplicateArticleException.class).verify();
        StepVerifier.create(useCase.execute(first.getId(), org, "a", REQUESTER)).expectError(ArticleAccessDeniedException.class).verify();
        StepVerifier.create(useCase.execute(unknownId, org, "a", null)).expectError(ArticleNotFoundException.class).verify();
    }

    @Test
    @DisplayName("only a published article can have a new version")
    void startVersionNeedsPublished() {
        Article draft = found(draft(Visibility.PUBLIC));
        when(hashService.generateSovereignId("article-version-creation")).thenReturn(Mono.just(UUID.randomUUID()));

        StepVerifier.create(new StartArticleVersionUseCase(hashService, repository).execute(draft.getId(), org, "a", null)).expectError(InvalidArticleStatusException.class).verify();
        verify(repository, never()).create(any());
    }

    @Test
    @DisplayName("the audit log is for staff; an unknown article is a 404")
    void auditLog() {
        Article article = found(draft(Visibility.PUBLIC));
        UUID unknownId = missing();
        RetrieveArticleAuditLogUseCase useCase = new RetrieveArticleAuditLogUseCase(repository);

        StepVerifier.create(useCase.execute(article.getId(), org, null)).assertNext(log -> assertEquals(1, log.size())).verifyComplete();
        StepVerifier.create(useCase.execute(article.getId(), org, REQUESTER)).expectError(ArticleAccessDeniedException.class).verify();
        StepVerifier.create(useCase.execute(unknownId, org, "ADMIN")).expectError(ArticleNotFoundException.class).verify();
        verifyNoInteractions(hashService);
    }
}
