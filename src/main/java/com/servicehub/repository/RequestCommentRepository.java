package com.servicehub.repository;

import com.servicehub.entity.RequestComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestCommentRepository extends JpaRepository<RequestComment, Long> {

    List<RequestComment> findByRequestIdOrderByCreatedAtAsc(Long requestId);
}
