package com.classmgmt.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录会话管理：内存态 token -> 过期时间，重启即全部失效。
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
    private final Map<String, Instant> sessions = new ConcurrentHashMap<>();

    public String create() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        sessions.put(token, Instant.now().plus(SESSION_TTL));
        evictIfNeeded();
        return token;
    }

    public boolean isValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Instant expiresAt = sessions.get(token);
        if (expiresAt == null) {
            return false;
        }
        Instant now = Instant.now();
        if (expiresAt.isBefore(now)) {
            sessions.remove(token);
            return false;
        }
        sessions.put(token, now.plus(SESSION_TTL));
        return true;
    }

    public void invalidate(String token) {
        if (token != null && !token.isBlank()) {
            sessions.remove(token);
        }
    }

    private void evictIfNeeded() {
        if (sessions.size() <= MAX_SESSIONS) {
            return;
        }
        sessions.entrySet().stream()
                .min(Comparator.comparing(Map.Entry::getValue))
                .ifPresent(e -> sessions.remove(e.getKey()));
    }
}
