package com.servicehub.service;

import com.servicehub.dto.category.CategoryResponse;
import com.servicehub.dto.category.CreateCategoryRequest;
import com.servicehub.dto.category.PatchCategoryRequest;
import com.servicehub.dto.category.UpdateCategoryRequest;
import com.servicehub.dto.common.PageResponse;
import com.servicehub.entity.ServiceCategory;
import com.servicehub.exception.BadRequestException;
import com.servicehub.exception.ConflictException;
import com.servicehub.exception.ResourceNotFoundException;
import com.servicehub.repository.ServiceCategoryRepository;
import com.servicehub.repository.ServiceRequestRepository;
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
public class ServiceCategoryService {

    private static final Map<String, String> SORTABLE_FIELDS = Map.of(
            "id", "id",
            "name", "name",
            "active", "active",
            "createdAt", "createdAt",
            "updatedAt", "updatedAt");

    private final ServiceCategoryRepository categoryRepository;
    private final ServiceRequestRepository requestRepository;

    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String trimmedName = request.name().trim();
        if (trimmedName.isEmpty()) {
            throw new BadRequestException("Name must not be blank");
        }

        if (categoryRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new ConflictException("Category with name '" + trimmedName + "' already exists");
        }

        ServiceCategory category = new ServiceCategory();
        category.setName(trimmedName);
        category.setDescription(trimToNull(request.description()));
        category.setActive(request.active() == null || request.active());

        ServiceCategory saved = categoryRepository.saveAndFlush(category);
        return CategoryResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        ServiceCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service category", id));
        return CategoryResponse.from(category);
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> list(String search, Boolean active,
                                              int page, int size,
                                              String sort, String direction) {
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        String sortProperty = SORTABLE_FIELDS.getOrDefault(sort, "id");
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortProperty));

        Specification<ServiceCategory> specification = buildSpecification(search, active);
        Page<ServiceCategory> categories = categoryRepository.findAll(specification, pageable);

        return PageResponse.from(categories.map(CategoryResponse::from));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll(Boolean active) {
        if (Boolean.TRUE.equals(active)) {
            return categoryRepository.findByActiveTrueOrderByNameAsc().stream()
                    .map(CategoryResponse::from)
                    .toList();
        }
        if (Boolean.FALSE.equals(active)) {
            Specification<ServiceCategory> spec = (root, query, cb) -> cb.isFalse(root.get("active"));
            return categoryRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "name")).stream()
                    .map(CategoryResponse::from)
                    .toList();
        }
        return categoryRepository.findByOrderByNameAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse update(Long id, UpdateCategoryRequest request) {
        ServiceCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service category", id));

        String trimmedName = request.name().trim();
        if (trimmedName.isEmpty()) {
            throw new BadRequestException("Name must not be blank");
        }

        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
            throw new ConflictException("Category with name '" + trimmedName + "' already exists");
        }

        category.setName(trimmedName);
        category.setDescription(trimToNull(request.description()));
        if (request.active() != null) {
            category.setActive(request.active());
        }

        ServiceCategory saved = categoryRepository.saveAndFlush(category);
        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse patch(Long id, PatchCategoryRequest request) {
        ServiceCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service category", id));

        if (request.name() != null) {
            String trimmedName = request.name().trim();
            if (trimmedName.isEmpty()) {
                throw new BadRequestException("Name must not be blank");
            }
            if (categoryRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
                throw new ConflictException("Category with name '" + trimmedName + "' already exists");
            }
            category.setName(trimmedName);
        }

        if (request.description() != null) {
            category.setDescription(trimToNull(request.description()));
        }

        if (request.active() != null) {
            category.setActive(request.active());
        }

        ServiceCategory saved = categoryRepository.saveAndFlush(category);
        return CategoryResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        ServiceCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service category", id));

        if (requestRepository.existsByCategoryId(id)) {
            throw new ConflictException(
                    "Cannot delete service category because it is in use by one or more service requests");
        }

        categoryRepository.delete(category);
        categoryRepository.flush();
    }

    private Specification<ServiceCategory> buildSpecification(String search, Boolean active) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern)));
            }

            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
