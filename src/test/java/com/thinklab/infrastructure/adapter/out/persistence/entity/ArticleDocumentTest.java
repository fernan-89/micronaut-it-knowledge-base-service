package com.thinklab.infrastructure.adapter.out.persistence.entity;

import com.thinklab.domain.model.Article;
import com.thinklab.domain.model.Article.ArticleStatus;
import com.thinklab.domain.model.Article.Visibility;
import com.thinklab.infrastructure.adapter.out.persistence.entity.ArticleDocument.ArticlePersistenceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArticleDocumentTest {

    @Test
    @DisplayName("an article that went through review and a new version survives storage: key, version, keywords, links, reviewer, dates and the audit trail")
    void roundTrip() {
        UUID problem = UUID.randomUUID();
        UUID incident = UUID.randomUUID();
        Article first = Article.createNew(UUID.randomUUID(), UUID.randomUUID(), "Switch drops packets", "Reboot it", "NETWORK", Set.of("switch", "firmware"),
                Visibility.PUBLIC, Set.of(problem), Set.of(incident), "author-1");
        first.submit("author-1");
        first.returnForChanges("Say which firmware", "reviewer-1");
        first.submit("author-1");
        first.publish("reviewer-1");
        Article second = Article.nextVersionOf(first, UUID.randomUUID(), "author-2");

        for (Article article : new Article[] {first, second}) {
            ArticleDocument document = ArticlePersistenceMapper.toDocument(article);
            Article restored = ArticlePersistenceMapper.toDomain(document);

            assertEquals(article.getId(), restored.getId());
            assertEquals(article.getArticleKey(), restored.getArticleKey());
            assertEquals(article.getVersion(), restored.getVersion());
            assertEquals(article.getStatus(), restored.getStatus());
            assertEquals(article.getKeywords(), restored.getKeywords());
            assertEquals(Visibility.PUBLIC, restored.getVisibility());
            assertEquals(article.getAuthorId(), restored.getAuthorId());
            assertEquals(article.getReviewerId(), restored.getReviewerId());
            assertEquals(article.getReviewComment(), restored.getReviewComment());
            assertEquals(article.getPublishedAt(), restored.getPublishedAt());
            assertEquals(Set.of(problem), restored.getRelatedProblemIds());
            assertEquals(Set.of(incident), restored.getRelatedIncidentIds());
            assertEquals(article.getAuditTrail().size(), restored.getAuditTrail().size());
            assertNull(restored.getAuditTrail().get(0).fromStatus());
            assertEquals(article.getCreatedAt(), document.getCreatedAt());
            assertEquals(article.getUpdatedAt(), document.getUpdatedAt());
            assertEquals(article.getOrganisationId(), document.getOrganisationId());
            assertEquals("Switch drops packets", document.getTitle());
            assertEquals("Reboot it", document.getBody());
            assertEquals("NETWORK", document.getCategory());
            assertEquals(article.getAuthorId(), document.getAuditTrail().get(0).executor());
        }
        assertEquals(ArticleStatus.DRAFT, ArticlePersistenceMapper.toDomain(ArticlePersistenceMapper.toDocument(second)).getStatus());
    }

    @Test
    @DisplayName("the persistence mapper is a non-instantiable utility class")
    void utilityClass() throws Exception {
        Constructor<ArticlePersistenceMapper> constructor = ArticlePersistenceMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException failure = assertThrows(InvocationTargetException.class, constructor::newInstance);

        assertInstanceOf(UnsupportedOperationException.class, failure.getCause());
    }
}
