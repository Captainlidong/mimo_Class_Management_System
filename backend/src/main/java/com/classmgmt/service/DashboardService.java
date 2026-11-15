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

    public DashboardService(StudentRepository studentRepository,
                            StudentService studentService,
                            TodoItemRepository todoItemRepository,
                            CollectionTaskRepository collectionTaskRepository,
                            CollectionEntryRepository collectionEntryRepository,
                            LeaveRecordRepository leaveRecordRepository,
                            HistoryService historyService) {
        this.studentRepository = studentRepository;
        this.studentService = studentService;
        this.todoItemRepository = todoItemRepository;
        this.collectionTaskRepository = collectionTaskRepository;
        this.collectionEntryRepository = collectionEntryRepository;
        this.leaveRecordRepository = leaveRecordRepository;
        this.historyService = historyService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        Instant now = Instant.now();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        Instant dayStart = today.atStartOfDay(zone).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant();

        Map<String, Object> todos = new LinkedHashMap<>();
        todos.put("pending", todoItemRepository.countByDoneFalse());
        todos.put("overdue", todoItemRepository.countByDoneFalseAndDueAtBefore(now));
        todos.put("dueToday", todoItemRepository.countByDoneFalseAndDueAtGreaterThanEqualAndDueAtLessThan(dayStart, dayEnd));

        Map<String, Object> collections = new LinkedHashMap<>();
        collections.put("tasks", collectionTaskRepository.count());
        collections.put("totalEntries", collectionEntryRepository.count());
        collections.put("submittedEntries", collectionEntryRepository.countBySubmittedTrue());

        Map<String, Object> leaves = new LinkedHashMap<>();
        leaves.put("active", leaveRecordRepository.countByStatus(LeaveRecord.STATUS_ACTIVE));

        Map<String, Object> students = new LinkedHashMap<>();
        students.put("count", studentRepository.count());
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
