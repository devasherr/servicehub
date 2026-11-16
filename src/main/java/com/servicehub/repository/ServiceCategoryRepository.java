package com.servicehub.repository;

import com.servicehub.entity.ServiceCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long>,
        JpaSpecificationExecutor<ServiceCategory> {

    Optional<ServiceCategory> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<ServiceCategory> findByActiveTrueOrderByNameAsc();

    List<ServiceCategory> findByOrderByNameAsc();
}
