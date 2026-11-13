package com.servicehub.dto.request;

import com.servicehub.entity.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateRequestRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @NotBlank(message = "Description is required")
        String description,

        @NotNull(message = "Category is required")
        @Positive(message = "Category id must be positive")
        Long categoryId,

        Priority priority,

        @Size(max = 150, message = "Location must be at most 150 characters")
        String location) {
}
