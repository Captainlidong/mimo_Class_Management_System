package com.classmgmt.service;

import com.classmgmt.domain.CollectionTask;
import com.classmgmt.domain.LeaveRecord;
import com.classmgmt.domain.Notification;
import com.classmgmt.domain.Student;
import com.classmgmt.domain.TodoItem;
import com.classmgmt.repo.CollectionEntryRepository;
import com.classmgmt.repo.CollectionTaskRepository;
import com.classmgmt.repo.LeaveRecordRepository;
import com.classmgmt.repo.NotificationRepository;
import com.classmgmt.repo.StudentRepository;
import com.classmgmt.repo.TodoItemRepository;
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
import java.util.List;
import java.util.Map;

/**
 * 通知中心：待办到期 / 收集截止 / 请假销假的提醒聚合。
 * 登录拉取时惰性生成（无定时任务）：同一来源对象只生成一条通知（dedupeKey = type:refId），
 * 处理完点已读即可；90 天前旧通知自动清理。
 */
@Service
public class NotificationService {

    private static final long KEEP_DAYS = 90;

    private final NotificationRepository notificationRepository;
    private final TodoItemRepository todoItemRepository;
    private final CollectionTaskRepository collectionTaskRepository;
    private final CollectionEntryRepository collectionEntryRepository;
    private final LeaveRecordRepository leaveRecordRepository;
    private final StudentRepository studentRepository;
    private final TermService termService;

    public NotificationService(NotificationRepository notificationRepository,
                               TodoItemRepository todoItemRepository,
                               CollectionTaskRepository collectionTaskRepository,
                               CollectionEntryRepository collectionEntryRepository,
                               LeaveRecordRepository leaveRecordRepository,
                               StudentRepository studentRepository,
                               TermService termService) {
        this.notificationRepository = notificationRepository;
        this.todoItemRepository = todoItemRepository;
        this.collectionTaskRepository = collectionTaskRepository;
        this.collectionEntryRepository = collectionEntryRepository;
        this.leaveRecordRepository = leaveRecordRepository;
        this.studentRepository = studentRepository;
        this.termService = termService;
    }

    /** 列表（先惰性刷新再返回）。 */
    @Transactional
    public Map<String, Object> list() {
        refresh();
        List<Notification> all = notificationRepository.findAllByOrderByCreatedAtDescIdDesc();
        List<Map<String, Object>> items = new ArrayList<>();
        long unread = 0;
        for (Notification n : all) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("type", n.getType());
            m.put("title", n.getTitle());
            m.put("body", n.getBody() == null ? "" : n.getBody());
            m.put("read", Boolean.TRUE.equals(n.getRead()));
            m.put("createdAt", n.getCreatedAt());
            items.add(m);
            if (!Boolean.TRUE.equals(n.getRead())) {
                unread++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("unread", unread);
        return result;
    }

    @Transactional
    public void read(Long id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> GlobalExceptionHandler.notFound("通知不存在"));
        n.setRead(true);
        notificationRepository.save(n);
    }

    @Transactional
    public void readAll() {
        for (Notification n : notificationRepository.findAllByOrderByCreatedAtDescIdDesc()) {
            if (!Boolean.TRUE.equals(n.getRead())) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        }
    }

    /** 惰性生成：以未归档学期（工作学期）为口径扫描三类提醒。 */
    private void refresh() {
        notificationRepository.deleteByCreatedAtBefore(Instant.now().minus(java.time.Duration.ofDays(KEEP_DAYS)));
        Long activeTermId = termService.getActiveTermId();
        Instant now = Instant.now();
        java.util.Set<String> existingKeys = new java.util.HashSet<>();
        for (Notification n : notificationRepository.findAllByOrderByCreatedAtDescIdDesc()) {
            existingKeys.add(n.getDedupeKey());
        }

        // 1. 待办：未完成且（已过期 或 今日到期）
        for (TodoItem t : todoItemRepository.findAll()) {
            if (Boolean.TRUE.equals(t.getDone()) || t.getDueAt() == null) {
                continue;
            }
            boolean sameTerm = t.getTermId() == null || t.getTermId().equals(activeTermId);
            boolean overdue = t.getDueAt().isBefore(now);
            boolean dueToday = LocalDate.ofInstant(t.getDueAt(), ZoneId.systemDefault())
                    .equals(LocalDate.now(ZoneId.systemDefault()));
            if (!sameTerm || (!overdue && !dueToday)) {
                continue;
            }
            emit(existingKeys, Notification.TYPE_TODO_DUE, "todo:" + t.getId(),
                    (overdue ? "待办已过期：" : "待办今天到期：") + t.getTitle(),
                    (overdue ? "已过期 · " : "今日到期 · ") + fmt(t.getDueAt()));
        }

        // 2. 收集任务：设了截止时间且已过/当天，且尚未收齐
        for (CollectionTask task : collectionTaskRepository.findAll()) {
            if (task.getDeadline() == null) {
                continue;
            }
            boolean sameTerm = task.getTermId() == null || task.getTermId().equals(activeTermId);
            boolean dueOrOver = !task.getDeadline().isAfter(now)
                    || LocalDate.ofInstant(task.getDeadline(), ZoneId.systemDefault())
                    .equals(LocalDate.now(ZoneId.systemDefault()));
            if (!sameTerm || !dueOrOver) {
                continue;
            }
            long total = collectionEntryRepository.findByTaskId(task.getId()).size();
            long received = collectionEntryRepository.findByTaskId(task.getId())
                    .stream().filter(e -> Boolean.TRUE.equals(e.getSubmitted())).count();
            if (received >= total) {
                continue;
            }
            emit(existingKeys, Notification.TYPE_COLLECTION_DEADLINE, "collection:" + task.getId(),
                    "收集任务已到截止：" + task.getName(),
                    "已交 " + received + " / " + total + "，截止 " + fmt(task.getDeadline()));
        }

        // 3. 请假：已结束仍未销假
        for (LeaveRecord r : leaveRecordRepository.findAll()) {
            if (!LeaveRecord.STATUS_ACTIVE.equals(r.getStatus()) || r.getEndTime() == null
                    || !r.getEndTime().isBefore(now)) {
                continue;
            }
            Student s = r.getStudentId() == null ? null : studentRepository.findById(r.getStudentId()).orElse(null);
            String name = s == null ? "（已删除）" : s.getName();
            emit(existingKeys, Notification.TYPE_LEAVE_END, "leave:" + r.getId(),
                    "请假已结束未销假：" + name,
                    r.getReason() == null ? "" : r.getReason());
        }
    }

    private void emit(java.util.Set<String> existingKeys, String type, String refKey, String title, String body) {
        String dedupeKey = type + ":" + refKey;
        if (existingKeys.contains(dedupeKey)) {
            return;
        }
        Notification n = new Notification();
        n.setType(type);
        n.setDedupeKey(dedupeKey);
        n.setTitle(title);
        n.setBody(body == null || body.isBlank() ? null : body);
        n.setRead(false);
        n.setCreatedAt(Instant.now());
        notificationRepository.save(n);
        existingKeys.add(dedupeKey);
    }

    private String fmt(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
    }
}
