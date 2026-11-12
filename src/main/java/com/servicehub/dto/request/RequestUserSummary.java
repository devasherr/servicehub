package com.servicehub.dto.request;

public record RequestUserSummary(
        Long id,
        String email,
        String fullName) {
}
