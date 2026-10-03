package com.classmgmt.repo;

import com.classmgmt.domain.ScholarshipAward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScholarshipAwardRepository extends JpaRepository<ScholarshipAward, Long> {
    List<ScholarshipAward> findByBatchIdOrderByCreatedAtAscIdAsc(Long batchId);

    void deleteByBatchId(Long batchId);
}
