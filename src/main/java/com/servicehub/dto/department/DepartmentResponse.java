package com.servicehub.dto.department;

import com.servicehub.entity.Department;
import java.time.Instant;

public record DepartmentResponse(
        Long id,
        String name,
        Instant createdAt,
        Instant updatedAt) {

    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getName(),
                department.getCreatedAt(),
                department.getUpdatedAt());
    }
}
