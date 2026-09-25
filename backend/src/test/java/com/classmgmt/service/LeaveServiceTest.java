package com.classmgmt.service;

import com.classmgmt.domain.LeaveRecord;
import com.classmgmt.domain.Student;
import com.classmgmt.repo.LeaveRecordRepository;
import com.classmgmt.repo.StudentRepository;
import com.classmgmt.web.GlobalExceptionHandler.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock
    private LeaveRecordRepository leaveRecordRepository;
    @Mock
    private StudentRepository studentRepository;

    private LeaveService service;

    @BeforeEach
    void setUp() {
        service = new LeaveService(leaveRecordRepository, studentRepository);
    }

    private Student student(long id, String name) {
        Student s = new Student();
        s.setId(id);
        s.setName(name);
        s.setStudentNo("2024" + id);
        return s;
    }

    private LeaveRecord record(long studentId, String status, Instant start) {
        LeaveRecord r = new LeaveRecord();
        r.setStudentId(studentId);
        r.setStatus(status);
        r.setLeaveType(LeaveRecord.TYPE_SICK);
        r.setStartTime(start);
        r.setEndTime(start.plus(1, ChronoUnit.DAYS));
        return r;
    }

    @Test
    void createRequiresStudentSelection() {
        ApiException ex = assertThrows(ApiException.class, () -> service.create(Map.of(
                "startTime", "2026-09-25T08:00:00",
                "endTime", "2026-09-26T08:00:00")));
        assertEquals(400, ex.getCode());
    }

    @Test
    void createRejectsUnknownStudent() {
        when(studentRepository.findAll()).thenReturn(List.of(student(1L, "张三")));
        ApiException ex = assertThrows(ApiException.class, () -> service.create(Map.of(
                "studentIds", List.of(2L),
                "startTime", "2026-09-25T08:00:00",
                "endTime", "2026-09-26T08:00:00")));
        assertEquals(400, ex.getCode());
    }

    @Test
    void createRejectsEndBeforeStart() {
        when(studentRepository.findAll()).thenReturn(List.of(student(1L, "张三")));
        ApiException ex = assertThrows(ApiException.class, () -> service.create(Map.of(
                "studentIds", List.of(1L),
                "startTime", "2026-09-26T08:00:00",
                "endTime", "2026-09-25T08:00:00")));
        assertEquals(400, ex.getCode());
    }

    @Test
    void unknownTypeRejected() {
        when(studentRepository.findAll()).thenReturn(List.of(student(1L, "张三")));
        ApiException ex = assertThrows(ApiException.class, () -> service.create(Map.of(
                "studentIds", List.of(1L),
                "leaveType", "annual",
                "startTime", "2026-09-25T08:00:00",
                "endTime", "2026-09-26T08:00:00")));
        assertEquals(400, ex.getCode());
    }

    @Test
    void batchCreateMakesOneRecordPerStudent() {
        when(studentRepository.findAll()).thenReturn(List.of(student(1L, "张三"), student(2L, "李四")));
        when(leaveRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Map<String, Object>> created = service.create(Map.of(
                "studentIds", List.of(1L, 2L),
                "leaveType", "sick",
                "startTime", "2026-09-25 08:00",
                "endTime", "2026-09-26 08:00",
                "reason", "感冒"));

        assertEquals(2, created.size());
        assertEquals("张三", created.get(0).get("name"));
        assertEquals(LeaveRecord.STATUS_ACTIVE, created.get(1).get("status"));
        verify(leaveRecordRepository, times(2)).save(any());
    }

    @Test
    void closeThenReopenCyclesStatus() {
        LeaveRecord r = record(1L, LeaveRecord.STATUS_ACTIVE, Instant.parse("2026-09-24T00:00:00Z"));
        when(leaveRecordRepository.findById(5L)).thenReturn(Optional.of(r));
        when(leaveRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(studentRepository.findAll()).thenReturn(List.of(student(1L, "张三")));

        Map<String, Object> closed = service.close(5L, Map.of("returnedAt", "2026-09-25 14:30"));
        assertEquals(LeaveRecord.STATUS_CLOSED, closed.get("status"));
        assertNotNull(closed.get("returnedAt"));

        ApiException again = assertThrows(ApiException.class, () -> service.close(5L, Map.of()));
        assertEquals(409, again.getCode());

        Map<String, Object> reopened = service.reopen(5L);
        assertEquals(LeaveRecord.STATUS_ACTIVE, reopened.get("status"));
        assertNull(reopened.get("returnedAt"));
    }

    @Test
    void closeDefaultsReturnedAtToNow() {
        LeaveRecord r = record(1L, LeaveRecord.STATUS_ACTIVE, Instant.parse("2026-09-24T00:00:00Z"));
        when(leaveRecordRepository.findById(5L)).thenReturn(Optional.of(r));
        when(leaveRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(studentRepository.findAll()).thenReturn(List.of(student(1L, "张三")));

        Map<String, Object> closed = service.close(5L, Map.of());
        assertNotNull(closed.get("returnedAt"));
    }

    @Test
    void statsCountsActiveAndMonthly() {
        when(studentRepository.findAll()).thenReturn(List.of(student(1L, "张三")));
        LeaveRecord active = record(1L, LeaveRecord.STATUS_ACTIVE, Instant.now());
        LeaveRecord closedOld = record(1L, LeaveRecord.STATUS_CLOSED, Instant.now().minus(40, ChronoUnit.DAYS));
        when(leaveRecordRepository.findAll()).thenReturn(List.of(active, closedOld));
        when(leaveRecordRepository.count()).thenReturn(2L);

        Map<String, Object> stats = service.stats();

        assertEquals(1L, stats.get("activeCount"));
        assertEquals(1L, stats.get("monthCount"));
        assertEquals(2L, stats.get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byStudent = (List<Map<String, Object>>) stats.get("byStudent");
        assertEquals(1, byStudent.size());
        assertEquals(2, byStudent.get(0).get("total"));
        assertEquals(1, byStudent.get(0).get("active"));
    }
}
