package com.classmgmt.repo;

import com.classmgmt.domain.LeaveRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveRecordRepository extends JpaRepository<LeaveRecord, Long> {
}
