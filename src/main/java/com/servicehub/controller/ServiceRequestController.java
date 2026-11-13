package com.servicehub.controller;

import com.servicehub.dto.common.PageResponse;
import com.servicehub.dto.request.CreateRequestRequest;
import com.servicehub.dto.request.ServiceRequestResponse;
import com.servicehub.dto.request.UpdateRequestRequest;
import com.servicehub.security.UserPrincipal;
import com.servicehub.service.ServiceRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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
