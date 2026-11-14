package com.servicehub.repository;

import com.servicehub.entity.RequestComment;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestCommentRepository extends JpaRepository<RequestComment, Long> {

    List<RequestComment> findByRequestIdOrderByCreatedAtAsc(Long requestId);

    @EntityGraph(attributePaths = "author")
    Page<RequestComment> findByRequestId(Long requestId, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Page<RequestComment> findByRequestIdAndInternalFalse(Long requestId, Pageable pageable);
}
