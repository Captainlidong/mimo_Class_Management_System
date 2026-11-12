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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TodoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private MvcResult createTodo(String title, String detail, Instant dueAt) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        if (detail != null) {
            body.put("detail", detail);
        }
        if (dueAt != null) {
            body.put("dueAt", dueAt.toString());
        }
        return mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn();
    }

    private JsonNode listTodos() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/todos")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void todoCrudAndUrgencySorting() throws Exception {
        Instant now = Instant.now();
        // 1. 建三条：已过期 / 未来一小时 / 无期限（跟进事项）
        long expiredId = readJson(createTodo("结课前收齐课程作业", "老师要求结课前一周收齐打印版", now.minus(2, ChronoUnit.DAYS))
                ).get("data").get("id").asLong();
        long soonId = readJson(createTodo("收齐奖学金申请表", null, now.plus(1, ChronoUnit.HOURS))
                ).get("data").get("id").asLong();
        long noDueId = readJson(createTodo("团组织关系转接跟进", "等系统恢复后办理，不能错过", null)
                ).get("data").get("id").asLong();

        // 2. 排序：已过期 → 临期 → 无期限
        JsonNode todos = listTodos().get("data");
        assertEquals(expiredId, todos.get(0).get("id").asLong(), "已过期应排最前");
        assertEquals(soonId, todos.get(1).get("id").asLong(), "临期其次");
        assertEquals(noDueId, todos.get(2).get("id").asLong(), "无期限排未完成最后");
        assertTrue(todos.get(0).get("overdue").asBoolean(), "过期标记应为 true");
        assertTrue(todos.get(0).get("dueToday").asBoolean() || todos.get(1).get("dueToday").asBoolean(),
                "过期项或临期项至少一个标记当天到期");

        // 3. 标记未来那条完成：沉底且 doneAt 有值；列表顺序变为 过期 → 无期限 → 已完成
        Map<String, Object> doneBody = new HashMap<>();
        doneBody.put("done", true);
        MvcResult doneResult = mockMvc.perform(patch("/api/todos/" + soonId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(doneBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.done").value(true))
                .andExpect(jsonPath("$.data.doneAt").isNotEmpty())
                .andReturn();
        assertEquals(soonId, readJson(doneResult).get("data").get("id").asLong());

        JsonNode afterDone = listTodos().get("data");
        assertEquals(expiredId, afterDone.get(0).get("id").asLong());
        assertEquals(noDueId, afterDone.get(1).get("id").asLong());
        assertEquals(soonId, afterDone.get(2).get("id").asLong(), "已完成沉底");
        assertTrue(afterDone.get(2).get("done").asBoolean());

        // 4. 部分更新：改标题 + 截止时间（无期限 → 有期限）
        Map<String, Object> editBody = new HashMap<>();
        editBody.put("title", "团组织关系转接（等通知）");
        editBody.put("dueAt", now.plus(3, ChronoUnit.DAYS).toString());
        mockMvc.perform(patch("/api/todos/" + noDueId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(editBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("团组织关系转接（等通知）"))
                .andExpect(jsonPath("$.data.dueAt").isNotEmpty());

        // 5. 空标题 → 400
        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest());

        // 6. 清理
        mockMvc.perform(delete("/api/todos/" + expiredId)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/todos/" + soonId)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/todos/" + noDueId)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/todos/" + expiredId)).andExpect(status().isNotFound());
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
