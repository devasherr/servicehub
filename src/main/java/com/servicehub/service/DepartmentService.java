package com.servicehub.service;

import com.servicehub.dto.common.PageResponse;
import com.servicehub.dto.department.CreateDepartmentRequest;
import com.servicehub.dto.department.DepartmentResponse;
import com.servicehub.dto.department.UpdateDepartmentRequest;
import com.servicehub.entity.Department;
import com.servicehub.exception.BadRequestException;
import com.servicehub.exception.ConflictException;
import com.servicehub.exception.ResourceNotFoundException;
import com.servicehub.repository.DepartmentRepository;
import com.servicehub.repository.ServiceRequestRepository;
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
public class DepartmentService {

    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "id", "id",
            "name", "name",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt");

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final ServiceRequestRepository requestRepository;

    @Transactional
    public DepartmentResponse create(CreateDepartmentRequest request) {
        String trimmedName = request.name().trim();
        if (trimmedName.isEmpty()) {
            throw new BadRequestException("Name must not be blank");
        }

        if (departmentRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new ConflictException("Department with name '" + trimmedName + "' already exists");
        }

        Department department = new Department();
        department.setName(trimmedName);

        Department saved = departmentRepository.saveAndFlush(department);
        return DepartmentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public DepartmentResponse getById(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", id));
        return DepartmentResponse.from(department);
    }

    @Transactional(readOnly = true)
    public PageResponse<DepartmentResponse> list(String search, int page, int size,
                                                 String sort, String direction) {
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        String sortProperty = SORTABLE_FIELDS.getOrDefault(sort, "id");
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortProperty));

        Page<Department> departments = departmentRepository.findAll(buildSpecification(search), pageable);
        return PageResponse.from(departments.map(DepartmentResponse::from));
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> listAll() {
        return departmentRepository.findByOrderByNameAsc().stream()
                .map(DepartmentResponse::from)
                .toList();
    }

    @Transactional
    public DepartmentResponse update(Long id, UpdateDepartmentRequest request) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", id));

        String trimmedName = request.name().trim();
        if (trimmedName.isEmpty()) {
            throw new BadRequestException("Name must not be blank");
        }

        if (departmentRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
            throw new ConflictException("Department with name '" + trimmedName + "' already exists");
        }

        department.setName(trimmedName);
        Department saved = departmentRepository.saveAndFlush(department);
        return DepartmentResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department", id));

        if (userRepository.existsByDepartmentId(id) || requestRepository.existsByDepartmentId(id)) {
            throw new ConflictException(
                    "Cannot delete department because it is in use by one or more users or service requests");
        }

        departmentRepository.delete(department);
        departmentRepository.flush();
    }

    private Specification<Department> buildSpecification(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }
            List<Predicate> predicates = new ArrayList<>();
            String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            predicates.add(cb.like(cb.lower(root.get("name")), pattern));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
