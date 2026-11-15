package com.classmgmt.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "task_group")
public class GroupInfo {

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

    @Column(length = 255)
    private String remark;

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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
