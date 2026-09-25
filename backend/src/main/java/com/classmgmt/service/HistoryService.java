package com.classmgmt.service;

import com.classmgmt.domain.CheckRecord;
import com.classmgmt.domain.LongTask;
import com.classmgmt.dto.CheckRecordSummaryDTO;
import com.classmgmt.dto.CheckResultDTO;
import com.classmgmt.repo.CheckRecordRepository;
import com.classmgmt.repo.LongTaskRepository;
import com.classmgmt.web.GlobalExceptionHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class HistoryService {

    private final CheckRecordRepository checkRecordRepository;
    private final LongTaskRepository longTaskRepository;
    private final CheckService checkService;

    public HistoryService(CheckRecordRepository checkRecordRepository,
                          LongTaskRepository longTaskRepository,
                          CheckService checkService) {
        this.checkRecordRepository = checkRecordRepository;
        this.longTaskRepository = longTaskRepository;
        this.checkService = checkService;
    }

    @Transactional(readOnly = true)
    public List<CheckRecordSummaryDTO> search(Long taskId, String q) {
        String query = (q == null || q.isBlank()) ? null : q.trim();
        List<CheckRecord> records = checkRecordRepository.search(taskId, query);
        List<CheckRecordSummaryDTO> list = new ArrayList<>();
        for (CheckRecord r : records) {
            list.add(toSummary(r));
        }
        return list;
    }

    @Transactional(readOnly = true)
    public CheckResultDTO detail(Long id) {
        CheckRecord record = checkRecordRepository.findById(id)
                .orElseThrow(() -> GlobalExceptionHandler.notFound("核对记录不存在"));
        return checkService.toDto(record);
    }

    @Transactional
    public CheckRecordSummaryDTO patch(Long id, Map<String, String> body) {
        CheckRecord record = checkRecordRepository.findById(id)
                .orElseThrow(() -> GlobalExceptionHandler.notFound("核对记录不存在"));
        if (body != null && body.containsKey("title")) {
            String title = body.get("title");
            if (title == null || title.isBlank()) {
                throw GlobalExceptionHandler.badRequest("任务名称不能为空");
            }
            record.setTitle(title.trim());
        }
        if (body != null && body.containsKey("remark")) {
            record.setRemark(body.get("remark"));
        }
        checkRecordRepository.save(record);
        return toSummary(record);
    }

    @Transactional
    public void delete(Long id) {
        if (!checkRecordRepository.existsById(id)) {
            throw GlobalExceptionHandler.notFound("核对记录不存在");
        }
        checkRecordRepository.deleteById(id);
    }

    private CheckRecordSummaryDTO toSummary(CheckRecord r) {
        CheckRecordSummaryDTO dto = new CheckRecordSummaryDTO();
        dto.setId(r.getId());
        dto.setTaskId(r.getTaskId());
        dto.setTitle(r.getTitle());
        dto.setCheckedAt(r.getCheckedAt() == null ? null : r.getCheckedAt().toString());
        dto.setParticipatedCount(r.getParticipatedCount());
        dto.setAbsentCount(r.getAbsentCount());
        dto.setInvalidCount(r.getInvalidCount());
        dto.setDuplicateCount(r.getDuplicateCount());
        dto.setBaselineCount(r.getBaselineCount());
        dto.setRemark(r.getRemark());
        return dto;
    }

    public String resolveTaskName(Long taskId) {
        if (taskId == null) {
            return "";
        }
        return longTaskRepository.findById(taskId).map(LongTask::getName).orElse("");
    }
}
