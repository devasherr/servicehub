package com.servicehub.repository;

import com.servicehub.entity.ServiceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, Long> {

    @Query(value = "SELECT nextval('service_request_ref_seq')", nativeQuery = true)
    Long nextReferenceSequence();
}
