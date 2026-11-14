package com.servicehub.dto.request;

import java.time.Instant;

public record RequestCommentResponse(
        Long id,
        Long requestId,
        String content,
        boolean internal,
        RequestUserSummary author,
        Instant createdAt,
        Instant updatedAt) {
}
