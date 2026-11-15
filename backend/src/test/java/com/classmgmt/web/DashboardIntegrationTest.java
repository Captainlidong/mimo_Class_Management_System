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
}
