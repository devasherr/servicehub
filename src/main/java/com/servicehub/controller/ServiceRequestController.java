package com.servicehub.controller;

import com.servicehub.dto.common.PageResponse;
import com.servicehub.dto.request.AssignTechnicianRequest;
import com.servicehub.dto.request.CreateCommentRequest;
import com.servicehub.dto.request.CreateRequestRequest;
import com.servicehub.dto.request.RequestCommentResponse;
import com.servicehub.dto.request.RequestStatusCountResponse;
import com.servicehub.dto.request.ServiceRequestResponse;
import com.servicehub.dto.request.UpdateRequestRequest;
import com.servicehub.dto.request.UpdateRequestStatusRequest;
import com.servicehub.security.UserPrincipal;
import com.servicehub.service.ServiceRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
@Validated
public class ServiceRequestController {

    private final ServiceRequestService serviceRequestService;

    @PostMapping
    public ResponseEntity<ServiceRequestResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateRequestRequest request) {
        ServiceRequestResponse response =
                serviceRequestService.create(request, principal.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceRequestResponse> get(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(serviceRequestService.getById(id, principal));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceRequestResponse> update(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateRequestRequest request) {
        return ResponseEntity.ok(serviceRequestService.update(id, request, principal));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ServiceRequestResponse> assign(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AssignTechnicianRequest request) {
        return ResponseEntity.ok(serviceRequestService.assign(id, request, principal));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<RequestCommentResponse> addComment(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(serviceRequestService.addComment(id, request, principal));
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<PageResponse<RequestCommentResponse>> listComments(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(
                serviceRequestService.listComments(id, principal, page, size));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TECHNICIAN')")
    public ResponseEntity<ServiceRequestResponse> changeStatus(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateRequestStatusRequest request) {
        return ResponseEntity.ok(serviceRequestService.changeStatus(id, request, principal));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TECHNICIAN')")
    public ResponseEntity<ServiceRequestResponse> changeStatusPut(
            @PathVariable @Positive Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateRequestStatusRequest request) {
        return ResponseEntity.ok(serviceRequestService.changeStatus(id, request, principal));
    }

    @GetMapping(value = {
            "/status-counts",
            "/status_counts",
            "/statusCounts",
            "/counts",
            "/count",
            "/stats",
            "/status-summary"
    })
    public ResponseEntity<RequestStatusCountResponse> getStatusCounts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(required = false) @Positive Long departmentId,
            @RequestParam(required = false) @Positive Long assignedToId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(serviceRequestService.getStatusCounts(
                principal, search, priority, categoryId, departmentId, assignedToId,
                dateFrom, dateTo));
    }

    @GetMapping
    public ResponseEntity<PageResponse<ServiceRequestResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(required = false) @Positive Long departmentId,
            @RequestParam(required = false) @Positive Long assignedToId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return ResponseEntity.ok(serviceRequestService.list(
                principal, search, status, priority, categoryId, departmentId, assignedToId,
                page, size, sort, direction));
    }
}
