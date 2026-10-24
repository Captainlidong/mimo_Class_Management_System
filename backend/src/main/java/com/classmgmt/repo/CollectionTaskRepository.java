package com.classmgmt.repo;

import com.classmgmt.domain.CollectionTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionTaskRepository extends JpaRepository<CollectionTask, Long> {
}
