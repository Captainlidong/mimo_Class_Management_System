package com.classmgmt.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 资料收集/分发任务：收培养计划、问卷、表格，或发通知附件、盖章材料等一次活动。 */
@Entity
@Table(name = "collection_task")
public class CollectionTask {

    public static final String KIND_COLLECT = "collect";
    public static final String KIND_DISTRIBUTE = "distribute";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属学期（换届归档维度），由 TermService.ensureDefault/创建路径写入。 */
    @Column(name = "term_id")
    private Long termId;

    public Long getTermId() {
        return termId;
    }

    public void setTermId(Long termId) {
        this.termId = termId;
    }
    @Column(nullable = false, length = 128)
    private String name;

    /** 任务类型：collect=收集材料（默认），distribute=分发材料。 */
    @Column(name = "kind", length = 16)
    private String kind;

    @Column(length = 512)
    private String remark;

    @Column
    private Instant deadline;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** 归一化：历史数据该列为 null，一律按收集处理。 */
    public String getKind() {
        return kind == null || kind.isBlank() ? KIND_COLLECT : kind;
    }

    public void setKind(String kind) {
        this.kind = KIND_DISTRIBUTE.equals(kind) ? KIND_DISTRIBUTE : KIND_COLLECT;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public void setDeadline(Instant deadline) {
        this.deadline = deadline;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
