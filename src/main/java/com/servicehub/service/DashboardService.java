package com.servicehub.service;

import com.servicehub.dto.dashboard.DashboardSummaryResponse;
import com.servicehub.dto.dashboard.DashboardSummaryResponse.CategoryBreakdown;
import com.servicehub.dto.dashboard.DashboardSummaryResponse.TechnicianWorkload;
import com.servicehub.dto.request.RequestStatusCountResponse;
import com.servicehub.entity.ServiceRequest;
import com.servicehub.entity.User;
import com.servicehub.entity.enums.Priority;
import com.servicehub.entity.enums.RequestStatus;
import com.servicehub.exception.BadRequestException;
import com.servicehub.exception.ForbiddenOperationException;
import com.servicehub.repository.ServiceRequestRepository;
import com.servicehub.security.UserPrincipal;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ServiceRequestRepository serviceRequestRepository;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(UserPrincipal principal,
                                            LocalDate dateFrom,
                                            LocalDate dateTo,
                                            Long departmentId,
                                            String priority,
                                            String status) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BadRequestException("dateFrom must not be after dateTo");
        }

        Priority priorityFilter = parsePriority(priority);
        RequestStatus statusFilter = parseStatus(status);

        Specification<ServiceRequest> specification = reportScopeFor(principal)
                .and(dateRange(dateFrom, dateTo));

        if (departmentId != null) {
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("department").get("id"),
                            departmentId));
        }
        if (priorityFilter != null) {
            Priority value = priorityFilter;
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("priority"), value));
        }
        if (statusFilter != null) {
            RequestStatus value = statusFilter;
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("status"), value));
        }
        specification = specification.and(fetchBreakdownAssociations());

        List<ServiceRequest> requests = serviceRequestRepository.findAll(specification);

        Map<RequestStatus, Long> byStatus = new EnumMap<>(RequestStatus.class);
        for (RequestStatus value : RequestStatus.values()) {
            byStatus.put(value, 0L);
        }
        Map<Priority, Long> byPriority = new EnumMap<>(Priority.class);
        for (Priority value : Priority.values()) {
            byPriority.put(value, 0L);
        }

        Map<Long, Long> categoryCounts = new HashMap<>();
        Map<Long, String> categoryNames = new HashMap<>();
        Map<Long, TechnicianAccumulator> technicianCounts = new HashMap<>();

        long criticalRequests = 0;
        long resolutionSamples = 0;
        long totalResolutionSeconds = 0;

        for (ServiceRequest request : requests) {
            byStatus.merge(request.getStatus(), 1L, Long::sum);
            byPriority.merge(request.getPriority(), 1L, Long::sum);

            if (request.getPriority() == Priority.CRITICAL) {
                criticalRequests++;
            }

            Long categoryId = request.getCategory().getId();
            categoryCounts.merge(categoryId, 1L, Long::sum);
            categoryNames.putIfAbsent(categoryId, request.getCategory().getName());

            User technician = request.getAssignedTo();
            if (technician != null) {
                TechnicianAccumulator accumulator = technicianCounts.computeIfAbsent(
                        technician.getId(),
                        id -> new TechnicianAccumulator(technician.getId(),
                                technician.getFullName(), technician.getEmail()));
                accumulator.assignedRequests++;
                if (request.getStatus() == RequestStatus.IN_PROGRESS) {
                    accumulator.inProgressRequests++;
                }
                if (request.getStatus() == RequestStatus.RESOLVED) {
                    accumulator.resolvedRequests++;
                }
            }

            if (request.getCreatedAt() != null && request.getResolvedAt() != null) {
                long seconds = Duration.between(request.getCreatedAt(),
                        request.getResolvedAt()).getSeconds();
                if (seconds >= 0) {
                    totalResolutionSeconds += seconds;
                    resolutionSamples++;
                }
            }
        }

        List<CategoryBreakdown> byCategory = categoryCounts.entrySet().stream()
                .map(entry -> new CategoryBreakdown(entry.getKey(),
                        categoryNames.get(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparingLong(CategoryBreakdown::requestCount).reversed()
                        .thenComparing(CategoryBreakdown::categoryName,
                                String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<TechnicianWorkload> workload = technicianCounts.values().stream()
                .map(accumulator -> new TechnicianWorkload(
                        accumulator.technicianId,
                        accumulator.fullName,
                        accumulator.email,
                        accumulator.assignedRequests,
                        accumulator.inProgressRequests,
                        accumulator.resolvedRequests))
                .sorted(Comparator.comparingLong(TechnicianWorkload::assignedRequests)
                        .reversed()
                        .thenComparing(TechnicianWorkload::fullName,
                                String.CASE_INSENSITIVE_ORDER))
                .toList();

        Double averageResolutionTimeHours = resolutionSamples == 0
                ? null
                : Math.round(totalResolutionSeconds / 3600.0 / resolutionSamples * 100.0)
                        / 100.0;

        return new DashboardSummaryResponse(
                requests.size(),
                byStatus.get(RequestStatus.PENDING),
                byStatus.get(RequestStatus.IN_PROGRESS),
                byStatus.get(RequestStatus.RESOLVED),
                criticalRequests,
                byStatus,
                byPriority,
                byCategory,
                workload,
                averageResolutionTimeHours);
    }

    @Transactional(readOnly = true)
    public RequestStatusCountResponse statusCounts(UserPrincipal principal,
                                                  LocalDate dateFrom,
                                                  LocalDate dateTo,
                                                  Long departmentId,
                                                  String priority,
                                                  String status) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BadRequestException("dateFrom must not be after dateTo");
        }

        Priority priorityFilter = parsePriority(priority);
        RequestStatus statusFilter = parseStatus(status);

        Specification<ServiceRequest> specification = reportScopeFor(principal)
                .and(dateRange(dateFrom, dateTo));

        if (departmentId != null) {
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("department").get("id"),
                            departmentId));
        }
        if (priorityFilter != null) {
            Priority value = priorityFilter;
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("priority"), value));
        }
        if (statusFilter != null) {
            RequestStatus value = statusFilter;
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("status"), value));
        }

        List<ServiceRequest> requests = serviceRequestRepository.findAll(specification);

        Map<RequestStatus, Long> byStatus = new EnumMap<>(RequestStatus.class);
        for (RequestStatus value : RequestStatus.values()) {
            byStatus.put(value, 0L);
        }
        for (ServiceRequest request : requests) {
            byStatus.merge(request.getStatus(), 1L, Long::sum);
        }

        return RequestStatusCountResponse.of(byStatus);
    }

    private Specification<ServiceRequest> reportScopeFor(UserPrincipal principal) {
        String role = principal.getRole();
        Long userId = principal.getId();
        Long departmentId = principal.getDepartmentId();

        return switch (role) {
            case "ADMIN" -> (root, query, cb) -> cb.conjunction();
            case "MANAGER" -> departmentId == null
                    ? (root, query, cb) -> cb.equal(root.get("requester").get("id"), userId)
                    : (root, query, cb) -> cb.equal(root.get("department").get("id"),
                            departmentId);
            case "TECHNICIAN" -> (root, query, cb) -> cb.equal(root.get("assignedTo").get("id"),
                    userId);
            default -> throw new ForbiddenOperationException(
                    "You are not allowed to view dashboard statistics");
        };
    }

    private Specification<ServiceRequest> dateRange(LocalDate dateFrom, LocalDate dateTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (dateFrom != null) {
                Instant from = dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (dateTo != null) {
                Instant toExclusive = dateTo.plusDays(1)
                        .atStartOfDay(ZoneOffset.UTC).toInstant();
                predicates.add(cb.lessThan(root.get("createdAt"), toExclusive));
            }
            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<ServiceRequest> fetchBreakdownAssociations() {
        return (root, query, cb) -> {
            root.fetch("category");
            root.fetch("assignedTo", JoinType.LEFT);
            return cb.conjunction();
        };
    }

    private Priority parsePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return null;
        }
        try {
            return Priority.valueOf(priority.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid priority: " + priority);
        }
    }

    private RequestStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return RequestStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid status: " + status);
        }
    }

    private static final class TechnicianAccumulator {

        private final Long technicianId;
        private final String fullName;
        private final String email;
        private long assignedRequests;
        private long inProgressRequests;
        private long resolvedRequests;

        private TechnicianAccumulator(Long technicianId, String fullName, String email) {
            this.technicianId = technicianId;
            this.fullName = fullName;
            this.email = email;
        }
    }
}
