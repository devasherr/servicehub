package com.servicehub.service;

import com.servicehub.dto.common.PageResponse;
import com.servicehub.dto.request.CreateRequestRequest;
import com.servicehub.dto.request.ServiceRequestResponse;
import com.servicehub.entity.Department;
import com.servicehub.entity.RequestStatusHistory;
import com.servicehub.entity.ServiceCategory;
import com.servicehub.entity.ServiceRequest;
import com.servicehub.entity.User;
import com.servicehub.entity.enums.Priority;
import com.servicehub.entity.enums.RequestStatus;
import com.servicehub.exception.BadRequestException;
import com.servicehub.exception.ResourceNotFoundException;
import com.servicehub.mapper.ServiceRequestMapper;
import com.servicehub.repository.DepartmentRepository;
import com.servicehub.repository.RequestStatusHistoryRepository;
import com.servicehub.repository.ServiceCategoryRepository;
import com.servicehub.repository.ServiceRequestRepository;
import com.servicehub.repository.UserRepository;
import com.servicehub.security.UserPrincipal;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ServiceRequestService {

    private static final Map<String, String> SORTABLE_FIELDS = Map.ofEntries(
            Map.entry("id", "id"),
            Map.entry("referenceNumber", "referenceNumber"),
            Map.entry("title", "title"),
            Map.entry("priority", "priority"),
            Map.entry("status", "status"),
            Map.entry("location", "location"),
            Map.entry("createdAt", "createdAt"),
            Map.entry("updatedAt", "updatedAt"),
            Map.entry("resolvedAt", "resolvedAt"),
            Map.entry("closedAt", "closedAt"));

    private final ServiceRequestRepository serviceRequestRepository;
    private final RequestStatusHistoryRepository statusHistoryRepository;
    private final ServiceCategoryRepository serviceCategoryRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    @Transactional
    public ServiceRequestResponse create(CreateRequestRequest request, Long requesterId) {
        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", requesterId));

        ServiceCategory category = serviceCategoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service category", request.categoryId()));
        if (!category.isActive()) {
            throw new BadRequestException("Service category is not active");
        }

        Department department = resolveDepartment(request.departmentId(), requester);

        ServiceRequest serviceRequest = new ServiceRequest();
        serviceRequest.setReferenceNumber(nextReferenceNumber());
        serviceRequest.setTitle(request.title().trim());
        serviceRequest.setDescription(request.description().trim());
        serviceRequest.setCategory(category);
        serviceRequest.setPriority(request.priority() == null ? Priority.MEDIUM : request.priority());
        serviceRequest.setStatus(RequestStatus.SUBMITTED);
        serviceRequest.setLocation(trimToNull(request.location()));
        serviceRequest.setRequester(requester);
        serviceRequest.setDepartment(department);

        ServiceRequest saved = serviceRequestRepository.saveAndFlush(serviceRequest);
        recordInitialStatus(saved, requester);

        return ServiceRequestMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ServiceRequestResponse> list(
            UserPrincipal principal,
            String search,
            String status,
            String priority,
            Long categoryId,
            Long departmentId,
            Long assignedToId,
            int page,
            int size,
            String sort,
            String direction) {

        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        String sortProperty = SORTABLE_FIELDS.getOrDefault(sort, "createdAt");
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortProperty));

        RequestStatus statusFilter = parseStatus(status);
        Priority priorityFilter = parsePriority(priority);
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();

        Specification<ServiceRequest> specification = visibilityFor(principal);
        if (normalizedSearch != null) {
            specification = specification.and(searchMatches(normalizedSearch));
        }
        if (statusFilter != null) {
            RequestStatus value = statusFilter;
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("status"), value));
        }
        if (priorityFilter != null) {
            Priority value = priorityFilter;
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("priority"), value));
        }
        if (categoryId != null) {
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }
        if (departmentId != null) {
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("department").get("id"), departmentId));
        }
        if (assignedToId != null) {
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("assignedTo").get("id"), assignedToId));
        }
        specification = specification.and(fetchAssociations());

        Page<ServiceRequest> requests =
                serviceRequestRepository.findAll(specification, pageable);

        return PageResponse.from(requests.map(ServiceRequestMapper::toResponse));
    }

    private Specification<ServiceRequest> visibilityFor(UserPrincipal principal) {
        String role = principal.getRole();
        Long userId = principal.getId();
        Long departmentId = principal.getDepartmentId();

        return (root, query, cb) -> switch (role) {
            case "ADMIN" -> cb.conjunction();
            case "MANAGER" -> departmentId == null
                    ? cb.equal(root.get("requester").get("id"), userId)
                    : cb.or(
                            cb.equal(root.get("requester").get("id"), userId),
                            cb.equal(root.get("department").get("id"), departmentId));
            case "TECHNICIAN" -> cb.or(
                    cb.equal(root.get("requester").get("id"), userId),
                    cb.equal(root.get("assignedTo").get("id"), userId));
            default -> cb.equal(root.get("requester").get("id"), userId);
        };
    }

    private Specification<ServiceRequest> searchMatches(String search) {
        return (root, query, cb) -> {
            String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
            List<Predicate> matches = new ArrayList<>();
            matches.add(cb.like(cb.lower(root.get("title")), pattern));
            matches.add(cb.like(cb.lower(root.get("description")), pattern));
            matches.add(cb.like(cb.lower(root.get("referenceNumber")), pattern));
            return cb.or(matches.toArray(new Predicate[0]));
        };
    }

    private Specification<ServiceRequest> fetchAssociations() {
        return (root, query, cb) -> {
            // The count query must not contain fetch joins.
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("category");
                root.fetch("requester");
                root.fetch("department");
                root.fetch("assignedTo", JoinType.LEFT);
            }
            return cb.conjunction();
        };
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

    private Department resolveDepartment(Long departmentId, User requester) {
        if (departmentId != null) {
            return departmentRepository.findById(departmentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));
        }

        Department requesterDepartment = requester.getDepartment();
        if (requesterDepartment == null) {
            throw new BadRequestException(
                    "Department is required because the authenticated user has no department");
        }
        return requesterDepartment;
    }

    private void recordInitialStatus(ServiceRequest request, User changedBy) {
        RequestStatusHistory history = new RequestStatusHistory();
        history.setRequest(request);
        history.setFromStatus(null);
        history.setToStatus(RequestStatus.SUBMITTED);
        history.setChangedBy(changedBy);
        history.setNote("Request submitted");
        statusHistoryRepository.save(history);
    }

    private String nextReferenceNumber() {
        Long sequence = serviceRequestRepository.nextReferenceSequence();
        return "SR-%d-%04d".formatted(Year.now().getValue(), sequence);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
