package com.thinklab.application.mapper;

import com.thinklab.application.dto.request.InitiateArticleRequest;
import com.thinklab.application.dto.response.ArticleResponse;
import com.thinklab.domain.model.Article;
import com.thinklab.domain.model.Article.Visibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArticleMapperTest {

    @Test
    @DisplayName("a request becomes a DRAFT; staff see the people and links, a requester sees the content only")
    void mapping() {
        UUID problem = UUID.randomUUID();
        var request = new InitiateArticleRequest("Switch drops packets", "Reboot it", "NETWORK", Set.of("switch"), Visibility.PUBLIC, Set.of(problem), null);

        Article article = ArticleMapper.toDomain(request, UUID.randomUUID(), UUID.randomUUID(), "author-1");
        ArticleResponse staff = ArticleMapper.toResponse(article, false);
        ArticleResponse requester = ArticleMapper.toResponse(article, true);

        assertEquals("DRAFT", staff.status());
        assertEquals("PUBLIC", staff.visibility());
        assertEquals("author-1", staff.authorId());
        assertEquals(Set.of(problem), staff.relatedProblemIds());
        assertEquals("Reboot it", requester.body());
        assertNull(requester.authorId());
        assertNull(requester.reviewerId());
        assertNull(requester.reviewComment());
        assertNull(requester.relatedProblemIds());
        assertNull(requester.relatedIncidentIds());
        assertNotNull(requester.createdAt());
        assertNull(ArticleMapper.toResponse(article.getAuditTrail().get(0)).fromStatus());
        article.submit("author-1");
        assertEquals("DRAFT", ArticleMapper.toResponse(article.getAuditTrail().get(1)).fromStatus());
        assertEquals("IN_REVIEW", ArticleMapper.toResponse(article.getAuditTrail().get(1)).toStatus());
    }

    @Test
    @DisplayName("the mapper is a non-instantiable utility class")
    void utilityClass() throws Exception {
        Constructor<ArticleMapper> constructor = ArticleMapper.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException failure = assertThrows(InvocationTargetException.class, constructor::newInstance);

        assertInstanceOf(UnsupportedOperationException.class, failure.getCause());
    }
}
