package com.classmgmt.repo;

import com.classmgmt.domain.SessionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SessionRecordRepository extends JpaRepository<SessionRecord, Long> {

    Optional<SessionRecord> findByToken(String token);

    void deleteByToken(String token);

    void deleteByExpiresAtBefore(Instant now);

    /** 全部会话按过期时间升序（最早的在前），用于超出上限时淘汰最旧会话。 */
    List<SessionRecord> findAllByOrderByExpiresAtAsc();
}
