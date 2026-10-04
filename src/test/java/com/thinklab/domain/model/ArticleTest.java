package com.thinklab.domain.model;

import com.thinklab.domain.exception.InvalidArticleStatusException;
import com.thinklab.domain.model.Article.ArticleStatus;
import com.thinklab.domain.model.Article.Visibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleTest {

    private final UUID org = UUID.randomUUID();

    private Article draft() {
        return Article.createNew(UUID.randomUUID(), org, "Switch drops packets", "Reboot it every Sunday", "NETWORK", Set.of("Switch", " Firmware "), Visibility.PUBLIC,
                Set.of(UUID.randomUUID()), Set.of(UUID.randomUUID()), "author-1");
    }

    private Article inReview() {
        Article article = draft();
        article.submit("author-1");
        return article;
    }

    private Article published() {
        Article article = inReview();
        article.publish("reviewer-1");
        return article;
    }

    @Test
    @DisplayName("a new article is version 1 of its own key, a DRAFT, with normalised keywords and an INITIATED audit entry")
    void createNew() {
        Article article = draft();

        assertEquals(article.getId(), article.getArticleKey());
        assertEquals(1, article.getVersion());
        assertEquals(ArticleStatus.DRAFT, article.getStatus());
        assertEquals(Set.of("switch", "firmware"), article.getKeywords());
        assertEquals("author-1", article.getAuthorId());
        assertEquals(1, article.getAuditTrail().size());
        assertNull(article.getAuditTrail().get(0).fromStatus());
        assertNotNull(article.getCreatedAt());
    }

    @Test
    @DisplayName("creation refuses missing ids, a blank title or body, no visibility, bad or too many keywords, too many links and a blank author")
    void createGuards() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> Article.createNew(null, org, "t", "b", null, null, Visibility.PUBLIC, null, null, "a"));
        assertThrows(IllegalArgumentException.class, () -> Article.createNew(id, null, "t", "b", null, null, Visibility.PUBLIC, null, null, "a"));
        assertThrows(IllegalArgumentException.class, () -> Article.createNew(id, org, null, "b", null, null, Visibility.PUBLIC, null, null, "a"));
        assertThrows(IllegalArgumentException.class, () -> Article.createNew(id, org, " ", "b", null, null, Visibility.PUBLIC, null, null, "a"));
        assertThrows(IllegalArgumentException.class, () -> Article.createNew(id, org, "t", null, null, null, Visibility.PUBLIC, null, null, "a"));
        assertThrows(IllegalArgumentException.class, () -> Article.createNew(id, org, "t", " ", null, null, Visibility.PUBLIC, null, null, "a"));
        assertThrows(IllegalArgumentException.class, () -> Article.createNew(id, org, "t", "b", null, null, null, null, null, "a"));
        Set<String> blankKeyword = new HashSet<>(List.of(" "));
        assertThrows(IllegalArgumentException.class, () -> Article.createNew(id, org, "t", "b", null, blankKeyword, Visibility.PUBLIC, null, null, "a"));
        Set<String> nullKeyword = new HashSet<>();
        nullKeyword.add(null);
        assertThrows(IllegalArgumentException.class, () -> Article.createNew(id, org, "t", "b", null, nullKeyword, Visibility.PUBLIC, null, null, "a"));
        assertThrows(IllegalArgumentException.class, () -> Article.createNew(id, org, "t", "b", null, Set.of("x".repeat(Article.MAX_KEYWORD_LENGTH + 1)), Visibility.PUBLIC, null, null, "a"));
        Set<String> tooManyKeywords = new LinkedHashSet<>();
        for (int i = 0; i <= Article.MAX_KEYWORDS; i++) {
            tooManyKeywords.add("k" + i);
        }
        assertThrows(IllegalArgumentException.class, () -> Article.createNew(id, org, "t", "b", null, tooManyKeywords, Visibility.PUBLIC, null, null, "a"));
        Set<UUID> tooManyLinks = new HashSet<>();
        for (int i = 0; i <= Article.MAX_LINKS; i++) {
            tooManyLinks.add(UUID.randomUUID());
        }
        assertThrows(IllegalArgumentException.class, () -> Article.createNew(id, org, "t", "b", null, null, Visibility.PUBLIC, tooManyLinks, null, "a"));
        assertThrows(IllegalArgumentException.class, () -> Article.createNew(id, org, "t", "b", null, null, Visibility.PUBLIC, null, null, " "));
        assertThrows(IllegalArgumentException.class, () -> Article.createNew(id, org, "t", "b", null, null, Visibility.PUBLIC, null, null, null));
        assertEquals(1, Article.createNew(id, org, "t", "b", null, Set.of("x".repeat(Article.MAX_KEYWORD_LENGTH)), Visibility.INTERNAL, null, null, "a").getVersion());
    }

    @Test
    @DisplayName("content is editable only in DRAFT and validated like on creation")
    void update() {
        Article article = draft();
        UUID problem = UUID.randomUUID();

        var entry = article.updateContent("New title", "New body", "OPS", Set.of("Reboot"), Visibility.INTERNAL, Set.of(problem), Set.of(), "author-1");

        assertEquals("UPDATED", entry.action());
        assertEquals(Visibility.INTERNAL, article.getVisibility());
        assertEquals(Set.of("reboot"), article.getKeywords());
        assertEquals(Set.of(problem), article.getRelatedProblemIds());
        assertTrue(article.getRelatedIncidentIds().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> article.updateContent(" ", "b", null, null, Visibility.PUBLIC, null, null, "a"));
        assertThrows(IllegalArgumentException.class, () -> article.updateContent("t", "b", null, null, Visibility.PUBLIC, null, null, " "));
        article.submit("author-1");
        assertThrows(InvalidArticleStatusException.class, () -> article.updateContent("t", "b", null, null, Visibility.PUBLIC, null, null, "a"));
    }

    @Test
    @DisplayName("a reviewer other than the author publishes: DRAFT -> IN_REVIEW -> PUBLISHED, recording who and when")
    void publish() {
        Article article = draft();
        assertThrows(InvalidArticleStatusException.class, () -> article.publish("reviewer-1"));
        article.submit("author-1");
        assertThrows(InvalidArticleStatusException.class, () -> article.submit("author-1"));

        var entry = article.publish("reviewer-1");

        assertEquals(ArticleStatus.PUBLISHED, article.getStatus());
        assertEquals(ArticleStatus.IN_REVIEW, entry.fromStatus());
        assertEquals("reviewer-1", article.getReviewerId());
        assertNotNull(article.getPublishedAt());
        assertTrue(article.isReadableByRequesters());
    }

    @Test
    @DisplayName("the author cannot publish or return their own article (segregation of duties), and a blank reviewer is refused")
    void segregationOfDuties() {
        Article article = inReview();

        InvalidArticleStatusException publish = assertThrows(InvalidArticleStatusException.class, () -> article.publish("author-1"));
        assertTrue(publish.getMessage().contains("Compliance Violation"));
        assertThrows(InvalidArticleStatusException.class, () -> article.returnForChanges("fix it", "author-1"));
        assertThrows(IllegalArgumentException.class, () -> article.publish(" "));
        assertThrows(IllegalArgumentException.class, () -> article.publish(null));
        assertEquals(ArticleStatus.IN_REVIEW, article.getStatus());
    }

    @Test
    @DisplayName("a reviewer returns an article with a mandatory comment; it goes back to DRAFT, can be edited, and resubmitting drops the comment")
    void returnForChanges() {
        Article article = inReview();
        assertThrows(IllegalArgumentException.class, () -> article.returnForChanges(null, "reviewer-1"));
        assertThrows(IllegalArgumentException.class, () -> article.returnForChanges(" ", "reviewer-1"));
        assertEquals(ArticleStatus.IN_REVIEW, article.getStatus());

        var entry = article.returnForChanges("Say which firmware", "reviewer-1");

        assertEquals(ArticleStatus.DRAFT, article.getStatus());
        assertEquals("Say which firmware", article.getReviewComment());
        assertTrue(entry.detail().contains("Say which firmware"));
        assertThrows(InvalidArticleStatusException.class, () -> article.returnForChanges("again", "reviewer-1"));
        article.submit("author-1");
        assertNull(article.getReviewComment());
    }

    @Test
    @DisplayName("a draft, an article in review and a published one can be retired (terminal); a retired one cannot")
    void retire() {
        for (Article article : List.of(draft(), inReview(), published())) {
            article.retire("op-1");
            assertEquals(ArticleStatus.RETIRED, article.getStatus());
            assertFalse(article.isReadableByRequesters());
            assertThrows(InvalidArticleStatusException.class, () -> article.retire("op-1"));
            assertThrows(InvalidArticleStatusException.class, () -> article.submit("op-1"));
        }
    }

    @Test
    @DisplayName("an internal article is never readable by requesters, even published; a draft is not either")
    void readableByRequesters() {
        Article internal = Article.createNew(UUID.randomUUID(), org, "t", "b", null, null, Visibility.INTERNAL, null, null, "author-1");
        assertFalse(internal.isReadableByRequesters());
        internal.submit("author-1");
        internal.publish("reviewer-1");
        assertFalse(internal.isReadableByRequesters());
        assertFalse(draft().isReadableByRequesters());
    }

    @Test
    @DisplayName("a new version is a new draft with the same key and content, authored by whoever asked; only a PUBLISHED article can have one")
    void nextVersion() {
        Article first = published();
        UUID newId = UUID.randomUUID();

        Article second = Article.nextVersionOf(first, newId, "reviewer-1");

        assertEquals(first.getArticleKey(), second.getArticleKey());
        assertEquals(2, second.getVersion());
        assertEquals(ArticleStatus.DRAFT, second.getStatus());
        assertEquals("reviewer-1", second.getAuthorId());
        assertEquals(first.getTitle(), second.getTitle());
        assertEquals(first.getKeywords(), second.getKeywords());
        assertEquals(first.getRelatedProblemIds(), second.getRelatedProblemIds());
        assertTrue(second.getAuditTrail().get(0).detail().contains("Version 2"));
        assertEquals(ArticleStatus.PUBLISHED, first.getStatus());
        assertThrows(NullPointerException.class, () -> Article.nextVersionOf(null, newId, "a"));
        assertThrows(IllegalArgumentException.class, () -> Article.nextVersionOf(first, null, "a"));
        assertThrows(IllegalArgumentException.class, () -> Article.nextVersionOf(first, newId, " "));
        assertThrows(InvalidArticleStatusException.class, () -> Article.nextVersionOf(draft(), newId, "a"));
    }

    @Test
    @DisplayName("supersede retires a PUBLISHED article and records it as SUPERSEDED; nothing else can be superseded")
    void supersede() {
        Article article = published();

        var entry = article.supersede("reviewer-2");

        assertEquals("SUPERSEDED", entry.action());
        assertEquals(ArticleStatus.RETIRED, article.getStatus());
        assertThrows(InvalidArticleStatusException.class, () -> article.supersede("reviewer-2"));
    }

    @Test
    @DisplayName("every mutation needs an executor")
    void executorRequired() {
        assertThrows(IllegalArgumentException.class, () -> draft().submit(" "));
        assertThrows(IllegalArgumentException.class, () -> draft().retire(null));
    }

    @Test
    @DisplayName("reconstitute needs the identity and defaults the optional state")
    void reconstitute() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> Article.reconstitute(null, org, id, 1, "t", "b", null, null, Visibility.PUBLIC, null, "a", null, null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Article.reconstitute(id, null, id, 1, "t", "b", null, null, Visibility.PUBLIC, null, "a", null, null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Article.reconstitute(id, org, null, 1, "t", "b", null, null, Visibility.PUBLIC, null, "a", null, null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Article.reconstitute(id, org, id, 1, null, "b", null, null, Visibility.PUBLIC, null, "a", null, null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Article.reconstitute(id, org, id, 1, "t", "b", null, null, null, null, "a", null, null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> Article.reconstitute(id, org, id, 1, "t", "b", null, null, Visibility.PUBLIC, null, null, null, null, null, null, null, null, null, null));

        Article bare = Article.reconstitute(id, org, id, 1, "t", "b", null, null, Visibility.PUBLIC, null, "a", null, null, null, null, null, null, null, null);
        assertEquals(ArticleStatus.DRAFT, bare.getStatus());
        assertTrue(bare.getKeywords().isEmpty());
        assertTrue(bare.getRelatedProblemIds().isEmpty());
        assertTrue(bare.getAuditTrail().isEmpty());
        assertEquals(bare.getCreatedAt(), bare.getUpdatedAt());

        Article source = published();
        Article full = Article.reconstitute(source.getId(), org, source.getArticleKey(), 3, "t", "b", "OPS", Set.of("k"), Visibility.INTERNAL, ArticleStatus.IN_REVIEW,
                "a", "r", "comment", Instant.parse("2026-10-04T10:00:00Z"), Set.of(UUID.randomUUID()), Set.of(UUID.randomUUID()), source.getCreatedAt(),
                source.getUpdatedAt(), source.getAuditTrail());
        assertEquals(ArticleStatus.IN_REVIEW, full.getStatus());
        assertEquals(3, full.getVersion());
        assertEquals("comment", full.getReviewComment());
        assertEquals(source.getUpdatedAt(), full.getUpdatedAt());
        assertEquals(3, full.getAuditTrail().size());
    }
}
