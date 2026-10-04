package com.thinklab.application.dto.request;

import com.thinklab.domain.model.Article.Visibility;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/** DTO for Article creation (BIAN Behavior Qualifier: {@code initiate}). The author is the caller (X-Executor). */
@Serdeable
public record InitiateArticleRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,
        @NotBlank(message = "Body is required")
        @Size(max = 20000, message = "Body must not exceed 20000 characters")
        String body,
        @Nullable
        @Size(max = 60, message = "Category must not exceed 60 characters")
        String category,
        @Nullable
        @Size(max = 20, message = "At most 20 keywords")
        Set<String> keywords,
        @NotNull(message = "Visibility is required")
        Visibility visibility,
        @Nullable
        @Size(max = 100, message = "At most 100 related problems")
        Set<UUID> relatedProblemIds,
        @Nullable
        @Size(max = 100, message = "At most 100 related incidents")
        Set<UUID> relatedIncidentIds
) {}
