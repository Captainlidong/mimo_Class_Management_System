package com.classmgmt.service;

import com.classmgmt.domain.CollectionEntry;
import com.classmgmt.domain.CollectionTask;
import com.classmgmt.domain.Student;
import com.classmgmt.repo.CollectionEntryRepository;
import com.classmgmt.repo.CollectionTaskRepository;
import com.classmgmt.repo.StudentRepository;
import com.classmgmt.web.GlobalExceptionHandler.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {

    @Mock
    private CollectionTaskRepository taskRepository;
    @Mock
    private CollectionEntryRepository entryRepository;
    @Mock
    private StudentRepository studentRepository;

    private CollectionService service;

    @BeforeEach
    void setUp() {
        service = new CollectionService(taskRepository, entryRepository, studentRepository);
    }

    private Student student(long id, String name, String no) {
        Student s = new Student();
        s.setId(id);
        s.setName(name);
        s.setStudentNo(no);
        return s;
    }

    private void stubRoster() {
        when(studentRepository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(List.of(
                student(1L, "张三", "2112616103"),
                student(2L, "李四", "2112616120"),
                student(3L, "王五", "2112616155")));
    }

    private CollectionTask stubTask() {
        CollectionTask task = new CollectionTask();
        task.setId(9L);
        task.setName("培养计划");
        lenient().when(taskRepository.findById(9L)).thenReturn(Optional.of(task));
        lenient().when(entryRepository.findByTaskId(9L)).thenReturn(List.of(
                entry(100L, 1L, true), entry(101L, 2L, false), entry(102L, 3L, false)));
        lenient().when(studentRepository.findById(1L)).thenReturn(Optional.of(student(1L, "张三", "2112616103")));
        lenient().when(studentRepository.findById(2L)).thenReturn(Optional.of(student(2L, "李四", "2112616120")));
        lenient().when(studentRepository.findById(3L)).thenReturn(Optional.of(student(3L, "王五", "2112616155")));
        return task;
    }

    private CollectionEntry entry(long id, long studentId, boolean submitted) {
        CollectionEntry e = new CollectionEntry();
        e.setId(id);
        e.setTaskId(9L);
        e.setStudentId(studentId);
        e.setSubmitted(submitted);
        return e;
    }

    @Test
    void createTaskDefaultsToWholeRoster() {
        stubRoster();
        when(taskRepository.save(any())).thenAnswer(inv -> {
            CollectionTask t = inv.getArgument(0);
            t.setId(9L); // 模拟 JPA 保存后回填主键
            return t;
        });
        when(taskRepository.findById(9L)).thenAnswer(inv -> {
            CollectionTask t = new CollectionTask();
            t.setId(9L);
            t.setName("心理问卷");
            return Optional.of(t);
        });
        List<CollectionEntry> saved = new ArrayList<>();
        when(entryRepository.save(any())).thenAnswer(inv -> {
            CollectionEntry e = inv.getArgument(0);
            saved.add(e);
            return e;
        });
        when(entryRepository.findByTaskId(9L)).thenReturn(saved);

        Map<String, Object> result = service.createTask(Map.of("name", "心理问卷"));

        assertEquals(3, result.get("total"), "缺省人员范围时应为全班 3 人");
        assertEquals(0L, result.get("received"));
    }

    @Test
    void createTaskWithSelectedStudentsAndDedup() {
        stubRoster();
        when(taskRepository.save(any())).thenAnswer(inv -> {
            CollectionTask t = inv.getArgument(0);
            t.setId(9L); // 模拟 JPA 保存后回填主键
            return t;
        });
        when(taskRepository.findById(9L)).thenAnswer(inv -> {
            CollectionTask t = new CollectionTask();
            t.setId(9L);
            t.setName("培养计划");
            return Optional.of(t);
        });
        List<CollectionEntry> saved = new ArrayList<>();
        when(entryRepository.save(any())).thenAnswer(inv -> {
            CollectionEntry e = inv.getArgument(0);
            saved.add(e);
            return e;
        });
        when(entryRepository.findByTaskId(9L)).thenReturn(saved);

        service.createTask(Map.of("name", "培养计划", "studentIds", List.of(2L, 3L, 2L)));

        ArgumentCaptor<CollectionEntry> captor = ArgumentCaptor.forClass(CollectionEntry.class);
        verify(entryRepository, times(2)).save(captor.capture());
        assertEquals(List.of(2L, 3L), captor.getAllValues().stream()
                .map(CollectionEntry::getStudentId).collect(Collectors.toList()),
                "重复的 2 应去重，顺序保持基准名单序");
    }

    @Test
    void createTaskRejectsUnknownStudent() {
        stubRoster();
        ApiException ex = assertThrows(ApiException.class,
                () -> service.createTask(Map.of("name", "X", "studentIds", List.of(999L))));
        assertEquals(400, ex.getCode());
    }

    @Test
    void markByTextMatchesAndReportsNotFound() {
        stubTask();

        Map<String, Object> result = service.markByText(9L, Map.of(
                "text", "张三、王五，外校李雷"));

        @SuppressWarnings("unchecked")
        List<String> marked = (List<String>) result.get("marked");
        @SuppressWarnings("unchecked")
        List<String> notFound = (List<String>) result.get("notFound");
        assertEquals(List.of("张三", "王五"), marked);
        assertEquals(List.of("外校李雷"), notFound);
    }

    @Test
    void markByTextSkipsAlreadySubmittedWithoutTouchingTime() {
        stubTask();
        // 张三已交：重复标记不应再 save
        service.markByText(9L, Map.of("text", "张三"));
        verify(entryRepository, times(0)).save(any());
    }

    @Test
    void toggleEntryRecordsTime() {
        CollectionEntry e = entry(100L, 1L, false);
        when(entryRepository.findById(100L)).thenReturn(Optional.of(e));
        when(entryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.setEntrySubmitted(100L, Map.of("submitted", true));

        assertTrue((Boolean) result.get("submitted"));
        assertNotNull(e.getSubmittedAt());
    }

    @Test
    void addStudentsSkipsExistingAndDedupesPayload() {
        stubTask();
        when(studentRepository.findAll()).thenReturn(List.of(
                student(1L, "张三", "2112616103"),
                student(2L, "李四", "2112616120"),
                student(3L, "王五", "2112616155")));
        when(entryRepository.existsByTaskIdAndStudentId(9L, 1L)).thenReturn(true);
        when(entryRepository.existsByTaskIdAndStudentId(9L, 2L)).thenReturn(false);
        when(entryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.addStudents(9L, Map.of("studentIds", List.of(1L, 2L, 1L)));

        assertEquals(1, result.get("added"));
        assertEquals(1, result.get("skipped"), "已在名单的 1 跳过 + 载荷里重复的 1 跳过");
        ArgumentCaptor<CollectionEntry> captor = ArgumentCaptor.forClass(CollectionEntry.class);
        verify(entryRepository).save(captor.capture());
        assertEquals(2L, captor.getValue().getStudentId());
    }

    @Test
    void addStudentsRejectsUnknownStudent() {
        stubTask();
        when(studentRepository.findAll()).thenReturn(List.of(
                student(2L, "李四", "2112616120")));
        when(entryRepository.existsByTaskIdAndStudentId(9L, 2L)).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.addStudents(9L, Map.of("studentIds", List.of(2L, 999L))));
        assertEquals(400, ex.getCode());
    }

    @Test
    void removeStudentDeletesEntry() {
        stubTask();
        CollectionEntry e = entry(100L, 1L, true);
        when(entryRepository.findByTaskIdAndStudentId(9L, 1L)).thenReturn(Optional.of(e));

        service.removeStudent(9L, 1L);
        verify(entryRepository).delete(e);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.removeStudent(9L, 999L));
        assertEquals(404, ex.getCode());
    }
}
