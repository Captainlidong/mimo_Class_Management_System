package com.classmgmt.service;

import com.classmgmt.domain.CollectionEntry;
import com.classmgmt.domain.CollectionTask;
import com.classmgmt.domain.Student;
import com.classmgmt.repo.CollectionEntryRepository;
import com.classmgmt.repo.CollectionTaskRepository;
import com.classmgmt.repo.StudentRepository;
import com.classmgmt.web.GlobalExceptionHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CollectionService {

    private final CollectionTaskRepository taskRepository;
    private final CollectionEntryRepository entryRepository;
    private final StudentRepository studentRepository;

    public CollectionService(CollectionTaskRepository taskRepository,
                             CollectionEntryRepository entryRepository,
                             StudentRepository studentRepository) {
        this.taskRepository = taskRepository;
        this.entryRepository = entryRepository;
        this.studentRepository = studentRepository;
    }

    // —— 任务 ——

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTasks() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (CollectionTask t : taskRepository.findAll()) {
            List<CollectionEntry> entries = entryRepository.findByTaskId(t.getId());
            long received = entries.stream().filter(e -> Boolean.TRUE.equals(e.getSubmitted())).count();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", t.getId());
            item.put("name", t.getName());
            item.put("remark", t.getRemark());
            item.put("deadline", t.getDeadline());
            item.put("createdAt", t.getCreatedAt());
            item.put("total", entries.size());
            item.put("received", received);
            result.add(item);
        }
        result.sort((a, b) -> ((Instant) b.get("createdAt")).compareTo((Instant) a.get("createdAt")));
        return result;
    }

    /** studentIds 为空/缺省时默认全班（按基准名单顺序）；显式传入则去重并校验存在。 */
    @Transactional
    public Map<String, Object> createTask(Map<String, Object> body) {
        String name = textOrNull(body.get("name"));
        if (name == null) {
            throw GlobalExceptionHandler.badRequest("任务名称不能为空");
        }
        List<Student> roster = studentRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, Student> rosterMap = new LinkedHashMap<>();
        for (Student s : roster) {
            rosterMap.put(s.getId(), s);
        }

        List<Student> members = new ArrayList<>();
        if (body.get("studentIds") instanceof List<?> ids && !ids.isEmpty()) {
            Set<Long> seen = new LinkedHashSet<>();
            for (Object o : ids) {
                Long sid;
                try {
                    sid = Long.valueOf(String.valueOf(o).trim());
                } catch (NumberFormatException e) {
                    throw GlobalExceptionHandler.badRequest("学生编号格式不正确：" + o);
                }
                if (!rosterMap.containsKey(sid)) {
                    throw GlobalExceptionHandler.badRequest("学生不存在或不在基准名单中：" + sid);
                }
                if (seen.add(sid)) {
                    members.add(rosterMap.get(sid));
                }
            }
            // 保持基准名单的显示顺序
            members.sort(Comparator.comparingInt(s -> roster.indexOf(s)));
        } else {
            members.addAll(roster);
        }
        if (members.isEmpty()) {
            throw GlobalExceptionHandler.badRequest("收集名单不能为空");
        }

        CollectionTask task = new CollectionTask();
        task.setName(name);
        task.setRemark(textOrNull(body.get("remark")));
        task.setDeadline(parseTime(textOrNull(body.get("deadline")), "截止日期"));
        taskRepository.save(task);

        for (Student s : members) {
            CollectionEntry e = new CollectionEntry();
            e.setTaskId(task.getId());
            e.setStudentId(s.getId());
            entryRepository.save(e);
        }
        return taskSummary(task.getId());
    }

    @Transactional
    public Map<String, Object> updateTask(Long id, Map<String, Object> body) {
        CollectionTask t = findTask(id);
        if (body.containsKey("name")) {
            String name = textOrNull(body.get("name"));
            if (name == null) {
                throw GlobalExceptionHandler.badRequest("任务名称不能为空");
            }
            t.setName(name);
        }
        if (body.containsKey("remark")) {
            t.setRemark(textOrNull(body.get("remark")));
        }
        if (body.containsKey("deadline")) {
            t.setDeadline(parseTime(textOrNull(body.get("deadline")), "截止日期"));
        }
        taskRepository.save(t);
        return taskSummary(id);
    }

    @Transactional
    public void deleteTask(Long id) {
        if (!taskRepository.existsById(id)) {
            throw GlobalExceptionHandler.notFound("任务不存在");
        }
        entryRepository.deleteByTaskId(id);
        taskRepository.deleteById(id);
    }

    // —— 详情 / 条目 ——

    @Transactional(readOnly = true)
    public Map<String, Object> taskSummary(Long taskId) {
        CollectionTask t = findTask(taskId);
        List<CollectionEntry> entries = entryRepository.findByTaskId(taskId);
        long received = entries.stream().filter(e -> Boolean.TRUE.equals(e.getSubmitted())).count();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", t.getId());
        item.put("name", t.getName());
        item.put("remark", t.getRemark());
        item.put("deadline", t.getDeadline());
        item.put("createdAt", t.getCreatedAt());
        item.put("total", entries.size());
        item.put("received", received);
        return item;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTask(Long id) {
        Map<String, Object> result = taskSummary(id);
        result.put("entries", listEntries(id));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listEntries(Long taskId) {
        Map<Long, Student> studentMap = new LinkedHashMap<>();
        for (Student s : studentRepository.findAll()) {
            studentMap.put(s.getId(), s);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CollectionEntry e : entryRepository.findByTaskId(taskId)) {
            Student s = studentMap.get(e.getStudentId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("taskId", e.getTaskId());
            item.put("studentId", e.getStudentId());
            item.put("name", s == null ? "（已删除）" : s.getName());
            item.put("studentNo", s == null ? null : s.getStudentNo());
            item.put("submitted", Boolean.TRUE.equals(e.getSubmitted()));
            item.put("submittedAt", e.getSubmittedAt());
            rows.add(item);
        }
        rows.sort(Comparator
                .comparingInt((Map<String, Object> r) -> NameNormalizer.trailingDigits((String) r.get("studentNo")))
                .thenComparing(r -> String.valueOf(r.get("studentNo") == null ? "" : r.get("studentNo"))));
        return rows;
    }

    /** 点击标签切换交表状态，自动记录/清除交表时间。 */
    @Transactional
    public Map<String, Object> setEntrySubmitted(Long entryId, Map<String, Object> body) {
        CollectionEntry e = entryRepository.findById(entryId)
                .orElseThrow(() -> GlobalExceptionHandler.notFound("名单条目不存在"));
        boolean submitted = body == null || !body.containsKey("submitted")
                ? true
                : Boolean.parseBoolean(String.valueOf(body.get("submitted")));
        e.setSubmitted(submitted);
        e.setSubmittedAt(submitted ? Instant.now() : null);
        entryRepository.save(e);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", e.getId());
        result.put("submitted", submitted);
        result.put("submittedAt", e.getSubmittedAt());
        return result;
    }

    /**
     * 粘贴名单批量标记：text 为已交同学名单（任意分隔）。返回 {marked, notFound}。
     */
    @Transactional
    public Map<String, Object> markByText(Long taskId, Map<String, Object> body) {
        findTask(taskId);
        boolean submitted = body.get("submitted") == null
                || Boolean.parseBoolean(String.valueOf(body.get("submitted")));
        List<String> tokens = NameNormalizer.parseTokens(textOrNull(body.get("text")));
        if (tokens.isEmpty()) {
            throw GlobalExceptionHandler.badRequest("请粘贴已交同学名单");
        }
        Map<String, CollectionEntry> byName = new LinkedHashMap<>();
        for (CollectionEntry e : entryRepository.findByTaskId(taskId)) {
            studentRepository.findById(e.getStudentId()).ifPresent(s ->
                    byName.put(NameNormalizer.normalize(s.getName()), e));
        }

        List<String> marked = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        for (String token : tokens) {
            CollectionEntry e = byName.get(token);
            if (e == null) {
                notFound.add(token);
                continue;
            }
            if (!Boolean.TRUE.equals(e.getSubmitted()) && submitted) {
                e.setSubmitted(true);
                e.setSubmittedAt(Instant.now());
                entryRepository.save(e);
            }
            marked.add(token);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("marked", marked);
        result.put("notFound", notFound);
        return result;
    }

    /** 供奖学金批次迁移等内部流程使用：按学生设置交表状态。 */
    @Transactional
    public void setSubmittedByStudent(Long taskId, Long studentId, boolean submitted) {
        entryRepository.findByTaskIdAndStudentId(taskId, studentId).ifPresent(e -> {
            e.setSubmitted(submitted);
            e.setSubmittedAt(submitted ? Instant.now() : null);
            entryRepository.save(e);
        });
    }

    /** 供迁移流程使用：为任务补建缺失学生的条目。 */
    @Transactional
    public void ensureEntry(Long taskId, Long studentId) {
        if (!entryRepository.existsByTaskIdAndStudentId(taskId, studentId)) {
            CollectionEntry e = new CollectionEntry();
            e.setTaskId(taskId);
            e.setStudentId(studentId);
            entryRepository.save(e);
        }
    }

    /** 向已有任务补充人员：新人员默认未交；已在任务里的自动跳过。返回 {added, skipped}。 */
    @Transactional
    public Map<String, Object> addStudents(Long taskId, Map<String, Object> body) {
        findTask(taskId);
        if (!(body.get("studentIds") instanceof List<?> ids) || ids.isEmpty()) {
            throw GlobalExceptionHandler.badRequest("请选择要添加的同学");
        }
        Map<Long, Student> rosterMap = new LinkedHashMap<>();
        for (Student s : studentRepository.findAll()) {
            rosterMap.put(s.getId(), s);
        }
        int added = 0;
        int skipped = 0;
        Set<Long> seen = new LinkedHashSet<>();
        for (Object o : ids) {
            Long sid;
            try {
                sid = Long.valueOf(String.valueOf(o).trim());
            } catch (NumberFormatException e) {
                throw GlobalExceptionHandler.badRequest("学生编号格式不正确：" + o);
            }
            if (!seen.add(sid)) {
                continue;
            }
            if (!rosterMap.containsKey(sid)) {
                throw GlobalExceptionHandler.badRequest("学生不存在或不在基准名单中：" + sid);
            }
            if (entryRepository.existsByTaskIdAndStudentId(taskId, sid)) {
                skipped++;
                continue;
            }
            CollectionEntry e = new CollectionEntry();
            e.setTaskId(taskId);
            e.setStudentId(sid);
            entryRepository.save(e);
            added++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("added", added);
        result.put("skipped", skipped);
        return result;
    }

    /** 从任务名单中移除一位同学（连同其交表记录）。 */
    @Transactional
    public void removeStudent(Long taskId, Long studentId) {
        CollectionEntry e = entryRepository.findByTaskIdAndStudentId(taskId, studentId)
                .orElseThrow(() -> GlobalExceptionHandler.notFound("该同学不在此任务的名单中"));
        entryRepository.delete(e);
    }

    // —— 工具 ——

    private Instant parseTime(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
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
        try {
            return LocalDateTime.parse(v.replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception e) {
            throw GlobalExceptionHandler.badRequest(field + "格式不正确：" + value);
        }
    }

    private String textOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String v = String.valueOf(value).trim();
        return v.isEmpty() ? null : v;
    }

    private CollectionTask findTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> GlobalExceptionHandler.notFound("任务不存在"));
    }
}
