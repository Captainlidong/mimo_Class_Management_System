package com.classmgmt.repo;

import com.classmgmt.domain.AnnouncementVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementVersionRepository extends JpaRepository<AnnouncementVersion, Long> {
    List<AnnouncementVersion> findByAnnouncementIdOrderBySavedAtDescIdDesc(Long announcementId);

    void deleteByAnnouncementId(Long announcementId);
}
