package com.servicehub.repository;

import com.servicehub.entity.RequestStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestStatusHistoryRepository extends JpaRepository<RequestStatusHistory, Long> {

    List<RequestStatusHistory> findByRequestIdOrderByCreatedAtAsc(Long requestId);
}
