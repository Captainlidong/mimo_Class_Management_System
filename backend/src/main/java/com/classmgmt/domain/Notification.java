package com.classmgmt.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 通知中心：待办到期 / 收集截止 / 请假销假等提醒聚合，登录拉取时惰性生成。 */
@Entity
@Table(name = "notification")
public class Notification {

    public static final String TYPE_TODO_DUE = "todo_due";
    public static final String TYPE_COLLECTION_DEADLINE = "collection_deadline";
    public static final String TYPE_LEAVE_END = "leave_end";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 类型：todo_due / collection_deadline / leave_end。 */
    @Column(nullable = false, length = 32)
    private String type;

    /** 幂等键：同类型 + 同一来源对象同一天只生成一条（type:refId:yyyyMMdd）。 */
    @Column(name = "dedupe_key", nullable = false, length = 64, unique = true)
    private String dedupeKey;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(length = 256)
    private String body;

    /** MySQL 里 read 是保留字，列名用 is_read。 */
    @Column(name = "is_read", nullable = false)
    private Boolean read = Boolean.FALSE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public void setDedupeKey(String dedupeKey) {
        this.dedupeKey = dedupeKey;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Boolean getRead() {
        return read;
    }

    public void setRead(Boolean read) {
        this.read = read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
