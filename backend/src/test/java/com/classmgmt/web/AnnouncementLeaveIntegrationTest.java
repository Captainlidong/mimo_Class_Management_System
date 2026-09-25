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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnnouncementLeaveIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private long firstStudentId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/students")).andExpect(status().isOk()).andReturn();
        return readJson(result).get("data").get("students").get(0).get("id").asLong();
    }

    @Test
    void announcementVersionLifecycle() throws Exception {
        Map<String, Object> createBody = new HashMap<>();
        createBody.put("title", "转：资助材料通知");
        createBody.put("category", "notice");
        createBody.put("source", "导员张老师");
        createBody.put("originalText", "原文A");
        createBody.put("editedText", "第一版");
        MvcResult created = mockMvc.perform(post("/api/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(json(createBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("draft"))
                .andReturn();
        long id = readJson(created).get("data").get("id").asLong();

        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("editedText", "第二版");
        updateBody.put("remark", "导员要求改口吻");
        mockMvc.perform(put("/api/announcements/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(json(updateBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editedText").value("第二版"))
                .andExpect(jsonPath("$.data.versions.length()").value(1))
                .andExpect(jsonPath("$.data.versions[0].content").value("第一版"));

        Map<String, Object> sameBody = new HashMap<>();
        sameBody.put("editedText", "第二版");
        mockMvc.perform(put("/api/announcements/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(json(sameBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versions.length()").value(1));

        long versionId = readJson(mockMvc.perform(get("/api/announcements/" + id)).andReturn())
                .get("data").get("versions").get(0).get("id").asLong();
        mockMvc.perform(post("/api/announcements/" + id + "/versions/" + versionId + "/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editedText").value("第一版"))
                .andExpect(jsonPath("$.data.versions.length()").value(2));

        mockMvc.perform(post("/api/announcements/" + id + "/mark-sent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("sent"))
                .andExpect(jsonPath("$.data.sentAt").isNotEmpty());

        mockMvc.perform(patch("/api/announcements/" + id + "/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pinned\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pinned").value(true));

        MvcResult list = mockMvc.perform(get("/api/announcements").param("status", "sent")
                        .param("q", "资助材料"))
                .andExpect(status().isOk())
                .andReturn();
        boolean found = false;
        for (JsonNode item : readJson(list).get("data")) {
            if (item.get("id").asLong() == id) {
                found = true;
            }
        }
        assertTrue(found, "按状态+关键字应能找到该素材");

        mockMvc.perform(delete("/api/announcements/" + id)).andExpect(status().isOk());
        mockMvc.perform(get("/api/announcements/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void leaveFlowEndToEnd() throws Exception {
        long sid = firstStudentId();

        Map<String, Object> createBody = new HashMap<>();
        createBody.put("studentIds", java.util.List.of(sid));
        createBody.put("leaveType", "sick");
        createBody.put("startTime", "2026-09-25T08:00:00");
        createBody.put("endTime", "2026-09-26T08:00:00");
        createBody.put("reason", "感冒发烧");
        createBody.put("approver", "导员张老师");
        MvcResult created = mockMvc.perform(post("/api/leaves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(json(createBody)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = readJson(created).get("data");
        assertEquals(1, rows.size());
        assertEquals("active", rows.get(0).get("status").asText());
        long leaveId = rows.get(0).get("id").asLong();

        mockMvc.perform(get("/api/leaves/stats"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/leaves/" + leaveId + "/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"returnedAt\":\"2026-09-26 09:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("closed"))
                .andExpect(jsonPath("$.data.returnedAt").isNotEmpty());

        mockMvc.perform(post("/api/leaves/" + leaveId + "/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        Map<String, Object> badRange = new HashMap<>();
        badRange.put("studentIds", java.util.List.of(sid));
        badRange.put("startTime", "2026-09-27T08:00:00");
        badRange.put("endTime", "2026-09-26T08:00:00");
        mockMvc.perform(post("/api/leaves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(badRange)))
                .andExpect(status().isBadRequest());

        Map<String, Object> unknownStudent = new HashMap<>();
        unknownStudent.put("studentIds", java.util.List.of(999999L));
        unknownStudent.put("startTime", "2026-09-25T08:00:00");
        unknownStudent.put("endTime", "2026-09-26T08:00:00");
        mockMvc.perform(post("/api/leaves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(unknownStudent)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/leaves/export").param("format", "xlsx"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/leaves/export").param("format", "csv"))
                .andExpect(status().isOk());

        MvcResult exportList = mockMvc.perform(get("/api/leaves").param("status", "closed"))
                .andExpect(status().isOk())
                .andReturn();
        boolean found = false;
        for (JsonNode row : readJson(exportList).get("data")) {
            if (row.get("id").asLong() == leaveId) {
                found = true;
                assertFalse(row.get("name").isMissingNode());
            }
        }
        assertTrue(found, "按状态应能查到已销假记录");

        mockMvc.perform(delete("/api/leaves/" + leaveId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/leaves").param("status", "closed")).andExpect(status().isOk());
    }
}
