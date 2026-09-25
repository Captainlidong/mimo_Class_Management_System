package com.classmgmt.web;

import com.classmgmt.dto.CheckRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.classmgmt.dto.StudentsReplaceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void seedProvidesStudentsAndCheckWorks() throws Exception {
        restoreSampleBaseline();

        MvcResult result = mockMvc.perform(get("/api/students"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode payload = readJson(result);
        assertEquals(0, payload.get("code").asInt());
        JsonNode data = payload.get("data");
        assertTrue(data.get("count").asInt() >= 36);
        assertFalse(data.get("locked").asBoolean());

        String first = data.get("students").get(0).get("name").asText();

        CheckRequest req = new CheckRequest();
        req.setText("1. " + first + "\n" + first + "\nOutsiderXYZ");
        req.setSave(true);
        req.setTitle("IntegrationCheck");

        MvcResult checkResult = mockMvc.perform(post("/api/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode checkPayload = readJson(checkResult);
        assertEquals(0, checkPayload.get("code").asInt());
        JsonNode checkData = checkPayload.get("data");
        assertEquals(first, checkData.get("participated").get(0).asText());
        assertTrue(checkData.get("recordId").asLong() > 0);
        assertTrue(checkData.get("absent").size() >= 35);
        assertEquals("OutsiderXYZ", checkData.get("invalid").get(0).asText());

        long recordId = checkData.get("recordId").asLong();
        MvcRecordDetail(recordId, first);
    }

    private void restoreSampleBaseline() throws Exception {
        mockMvc.perform(post("/api/students/unlock")).andExpect(status().isOk());
        StudentsReplaceRequest restore = new StudentsReplaceRequest();
        for (int i = 0; i < 36; i++) {
            StudentsReplaceRequest.Item it = new StudentsReplaceRequest.Item();
            it.setName("Sample" + String.format("%02d", i + 1));
            it.setStudentNo(String.format("2024%02d", i + 1));
            it.setSortOrder(i + 1);
            restore.getStudents().add(it);
        }
        mockMvc.perform(put("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(restore)))
                .andExpect(status().isOk());
    }

    private void MvcRecordDetail(long recordId, String expectedName) throws Exception {
        MvcResult detailResult = mockMvc.perform(get("/api/check-records/" + recordId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode detail = readJson(detailResult).get("data");
        assertEquals("IntegrationCheck", detail.get("title").asText());
        assertEquals(expectedName, detail.get("participated").get(0).asText());
    }

    @Test
    void lockedStudentsRejectReplace() throws Exception {
        mockMvc.perform(post("/api/students/unlock")).andExpect(status().isOk());
        mockMvc.perform(post("/api/students/lock")).andExpect(status().isOk());

        StudentsReplaceRequest request = new StudentsReplaceRequest();
        StudentsReplaceRequest.Item item = new StudentsReplaceRequest.Item();
        item.setName("TestUser");
        request.setStudents(List.of(item));

        mockMvc.perform(put("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/students/unlock")).andExpect(status().isOk());
        mockMvc.perform(put("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void historyTitlePatchedButSnapshotStable() throws Exception {
        mockMvc.perform(post("/api/students/unlock")).andExpect(status().isOk());
        CheckRequest req = new CheckRequest();
        req.setText("TestUser");
        req.setSave(true);
        req.setTitle("OldTitle");
        MvcResult created = mockMvc.perform(post("/api/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        long id = readJson(created).get("data").get("recordId").asLong();

        mockMvc.perform(patch("/api/check-records/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"title\":\"NewTitle\",\"remark\":\"note\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("NewTitle"));

        mockMvc.perform(get("/api/check-records/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("NewTitle"))
                .andExpect(jsonPath("$.data.remark").value("note"));
    }

    @Test
    void healthAndGroupsCrud() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"name\":\"GroupA\",\"studentIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("GroupA"));

        MvcResult groups = mockMvc.perform(get("/api/groups"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = readJson(groups).get("data");
        assertTrue(data.isArray());
        assertFalse(data.isEmpty());
    }
}
