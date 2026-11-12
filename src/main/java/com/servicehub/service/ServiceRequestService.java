package com.servicehub.service;

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
import java.time.Year;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ServiceRequestService {

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
