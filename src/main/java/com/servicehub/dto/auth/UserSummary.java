package com.servicehub.dto.auth;

public record UserSummary(
        Long id,
        String email,
        String fullName,
        String role,
        Long departmentId) {
}
