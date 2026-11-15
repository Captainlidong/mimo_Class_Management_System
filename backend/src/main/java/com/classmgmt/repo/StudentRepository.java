package com.classmgmt.repo;

import com.classmgmt.domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentRepository extends JpaRepository<Student, Long> {
    List<Student> findAllByOrderBySortOrderAscIdAsc();

    List<Student> findAllByTermIdOrderBySortOrderAscIdAsc(Long termId);

    List<Student> findByTermId(Long termId);

    void deleteByTermId(Long termId);
}
