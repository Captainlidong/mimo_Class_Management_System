package com.classmgmt.repo;

import com.classmgmt.domain.LongTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LongTaskRepository extends JpaRepository<LongTask, Long> {
}
