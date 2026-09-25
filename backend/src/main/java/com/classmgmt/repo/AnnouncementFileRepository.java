package com.classmgmt.repo;

import com.classmgmt.domain.AnnouncementFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementFileRepository extends JpaRepository<AnnouncementFile, Long> {
    List<AnnouncementFile> findByAnnouncementIdOrderByUploadedAtAscIdAsc(Long announcementId);

    void deleteByAnnouncementId(Long announcementId);
}
