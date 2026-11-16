package com.servicehub.controller;

import com.servicehub.dto.common.PageResponse;
import com.servicehub.dto.request.ServiceRequestResponse;
import com.servicehub.dto.request.UpdateRequestStatusRequest;
import com.servicehub.security.UserPrincipal;
import com.servicehub.service.ServiceRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/technician/tasks", "/api/tasks", "/api/technician/requests"})
@RequiredArgsConstructor
@Validated
public class TechnicianTaskController {

    private final ServiceRequestService serviceRequestService;

    @PatchMapping(value = {"/{id}/status", "/{id}"})
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    public ResponseEntity<ServiceRequestResponse> updateStatus(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateRequestStatusRequest request) {
        return ResponseEntity.ok(serviceRequestService.updateTechnicianTaskStatus(id, request, principal));
    }

    @PutMapping(value = {"/{id}/status", "/{id}"})
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    public ResponseEntity<ServiceRequestResponse> updateStatusPut(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateRequestStatusRequest request) {
        return ResponseEntity.ok(serviceRequestService.updateTechnicianTaskStatus(id, request, principal));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    public ResponseEntity<PageResponse<ServiceRequestResponse>> listTasks(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        Long assignedToId = "ADMIN".equals(principal.getRole()) ? null : principal.getId();
        return ResponseEntity.ok(serviceRequestService.list(
                principal, search, status, priority, categoryId, null, assignedToId,
                page, size, sort, direction));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    public ResponseEntity<ServiceRequestResponse> getTask(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(serviceRequestService.getById(id, principal));
    }
}
