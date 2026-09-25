package com.classmgmt.repo;

import com.classmgmt.domain.CheckRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CheckRecordRepository extends JpaRepository<CheckRecord, Long> {

    @Query("select c from CheckRecord c where (:taskId is null or c.taskId = :taskId) "
            + "and (:q is null or lower(c.title) like lower(concat('%', :q, '%'))) "
            + "order by c.checkedAt desc")
    List<CheckRecord> search(@Param("taskId") Long taskId, @Param("q") String q);

    List<CheckRecord> findByTaskIdOrderByCheckedAtDesc(Long taskId);

    long countByTaskId(Long taskId);
}
