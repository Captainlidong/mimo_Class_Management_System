package com.classmgmt.service;

import com.classmgmt.domain.ScholarshipAward;
import com.classmgmt.domain.ScholarshipBatch;
import com.classmgmt.domain.Student;
import com.classmgmt.repo.ScholarshipAwardRepository;
import com.classmgmt.repo.ScholarshipBatchRepository;
import com.classmgmt.repo.StudentRepository;
import com.classmgmt.web.GlobalExceptionHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScholarshipService {

    private final ScholarshipBatchRepository batchRepository;
    private final ScholarshipAwardRepository awardRepository;
    private final StudentRepository studentRepository;

    public ScholarshipService(ScholarshipBatchRepository batchRepository,
                              ScholarshipAwardRepository awardRepository,
                              StudentRepository studentRepository) {
        this.batchRepository = batchRepository;
        this.awardRepository = awardRepository;
        this.studentRepository = studentRepository;
    }

    // —— 批次 ——

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listBatches() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ScholarshipBatch b : batchRepository.findAll()) {
            List<ScholarshipAward> awards =
                    awardRepository.findByBatchIdOrderByCreatedAtAscIdAsc(b.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", b.getId());
            item.put("name", b.getName());
            item.put("remark", b.getRemark());
            item.put("createdAt", b.getCreatedAt());
            item.put("awardCount", awards.size());
            item.put("receivedCount", awards.stream().filter(a -> Boolean.TRUE.equals(a.getFormReceived())).count());
            item.put("levels", levelDistribution(awards));
            result.add(item);
        }
        result.sort((a, b) -> ((Instant) b.get("createdAt")).compareTo((Instant) a.get("createdAt")));
        return result;
    }

    @Transactional
    public Map<String, Object> createBatch(Map<String, Object> body) {
        String name = textOrNull(body.get("name"));
        if (name == null) {
            throw GlobalExceptionHandler.badRequest("批次名称不能为空");
        }
        ScholarshipBatch b = new ScholarshipBatch();
        b.setName(name);
        b.setRemark(textOrNull(body.get("remark")));
        batchRepository.save(b);
        return batchSummary(b.getId());
    }

    @Transactional
    public Map<String, Object> updateBatch(Long id, Map<String, Object> body) {
        ScholarshipBatch b = findBatch(id);
        if (body.containsKey("name")) {
            String name = textOrNull(body.get("name"));
            if (name == null) {
                throw GlobalExceptionHandler.badRequest("批次名称不能为空");
            }
            b.setName(name);
        }
        if (body.containsKey("remark")) {
            b.setRemark(textOrNull(body.get("remark")));
        }
        batchRepository.save(b);
        return batchSummary(id);
    }

    @Transactional
    public void deleteBatch(Long id) {
        if (!batchRepository.existsById(id)) {
            throw GlobalExceptionHandler.notFound("批次不存在");
        }
        awardRepository.deleteByBatchId(id);
        batchRepository.deleteById(id);
    }

    // —— 批次详情 ——

    @Transactional(readOnly = true)
    public Map<String, Object> batchSummary(Long batchId) {
        ScholarshipBatch b = findBatch(batchId);
        List<ScholarshipAward> awards = awardRepository.findByBatchIdOrderByCreatedAtAscIdAsc(batchId);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", b.getId());
        item.put("name", b.getName());
        item.put("remark", b.getRemark());
        item.put("createdAt", b.getCreatedAt());
        item.put("awardCount", awards.size());
        item.put("receivedCount", awards.stream().filter(a -> Boolean.TRUE.equals(a.getFormReceived())).count());
        item.put("levels", levelDistribution(awards));
        return item;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getBatch(Long id) {
        Map<String, Object> result = batchSummary(id);
        result.put("awards", listAwards(id));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAwards(Long batchId) {
        Map<Long, Student> studentMap = new LinkedHashMap<>();
        for (Student s : studentRepository.findAll()) {
            studentMap.put(s.getId(), s);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ScholarshipAward a : awardRepository.findByBatchIdOrderByCreatedAtAscIdAsc(batchId)) {
            Student s = studentMap.get(a.getStudentId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", a.getId());
            item.put("batchId", a.getBatchId());
            item.put("studentId", a.getStudentId());
            item.put("name", s == null ? "（已删除）" : s.getName());
            item.put("studentNo", s == null ? null : s.getStudentNo());
            item.put("awardName", a.getAwardName() == null ? "未注明" : a.getAwardName());
            item.put("amount", a.getAmount());
            item.put("formReceived", Boolean.TRUE.equals(a.getFormReceived()));
            item.put("receivedAt", a.getReceivedAt());
            item.put("remark", a.getRemark());
            item.put("sourceFile", a.getSourceFile());
            item.put("createdAt", a.getCreatedAt());
            rows.add(item);
        }
        // 按学号后两位升序排列（无学号的排最后；后两位相同时按完整学号排）
        rows.sort(Comparator
                .comparingInt((Map<String, Object> r) -> trailingTwoDigits((String) r.get("studentNo")))
                .thenComparing(r -> String.valueOf(r.get("studentNo") == null ? "" : r.get("studentNo"))));
        return rows;
    }

    /** 学号后两位数字，用于排序；无学号或学号里没有数字时排到最后。 */
    private int trailingTwoDigits(String studentNo) {
        if (studentNo == null) {
            return Integer.MAX_VALUE;
        }
        String digits = studentNo.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        String last2 = digits.length() <= 2 ? digits : digits.substring(digits.length() - 2);
        return Integer.parseInt(last2);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats(Long batchId) {
        List<ScholarshipAward> awards = awardRepository.findByBatchIdOrderByCreatedAtAscIdAsc(batchId);
        long received = awards.stream().filter(a -> Boolean.TRUE.equals(a.getFormReceived())).count();
        BigDecimal amountSum = awards.stream()
                .map(ScholarshipAward::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", awards.size());
        stats.put("received", received);
        stats.put("unreceived", awards.size() - received);
        stats.put("levels", levelDistribution(awards));
        stats.put("amountSum", amountSum);
        return stats;
    }

    private List<Map<String, Object>> levelDistribution(List<ScholarshipAward> awards) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ScholarshipAward a : awards) {
            String key = a.getAwardName() == null ? "未注明" : a.getAwardName();
            counts.merge(key, 1L, Long::sum);
        }
        List<Map<String, Object>> levels = new ArrayList<>();
        counts.entrySet().stream()
                .sorted((x, y) -> Long.compare(y.getValue(), x.getValue()))
                .forEach(e -> {
                    Map<String, Object> lv = new LinkedHashMap<>();
                    lv.put("name", e.getKey());
                    lv.put("count", e.getValue());
                    levels.add(lv);
                });
        return levels;
    }

    // —— 获奖记录 ——

    @Transactional
    public List<Map<String, Object>> addAward(Long batchId, Map<String, Object> body) {
        return addAward(batchId, body, null);
    }

    /** rows: [{studentId, awardName, amount}]；sourceFile 记录来源文件名。 */
    @Transactional
    public List<Map<String, Object>> addAward(Long batchId, Map<String, Object> body, String sourceFile) {
        ScholarshipBatch batch = findBatch(batchId);
        List<Map<String, Object>> created = new ArrayList<>();
        Object rowsObj = body.get("rows");
        if (rowsObj instanceof List<?> rows && !rows.isEmpty()) {
            for (Object o : rows) {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) o;
                created.add(insertAward(batch.getId(), row, sourceFile));
            }
        } else {
            created.add(insertAward(batch.getId(), body, sourceFile));
        }
        return created;
    }

    private Map<String, Object> insertAward(Long batchId, Map<String, Object> row, String sourceFile) {
        Long studentId = row.get("studentId") == null ? null : Long.valueOf(String.valueOf(row.get("studentId")));
        if (studentId == null || !studentRepository.existsById(studentId)) {
            throw GlobalExceptionHandler.badRequest("学生不存在或不在基准名单中");
        }
        ScholarshipAward a = new ScholarshipAward();
        a.setBatchId(batchId);
        a.setStudentId(studentId);
        String awardName = textOrNull(row.get("awardName"));
        a.setAwardName(awardName == null ? "未注明" : awardName);
        a.setAmount(parseAmountValue(row.get("amount")));
        a.setRemark(textOrNull(row.get("remark")));
        a.setSourceFile(sourceFile);
        awardRepository.save(a);
        return Map.of("id", a.getId(), "studentId", studentId);
    }

    @Transactional
    public Map<String, Object> updateAward(Long id, Map<String, Object> body) {
        ScholarshipAward a = findAward(id);
        if (body.containsKey("awardName")) {
            String awardName = textOrNull(body.get("awardName"));
            a.setAwardName(awardName == null ? "未注明" : awardName);
        }
        if (body.containsKey("amount")) {
            a.setAmount(parseAmountValue(body.get("amount")));
        }
        if (body.containsKey("remark")) {
            a.setRemark(textOrNull(body.get("remark")));
        }
        awardRepository.save(a);
        return Map.of("id", a.getId());
    }

    @Transactional
    public void deleteAward(Long id) {
        if (!awardRepository.existsById(id)) {
            throw GlobalExceptionHandler.notFound("获奖记录不存在");
        }
        awardRepository.deleteById(id);
    }

    @Transactional
    public Map<String, Object> markReceived(Long id, Map<String, Object> body) {
        ScholarshipAward a = findAward(id);
        boolean received = true;
        if (body != null && body.containsKey("received")) {
            received = Boolean.parseBoolean(String.valueOf(body.get("received")));
        }
        a.setFormReceived(received);
        a.setReceivedAt(received ? Instant.now() : null);
        awardRepository.save(a);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", a.getId());
        result.put("formReceived", received);
        result.put("receivedAt", a.getReceivedAt());
        return result;
    }

    // —— 工具 ——

    public BigDecimal parseAmountValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        if (raw instanceof Number num) {
            return new BigDecimal(num.toString());
        }
        String v = String.valueOf(raw).trim();
        if (v.isEmpty() || "-".equals(v)) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(v.replace(",", ""));
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }

    private String textOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String v = String.valueOf(value).trim();
        return v.isEmpty() ? null : v;
    }

    private ScholarshipBatch findBatch(Long id) {
        return batchRepository.findById(id)
                .orElseThrow(() -> GlobalExceptionHandler.notFound("批次不存在"));
    }

    private ScholarshipAward findAward(Long id) {
        return awardRepository.findById(id)
                .orElseThrow(() -> GlobalExceptionHandler.notFound("获奖记录不存在"));
    }
}
