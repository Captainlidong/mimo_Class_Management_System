package com.classmgmt.service;

import com.classmgmt.domain.Student;
import com.classmgmt.dto.CheckResultDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CheckServiceTest {

    private CheckService checkService;
    private List<Student> baseline;

    @BeforeEach
    void setUp() {
        checkService = new CheckService(null, null, new ObjectMapper(), null);
        baseline = new ArrayList<>();
        baseline.add(student("张三", "001"));
        baseline.add(student("李四", "002"));
        baseline.add(student("王五", "003"));
    }

    private Student student(String name, String no) {
        Student s = new Student();
        s.setName(name);
        s.setStudentNo(no);
        return s;
    }

    @Test
    void classifiesParticipatedAbsentInvalid() {
        CheckResultDTO result = checkService.evaluate("1. 张三\n李四\n路人甲\n！！", baseline);
        assertEquals(List.of("张三", "李四"), result.getParticipated());
        assertEquals(List.of("王五"), result.getAbsent());
        assertTrue(result.getInvalid().contains("路人甲"));
        assertEquals(2, result.getCounts().get("participated"));
        assertEquals(1, result.getCounts().get("absent"));
        assertEquals(3, result.getCounts().get("baseline"));
    }

    @Test
    void deduplicatesNames() {
        CheckResultDTO result = checkService.evaluate("张三\n张三\n李四", baseline);
        assertEquals(List.of("张三", "李四"), result.getParticipated());
        assertEquals(List.of("张三"), result.getDuplicates());
        assertEquals(1, result.getCounts().get("duplicates"));
        assertEquals(List.of("王五"), result.getAbsent());
    }

    @Test
    void emptyBaselineAllInvalid() {
        CheckResultDTO result = checkService.evaluate("张三", List.of());
        assertTrue(result.getParticipated().isEmpty());
        assertTrue(result.getAbsent().isEmpty());
        assertEquals(List.of("张三"), result.getInvalid());
    }

    @Test
    void rejectsFuzzyPartialNameMatch() {
        Student s = new Student();
        s.setName("张三");
        s.setStudentNo("001");
        CheckResultDTO result = checkService.evaluate("张三丰", List.of(s));
        assertTrue(result.getParticipated().isEmpty());
        assertEquals(List.of("张三"), result.getAbsent());
        assertEquals(List.of("张三丰"), result.getInvalid());
    }

    @Test
    void matchesExactStudentNoOnly() {
        Student s = new Student();
        s.setName("张三");
        s.setStudentNo("202401");
        CheckResultDTO byNo = checkService.evaluate("202401", List.of(s));
        assertEquals(List.of("张三"), byNo.getParticipated());
        CheckResultDTO byPartialNo = checkService.evaluate("20240101", List.of(s));
        assertTrue(byPartialNo.getParticipated().isEmpty());
        assertEquals(List.of("20240101"), byPartialNo.getInvalid());
    }
}
