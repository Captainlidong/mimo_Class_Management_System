package com.classmgmt.service;

import com.classmgmt.domain.ScholarshipAward;
import com.classmgmt.domain.Student;
import com.classmgmt.repo.ScholarshipAwardRepository;
import com.classmgmt.repo.ScholarshipBatchRepository;
import com.classmgmt.repo.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
