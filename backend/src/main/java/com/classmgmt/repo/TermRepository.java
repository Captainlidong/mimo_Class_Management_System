package com.classmgmt.repo;

import com.classmgmt.domain.Term;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TermRepository extends JpaRepository<Term, Long> {

    List<Term> findAllByOrderByCreatedAtDescIdDesc();
}
