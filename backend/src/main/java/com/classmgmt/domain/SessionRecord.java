package com.classmgmt.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 登录会话记录（session_record 表）。
 * 会话落库后服务器重启不再导致全员掉线；过期与上限淘汰逻辑与旧内存实现一致。
 */
@Entity
@Table(name = "session_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_session_token", columnNames = "token"))
public class SessionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
