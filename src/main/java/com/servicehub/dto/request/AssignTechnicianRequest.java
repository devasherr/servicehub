package com.servicehub.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AssignTechnicianRequest(
        @NotNull(message = "Technician is required")
        @Positive(message = "Technician id must be positive")
        Long technicianId) {
}
