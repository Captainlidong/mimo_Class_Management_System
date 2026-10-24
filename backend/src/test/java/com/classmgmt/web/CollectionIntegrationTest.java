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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CollectionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void collectionFlowWithMarkAndConvert() throws Exception {
        JsonNode first = readJson(mockMvc.perform(get("/api/students")).andExpect(status().isOk()).andReturn())
                .get("data").get("students").get(0);
        long sid = first.get("id").asLong();
        String sname = first.get("name").asText();

        // 1. 建任务（指定 1 名同学）
        Map<String, Object> createBody = new HashMap<>();
        createBody.put("name", "培养计划提交");
        createBody.put("studentIds", java.util.List.of(sid));
        MvcResult created = mockMvc.perform(post("/api/collections/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(createBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.received").value(0))
                .andReturn();
        long taskId = readJson(created).get("data").get("id").asLong();

        // 2. 粘贴名单标记：命中 1 人 + 1 个未匹配名字
        Map<String, Object> markBody = new HashMap<>();
        markBody.put("text", sname + "，外校路人甲");
        mockMvc.perform(post("/api/collections/tasks/" + taskId + "/mark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(markBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.marked.length()").value(1))
                .andExpect(jsonPath("$.data.notFound[0]").value("外校路人甲"));

        mockMvc.perform(get("/api/collections/tasks/" + taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.received").value(1))
                .andExpect(jsonPath("$.data.entries[0].submitted").value(true))
                .andExpect(jsonPath("$.data.entries[0].submittedAt").isNotEmpty());

        // 3. 切换回未交
        long entryId = readJson(mockMvc.perform(get("/api/collections/tasks/" + taskId)).andReturn())
                .get("data").get("entries").get(0).get("id").asLong();
        mockMvc.perform(patch("/api/collections/entries/" + entryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"submitted\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.submitted").value(false));

        // 4. 导出
        mockMvc.perform(get("/api/collections/tasks/" + taskId + "/export").param("format", "xlsx"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/collections/tasks/" + taskId + "/export")
                        .param("format", "xlsx").param("scope", "undelivered"))
                .andExpect(status().isOk());

        // 5. 建一个奖学金批次并转为资料收集任务
        Map<String, Object> batchBody = new HashMap<>();
        batchBody.put("name", "转换测试批次");
        MvcResult batchResult = mockMvc.perform(post("/api/scholarships/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(batchBody)))
                .andExpect(status().isOk())
                .andReturn();
        long batchId = readJson(batchResult).get("data").get("id").asLong();

        Map<String, Object> awardBody = new HashMap<>();
        awardBody.put("studentId", sid);
        awardBody.put("awardName", "三等");
        MvcResult awardResult = mockMvc.perform(post("/api/scholarships/batches/" + batchId + "/awards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(awardBody)))
                .andExpect(status().isOk())
                .andReturn();
        long awardId = readJson(awardResult).get("data").get(0).get("id").asLong();
        mockMvc.perform(post("/api/scholarships/awards/" + awardId + "/mark-received")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"received\":true}"))
                .andExpect(status().isOk());

        MvcResult converted = mockMvc.perform(post("/api/scholarships/batches/" + batchId + "/convert-to-collection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andReturn();
        long newTaskId = readJson(converted).get("data").get("taskId").asLong();

        mockMvc.perform(get("/api/collections/tasks/" + newTaskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.received").value(1))
                .andExpect(jsonPath("$.data.entries[0].name").value(sname));

        // 6. 清理
        mockMvc.perform(delete("/api/collections/tasks/" + taskId)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/collections/tasks/" + newTaskId)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/scholarships/awards/" + awardId)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/scholarships/batches/" + batchId)).andExpect(status().isOk());
    }
}
