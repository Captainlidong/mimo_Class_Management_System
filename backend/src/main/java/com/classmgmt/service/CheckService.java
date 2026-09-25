package com.classmgmt.service;

import com.classmgmt.domain.CheckRecord;
import com.classmgmt.domain.Student;
import com.classmgmt.dto.CheckRequest;
import com.classmgmt.dto.CheckResultDTO;
import com.classmgmt.repo.CheckRecordRepository;
import com.classmgmt.repo.StudentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
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

    public CheckService(StudentRepository studentRepository,
                        CheckRecordRepository checkRecordRepository,
                        ObjectMapper objectMapper,
                        TransactionTemplate transactionTemplate) {
        this.studentRepository = studentRepository;
        this.checkRecordRepository = checkRecordRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public CheckResultDTO check(CheckRequest request) {
        List<Student> baseline = studentRepository.findAllByOrderBySortOrderAscIdAsc();
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
