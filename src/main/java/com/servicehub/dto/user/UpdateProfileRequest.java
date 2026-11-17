package com.servicehub.dto.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-service profile edit. Both fields are optional: only supplied fields are changed.
 * Email, role, department and active status are not editable through this request.
 */
public record UpdateProfileRequest(
        @Size(max = 150, message = "Full name must be at most 150 characters")
        @Pattern(regexp = "(?s).*\\S.*", message = "Full name must not be blank")
        String fullName,

        @Size(max = 30, message = "Phone must be at most 30 characters")
        String phone) {
}
