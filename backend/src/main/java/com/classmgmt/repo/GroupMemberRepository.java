package com.classmgmt.repo;

import com.classmgmt.domain.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    List<GroupMember> findByGroupId(Long groupId);

    List<GroupMember> findByStudentId(Long studentId);

    void deleteByGroupId(Long groupId);

    void deleteByGroupIdAndStudentId(Long groupId, Long studentId);
}
