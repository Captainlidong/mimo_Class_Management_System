package com.classmgmt.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScholarshipIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode firstStudent() throws Exception {
        return readJson(mockMvc.perform(get("/api/students")).andExpect(status().isOk()).andReturn())
                .get("data").get("students").get(0);
    }

    private MockMultipartFile collegeExcel(String studentName, String studentNo) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("全院获奖名单");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("序号");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("学号");
            header.createCell(3).setCellValue("奖学金名称");
            header.createCell(4).setCellValue("金额（元）");
            Row mine = sheet.createRow(1);
            mine.createCell(0).setCellValue("88");
            mine.createCell(1).setCellValue(studentName);
            mine.createCell(2).setCellValue(studentNo);
            mine.createCell(3).setCellValue("国家励志奖学金");
            mine.createCell(4).setCellValue("5000");
            Row other = sheet.createRow(2);
            other.createCell(0).setCellValue("89");
            other.createCell(1).setCellValue("外班同学甲");
            other.createCell(2).setCellValue("9999999999");
            other.createCell(3).setCellValue("二等奖学金");
            other.createCell(4).setCellValue("2000");
            wb.write(out);
            return new MockMultipartFile("file", "全院名单.xlsx",
                    "application/octet-stream", out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void scholarshipBatchFlowWithExcelImport() throws Exception {
        JsonNode student = firstStudent();
        long sid = student.get("id").asLong();
        String sname = student.get("name").asText();
        String sno = student.get("studentNo").asText();

        // 1. 建批次
        Map<String, Object> batchBody = new HashMap<>();
        batchBody.put("name", "2025-2026学年秋季奖学金");
        batchBody.put("remark", "集成测试批次");
        MvcResult batchResult = mockMvc.perform(post("/api/scholarships/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(batchBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("2025-2026学年秋季奖学金"))
                .andReturn();
        long batchId = readJson(batchResult).get("data").get("id").asLong();

        // 2. 上传全院 Excel 识别 → 本班命中 1 人，外班被过滤
        MvcResult parseResult = mockMvc.perform(multipart("/api/scholarships/batches/" + batchId + "/import/parse")
                        .file(collegeExcel(sname, sno)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode parsed = readJson(parseResult).get("data");
        assertTrue(parsed.get("recognized").asBoolean());
        assertEquals(2, parsed.get("totalRows").asInt(), "外班行也计入总行数");
        assertEquals(1, parsed.get("matched").size(), "只命中本班 1 人");
        assertEquals("学号", parsed.get("matched").get(0).get("matchedBy").asText());

        // 3. 确认导入
        Map<String, Object> confirmBody = new HashMap<>();
        confirmBody.put("sourceFile", "全院名单.xlsx");
        confirmBody.put("rows", java.util.List.of(Map.of(
                "studentId", sid,
                "awardName", parsed.get("matched").get(0).get("awardName").asText(),
                "amount", "5000")));
        mockMvc.perform(post("/api/scholarships/batches/" + batchId + "/import/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(confirmBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        // 4. 详情：1 条记录、未交表、等级分布
        MvcResult detailResult = mockMvc.perform(get("/api/scholarships/batches/" + batchId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode detail = readJson(detailResult).get("data");
        assertEquals(1, detail.get("awards").size());
        assertEquals(0, detail.get("receivedCount").asInt());
        assertEquals(sname, detail.get("awards").get(0).get("name").asText());
        long awardId = detail.get("awards").get(0).get("id").asLong();

        // 5. 标记已交表
        mockMvc.perform(post("/api/scholarships/awards/" + awardId + "/mark-received")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"received\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.formReceived").value(true))
                .andExpect(jsonPath("$.data.receivedAt").isNotEmpty());

        mockMvc.perform(get("/api/scholarships/batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].receivedCount").value(1));

        // 6. 重复导入同一人 → 标记 duplicate
        MvcResult reParse = mockMvc.perform(multipart("/api/scholarships/batches/" + batchId + "/import/parse")
                        .file(collegeExcel(sname, sno)))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(readJson(reParse).get("data").get("matched").get(0).get("duplicate").asBoolean());

        // 7. 编辑 + 导出
        mockMvc.perform(patch("/api/scholarships/awards/" + awardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"remark\":\"已电话通知\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/scholarships/batches/" + batchId + "/export").param("format", "xlsx"))
                .andExpect(status().isOk());

        // 8. 清理
        mockMvc.perform(delete("/api/scholarships/awards/" + awardId)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/scholarships/batches/" + batchId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/scholarships/batches/" + batchId)).andExpect(status().isNotFound());
    }
}
