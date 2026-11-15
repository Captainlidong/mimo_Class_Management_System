package com.classmgmt.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "student")
public class Student {

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
    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "student_no", length = 32)
    private String studentNo;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "created_at")
    private Instant createdAt;

    /** 档案状态：in_school（在班，默认）/ left（离班）。离班为标记而非删除，历史引用保留。 */
    @Column(name = "status", length = 16)
    private String status;

    public static final String STATUS_IN = "in_school";
    public static final String STATUS_LEFT = "left";

    /** null 视为在班（兼容旧数据）。 */
    public boolean isInSchool() {
        return !STATUS_LEFT.equals(status);
    }

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

    public String getStudentNo() {
        return studentNo;
    }

    public void setStudentNo(String studentNo) {
        this.studentNo = studentNo;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status == null ? STATUS_IN : status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
