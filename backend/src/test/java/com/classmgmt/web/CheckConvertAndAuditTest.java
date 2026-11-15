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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 筛查记录一键转收集任务 + 操作日志审计。 */
@SpringBootTest
@AutoConfigureMockMvc
class CheckConvertAndAuditTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode bodyOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data");
    }

    @Test
    void convertRecordToCollectionAndAuditLog() throws Exception {
        // 1. 建筛查记录：2 人参与（王伟/李娜在种子名单）、1 个外校无效、save=true
        Map<String, Object> checkBody = new HashMap<>();
        checkBody.put("text", "王伟\n李娜\n外校同学");
        checkBody.put("save", true);
        checkBody.put("title", "补交联动检查");
        MvcResult checkResult = mockMvc.perform(post("/api/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(checkBody)))
                .andExpect(status().isOk())
                .andReturn();
        long recordId = bodyOf(checkResult).get("recordId").asLong();
        long absentCount = bodyOf(checkResult).get("counts").get("absent").asLong();
        assertTrue(absentCount > 0, "演示筛查应有未参与名单");

        // 2. 一键转收集：任务覆盖全部未参与学生（外校同学属于无效项，不在未参与名单里）
        MvcResult convert = mockMvc.perform(post("/api/check-records/" + recordId + "/convert-to-collection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").isNumber())
                .andExpect(jsonPath("$.data.name").value("补交联动检查 补交收集"))
                .andReturn();
        JsonNode convertData = bodyOf(convert);
        assertEquals(absentCount, convertData.get("total").asInt(), "转收集任务应覆盖全部未参与学生");
        assertEquals(0, convertData.get("unmatched").size(), "未参与姓名全部来自基准名单，不应有未匹配项");

        // 3. 转出的任务存在且名单为未参与的若干人、全部未交
        long taskId = convertData.get("taskId").asLong();
        mockMvc.perform(get("/api/collections/tasks/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.received").value(0));

        // 4. 操作日志应记录 check.convert
        MvcResult logs = mockMvc.perform(get("/api/operation-logs")).andExpect(status().isOk()).andReturn();
        JsonNode items = bodyOf(logs).get("items");
        boolean found = false;
        for (JsonNode item : items) {
            if ("check.convert".equals(item.get("action").asText())
                    && item.get("summary").asText().contains("补交联动检查")) {
                found = true;
            }
        }
        assertTrue(found, "转收集动作应写入操作日志");
    }
}
