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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 学生档案状态：两态（在班/离班）、diff 式名单同步（软移出）、筛查口径只算在班。
 * 共享 H2 下其他类可能改过名单，全部用动态基线断言，不硬编码种子姓名。
 */
@SpringBootTest
@AutoConfigureMockMvc
class StudentStatusIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode students() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/students")).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data");
    }

    private JsonNode bodyOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data");
    }

    @Test
    void replaceSoftRemovesAndCheckExcludesLeft() throws Exception {
        // 动态基线（不硬编码种子姓名；共享 H2 下可能含其他类遗留）
        JsonNode before = students();
        assertTrue(before.get("students").size() >= 3, "需要至少 3 名学生");
        long id1 = before.get("students").get(0).get("id").asLong();
        long id2 = before.get("students").get(1).get("id").asLong();
        String name1 = before.get("students").get(0).get("name").asText();
        String name2 = before.get("students").get(1).get("name").asText();
        List<Map<String, Object>> keepRoster = new ArrayList<>();
        for (JsonNode s : before.get("students")) {
            if (s.get("id").asLong() == id1 || s.get("id").asLong() == id2) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("name", s.get("name").asText());
            row.put("studentNo", s.get("studentNo").asText());
            keepRoster.add(row);
        }

        // 1. 先建一个含全部学生的收集任务——验证软移出后历史条目保留
        List<Long> allIds = new ArrayList<>();
        for (JsonNode s : before.get("students")) {
            allIds.add(s.get("id").asLong());
        }
        Map<String, Object> taskBody = new HashMap<>();
        taskBody.put("name", "软移出历史条目检查");
        taskBody.put("studentIds", allIds);
        MvcResult taskResult = mockMvc.perform(post("/api/collections/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(taskBody)))
                .andExpect(status().isOk())
                .andReturn();
        long taskId = bodyOf(taskResult).get("id").asLong();

        // 2. replace 为去掉前 2 人的名单 → id1/id2 软移出而非物理删除；总人数不变
        int beforeCount = before.get("count").asInt();
        Map<String, Object> replaceBody = new HashMap<>();
        replaceBody.put("students", keepRoster);
        mockMvc.perform(put("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(replaceBody)))
                .andExpect(status().isOk());

        JsonNode afterReplace = students();
        assertEquals(beforeCount, afterReplace.get("count").asInt(), "diff 同步：总人数不变（软移出不删行）");
        int leftCount = 0;
        boolean firstLeft = false;
        boolean secondLeft = false;
        for (JsonNode s : afterReplace.get("students")) {
            if ("left".equals(s.get("status").asText())) {
                leftCount++;
                if (s.get("id").asLong() == id1) {
                    firstLeft = true;
                }
                if (s.get("id").asLong() == id2) {
                    secondLeft = true;
                }
            }
        }
        assertEquals(2, leftCount, "被移出的 2 人应标记为离班");
        assertTrue(firstLeft && secondLeft, "前两名学生应软移出");

        // 3. 历史收集条目保留（任务仍全部人数）
        mockMvc.perform(get("/api/collections/tasks/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(allIds.size()));

        // 4. 筛查口径：只算在班——把全部在班名单贴进去，未参与应不含离班学生
        StringBuilder text = new StringBuilder();
        for (JsonNode s : afterReplace.get("students")) {
            if (!"left".equals(s.get("status").asText())) {
                text.append(s.get("name").asText()).append('\n');
            }
        }
        Map<String, Object> checkBody = new HashMap<>();
        checkBody.put("text", text.toString());
        MvcResult checkResult = mockMvc.perform(post("/api/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(checkBody)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode counts = bodyOf(checkResult).get("counts");
        assertEquals(beforeCount - 2, counts.get("baseline").asInt(), "筛查基准应只含在班学生");
        boolean absentHasLeft = false;
        for (JsonNode a : bodyOf(checkResult).get("absent")) {
            if (a.asText().equals(name1) || a.asText().equals(name2)) {
                absentHasLeft = true;
            }
        }
        assertFalse(absentHasLeft, "离班学生不应出现在未参与名单");

        // 5. mark-leave / restore 往返（用尚在班的另一名学生）
        long thirdId = 0;
        for (JsonNode s : afterReplace.get("students")) {
            if (!"left".equals(s.get("status").asText()) && s.get("id").asLong() != id1
                    && s.get("id").asLong() != id2) {
                thirdId = s.get("id").asLong();
                break;
            }
        }
        MvcResult restored = mockMvc.perform(post("/api/students/" + thirdId + "/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("in_school"))
                .andReturn();
        assertTrue(bodyOf(restored).get("status").asText().contains("in_school"));
        MvcResult leftAgain = mockMvc.perform(post("/api/students/" + thirdId + "/mark-leave"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("left"))
                .andReturn();
        assertTrue(bodyOf(leftAgain).get("status").asText().contains("left"));
        mockMvc.perform(post("/api/students/" + thirdId + "/restore")).andExpect(status().isOk());

        // 6. 操作日志：软移出与恢复均留痕
        MvcResult logs = mockMvc.perform(get("/api/operation-logs")).andExpect(status().isOk()).andReturn();
        JsonNode logItems = bodyOf(logs).get("items");
        boolean hasMark = false;
        boolean hasRestore = false;
        for (JsonNode item : logItems) {
            if ("roster.markLeave".equals(item.get("action").asText())) {
                hasMark = true;
            }
            if ("roster.restore".equals(item.get("action").asText())) {
                hasRestore = true;
            }
        }
        assertTrue(hasMark && hasRestore, "离班/恢复应写入操作日志");
    }
}
