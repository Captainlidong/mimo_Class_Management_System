package com.classmgmt.service;

import com.classmgmt.domain.ScholarshipAward;
import com.classmgmt.domain.ScholarshipBatch;
import com.classmgmt.domain.Student;
import com.classmgmt.repo.ScholarshipAwardRepository;
import com.classmgmt.repo.ScholarshipBatchRepository;
import com.classmgmt.repo.StudentRepository;
import com.classmgmt.web.GlobalExceptionHandler.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScholarshipServiceTest {

    @Mock
    private ScholarshipBatchRepository batchRepository;
    @Mock
    private ScholarshipAwardRepository awardRepository;
    @Mock
    private StudentRepository studentRepository;

    private ScholarshipService service;

    @BeforeEach
    void setUp() {
        service = new ScholarshipService(batchRepository, awardRepository, studentRepository);
    }

    private Student student(long id, String name, String no) {
        Student s = new Student();
        s.setId(id);
        s.setName(name);
        s.setStudentNo(no);
        return s;
    }

    private ScholarshipAward award(long id, long studentId) {
        ScholarshipAward a = new ScholarshipAward();
        a.setId(id);
        a.setBatchId(1L);
        a.setStudentId(studentId);
        a.setAwardName("三等");
        return a;
    }

    @Test
    void listAwardsSortsByLastTwoDigitsAscendingWithNullsLast() {
        when(studentRepository.findAll()).thenReturn(List.of(
                student(1L, "张三", "2112616120"),
                student(2L, "李四", "2112616103"),
                student(3L, "王五", "2112616155"),
                student(4L, "赵六", null)));
        // 仓储按创建时间返回的乱序
        when(awardRepository.findByBatchIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of(
                award(10L, 3L), award(11L, 4L), award(12L, 2L), award(13L, 1L)));

        List<Map<String, Object>> rows = service.listAwards(1L);
        List<String> names = rows.stream().map(r -> String.valueOf(r.get("name"))).collect(Collectors.toList());

        assertEquals(List.of("李四", "张三", "王五", "赵六"), names,
                "应按学号后两位 03 < 20 < 55 升序，无学号的排最后");
    }

    @Test
    void trailingDigitsHandlesShortAndDashedNumbers() {
        when(studentRepository.findAll()).thenReturn(List.of(
                student(1L, "甲", "7"),
                student(2L, "乙", "2112-6161-09"),
                student(3L, "丙", "无")));
        when(awardRepository.findByBatchIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of(
                award(10L, 3L), award(11L, 2L), award(12L, 1L)));

        List<Map<String, Object>> rows = service.listAwards(1L);
        List<String> names = rows.stream().map(r -> String.valueOf(r.get("name"))).collect(Collectors.toList());

        assertEquals(List.of("甲", "乙", "丙"), names,
                "个位数学号按 7、含横杠学号按后两位 09、无数字学号排最后");
    }

    @Test
    void duplicateManualAddRejectedWithConflict() {
        ScholarshipBatch batch = new ScholarshipBatch();
        batch.setId(1L);
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(studentRepository.existsById(5L)).thenReturn(true);
        when(studentRepository.findById(5L)).thenReturn(Optional.of(student(5L, "张三", "2112616120")));
        when(awardRepository.existsByBatchIdAndStudentId(1L, 5L)).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.addAward(1L, Map.of("studentId", 5L, "awardName", "三等")));

        assertEquals(409, ex.getCode());
        assertTrue(ex.getMessage().contains("张三"), "错误提示应带上同学姓名");
    }

    @Test
    void batchAddSkipsStudentsAlreadyInBatchOrRepeatedInPayload() {
        ScholarshipBatch batch = new ScholarshipBatch();
        batch.setId(1L);
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(studentRepository.existsById(3L)).thenReturn(true);
        when(studentRepository.existsById(4L)).thenReturn(true);
        when(awardRepository.existsByBatchIdAndStudentId(1L, 2L)).thenReturn(true);
        when(awardRepository.existsByBatchIdAndStudentId(1L, 3L)).thenReturn(false);
        when(awardRepository.existsByBatchIdAndStudentId(1L, 4L)).thenReturn(false);
        when(awardRepository.save(any())).thenAnswer(inv -> {
            ScholarshipAward a = inv.getArgument(0);
            a.setId(a.getStudentId()); // 模拟 JPA 保存后回填主键
            return a;
        });

        List<Map<String, Object>> created = service.addAward(1L, Map.of("rows", List.of(
                Map.of("studentId", 2L),
                Map.of("studentId", 3L),
                Map.of("studentId", 3L),
                Map.of("studentId", 4L))), null);

        assertEquals(2, created.size(), "批次内已有的 2 跳过，重复的第二个 3 跳过");
        verify(awardRepository, times(2)).save(any());
    }
}
