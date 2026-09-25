package com.classmgmt.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "announcement")
public class Announcement {

    public static final String CATEGORY_NOTICE = "notice";
    public static final String CATEGORY_FILE = "file";
    public static final String CATEGORY_COPY = "copy";
    public static final String CATEGORY_OTHER = "other";

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_SENT = "sent";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(nullable = false, length = 32)
    private String category = CATEGORY_OTHER;

    @Column(length = 64)
    private String source;

    @Column(name = "original_text", columnDefinition = "LONGTEXT")
    private String originalText;

    @Column(name = "edited_text", columnDefinition = "LONGTEXT")
    private String editedText;

    @Column(length = 512)
    private String remark;

    @Column(nullable = false, length = 16)
    private String status = STATUS_DRAFT;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(nullable = false)
    private Boolean pinned = false;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public String getEditedText() {
        return editedText;
    }

    public void setEditedText(String editedText) {
        this.editedText = editedText;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Boolean getPinned() {
        return pinned;
    }

    public void setPinned(Boolean pinned) {
        this.pinned = pinned;
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
