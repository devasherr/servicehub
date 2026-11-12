package com.servicehub.mapper;

import com.servicehub.dto.user.DepartmentSummary;
import com.servicehub.dto.user.UserProfileResponse;
import com.servicehub.entity.Department;
import com.servicehub.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserProfileResponse toProfile(User user) {
        Department department = user.getDepartment();
        DepartmentSummary departmentSummary = department == null
                ? null
                : new DepartmentSummary(department.getId(), department.getName());

        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole().getName(),
                departmentSummary,
                user.isActive(),
                user.getCreatedAt());
    }
}
