package com.servicehub.dto.category;

import jakarta.validation.constraints.Size;

public record PatchCategoryRequest(
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Size(max = 255, message = "Description must not exceed 255 characters")
        String description,

        Boolean active) {
}
