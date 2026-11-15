package com.classmgmt.service;

import com.classmgmt.domain.LeaveRecord;
import com.classmgmt.dto.CheckRecordSummaryDTO;
import com.classmgmt.repo.CheckRecordRepository;
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
import java.util.ArrayList;
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
    private final CheckRecordRepository checkRecordRepository;

    public DashboardService(StudentRepository studentRepository,
                            StudentService studentService,
                            TodoItemRepository todoItemRepository,
                            CollectionTaskRepository collectionTaskRepository,
                            CollectionEntryRepository collectionEntryRepository,
                            LeaveRecordRepository leaveRecordRepository,
                            HistoryService historyService,
                            TermService termService,
                            CheckRecordRepository checkRecordRepository) {
        this.studentRepository = studentRepository;
        this.studentService = studentService;
        this.todoItemRepository = todoItemRepository;
        this.collectionTaskRepository = collectionTaskRepository;
        this.collectionEntryRepository = collectionEntryRepository;
        this.leaveRecordRepository = leaveRecordRepository;
        this.historyService = historyService;
        this.termService = termService;
        this.checkRecordRepository = checkRecordRepository;
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
                .filter(s -> termService.matchesView(s.getTermId()) && s.isInSchool()).count());
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

    /**
     * 数据洞察趋势：近 8 周请假人次 / 筛查次数、收集任务完成横条、近 30 天请假类型分布。
     * 全部按浏览学期过滤。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> trends() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate thisMonday = today.with(java.time.DayOfWeek.MONDAY);
        List<String> weeks = new ArrayList<>();
        List<LocalDate> weekStarts = new ArrayList<>();
        for (int i = 7; i >= 0; i--) {
            LocalDate ws = thisMonday.minusWeeks(i);
            weekStarts.add(ws);
            weeks.add((ws.getMonthValue()) + "/" + ws.getDayOfMonth());
        }
        List<Instant> weekStartInst = new ArrayList<>();
        for (LocalDate ws : weekStarts) {
            weekStartInst.add(ws.atStartOfDay(zone).toInstant());
        }
        Instant horizon = thisMonday.plusWeeks(1).atStartOfDay(zone).toInstant();

        long[] weeklyLeave = new long[8];
        for (LeaveRecord r : leaveRecordRepository.findAll()) {
            if (!termService.matchesView(r.getTermId()) || r.getStartTime() == null) {
                continue;
            }
            for (int i = 0; i < 8; i++) {
                Instant from = weekStartInst.get(i);
                Instant to = i < 7 ? weekStartInst.get(i + 1) : horizon;
                if (!r.getStartTime().isBefore(from) && r.getStartTime().isBefore(to)) {
                    weeklyLeave[i]++;
                    break;
                }
            }
        }

        long[] weeklyChecks = new long[8];
        for (com.classmgmt.domain.CheckRecord r : checkRecordRepository.findAll()) {
            if (!termService.matchesView(r.getTermId()) || r.getCheckedAt() == null) {
                continue;
            }
            Instant at = r.getCheckedAt();
            for (int i = 0; i < 8; i++) {
                Instant from = weekStartInst.get(i);
                Instant to = i < 7 ? weekStartInst.get(i + 1) : horizon;
                if (!at.isBefore(from) && at.isBefore(to)) {
                    weeklyChecks[i]++;
                    break;
                }
            }
        }

        // 近 30 天请假类型分布（含零值项，供环形图稳定渲染）
        Instant pieFrom = today.minusDays(29).atStartOfDay(zone).toInstant();
        long sick = 0;
        long personal = 0;
        long official = 0;
        long other = 0;
        for (LeaveRecord r : leaveRecordRepository.findAll()) {
            if (!termService.matchesView(r.getTermId()) || r.getStartTime() == null
                    || r.getStartTime().isBefore(pieFrom)) {
                continue;
            }
            switch (r.getLeaveType() == null ? LeaveRecord.TYPE_OTHER : r.getLeaveType()) {
                case LeaveRecord.TYPE_SICK -> sick++;
                case LeaveRecord.TYPE_PERSONAL -> personal++;
                case LeaveRecord.TYPE_OFFICIAL -> official++;
                default -> other++;
            }
        }
        List<Map<String, Object>> leaveTypePie = new ArrayList<>();
        leaveTypePie.add(Map.of("name", "病假", "value", sick));
        leaveTypePie.add(Map.of("name", "事假", "value", personal));
        leaveTypePie.add(Map.of("name", "公假", "value", official));
        leaveTypePie.add(Map.of("name", "其他", "value", other));

        // 收集任务完成横条（浏览学期全部任务，按创建时间倒序）
        List<Map<String, Object>> bars = new ArrayList<>();
        for (com.classmgmt.domain.CollectionTask t : collectionTaskRepository.findAll()) {
            if (!termService.matchesView(t.getTermId())) {
                continue;
            }
            List<com.classmgmt.domain.CollectionEntry> entries = collectionEntryRepository.findByTaskId(t.getId());
            long received = entries.stream().filter(e -> Boolean.TRUE.equals(e.getSubmitted())).count();
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("name", t.getName());
            b.put("kind", t.getKind() == null ? "collect" : t.getKind());
            b.put("received", received);
            b.put("total", entries.size());
            b.put("createdAt", t.getCreatedAt());
            bars.add(b);
        }
        bars.sort((a, b) -> ((Instant) b.get("createdAt")).compareTo((Instant) a.get("createdAt")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("weeks", weeks);
        result.put("weeklyLeave", weeklyLeave);
        result.put("weeklyChecks", weeklyChecks);
        result.put("leaveTypePie", leaveTypePie);
        result.put("collectionBars", bars);
        return result;
    }
}
