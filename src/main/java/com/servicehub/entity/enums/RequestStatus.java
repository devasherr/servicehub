package com.servicehub.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

public enum RequestStatus {
    SUBMITTED,
    PENDING,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    CLOSED;

    @JsonCreator
    public static RequestStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace("-", "_");
        if ("COMPLETED".equals(normalized)) {
            return RESOLVED;
        }
        if ("INPROGRESS".equals(normalized)) {
            return IN_PROGRESS;
        }
        return RequestStatus.valueOf(normalized);
    }
}
