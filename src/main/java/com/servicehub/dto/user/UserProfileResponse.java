package com.servicehub.dto.user;

import java.time.Instant;

public record UserProfileResponse(
        Long id,
        String email,
        String fullName,
        String phone,
        String role,
        DepartmentSummary department,
        boolean active,
        Instant createdAt) {
}
