package com.classmgmt.repo;

import com.classmgmt.domain.OperationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    List<OperationLog> findAllByOrderByCreatedAtDescIdDesc();

    void deleteByCreatedAtBefore(Instant time);
}
