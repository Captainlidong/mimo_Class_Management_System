package com.classmgmt.service;

import com.classmgmt.domain.LeaveRecord;
import com.classmgmt.domain.Student;
import com.classmgmt.repo.LeaveRecordRepository;
import com.classmgmt.repo.StudentRepository;
import com.classmgmt.web.GlobalExceptionHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class LeaveService {

    public static final Set<String> TYPES =
            Set.of(LeaveRecord.TYPE_SICK, LeaveRecord.TYPE_PERSONAL,
                    LeaveRecord.TYPE_OFFICIAL, LeaveRecord.TYPE_OTHER);

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final LeaveRecordRepository leaveRecordRepository;
    private final StudentRepository studentRepository;

    public LeaveService(LeaveRecordRepository leaveRecordRepository, StudentRepository studentRepository) {
        this.leaveRecordRepository = leaveRecordRepository;
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long studentId, String status, String type,
                                          Instant from, Instant to) {
        String st = normalizeOrNull(status);
        String tp = normalizeOrNull(type);
        Map<Long, Student> studentMap = studentMap();
        List<Map<String, Object>> result = new ArrayList<>();
        for (LeaveRecord r : leaveRecordRepository.findAll()) {
            if (studentId != null && !studentId.equals(r.getStudentId())) {
                continue;
            }
            if (st != null && !st.equals(r.getStatus())) {
                continue;
            }
            if (tp != null && !tp.equals(r.getLeaveType())) {
                continue;
            }
            if (from != null && r.getStartTime().isBefore(from)) {
                continue;
            }
            if (to != null && r.getStartTime().isAfter(to)) {
                continue;
            }
            result.add(toRow(r, studentMap));
        }
        result.sort((a, b) -> {
            int byStart = ((Instant) b.get("startTime")).compareTo((Instant) a.get("startTime"));
            return byStart != 0 ? byStart : ((Long) b.get("id")).compareTo((Long) a.get("id"));
        });
        return result;
    }

    @Transactional
    public List<Map<String, Object>> create(Map<String, Object> body) {
        Set<Long> studentIds = parseStudentIds(body.get("studentIds"));
        Map<Long, Student> studentMap = studentMap();
        for (Long sid : studentIds) {
            if (!studentMap.containsKey(sid)) {
                throw GlobalExceptionHandler.badRequest("学生不存在或不在基准名单中：" + sid);
            }
        }
        String type = parseType(body.get("leaveType"));
        Instant start = parseTime(textOrNull(body.get("startTime")), "开始时间");
        Instant end = parseTime(textOrNull(body.get("endTime")), "结束时间");
        if (end.isBefore(start)) {
            throw GlobalExceptionHandler.badRequest("结束时间不能早于开始时间");
        }
        String reason = textOrNull(body.get("reason"));
        String approver = textOrNull(body.get("approver"));
        String remark = textOrNull(body.get("remark"));

        List<Map<String, Object>> created = new ArrayList<>();
        for (Long sid : studentIds) {
            LeaveRecord r = new LeaveRecord();
            r.setStudentId(sid);
            r.setLeaveType(type);
            r.setStartTime(start);
            r.setEndTime(end);
            r.setReason(reason);
            r.setApprover(approver);
            r.setRemark(remark);
            leaveRecordRepository.save(r);
            created.add(toRow(r, studentMap));
        }
        return created;
    }

    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> body) {
        LeaveRecord r = find(id);
        if (body.containsKey("leaveType")) {
            r.setLeaveType(parseType(body.get("leaveType")));
        }
        Instant start = body.containsKey("startTime")
                ? parseTime(textOrNull(body.get("startTime")), "开始时间") : r.getStartTime();
        Instant end = body.containsKey("endTime")
                ? parseTime(textOrNull(body.get("endTime")), "结束时间") : r.getEndTime();
        if (end.isBefore(start)) {
            throw GlobalExceptionHandler.badRequest("结束时间不能早于开始时间");
        }
        r.setStartTime(start);
        r.setEndTime(end);
        if (body.containsKey("reason")) {
            r.setReason(textOrNull(body.get("reason")));
        }
        if (body.containsKey("approver")) {
            r.setApprover(textOrNull(body.get("approver")));
        }
        if (body.containsKey("remark")) {
            r.setRemark(textOrNull(body.get("remark")));
        }
        leaveRecordRepository.save(r);
        return toRow(r, studentMap());
    }

    @Transactional
    public Map<String, Object> close(Long id, Map<String, Object> body) {
        LeaveRecord r = find(id);
        if (LeaveRecord.STATUS_CLOSED.equals(r.getStatus())) {
            throw GlobalExceptionHandler.conflict("该记录已销假");
        }
        r.setStatus(LeaveRecord.STATUS_CLOSED);
        String returnedAt = textOrNull(body == null ? null : body.get("returnedAt"));
        r.setReturnedAt(returnedAt == null ? Instant.now() : parseTime(returnedAt, "返校时间"));
        leaveRecordRepository.save(r);
        return toRow(r, studentMap());
    }

    @Transactional
    public Map<String, Object> reopen(Long id) {
        LeaveRecord r = find(id);
        if (!LeaveRecord.STATUS_CLOSED.equals(r.getStatus())) {
            throw GlobalExceptionHandler.badRequest("该记录尚未销假，无需撤销");
        }
        r.setStatus(LeaveRecord.STATUS_ACTIVE);
        r.setReturnedAt(null);
        leaveRecordRepository.save(r);
        return toRow(r, studentMap());
    }

    @Transactional
    public void delete(Long id) {
        if (!leaveRecordRepository.existsById(id)) {
            throw GlobalExceptionHandler.notFound("请假记录不存在");
        }
        leaveRecordRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        Map<Long, Student> studentMap = studentMap();
        Instant now = Instant.now();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();
        Instant monthEnd = today.withDayOfMonth(today.lengthOfMonth())
                .atTime(23, 59, 59).atZone(zone).toInstant();

        long activeCount = 0;
        long monthCount = 0;
        Map<Long, int[]> perStudent = new LinkedHashMap<>();
        for (LeaveRecord r : leaveRecordRepository.findAll()) {
            if (LeaveRecord.STATUS_ACTIVE.equals(r.getStatus())) {
                activeCount++;
            }
            if (!r.getStartTime().isBefore(monthStart) && !r.getStartTime().isAfter(monthEnd)) {
                monthCount++;
            }
            int[] counts = perStudent.computeIfAbsent(r.getStudentId(), k -> new int[2]);
            counts[0]++;
            if (LeaveRecord.STATUS_ACTIVE.equals(r.getStatus())) {
                counts[1]++;
            }
        }
        List<Map<String, Object>> byStudent = new ArrayList<>();
        for (Map.Entry<Long, int[]> e : perStudent.entrySet()) {
            Student s = studentMap.get(e.getKey());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("studentId", e.getKey());
            item.put("name", s == null ? "（已删除）" : s.getName());
            item.put("studentNo", s == null ? null : s.getStudentNo());
            item.put("total", e.getValue()[0]);
            item.put("active", e.getValue()[1]);
            byStudent.add(item);
        }
        byStudent.sort((a, b) -> Integer.compare((int) b.get("total"), (int) a.get("total")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeCount", activeCount);
        result.put("monthCount", monthCount);
        result.put("total", leaveRecordRepository.count());
        result.put("now", now);
        result.put("byStudent", byStudent);
        return result;
    }

    public String typeLabel(String type) {
        return switch (type == null ? "" : type) {
            case LeaveRecord.TYPE_SICK -> "病假";
            case LeaveRecord.TYPE_PERSONAL -> "事假";
            case LeaveRecord.TYPE_OFFICIAL -> "公假";
            default -> "其他";
        };
    }

    public String statusLabel(String status) {
        return LeaveRecord.STATUS_CLOSED.equals(status) ? "已销假" : "请假中";
    }

    public String display(Instant time) {
        return time == null ? "" : DISPLAY.format(time.atZone(ZoneId.systemDefault()));
    }

    /** 解析前端传来的时间：支持 ISO 立即时间、带偏移量、本地日期时间（按系统时区）。 */
    public Instant parseTime(String value, String field) {
        if (value == null || value.isBlank()) {
            throw GlobalExceptionHandler.badRequest(field + "不能为空");
        }
        String v = value.trim();
        try {
            return Instant.parse(v);
        } catch (Exception ignored) {
            // fall through
        }
        try {
            return OffsetDateTime.parse(v).toInstant();
        } catch (Exception ignored) {
            // fall through
        }
        v = v.replace(' ', 'T');
        try {
            return LocalDateTime.parse(v).atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            throw GlobalExceptionHandler.badRequest(field + "格式不正确：" + value);
        }
    }

    private Map<String, Object> toRow(LeaveRecord r, Map<Long, Student> studentMap) {
        Student s = studentMap.get(r.getStudentId());
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", r.getId());
        item.put("studentId", r.getStudentId());
        item.put("name", s == null ? "（已删除）" : s.getName());
        item.put("studentNo", s == null ? null : s.getStudentNo());
        item.put("leaveType", r.getLeaveType());
        item.put("startTime", r.getStartTime());
        item.put("endTime", r.getEndTime());
        item.put("reason", r.getReason());
        item.put("status", r.getStatus());
        item.put("approver", r.getApprover());
        item.put("returnedAt", r.getReturnedAt());
        item.put("remark", r.getRemark());
        item.put("createdAt", r.getCreatedAt());
        return item;
    }

    private Set<Long> parseStudentIds(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw GlobalExceptionHandler.badRequest("请选择请假学生");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Object o : list) {
            if (o == null) {
                continue;
            }
            try {
                ids.add(Long.valueOf(String.valueOf(o)));
            } catch (NumberFormatException e) {
                throw GlobalExceptionHandler.badRequest("学生编号格式不正确：" + o);
            }
        }
        if (ids.isEmpty()) {
            throw GlobalExceptionHandler.badRequest("请选择请假学生");
        }
        return ids;
    }

    private String parseType(Object raw) {
        String type = textOrNull(raw);
        if (type == null) {
            return LeaveRecord.TYPE_OTHER;
        }
        if (!TYPES.contains(type)) {
            throw GlobalExceptionHandler.badRequest("请假类型不正确：" + type);
        }
        return type;
    }

    private String textOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String v = String.valueOf(value).trim();
        return v.isEmpty() ? null : v;
    }

    private String normalizeOrNull(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private Map<Long, Student> studentMap() {
        Map<Long, Student> map = new LinkedHashMap<>();
        for (Student s : studentRepository.findAll()) {
            map.put(s.getId(), s);
        }
        return map;
    }

    private LeaveRecord find(Long id) {
        return leaveRecordRepository.findById(id)
                .orElseThrow(() -> GlobalExceptionHandler.notFound("请假记录不存在"));
    }
}
