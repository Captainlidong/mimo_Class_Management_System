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

/** 通知中心：过期待办惰性生成 / 去重 / 已读。 */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode listNotifications() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/notifications")).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data");
    }

    @Test
    void notificationGenerateDedupeAndRead() throws Exception {
        // 1. 建一条已过期待办 → 拉取通知应自动生成对应提醒
        Map<String, Object> todo = new HashMap<>();
        todo.put("title", "通知中心联动检查");
        todo.put("dueAt", Instant.now().minus(1, ChronoUnit.DAYS).toString());
        MvcResult created = mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(todo)))
                .andExpect(status().isOk())
                .andReturn();
        long todoId = objectMapper.readTree(created.getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).get("data").get("id").asLong();

        JsonNode first = listNotifications();
        long foundId = 0;
        for (JsonNode n : first.get("items")) {
            if ("todo_due".equals(n.get("type").asText())
                    && n.get("title").asText().contains("通知中心联动检查")) {
                foundId = n.get("id").asLong();
                assertTrue(!n.get("read").asBoolean(), "新生成通知应为未读");
            }
        }
        assertTrue(foundId > 0, "过期待办应生成通知");

        // 2. 再次拉取不重复生成（dedupeKey 唯一）
        JsonNode second = listNotifications();
        int count = 0;
        for (JsonNode n : second.get("items")) {
            if (n.get("id").asLong() == foundId) {
                count++;
            }
        }
        assertEquals(1, count, "同一来源只应有一条通知");

        // 3. 标记已读 → 未读数下降
        long unreadBefore = second.get("unread").asLong();
        mockMvc.perform(post("/api/notifications/" + foundId + "/read")).andExpect(status().isOk());
        JsonNode third = listNotifications();
        assertTrue(third.get("unread").asLong() == unreadBefore - 1, "已读后未读数应下降");

        // 4. 清理待办（通知保留为历史，不校验）
        mockMvc.perform(delete("/api/todos/" + todoId)).andExpect(status().isOk());
    }
}
