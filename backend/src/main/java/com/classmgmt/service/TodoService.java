package com.classmgmt.service;

import com.classmgmt.domain.TodoItem;
import com.classmgmt.repo.TodoItemRepository;
import com.classmgmt.web.GlobalExceptionHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 待办事务：提醒班长"什么时候要收齐什么"以及"什么事还没解决不能忘"。 */
@Service
public class TodoService {

    private final TodoItemRepository repository;

    public TodoService(TodoItemRepository repository) {
        this.repository = repository;
    }

    /**
     * 列表排序：未完成在前（有截止时间的按时间升序，已过期自然排最前；无期限的排其后），
     * 已完成的按完成时间倒序沉底。每条附 overdue / dueToday 计算标记供前端着色。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list() {
        Instant now = Instant.now();
        List<TodoItem> all = repository.findAll();
        List<TodoItem> undone = new ArrayList<>();
        List<TodoItem> done = new ArrayList<>();
        for (TodoItem t : all) {
            (Boolean.TRUE.equals(t.getDone()) ? done : undone).add(t);
        }
        undone.sort(Comparator
                .comparing(TodoItem::getDueAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(TodoItem::getId));
        done.sort(Comparator
                .comparing(TodoItem::getDoneAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(TodoItem::getId, Comparator.reverseOrder()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (TodoItem t : undone) {
            result.add(toMap(t, now));
        }
        for (TodoItem t : done) {
            result.add(toMap(t, now));
        }
        return result;
    }

    private Map<String, Object> toMap(TodoItem t, Instant now) {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean done = Boolean.TRUE.equals(t.getDone());
        m.put("id", t.getId());
        m.put("title", t.getTitle());
        m.put("detail", t.getDetail() == null ? "" : t.getDetail());
        m.put("dueAt", t.getDueAt());
        m.put("done", done);
        m.put("overdue", !done && t.getDueAt() != null && t.getDueAt().isBefore(now));
        m.put("dueToday", !done && t.getDueAt() != null && isSameLocalDay(t.getDueAt(), now));
        m.put("createdAt", t.getCreatedAt());
        m.put("doneAt", t.getDoneAt());
        return m;
    }

    private boolean isSameLocalDay(Instant a, Instant b) {
        ZoneId zone = ZoneId.systemDefault();
        return LocalDate.ofInstant(a, zone).equals(LocalDate.ofInstant(b, zone));
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        String title = textOrNull(body.get("title"));
        if (title == null) {
            throw GlobalExceptionHandler.badRequest("待办标题不能为空");
        }
        TodoItem t = new TodoItem();
        t.setTitle(title);
        applyOptionalFields(t, body);
        t.setCreatedAt(Instant.now());
        repository.save(t);
        return itemMap(t);
    }

    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> body) {
        TodoItem t = repository.findById(id)
                .orElseThrow(() -> GlobalExceptionHandler.notFound("待办不存在"));
        if (body == null
                || (!body.containsKey("title") && !body.containsKey("detail")
                && !body.containsKey("dueAt") && !body.containsKey("done"))) {
            throw GlobalExceptionHandler.badRequest("没有需要更新的字段");
        }
        if (body.containsKey("title")) {
            String title = textOrNull(body.get("title"));
            if (title == null) {
                throw GlobalExceptionHandler.badRequest("待办标题不能为空");
            }
            t.setTitle(title);
        }
        applyOptionalFields(t, body);
        if (body.containsKey("done")) {
            Object v = body.get("done");
            boolean done = Boolean.TRUE.equals(v) || "true".equalsIgnoreCase(String.valueOf(v));
            t.setDone(done);
            t.setDoneAt(done ? Instant.now() : null);
        }
        repository.save(t);
        return itemMap(t);
    }

    private void applyOptionalFields(TodoItem t, Map<String, Object> body) {
        if (body.containsKey("detail")) {
            t.setDetail(textOrNull(body.get("detail")));
        }
        if (body.containsKey("dueAt")) {
            t.setDueAt(parseTime(textOrNull(body.get("dueAt")), "截止时间"));
        }
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw GlobalExceptionHandler.notFound("待办不存在");
        }
        repository.deleteById(id);
    }

    private Map<String, Object> itemMap(TodoItem t) {
        return toMap(t, Instant.now());
    }

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
}
