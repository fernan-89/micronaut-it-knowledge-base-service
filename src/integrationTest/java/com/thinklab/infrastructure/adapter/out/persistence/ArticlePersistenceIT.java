package com.thinklab.infrastructure.adapter.out.persistence;

import com.mongodb.reactivestreams.client.MongoClient;
import com.thinklab.domain.exception.DuplicateArticleException;
import com.thinklab.domain.exception.InvalidArticleStatusException;
import com.thinklab.domain.model.Article;
import com.thinklab.domain.model.Article.ArticleStatus;
import com.thinklab.domain.model.Article.Visibility;
import com.thinklab.domain.repository.ArticleRepository;
import com.thinklab.domain.repository.ArticleRepository.Filter;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.test.support.TestPropertyProvider;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Article aggregate through its repository against a real MongoDB: the guarded save, the unique {@code (key, version)} index, tenant
 * scoping, every collection filter, and above all the free-text search, which only a real text index can prove (mocks cannot): the
 * weighted text index {@link com.thinklab.infrastructure.adapter.out.persistence.repository.ArticleIndexInitializer} creates at startup
 * finds an article by a word of its title, its body or its keywords, and only inside the tenant.
 */
@MicronautTest(packages = "com.thinklab", transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArticlePersistenceIT implements TestPropertyProvider {

    private static final String DATABASE = "knowledge_base_it";

    @Override
    public Map<String, String> getProperties() {
        return Map.of("mongodb.uri", MongoContainer.uri(DATABASE));
    }

    @Inject
    ArticleRepository articles;

    @Inject
    MongoClient mongoClient;

    private Article newArticle(UUID organisation, String title, String body, Set<String> keywords, Visibility visibility, Set<UUID> problems) {
        return Article.createNew(UUID.randomUUID(), organisation, title, body, "NETWORK", keywords, visibility, problems, null, "author-1");
    }

    private static Set<UUID> ids(List<Article> found) {
        return found.stream().map(Article::getId).collect(Collectors.toSet());
    }

    private List<Article> list(UUID organisation, Filter filter) {
        return articles.findAll(organisation, filter).collectList().block();
    }

    private static Filter text(String text) {
        return new Filter(text, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("a created article is read back whole: key, version 1, normalised keywords, links and its INITIATED audit entry")
    void createAndFind() {
        UUID organisation = UUID.randomUUID();
        UUID problem = UUID.randomUUID();
        Article created = articles.create(newArticle(organisation, "Switch drops packets", "Reboot it", Set.of("Switch", "Firmware"), Visibility.PUBLIC, Set.of(problem))).block();

        Article found = articles.findById(created.getId(), organisation).block();

        assertEquals(ArticleStatus.DRAFT, found.getStatus());
        assertEquals(1, found.getVersion());
        assertEquals(created.getId(), found.getArticleKey());
        assertEquals(Set.of("switch", "firmware"), found.getKeywords());
        assertEquals(Set.of(problem), found.getRelatedProblemIds());
        assertEquals(1, found.getAuditTrail().size());
        assertNull(articles.findById(created.getId(), UUID.randomUUID()).block());
    }

    @Test
    @DisplayName("the unique (key, version) index lets one of two people starting the same new version win; the other gets DuplicateArticleException")
    void duplicateVersion() {
        UUID organisation = UUID.randomUUID();
        Article first = articles.create(newArticle(organisation, "t", "b", null, Visibility.PUBLIC, null)).block();
        first.submit("author-1");
        first.publish("reviewer-1");

        articles.create(Article.nextVersionOf(first, UUID.randomUUID(), "author-2")).block();

        assertThrows(DuplicateArticleException.class, () -> articles.create(Article.nextVersionOf(first, UUID.randomUUID(), "author-3")).block());
        List<Article> versions = articles.findByKey(first.getArticleKey(), organisation).collectList().block();
        assertEquals(List.of(1, 2), versions.stream().map(Article::getVersion).toList());
    }

    @Test
    @DisplayName("save persists the state the aggregate reached with its audit entry; two people from the same state cannot both win")
    void guardedSave() {
        UUID organisation = UUID.randomUUID();
        Article created = articles.create(newArticle(organisation, "t", "b", null, Visibility.PUBLIC, null)).block();
        Article seenByA = articles.findById(created.getId(), organisation).block();
        Article seenByB = articles.findById(created.getId(), organisation).block();
        var entryA = seenByA.submit("author-1");
        var entryB = seenByB.retire("op-b");

        articles.save(seenByA, ArticleStatus.DRAFT, entryA).block();

        assertThrows(InvalidArticleStatusException.class, () -> articles.save(seenByB, ArticleStatus.DRAFT, entryB).block());
        Article found = articles.findById(created.getId(), organisation).block();
        assertEquals(ArticleStatus.IN_REVIEW, found.getStatus());
        assertEquals(2, found.getAuditTrail().size());

        var returned = found.returnForChanges("Say which firmware", "reviewer-1");
        articles.save(found, ArticleStatus.IN_REVIEW, returned).block();
        Article again = articles.findById(created.getId(), organisation).block();
        assertEquals(ArticleStatus.DRAFT, again.getStatus());
        assertEquals("Say which firmware", again.getReviewComment());
        assertEquals("reviewer-1", again.getReviewerId());
    }

    @Test
    @DisplayName("the text search finds an article by a word of its title, its body or its keywords, never across tenants, and combines with filters")
    void textSearch() {
        UUID organisation = UUID.randomUUID();
        Article byTitle = articles.create(newArticle(organisation, "Firmware leak on the core switch", "Reboot it every Sunday", Set.of("network"), Visibility.PUBLIC, null)).block();
        Article byBody = articles.create(newArticle(organisation, "Printer queue stalls", "Restart the spooler after a firmware update", Set.of("printing"), Visibility.INTERNAL, null)).block();
        Article byKeyword = articles.create(newArticle(organisation, "Wi-Fi drops", "Move closer to the access point", Set.of("firmware"), Visibility.PUBLIC, null)).block();
        Article unrelated = articles.create(newArticle(organisation, "Reset a password", "Use the portal", Set.of("account"), Visibility.PUBLIC, null)).block();
        articles.create(newArticle(UUID.randomUUID(), "Firmware elsewhere", "Another tenant", Set.of("firmware"), Visibility.PUBLIC, null)).block();

        assertEquals(Set.of(byTitle.getId(), byBody.getId(), byKeyword.getId()), ids(list(organisation, text("firmware"))));
        assertEquals(Set.of(unrelated.getId()), ids(list(organisation, text("password"))));
        assertTrue(list(organisation, text("nonexistentterm")).isEmpty());
        assertEquals(Set.of(byTitle.getId(), byKeyword.getId()), ids(list(organisation, new Filter("firmware", null, null, Visibility.PUBLIC, null, null, null, null))));
        assertEquals(Set.of(byKeyword.getId()), ids(list(organisation, new Filter("firmware", null, null, null, "FIRMWARE", null, null, null))));
    }

    @Test
    @DisplayName("the collection honours status, category, visibility, keyword, problem, incident and author, always inside the tenant")
    void filters() {
        UUID organisation = UUID.randomUUID();
        UUID problem = UUID.randomUUID();
        Article open = articles.create(newArticle(organisation, "A", "a", Set.of("switch"), Visibility.PUBLIC, Set.of(problem))).block();
        Article internal = articles.create(newArticle(organisation, "B", "b", null, Visibility.INTERNAL, null)).block();
        articles.create(newArticle(UUID.randomUUID(), "C", "c", Set.of("switch"), Visibility.PUBLIC, Set.of(problem))).block();
        var submission = open.submit("author-1");
        articles.save(open, ArticleStatus.DRAFT, submission).block();

        assertEquals(Set.of(open.getId(), internal.getId()), ids(list(organisation, new Filter(null, null, null, null, null, null, null, null))));
        assertEquals(Set.of(open.getId()), ids(list(organisation, new Filter(null, ArticleStatus.IN_REVIEW, null, null, null, null, null, null))));
        assertEquals(Set.of(open.getId(), internal.getId()), ids(list(organisation, new Filter(null, null, "NETWORK", null, null, null, null, null))));
        assertEquals(Set.of(internal.getId()), ids(list(organisation, new Filter(null, null, null, Visibility.INTERNAL, null, null, null, null))));
        assertEquals(Set.of(open.getId()), ids(list(organisation, new Filter(null, null, null, null, "Switch", null, null, null))));
        assertEquals(Set.of(open.getId()), ids(list(organisation, new Filter(null, null, null, null, null, problem, null, null))));
        assertEquals(Set.of(open.getId(), internal.getId()), ids(list(organisation, new Filter(null, null, null, null, null, null, null, "author-1"))));
        assertTrue(list(UUID.randomUUID(), new Filter(null, null, null, null, null, null, null, null)).isEmpty());
    }

    @Test
    @DisplayName("the indexes exist: the unique version index and the weighted text index")
    void indexesExist() {
        articles.create(newArticle(UUID.randomUUID(), "t", "b", null, Visibility.PUBLIC, null)).block();

        List<Document> indexes = Flux.from(mongoClient.getDatabase(DATABASE).getCollection("articles").listIndexes()).collectList().block();
        Document version = indexes.stream().filter(index -> index.get("key", Document.class).equals(new Document("organisationId", 1).append("articleKey", 1).append("version", 1)))
                .findFirst().orElseThrow();
        assertEquals(true, version.getBoolean("unique"), () -> "articles: " + indexes);
        Document text = indexes.stream().filter(index -> "organisationId_1_article_text".equals(index.getString("name"))).findFirst().orElseThrow();
        assertEquals("none", text.getString("default_language"));
        assertEquals(10, text.get("weights", Document.class).getInteger("title"));
        Set<Document> keys = indexes.stream().map(index -> index.get("key", Document.class)).collect(Collectors.toSet());
        assertTrue(keys.contains(new Document("organisationId", 1).append("status", 1)));
        assertTrue(keys.contains(new Document("organisationId", 1).append("relatedProblemIds", 1)));
        assertTrue(keys.contains(new Document("organisationId", 1).append("relatedIncidentIds", 1)));
        assertTrue(keys.contains(new Document("organisationId", 1).append("keywords", 1)));
    }
}
