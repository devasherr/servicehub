package com.servicehub.service;

import com.servicehub.dto.common.PageResponse;
import com.servicehub.dto.user.UserProfileResponse;
import com.servicehub.entity.User;
import com.servicehub.exception.ResourceNotFoundException;
import com.servicehub.mapper.UserMapper;
import com.servicehub.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
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
public class UserService {

    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "id", "id",
            "email", "email",
            "fullName", "fullName",
            "active", "active",
            "createdAt", "createdAt");

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return UserMapper.toProfile(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> listUsers(String search, String role,
                                                       Long departmentId, Boolean active,
                                                       int page, int size,
                                                       String sort, String direction) {
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        String sortProperty = SORTABLE_FIELDS.getOrDefault(sort, "id");
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortProperty));

        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        String normalizedRole = role == null || role.isBlank()
                ? null
                : role.trim().toUpperCase(Locale.ROOT);

        Page<User> users = userRepository.findAll(
                buildSpecification(normalizedSearch, normalizedRole, departmentId, active),
                pageable);

        return PageResponse.from(users.map(UserMapper::toProfile));
    }

    private Specification<User> buildSpecification(String search, String role,
                                                   Long departmentId, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null) {
                String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(root.get("fullName")), pattern)));
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role").get("name"), role));
            }
            if (departmentId != null) {
                predicates.add(cb.equal(root.get("department").get("id"), departmentId));
            }
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
