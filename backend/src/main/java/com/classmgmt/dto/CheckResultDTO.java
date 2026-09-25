package com.classmgmt.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CheckResultDTO {
    private String title;
    private String checkedAt;
    private List<String> participated = new ArrayList<>();
    private List<String> absent = new ArrayList<>();
    private List<String> invalid = new ArrayList<>();
    private List<String> duplicates = new ArrayList<>();
    private Map<String, Integer> counts = new LinkedHashMap<>();
    private Long recordId;
    private Long taskId;
    private String remark;
    private String rawText;
    private String saveError;

    public String getSaveError() {
        return saveError;
    }

    public void setSaveError(String saveError) {
        this.saveError = saveError;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(String checkedAt) {
        this.checkedAt = checkedAt;
    }

    public List<String> getParticipated() {
        return participated;
    }

    public void setParticipated(List<String> participated) {
        this.participated = participated;
    }

    public List<String> getAbsent() {
        return absent;
    }

    public void setAbsent(List<String> absent) {
        this.absent = absent;
    }

    public List<String> getInvalid() {
        return invalid;
    }

    public void setInvalid(List<String> invalid) {
        this.invalid = invalid;
    }

    public List<String> getDuplicates() {
        return duplicates;
    }

    public void setDuplicates(List<String> duplicates) {
        this.duplicates = duplicates;
    }

    public Map<String, Integer> getCounts() {
        return counts;
    }

    public void setCounts(Map<String, Integer> counts) {
        this.counts = counts;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
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

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }
}
