package com.servicehub.mapper;

import com.servicehub.dto.common.LookupResponse;
import com.servicehub.dto.request.RequestUserSummary;
import com.servicehub.dto.request.ServiceRequestResponse;
import com.servicehub.entity.ServiceRequest;
import com.servicehub.entity.User;

public final class ServiceRequestMapper {

    private ServiceRequestMapper() {
    }

    public static ServiceRequestResponse toResponse(ServiceRequest request) {
        return new ServiceRequestResponse(
                request.getId(),
                request.getReferenceNumber(),
                request.getTitle(),
                request.getDescription(),
                new LookupResponse(request.getCategory().getId(), request.getCategory().getName()),
                request.getPriority(),
                request.getStatus(),
                request.getLocation(),
                toUserSummary(request.getRequester()),
                new LookupResponse(request.getDepartment().getId(),
                        request.getDepartment().getName()),
                request.getAssignedTo() == null ? null : toUserSummary(request.getAssignedTo()),
                request.getResolvedAt(),
                request.getClosedAt(),
                request.getCreatedAt(),
                request.getUpdatedAt());
    }

    private static RequestUserSummary toUserSummary(User user) {
        return new RequestUserSummary(user.getId(), user.getEmail(), user.getFullName());
    }
}
