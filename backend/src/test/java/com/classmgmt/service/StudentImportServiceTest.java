package com.classmgmt.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class StudentImportServiceTest {

    private final StudentImportService service = new StudentImportService();

    @Test
    void parsesTxtWithHeaderAndStudentNo() {
        String content = "学号,姓名\n202401,张三\n202402,李四\n1. 王五\n张三\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "名单.txt", "text/plain", content.getBytes(StandardCharsets.UTF_8));
        var result = service.parse(file);
        assertEquals(3, result.rows().size());
        assertEquals("张三", result.rows().get(0).name());
        assertEquals("202401", result.rows().get(0).studentNo());
        assertEquals("王五", result.rows().get(2).name());
        assertTrue(result.skipped() >= 1);
    }

    @Test
    void rejectsEmptyParse() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", "text/plain", "姓名\n学号\n".getBytes(StandardCharsets.UTF_8));
        var ex = assertThrows(RuntimeException.class, () -> service.parse(file));
        assertTrue(ex.getMessage().contains("未能从文件中解析"));
    }
}
