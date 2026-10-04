package com.thinklab.application.dto.response;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * An article as read. For a REQUESTER the people (author, reviewer), the review comment and the links to internal problems and incidents
 * are left out (ADR-032): they see what the article says and when it was published, not how it came to be.
 */
@Serdeable
public record ArticleResponse(
        UUID id,
        UUID organisationId,
        UUID articleKey,
        int version,
        String title,
        String body,
        @Nullable String category,
        Set<String> keywords,
        String visibility,
        String status,
        @Nullable String authorId,
        @Nullable String reviewerId,
        @Nullable String reviewComment,
        @Nullable Instant publishedAt,
        @Nullable Set<UUID> relatedProblemIds,
        @Nullable Set<UUID> relatedIncidentIds,
        Instant createdAt,
        Instant updatedAt
) {}
