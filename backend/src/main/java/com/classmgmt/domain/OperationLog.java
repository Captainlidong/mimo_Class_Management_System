package com.classmgmt.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 操作日志：关键动作审计（名单替换/锁定、备份恢复、换届、删除类操作等）。 */
@Entity
@Table(name = "operation_log")
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 动作标识，如 roster.replace / backup.restore / term.create。 */
    @Column(nullable = false, length = 48)
    private String action;

    /** 所属模块：名单 / 筛查 / 收集 / 请假 / 奖学金 / 系统 等。 */
    @Column(nullable = false, length = 24)
    private String module;

    @Column(nullable = false, length = 255)
    private String summary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
