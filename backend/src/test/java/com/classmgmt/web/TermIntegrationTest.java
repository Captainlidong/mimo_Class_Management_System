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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 学期档案：换届归档 / 只读 guard / 按学期过滤。 */
@SpringBootTest
@AutoConfigureMockMvc
class TermIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode terms() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/terms")).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data");
    }

    @Test
    void termLifecycleAndReadOnlyGuard() throws Exception {
        // 1. 默认学期存在且为浏览上下文
        JsonNode before = terms();
        assertTrue(before.get("terms").size() >= 1, "至少有默认学期");
        Long originalView = before.get("viewTermId").asLong();

        // 2. 在当前学期建一条待办
        Map<String, Object> todo = new HashMap<>();
        todo.put("title", "学期归属检查");
        MvcResult created = mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(todo)))
                .andExpect(status().isOk())
                .andReturn();
        long todoId = objectMapper.readTree(created.getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).get("data").get("id").asLong();

        // 3. 创建新学期并换届 → 浏览指针切到新学期，旧学期归档
        Map<String, Object> newTerm = new HashMap<>();
        newTerm.put("name", "2026-2027 学年第一学期");
        MvcResult switchResult = mockMvc.perform(post("/api/terms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(newTerm)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andReturn();
        long newTermId = objectMapper.readTree(switchResult.getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).get("data").get("id").asLong();

        JsonNode after = terms();
        assertEquals(newTermId, after.get("viewTermId").asLong(), "换届后浏览指针应指向新学期");
        assertNotEquals(originalView, after.get("viewTermId").asLong());

        // 4. 按学期过滤：新学期看不到旧学期待办
        JsonNode todosAfter = objectMapper.readTree(mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8));
        boolean oldVisible = false;
        for (JsonNode t : todosAfter.get("data")) {
            if (t.get("id").asLong() == todoId) {
                oldVisible = true;
            }
        }
        assertTrue(!oldVisible, "换届后旧学期待办不应出现在新学期列表");

        // 5. 只读 guard：直接改旧学期待办 → 400
        Map<String, Object> edit = new HashMap<>();
        edit.put("done", true);
        mockMvc.perform(patch("/api/todos/" + todoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(edit)))
                .andExpect(status().isBadRequest());

        // 6. 切回旧学期浏览（只读），旧待办重新可见；再切回新学期
        mockMvc.perform(post("/api/terms/" + originalView + "/switch")).andExpect(status().isOk());
        JsonNode todosOld = objectMapper.readTree(mockMvc.perform(get("/api/todos"))
                .andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8));
        boolean oldVisibleAgain = false;
        for (JsonNode t : todosOld.get("data")) {
            if (t.get("id").asLong() == todoId) {
                oldVisibleAgain = true;
            }
        }
        assertTrue(oldVisibleAgain, "切回旧学期应能查档看到原待办");
        mockMvc.perform(post("/api/terms/" + newTermId + "/switch")).andExpect(status().isOk());

        // 7. 清理（新学期上下文，旧学期待办不可删属预期）
        mockMvc.perform(delete("/api/todos/" + todoId)).andExpect(status().isBadRequest());
    }
}
