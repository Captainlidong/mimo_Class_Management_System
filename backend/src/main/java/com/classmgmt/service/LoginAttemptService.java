package com.classmgmt.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 登录失败锁定：按 IP 的滑动窗口计数。
 * 15 分钟内密码错 5 次 -> 该 IP 被锁到最早一次失败滑出窗口为止（约 15 分钟）。
 * 仅统计"已通过验证码、错在密码"的尝试；验证码错误不计数。
 */
@Service
public class LoginAttemptService {

    public static final int MAX_FAILURES = 5;
    public static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public boolean isLocked(String ip) {
        Deque<Instant> attempts = failures.get(ip);
        if (attempts == null) {
            return false;
        }
        Instant threshold = Instant.now().minus(WINDOW);
        attempts.removeIf(t -> t.isBefore(threshold));
        if (attempts.isEmpty()) {
            failures.remove(ip);
            return false;
        }
        return attempts.size() >= MAX_FAILURES;
    }

    public long lockedForSeconds(String ip) {
        Deque<Instant> attempts = failures.get(ip);
        if (attempts == null || attempts.isEmpty()) {
            return WINDOW.toSeconds();
        }
        Instant oldest = attempts.peekFirst();
        if (oldest == null) {
            return WINDOW.toSeconds();
        }
        long remain = Duration.between(Instant.now(), oldest.plus(WINDOW)).toSeconds();
        return Math.max(remain, 1);
    }

    public void recordFailure(String ip) {
        failures.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>()).add(Instant.now());
    }

    public void clear(String ip) {
        failures.remove(ip);
    }
}
