package com.classmgmt.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 修改稿的历史版本：每次修改 edited_text 前，旧内容自动存档到这张表。 */
@Entity
@Table(name = "announcement_version")
public class AnnouncementVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "announcement_id", nullable = false)
    private Long announcementId;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAnnouncementId() {
        return announcementId;
    }

    public void setAnnouncementId(Long announcementId) {
        this.announcementId = announcementId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getSavedAt() {
        return savedAt;
    }

    public void setSavedAt(Instant savedAt) {
        this.savedAt = savedAt;
    }
}
