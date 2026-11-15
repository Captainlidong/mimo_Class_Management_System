package com.classmgmt.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** 待办事务：有明确期限的收集任务，或无固定期限、不能忘的跟进事项。 */
@Entity
@Table(name = "todo_item")
public class TodoItem {

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
    private String title;

    @Column(length = 512)
    private String detail;

    /** 截止时间，可空：无固定期限的跟进事项不填。 */
    @Column(name = "due_at")
    private Instant dueAt;

    private Boolean done = Boolean.FALSE;

    /** 优先级：high / mid（默认）/ low。 */
    @Column(nullable = false, length = 8)
    private String priority = "mid";

    /** 分类：教学 / 活动 / 资料 / 通知 / 财务 / 其他（默认）。 */
    @Column(nullable = false, length = 16)
    private String category = "其他";

    /** 重复规则：none（默认）/ daily / weekly / monthly。完成后自动生成下期。 */
    @Column(name = "repeat_rule", nullable = false, length = 8)
    private String repeatRule = "none";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "done_at")
    private Instant doneAt;

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public Boolean getDone() {
        return done;
    }

    public void setDone(Boolean done) {
        this.done = done;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getRepeatRule() {
        return repeatRule;
    }

    public void setRepeatRule(String repeatRule) {
        this.repeatRule = repeatRule;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getDoneAt() {
        return doneAt;
    }

    public void setDoneAt(Instant doneAt) {
        this.doneAt = doneAt;
    }
}
