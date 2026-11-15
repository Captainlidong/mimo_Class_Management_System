package com.classmgmt.repo;

import com.classmgmt.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByOrderByCreatedAtDescIdDesc();

    long countByReadFalse();

    void deleteByCreatedAtBefore(Instant time);

    void deleteByDedupeKey(String dedupeKey);
}
