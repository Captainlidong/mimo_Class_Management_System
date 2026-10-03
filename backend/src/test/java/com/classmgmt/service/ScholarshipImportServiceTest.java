package com.classmgmt.service;

import com.classmgmt.domain.ScholarshipAward;
import com.classmgmt.domain.Student;
import com.classmgmt.repo.ScholarshipAwardRepository;
import com.classmgmt.repo.StudentRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScholarshipImportServiceTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private ScholarshipAwardRepository scholarshipAwardRepository;

    private ScholarshipImportService service() {
        return new ScholarshipImportService(studentRepository, scholarshipAwardRepository);
    }

    private List<Student> roster() {
        return List.of(
                student(1L, "绳涵戈", "2112616151"),
                student(2L, "孟庆成", "2112616152"),
                student(3L, "王小明", "2112616153"),
                student(4L, "王小明", "2112616154"),
                student(5L, "徐昊", null));
    }

    private Student student(long id, String name, String no) {
        Student s = new Student();
        s.setId(id);
        s.setName(name);
        s.setStudentNo(no);
        return s;
    }

    private MockMultipartFile excel(String[][] rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("获奖名单");
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    if (rows[r][c] != null) {
                        row.createCell(c).setCellValue(rows[r][c]);
                    }
                }
            }
            wb.write(out);
            return new MockMultipartFile("file", "全院名单.xlsx",
                    "application/octet-stream", out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> matchedRow(Map<String, Object> result, String studentName) {
        return ((List<Map<String, Object>>) result.get("matched")).stream()
                .filter(r -> studentName.equals(r.get("studentName")))
                .findFirst().orElseThrow();
    }

    @Test
    void detectHeaderMatchRosterAndFilterOutsiders() {
        when(studentRepository.findAll()).thenReturn(roster());
        MockMultipartFile file = excel(new String[][]{
                {"序号", "姓名", "学号", "奖学金名称", "金额（元）"},
                {"1", "绳涵戈", "2112616151", "国家励志奖学金", "5000"},
                {"2", "孟庆成", "2112616152", "一等奖学金", "3000元"},
                {"3", "张三丰", "2023111999", "二等奖学金", "2000"},
                {"4", "王小明", "2112699999", "三等奖", "1000"},
                {"5", "徐昊", "", "其他", "500"}
        });

        Map<String, Object> result = service().parse(null, file, null, null, null, null, null);

        assertTrue((Boolean) result.get("recognized"));
        assertEquals(5, result.get("totalRows"));
        List<Map<String, Object>> matched = (List<Map<String, Object>>) result.get("matched");
        assertEquals(3, matched.size());

        Map<String, Object> sheng = matchedRow(result, "绳涵戈");
        assertEquals("学号", sheng.get("matchedBy"));
        assertEquals("国家励志奖学金", sheng.get("awardName"));
        assertEquals(new BigDecimal("5000"), sheng.get("amount"));
        assertFalse((Boolean) sheng.get("duplicate"));

        Map<String, Object> meng = matchedRow(result, "孟庆成");
        assertEquals(new BigDecimal("3000"), meng.get("amount"), "「3000元」应提取出数字 3000");

        Map<String, Object> xu = matchedRow(result, "徐昊");
        assertEquals("姓名", xu.get("matchedBy"), "无学号时按姓名兜底匹配");

        List<Map<String, Object>> others = (List<Map<String, Object>>) result.get("others");
        assertEquals(2, others.size(), "张三丰未匹配 + 王小明同名歧义");
        assertEquals(1, result.get("unmatchedCount"));
        assertTrue(others.stream().anyMatch(r -> "ambiguous".equals(r.get("status"))), "王小明应标记为同名歧义");
        assertTrue(others.stream().anyMatch(r -> "unmatched".equals(r.get("status"))), "张三丰应标记为未匹配");
    }

    @Test
    void headerNotOnFirstRow() {
        when(studentRepository.findAll()).thenReturn(roster());
        MockMultipartFile file = excel(new String[][]{
                {"2025-2026学年秋季奖学金获奖名单"},
                {"姓名", "学号", "奖学金等级"},
                {"绳涵戈", "2112616151", "国家奖学金"}
        });

        Map<String, Object> result = service().parse(null, file, null, null, null, null, null);

        assertTrue((Boolean) result.get("recognized"));
        assertEquals(1, result.get("headerRow"));
        Map<String, Object> sheng = matchedRow(result, "绳涵戈");
        assertEquals("国家奖学金", sheng.get("awardName"));
    }

    @Test
    void unrecognizedHeaderFallsBackToManualMapping() {
        when(studentRepository.findAll()).thenReturn(roster());
        // 无表头：没有"姓名"字样
        MockMultipartFile file = excel(new String[][]{
                {"1", "绳涵戈", "2112616151", "一等"},
                {"2", "孟庆成", "2112616152", "二等"}
        });

        Map<String, Object> result = service().parse(null, file, null, null, null, null, null);
        assertFalse((Boolean) result.get("recognized"));
        assertFalse(((List<?>) result.get("samples")).isEmpty(), "失败时要带回原始行预览");

        // 手动指定：无表头（headerRow=-1），列 0 序号 / 1 姓名 / 2 学号 / 3 等级
        Map<String, Object> manual = service().parse(null, file, -1, 1, 2, 3, -1);
        assertTrue((Boolean) manual.get("recognized"));
        assertEquals(2, manual.get("totalRows"));
        Map<String, Object> sheng = matchedRow(manual, "绳涵戈");
        assertEquals("一等", sheng.get("awardName"));
        assertNull(sheng.get("amount"), "金额列未指定时应为空");
    }

    @Test
    void duplicateAwardInBatchIsMarked() {
        when(studentRepository.findAll()).thenReturn(roster());
        ScholarshipAward existing = new ScholarshipAward();
        existing.setBatchId(9L);
        existing.setStudentId(1L);
        when(scholarshipAwardRepository.findByBatchIdOrderByCreatedAtAscIdAsc(9L))
                .thenReturn(List.of(existing));

        MockMultipartFile file = excel(new String[][]{
                {"姓名", "学号", "奖学金名称"},
                {"绳涵戈", "2112616151", "国家励志奖学金"}
        });

        Map<String, Object> result = service().parse(9L, file, null, null, null, null, null);
        assertTrue((Boolean) matchedRow(result, "绳涵戈").get("duplicate"), "批次内已有记录应标记 duplicate");
    }

    @Test
    void cleanStudentNoRemovesSpacesAndDashes() {
        assertEquals("2112616151", service().cleanStudentNo("2112 616-151"));
        assertNull(service().cleanStudentNo("   "));
        assertNull(service().cleanStudentNo(null));
    }

    @Test
    void emptyFileRejected() {
        MockMultipartFile file = excel(new String[][]{});
        org.junit.jupiter.api.Assertions.assertThrows(
                com.classmgmt.web.GlobalExceptionHandler.ApiException.class,
                () -> service().parse(null, file, null, null, null, null, null));
    }
}
