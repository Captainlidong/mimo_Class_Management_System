package com.classmgmt.service;

import com.classmgmt.domain.CheckRecord;
import com.classmgmt.domain.Student;
import com.classmgmt.dto.CheckRequest;
import com.classmgmt.dto.CheckResultDTO;
import com.classmgmt.repo.CheckRecordRepository;
import com.classmgmt.repo.StudentRepository;
import com.classmgmt.web.GlobalExceptionHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CheckService {

    private final StudentRepository studentRepository;
    private final CheckRecordRepository checkRecordRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final TermService termService;
    private final CollectionService collectionService;
    private final OperationLogService operationLogService;

    public CheckService(StudentRepository studentRepository,
                        CheckRecordRepository checkRecordRepository,
                        ObjectMapper objectMapper,
                        TransactionTemplate transactionTemplate,
                        TermService termService,
                        CollectionService collectionService,
                        OperationLogService operationLogService) {
        this.studentRepository = studentRepository;
        this.checkRecordRepository = checkRecordRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.termService = termService;
        this.collectionService = collectionService;
        this.operationLogService = operationLogService;
    }

    public CheckResultDTO check(CheckRequest request) {
        termService.ensureActiveView();
        Long activeTermId = termService.getActiveTermId();
        List<Student> baseline = studentRepository.findAllByTermIdOrderBySortOrderAscIdAsc(activeTermId);
        CheckResultDTO result = buildResult(request.getText(), baseline);
        String title = request.getTitle() == null || request.getTitle().isBlank()
                ? "名单核对 " + Instant.now()
                : request.getTitle().trim();
        result.setTitle(title);
        result.setTaskId(request.getTaskId());
        result.setRemark(request.getRemark());
        result.setRawText(request.getText());

        if (request.isSave()) {
            try {
                Long id = transactionTemplate.execute(status -> {
                    CheckRecord record = new CheckRecord();
                    record.setTaskId(request.getTaskId());
                    record.setTermId(activeTermId);
                    record.setTitle(title);
                    record.setCheckedAt(Instant.parse(result.getCheckedAt()));
                    record.setRawText(truncate(request.getText(), 60_000));
                    record.setParticipatedJson(truncate(writeJson(result.getParticipated()), 1_000_000));
                    record.setAbsentJson(truncate(writeJson(result.getAbsent()), 1_000_000));
                    record.setInvalidJson(truncate(writeJson(result.getInvalid()), 1_000_000));
                    record.setDuplicatesJson(truncate(writeJson(result.getDuplicates()), 1_000_000));
                    record.setParticipatedCount(result.getParticipated().size());
                    record.setAbsentCount(result.getAbsent().size());
                    record.setInvalidCount(result.getInvalid().size());
                    record.setDuplicateCount(result.getDuplicates().size());
                    record.setBaselineCount(baseline.size());
                    record.setRemark(request.getRemark());
                    checkRecordRepository.saveAndFlush(record);
                    return record.getId();
                });
                result.setRecordId(id);
            } catch (Exception ex) {
                result.setSaveError("历史保存失败：" + ex.getMessage());
            }
        }
        return result;
    }

    public CheckResultDTO evaluate(String text, List<Student> baseline) {
        return buildResult(text, baseline);
    }

    private String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    private CheckResultDTO buildResult(String text, List<Student> baseline) {
        Map<String, Student> byName = new LinkedHashMap<>();
        Map<String, Student> byNo = new LinkedHashMap<>();
        List<String> baselineOrder = new ArrayList<>();
        for (Student s : baseline) {
            String key = NameNormalizer.normalize(s.getName());
            if (!key.isEmpty()) {
                byName.putIfAbsent(key, s);
                baselineOrder.add(key);
            }
            if (s.getStudentNo() != null && !s.getStudentNo().isBlank()) {
                byNo.put(NameNormalizer.normalize(s.getStudentNo()), s);
            }
        }

        List<String> tokens = NameNormalizer.parseTokens(text);
        Set<String> participatedNames = new LinkedHashSet<>();
        Set<String> invalid = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();

        for (String token : tokens) {
            if (NameNormalizer.isBlankOrSymbol(token)) {
                invalid.add(token);
                continue;
            }
            Student matched = byName.get(token);
            if (matched == null) {
                matched = byNo.get(token);
            }
            if (matched == null) {
                invalid.add(token);
                continue;
            }
            String nameKey = NameNormalizer.normalize(matched.getName());
            if (!participatedNames.add(nameKey)) {
                duplicates.add(nameKey);
            }
        }

        List<String> absent = new ArrayList<>();
        for (String baselineName : baselineOrder) {
            if (!participatedNames.contains(baselineName)) {
                absent.add(baselineName);
            }
        }

        CheckResultDTO dto = new CheckResultDTO();
        dto.setCheckedAt(Instant.now().toString());
        dto.setParticipated(new ArrayList<>(participatedNames));
        dto.setAbsent(absent);
        dto.setInvalid(new ArrayList<>(invalid));
        dto.setDuplicates(new ArrayList<>(duplicates));
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("participated", dto.getParticipated().size());
        counts.put("absent", dto.getAbsent().size());
        counts.put("invalid", dto.getInvalid().size());
        counts.put("duplicates", dto.getDuplicates().size());
        counts.put("baseline", baseline.size());
        dto.setCounts(counts);
        return dto;
    }

    public CheckResultDTO toDto(CheckRecord record) {
        CheckResultDTO dto = new CheckResultDTO();
        dto.setRecordId(record.getId());
        dto.setTaskId(record.getTaskId());
        dto.setTitle(record.getTitle());
        dto.setCheckedAt(record.getCheckedAt() == null ? null : record.getCheckedAt().toString());
        dto.setParticipated(readJson(record.getParticipatedJson()));
        dto.setAbsent(readJson(record.getAbsentJson()));
        dto.setInvalid(readJson(record.getInvalidJson()));
        dto.setDuplicates(readJson(record.getDuplicatesJson()));
        dto.setRemark(record.getRemark());
        dto.setRawText(record.getRawText());
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("participated", record.getParticipatedCount() == null ? 0 : record.getParticipatedCount());
        counts.put("absent", record.getAbsentCount() == null ? 0 : record.getAbsentCount());
        counts.put("invalid", record.getInvalidCount() == null ? 0 : record.getInvalidCount());
        counts.put("duplicates", record.getDuplicateCount() == null ? 0 : record.getDuplicateCount());
        counts.put("baseline", record.getBaselineCount() == null ? 0 : record.getBaselineCount());
        dto.setCounts(counts);
        return dto;
    }

    private String writeJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? List.of() : list);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 把历史筛查记录的未参与名单一键转成收集任务（新任务归属当前学期）。 */
    @Transactional
    public Map<String, Object> convertRecordToCollection(Long recordId) {
        CheckRecord record = checkRecordRepository.findById(recordId)
                .orElseThrow(() -> GlobalExceptionHandler.notFound("核对记录不存在"));
        List<String> absentNames = readJson(record.getAbsentJson());
        Long rosterTermId = record.getTermId() == null ? termService.getActiveTermId() : record.getTermId();
        Map<String, Long> byName = new LinkedHashMap<>();
        for (Student s : studentRepository.findAllByTermIdOrderBySortOrderAscIdAsc(rosterTermId)) {
            byName.putIfAbsent(NameNormalizer.normalize(s.getName()), s.getId());
        }
        List<Long> studentIds = new ArrayList<>();
        List<String> unmatched = new ArrayList<>();
        for (String raw : absentNames) {
            String name = NameNormalizer.normalize(raw);
            Long sid = byName.get(name);
            if (sid == null) {
                unmatched.add(raw);
            } else if (!studentIds.contains(sid)) {
                studentIds.add(sid);
            }
        }
        if (studentIds.isEmpty()) {
            throw GlobalExceptionHandler.badRequest("未参与名单中没有能匹配到基准名单的学生，无法转收集任务");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", (record.getTitle() == null ? "未命名筛查" : record.getTitle()) + " 补交收集");
        body.put("kind", "collect");
        body.put("studentIds", studentIds);
        body.put("remark", "由筛查记录「" + record.getTitle() + "」转换，未参与 " + absentNames.size() + " 人");
        Map<String, Object> task = collectionService.createTask(body);
        operationLogService.log("check.convert", "筛查",
                "筛查记录「" + record.getTitle() + "」转为收集任务：未参与 " + studentIds.size()
                        + " 人" + (unmatched.isEmpty() ? "" : "，" + unmatched.size() + " 人未匹配到名单"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", ((Number) task.get("id")).longValue());
        result.put("name", task.get("name"));
        result.put("total", task.get("total"));
        result.put("unmatched", unmatched);
        return result;
    }

    private List<String> readJson(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
