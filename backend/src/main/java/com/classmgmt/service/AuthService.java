package com.classmgmt.service;

import com.classmgmt.domain.SessionRecord;
import com.classmgmt.repo.SessionRecordRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 登录会话管理：会话落库（session_record 表），服务器重启后有效会话依然在线。
 * 会话 7 天滑动过期，最多保留 10 个，超出时淘汰最旧会话
 * （单用户场景下，重新登录即可挤掉可能泄露的旧会话）。
 */
@Service
public class AuthService {

    public static final String COOKIE_NAME = "CMS_SESSION";
    public static final String TOKEN_HEADER = "X-Auth-Token";
    public static final Duration SESSION_TTL = Duration.ofDays(7);
    private static final int MAX_SESSIONS = 10;

    private final SecureRandom random = new SecureRandom();
    private final SessionRecordRepository repository;

    public AuthService(SessionRecordRepository repository) {
        this.repository = repository;
    }

    /** 启动时清理历史遗留的过期会话。 */
    @PostConstruct
    public void cleanupExpired() {
        repository.deleteByExpiresAtBefore(Instant.now());
    }

    @Transactional
    public String create() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        Instant now = Instant.now();
        SessionRecord record = new SessionRecord();
        record.setToken(token);
        record.setExpiresAt(now.plus(SESSION_TTL));
        record.setCreatedAt(now);
        repository.save(record);
        evictIfNeeded();
        return token;
    }

    /** 校验并滑动续期：每次成功校验把过期时间重置为 now + 7 天。 */
    @Transactional
    public boolean isValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Instant now = Instant.now();
        SessionRecord record = repository.findByToken(token).orElse(null);
        if (record == null) {
            return false;
        }
        if (record.getExpiresAt().isBefore(now)) {
            repository.delete(record);
            return false;
        }
        record.setExpiresAt(now.plus(SESSION_TTL));
        repository.save(record);
        return true;
    }

    @Transactional
    public void invalidate(String token) {
        if (token != null && !token.isBlank()) {
            repository.deleteByToken(token);
        }
    }

    /** 超出会话上限时淘汰最早过期的会话。 */
    private void evictIfNeeded() {
        if (repository.count() <= MAX_SESSIONS) {
            return;
        }
        var oldest = repository.findAllByOrderByExpiresAtAsc();
        if (!oldest.isEmpty()) {
            repository.delete(oldest.get(0));
        }
    }
}
