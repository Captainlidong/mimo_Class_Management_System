package com.classmgmt.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 工作台聚合接口：结构完整 + 待办计数随创建/删除联动。 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode summary() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/dashboard/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data");
    }

    @Test
    void dashboardSummaryStructureAndTodoCounts() throws Exception {
        JsonNode data = summary();
        // 结构：五个分组齐全，子字段齐全
        assertNotNull(data.get("todos").get("pending"), "todos.pending 必须存在");
        assertNotNull(data.get("todos").get("overdue"));
        assertNotNull(data.get("todos").get("dueToday"));
        assertNotNull(data.get("collections").get("tasks"));
        assertNotNull(data.get("collections").get("totalEntries"));
        assertNotNull(data.get("collections").get("submittedEntries"));
        assertNotNull(data.get("leaves").get("active"));
        assertNotNull(data.get("students").get("count"));
        assertNotNull(data.get("students").get("locked"));
        assertTrue(data.get("students").get("count").asLong() >= 0);

        // 待办计数联动：新建一条 → pending+1；删除 → 回落
        long before = data.get("todos").get("pending").asLong();
        Map<String, Object> body = new HashMap<>();
        body.put("title", "工作台计数联动检查");
        MvcResult created = mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data").get("id").asLong();

        JsonNode afterCreate = summary();
        assertEquals(before + 1, afterCreate.get("todos").get("pending").asLong());

        mockMvc.perform(delete("/api/todos/" + id)).andExpect(status().isOk());
        JsonNode afterDelete = summary();
        assertEquals(before, afterDelete.get("todos").get("pending").asLong());

        // lastCheck 可为 null（无筛查记录时），有记录时结构完整
        JsonNode lastCheck = afterDelete.get("lastCheck");
        if (!lastCheck.isNull()) {
            assertNotNull(lastCheck.get("title").asText());
            assertNotNull(lastCheck.get("checkedAt").asText());
        }
    }

    @Test
    void trendsWeeklyBucketsAndPie() throws Exception {
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.LocalDate thisMonday = java.time.LocalDate.now(zone).with(java.time.DayOfWeek.MONDAY);
        java.time.Instant thisWeekTime = java.time.LocalDate.now(zone).atTime(12, 0).atZone(zone).toInstant();
        java.time.Instant lastWeekTime = thisMonday.minusDays(3).atTime(10, 0).atZone(zone).toInstant();

        // 学生 id 动态取（共享 H2 下其他测试可能整表替换过名单）
        MvcResult studentsResult = mockMvc.perform(get("/api/students")).andExpect(status().isOk()).andReturn();
        JsonNode students = objectMapper.readTree(studentsResult.getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).get("data").get("students");
        assertTrue(students.size() >= 3, "需要至少 3 名学生");
        long sid1 = students.get(0).get("id").asLong();
        long sid2 = students.get(1).get("id").asLong();
        long sid3 = students.get(2).get("id").asLong();

        // 造数：本周 2 条请假 + 上周 1 条（不同类型便于饼图断言）
        createLeave(sid1, "sick", thisWeekTime, thisWeekTime.plus(6, java.time.temporal.ChronoUnit.HOURS));
        createLeave(sid2, "sick", thisWeekTime, thisWeekTime.plus(6, java.time.temporal.ChronoUnit.HOURS));
        createLeave(sid3, "official", lastWeekTime, lastWeekTime.plus(6, java.time.temporal.ChronoUnit.HOURS));

        // 造数：本周 1 条筛查记录
        Map<String, Object> checkBody = new HashMap<>();
        checkBody.put("text", students.get(0).get("name").asText() + "\n" + students.get(1).get("name").asText());
        checkBody.put("save", true);
        checkBody.put("title", "趋势检查筛查");
        mockMvc.perform(post("/api/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(checkBody)))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/dashboard/trends")).andExpect(status().isOk()).andReturn();
        JsonNode trends = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data");

        // 周桶结构：8 个周标签，与两组序列等长
        assertEquals(8, trends.get("weeks").size());
        assertEquals(8, trends.get("weeklyLeave").size());
        assertEquals(8, trends.get("weeklyChecks").size());

        // 本周桶（最后一项）：请假 >= 2、筛查 >= 1（弱断言，容忍共享库遗留数据）
        assertTrue(trends.get("weeklyLeave").get(7).asInt() >= 2, "本周请假人次应落桶");
        assertTrue(trends.get("weeklyChecks").get(7).asInt() >= 1, "本周筛查次数应落桶");

        // 上周桶：请假 >= 1
        assertTrue(trends.get("weeklyLeave").get(6).asInt() >= 1, "上周请假人次应落桶");

        // 请假类型饼图：4 类齐全，病假 >= 2
        JsonNode pie = trends.get("leaveTypePie");
        assertEquals(4, pie.size());
        long sickCount = 0;
        for (JsonNode p : pie) {
            if ("病假".equals(p.get("name").asText())) {
                sickCount = p.get("value").asLong();
            }
        }
        assertTrue(sickCount >= 2, "近 30 天病假应至少 2 条");

        // 收集横条：结构与字段
        JsonNode bars = trends.get("collectionBars");
        assertTrue(bars.isArray());
        for (JsonNode b : bars) {
            assertTrue(b.get("name").asText().length() > 0);
            assertNotNull(b.get("received"));
            assertNotNull(b.get("total"));
        }
    }

    private void createLeave(long studentId, String type, java.time.Instant start, java.time.Instant end)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("studentIds", List.of(studentId));
        body.put("leaveType", type);
        body.put("startTime", start.toString());
        body.put("endTime", end.toString());
        body.put("reason", "趋势测试");
        mockMvc.perform(post("/api/leaves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }
}
