package com.servicehub.controller;

import com.servicehub.dto.dashboard.DashboardSummaryResponse;
import com.servicehub.dto.request.RequestStatusCountResponse;
import com.servicehub.security.UserPrincipal;
import com.servicehub.service.DashboardService;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Validated
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TECHNICIAN')")
    public ResponseEntity<DashboardSummaryResponse> summary(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) @Positive Long departmentId,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(dashboardService.summary(
                principal, dateFrom, dateTo, departmentId, priority, status));
    }

    @GetMapping(value = {
            "/status-counts",
            "/status_counts",
            "/statusCounts",
            "/counts",
            "/count",
            "/stats"
    })
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'TECHNICIAN')")
    public ResponseEntity<RequestStatusCountResponse> statusCounts(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) @Positive Long departmentId,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(dashboardService.statusCounts(
                principal, dateFrom, dateTo, departmentId, priority, status));
    }
}
