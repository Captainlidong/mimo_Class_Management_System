package com.classmgmt.repo;

import com.classmgmt.domain.GroupInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<GroupInfo, Long> {
}
