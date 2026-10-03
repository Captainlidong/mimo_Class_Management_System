package com.classmgmt.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** 一条获奖记录：某批次里某位同学的奖学金及申请表收取状态。 */
@Entity
@Table(name = "scholarship_award")
public class ScholarshipAward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    /** 奖学金名称/等级，照录名单原文（如"国家励志奖学金"、"一等"）。 */
    @Column(name = "award_name", length = 128)
    private String awardName;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "form_received", nullable = false)
    private Boolean formReceived = false;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(length = 512)
    private String remark;

    @Column(name = "source_file", length = 128)
    private String sourceFile;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getAwardName() {
        return awardName;
    }

    public void setAwardName(String awardName) {
        this.awardName = awardName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Boolean getFormReceived() {
        return formReceived;
    }

    public void setFormReceived(Boolean formReceived) {
        this.formReceived = formReceived;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
