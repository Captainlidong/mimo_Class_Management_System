package com.classmgmt.repo;

import com.classmgmt.domain.CollectionEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CollectionEntryRepository extends JpaRepository<CollectionEntry, Long> {
    List<CollectionEntry> findByTaskId(Long taskId);

    Optional<CollectionEntry> findByTaskIdAndStudentId(Long taskId, Long studentId);

    boolean existsByTaskIdAndStudentId(Long taskId, Long studentId);

    void deleteByTaskId(Long taskId);
}
