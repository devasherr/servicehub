package com.servicehub.dto.request;

import com.servicehub.entity.enums.RequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRequestStatusRequest(
        @NotNull(message = "Status is required")
        RequestStatus status,

        @Size(max = 500, message = "Note must be at most 500 characters")
        String note) {
}
