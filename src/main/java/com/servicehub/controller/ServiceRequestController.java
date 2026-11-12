package com.servicehub.controller;

import com.servicehub.dto.request.CreateRequestRequest;
import com.servicehub.dto.request.ServiceRequestResponse;
import com.servicehub.security.UserPrincipal;
import com.servicehub.service.ServiceRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
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
}
