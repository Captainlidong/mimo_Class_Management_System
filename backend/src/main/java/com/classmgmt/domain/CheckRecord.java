package com.classmgmt.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "check_record")
public class CheckRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id")
    private Long taskId;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @Column(name = "raw_text", columnDefinition = "LONGTEXT")
    private String rawText;

    @Column(name = "participated_json", columnDefinition = "LONGTEXT")
    private String participatedJson;

    @Column(name = "absent_json", columnDefinition = "LONGTEXT")
    private String absentJson;

    @Column(name = "invalid_json", columnDefinition = "LONGTEXT")
    private String invalidJson;

    @Column(name = "duplicates_json", columnDefinition = "LONGTEXT")
    private String duplicatesJson;

    @Column(name = "participated_count")
    private Integer participatedCount;

    @Column(name = "absent_count")
    private Integer absentCount;

    @Column(name = "invalid_count")
    private Integer invalidCount;

    @Column(name = "duplicate_count")
    private Integer duplicateCount;

    @Column(name = "baseline_count")
    private Integer baselineCount;

    @Column(length = 255)
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(Instant checkedAt) {
        this.checkedAt = checkedAt;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getParticipatedJson() {
        return participatedJson;
    }

    public void setParticipatedJson(String participatedJson) {
        this.participatedJson = participatedJson;
    }

    public String getAbsentJson() {
        return absentJson;
    }

    public void setAbsentJson(String absentJson) {
        this.absentJson = absentJson;
    }

    public String getInvalidJson() {
        return invalidJson;
    }

    public void setInvalidJson(String invalidJson) {
        this.invalidJson = invalidJson;
    }

    public String getDuplicatesJson() {
        return duplicatesJson;
    }

    public void setDuplicatesJson(String duplicatesJson) {
        this.duplicatesJson = duplicatesJson;
    }

    public Integer getParticipatedCount() {
        return participatedCount;
    }

    public void setParticipatedCount(Integer participatedCount) {
        this.participatedCount = participatedCount;
    }

    public Integer getAbsentCount() {
        return absentCount;
    }

    public void setAbsentCount(Integer absentCount) {
        this.absentCount = absentCount;
    }

    public Integer getInvalidCount() {
        return invalidCount;
    }

    public void setInvalidCount(Integer invalidCount) {
        this.invalidCount = invalidCount;
    }

    public Integer getDuplicateCount() {
        return duplicateCount;
    }

    public void setDuplicateCount(Integer duplicateCount) {
        this.duplicateCount = duplicateCount;
    }

    public Integer getBaselineCount() {
        return baselineCount;
    }

    public void setBaselineCount(Integer baselineCount) {
        this.baselineCount = baselineCount;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
