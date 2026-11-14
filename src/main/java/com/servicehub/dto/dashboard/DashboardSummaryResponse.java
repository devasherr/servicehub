package com.servicehub.dto.dashboard;

import com.servicehub.entity.enums.Priority;
import com.servicehub.entity.enums.RequestStatus;
import java.util.List;
import java.util.Map;

public record DashboardSummaryResponse(
        long totalRequests,
        long pendingRequests,
        long inProgressRequests,
        long resolvedRequests,
        long criticalRequests,
        Map<RequestStatus, Long> requestsByStatus,
        Map<Priority, Long> requestsByPriority,
        List<CategoryBreakdown> requestsByCategory,
        List<TechnicianWorkload> technicianWorkload,
        Double averageResolutionTimeHours) {

    public record CategoryBreakdown(
            Long categoryId,
            String categoryName,
            long requestCount) {
    }

    public record TechnicianWorkload(
            Long technicianId,
            String fullName,
            String email,
            long assignedRequests,
            long inProgressRequests,
            long resolvedRequests) {
    }
}
