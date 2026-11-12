package com.servicehub.dto.request;

import com.servicehub.dto.common.LookupResponse;
import com.servicehub.entity.enums.Priority;
import com.servicehub.entity.enums.RequestStatus;
import java.time.Instant;

public record ServiceRequestResponse(
        Long id,
        String referenceNumber,
        String title,
        String description,
        LookupResponse category,
        Priority priority,
        RequestStatus status,
        String location,
        RequestUserSummary requester,
        LookupResponse department,
        RequestUserSummary assignedTo,
        Instant resolvedAt,
        Instant closedAt,
        Instant createdAt,
        Instant updatedAt) {
}
