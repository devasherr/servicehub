package com.servicehub.dto.request;

import com.servicehub.entity.enums.RequestStatus;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public record RequestStatusCountResponse(
        long totalRequests,
        long pendingRequests,
        long inProgressRequests,
        long completedRequests,
        long submittedRequests,
        long assignedRequests,
        long resolvedRequests,
        long closedRequests,
        long total,
        long pending,
        long inProgress,
        long completed,
        Map<RequestStatus, Long> requestsByStatus,
        Map<RequestStatus, Long> byStatus,
        List<StatusCountItem> statusCounts) {

    public record StatusCountItem(
            RequestStatus status,
            long count) {
    }

    public static RequestStatusCountResponse of(Map<RequestStatus, Long> counts) {
        Map<RequestStatus, Long> safeCounts = new EnumMap<>(RequestStatus.class);
        for (RequestStatus status : RequestStatus.values()) {
            safeCounts.put(status, counts == null ? 0L : counts.getOrDefault(status, 0L));
        }

        long submitted = safeCounts.get(RequestStatus.SUBMITTED);
        long pending = safeCounts.get(RequestStatus.PENDING);
        long assigned = safeCounts.get(RequestStatus.ASSIGNED);
        long inProgress = safeCounts.get(RequestStatus.IN_PROGRESS);
        long resolved = safeCounts.get(RequestStatus.RESOLVED);
        long closed = safeCounts.get(RequestStatus.CLOSED);

        long total = submitted + pending + assigned + inProgress + resolved + closed;
        long completed = resolved + closed;

        List<StatusCountItem> items = new ArrayList<>();
        for (RequestStatus status : RequestStatus.values()) {
            items.add(new StatusCountItem(status, safeCounts.get(status)));
        }

        return new RequestStatusCountResponse(
                total,
                pending,
                inProgress,
                completed,
                submitted,
                assigned,
                resolved,
                closed,
                total,
                pending,
                inProgress,
                completed,
                safeCounts,
                safeCounts,
                items);
    }
}
