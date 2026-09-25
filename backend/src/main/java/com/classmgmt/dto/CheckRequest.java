package com.classmgmt.dto;

import jakarta.validation.constraints.NotBlank;

public class CheckRequest {
    @NotBlank(message = "粘贴内容不能为空")
    private String text;
    private boolean save;
    private String title;
    private Long taskId;
    private String remark;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isSave() {
        return save;
    }

    public void setSave(boolean save) {
        this.save = save;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
