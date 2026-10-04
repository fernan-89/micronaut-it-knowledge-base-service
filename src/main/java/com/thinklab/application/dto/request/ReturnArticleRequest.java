package com.thinklab.application.dto.request;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTO for {@code control/return}: what the author should change. */
@Serdeable
public record ReturnArticleRequest(
        @NotBlank(message = "A comment saying what to change is required")
        @Size(max = 1000, message = "Comment must not exceed 1000 characters")
        String comment
) {}
