package com.classmgmt.service;

import com.classmgmt.domain.LeaveRecord;
import com.classmgmt.dto.CheckRecordSummaryDTO;
import com.classmgmt.repo.CollectionEntryRepository;
import com.classmgmt.repo.CollectionTaskRepository;
import com.classmgmt.repo.LeaveRecordRepository;
import com.classmgmt.repo.StudentRepository;
import com.classmgmt.repo.TodoItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 工作台聚合：把散在各模块的关键数字汇成一屏（桌面端首页与移动端首页共用口径）。 */
@Service
public class DashboardService {

    private final StudentRepository studentRepository;
    private final StudentService studentService;
    private final TodoItemRepository todoItemRepository;
    private final CollectionTaskRepository collectionTaskRepository;
    private final CollectionEntryRepository collectionEntryRepository;
    private final LeaveRecordRepository leaveRecordRepository;
    private final HistoryService historyService;
    private final TermService termService;

    public DashboardService(StudentRepository studentRepository,
                            StudentService studentService,
                            TodoItemRepository todoItemRepository,
                            CollectionTaskRepository collectionTaskRepository,
                            CollectionEntryRepository collectionEntryRepository,
                            LeaveRecordRepository leaveRecordRepository,
                            HistoryService historyService,
                            TermService termService) {
        this.studentRepository = studentRepository;
        this.studentService = studentService;
        this.todoItemRepository = todoItemRepository;
        this.collectionTaskRepository = collectionTaskRepository;
        this.collectionEntryRepository = collectionEntryRepository;
        this.leaveRecordRepository = leaveRecordRepository;
        this.historyService = historyService;
        this.termService = termService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        Instant now = Instant.now();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();

        // 全部按浏览学期过滤（切到历史学期时，看板展示该学期数据）
        java.util.function.Predicate<com.classmgmt.domain.TodoItem> inTerm =
                t -> termService.matchesView(t.getTermId());
        List<com.classmgmt.domain.TodoItem> todosAll = todoItemRepository.findAll().stream()
                .filter(inTerm).toList();

        Map<String, Object> todos = new LinkedHashMap<>();
        todos.put("pending", todosAll.stream().filter(t -> !Boolean.TRUE.equals(t.getDone())).count());
        todos.put("overdue", todosAll.stream()
                .filter(t -> !Boolean.TRUE.equals(t.getDone()) && t.getDueAt() != null && t.getDueAt().isBefore(now))
                .count());
        todos.put("dueToday", todosAll.stream()
                .filter(t -> !Boolean.TRUE.equals(t.getDone()) && t.getDueAt() != null
                        && !t.getDueAt().isBefore(dayStart) && t.getDueAt().isBefore(dayEnd))
                .count());

        List<com.classmgmt.domain.CollectionTask> tasks = collectionTaskRepository.findAll().stream()
                .filter(t -> termService.matchesView(t.getTermId())).toList();
        long totalEntries = 0;
        long submittedEntries = 0;
        for (com.classmgmt.domain.CollectionTask task : tasks) {
            List<com.classmgmt.domain.CollectionEntry> entries = collectionEntryRepository.findByTaskId(task.getId());
            totalEntries += entries.size();
            submittedEntries += entries.stream().filter(e -> Boolean.TRUE.equals(e.getSubmitted())).count();
        }
        Map<String, Object> collections = new LinkedHashMap<>();
        collections.put("tasks", (long) tasks.size());
        collections.put("totalEntries", totalEntries);
        collections.put("submittedEntries", submittedEntries);

        long activeLeaves = leaveRecordRepository.findAll().stream()
                .filter(r -> termService.matchesView(r.getTermId()))
                .filter(r -> com.classmgmt.domain.LeaveRecord.STATUS_ACTIVE.equals(r.getStatus()))
                .count();
        Map<String, Object> leaves = new LinkedHashMap<>();
        leaves.put("active", activeLeaves);

        Map<String, Object> students = new LinkedHashMap<>();
        students.put("count", studentRepository.findAll().stream()
                .filter(s -> termService.matchesView(s.getTermId())).count());
        students.put("locked", studentService.isLocked());

        Map<String, Object> lastCheck = null;
        var records = historyService.search(null, null);
        if (!records.isEmpty()) {
            CheckRecordSummaryDTO r = records.get(0);
            lastCheck = new LinkedHashMap<>();
            lastCheck.put("title", r.getTitle());
            lastCheck.put("checkedAt", r.getCheckedAt());
            lastCheck.put("absentCount", r.getAbsentCount());
            lastCheck.put("baselineCount", r.getBaselineCount());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todos", todos);
        result.put("collections", collections);
        result.put("leaves", leaves);
        result.put("students", students);
        result.put("lastCheck", lastCheck);
        return result;
    }
}
