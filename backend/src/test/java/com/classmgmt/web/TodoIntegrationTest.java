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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 待办 CRUD / 紧迫度排序 / 优先级 / 重复规则。
 * 共享 H2 上下文下其他测试类可能遗留数据，断言一律用「本测试创建的唯一标题」做相对定位。
 */
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

    /** 按 title 找本测试创建的待办（可能有多期），返回 id 列表（列表顺序）。 */
    private List<Long> idsOf(JsonNode todos, String title) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode t : todos) {
            if (title.equals(t.get("title").asText())) {
                ids.add(t.get("id").asLong());
            }
        }
        return ids;
    }

    private String debugOrder(JsonNode todos) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode t : todos) {
            sb.append("[").append(t.get("title").asText()).append(" done=").append(t.get("done").asBoolean()).append("]");
        }
        return sb.toString();
    }

    /** 自愈：其他测试类可能把浏览指针留在历史学期（只读），先切回可写学期。 */
    private void ensureWritableView() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/terms")).andExpect(status().isOk()).andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data");
        if (data.get("viewTermId").asLong() != data.get("activeTermId").asLong()) {
            mockMvc.perform(post("/api/terms/" + data.get("activeTermId").asLong() + "/switch"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void todoCrudAndUrgencySorting() throws Exception {
        ensureWritableView();
        Instant now = Instant.now();
        // 1. 建三条：已过期 / 未来一小时 / 无期限（跟进事项）
        createTodo("结课前收齐课程作业", "老师要求结课前一周收齐打印版", now.minus(2, ChronoUnit.DAYS));
        createTodo("收齐奖学金申请表", null, now.plus(1, ChronoUnit.HOURS));
        createTodo("团组织关系转接跟进", "等系统恢复后办理，不能错过", null);
        JsonNode todos = listTodos().get("data");

        // 2. 相对排序：已过期 → 临期 → 无期限（用唯一标题定位，容忍库里其他遗留数据）
        int expiredIdx = -1;
        int soonIdx = -1;
        int noDueIdx = -1;
        JsonNode expiredNode = null;
        for (int i = 0; i < todos.size(); i++) {
            JsonNode t = todos.get(i);
            switch (t.get("title").asText()) {
                case "结课前收齐课程作业" -> { expiredIdx = i; expiredNode = t; }
                case "收齐奖学金申请表" -> soonIdx = i;
                case "团组织关系转接跟进" -> noDueIdx = i;
                default -> { }
            }
        }
        assertTrue(expiredIdx >= 0 && soonIdx >= 0 && noDueIdx >= 0, "三条测试待办都应存在");
        assertTrue(expiredIdx < soonIdx && soonIdx < noDueIdx, "排序应为 已过期 → 临期 → 无期限");
        assertTrue(expiredNode.get("overdue").asBoolean(), "过期标记应为 true");

        // 3. 标记临期那条完成：完成后沉底（在其无期限项之后）
        Map<String, Object> doneBody = new HashMap<>();
        doneBody.put("done", true);
        mockMvc.perform(patch("/api/todos/" + idsOf(todos, "收齐奖学金申请表").get(0))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(doneBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.done").value(true));
        todos = listTodos().get("data");
        int doneIdx = -1;
        for (int i = 0; i < todos.size(); i++) {
            if ("收齐奖学金申请表".equals(todos.get(i).get("title").asText())) {
                doneIdx = i;
                assertTrue(todos.get(i).get("done").asBoolean(), "已完成标记");
            }
        }
        int lastUndoneIdx = -1;
        for (int i = 0; i < todos.size(); i++) {
            if (!todos.get(i).get("done").asBoolean()) {
                lastUndoneIdx = i;
            }
        }
        assertTrue(doneIdx > lastUndoneIdx, "已完成项应排在未完成之后 | debug: doneIdx=" + doneIdx
                + " lastUndoneIdx=" + lastUndoneIdx + " order=" + debugOrder(todos));

        // 4. 部分更新：改标题 + 截止时间（无期限 → 有期限）
        Map<String, Object> editBody = new HashMap<>();
        editBody.put("title", "团组织关系转接（等通知）");
        editBody.put("dueAt", now.plus(3, ChronoUnit.DAYS).toString());
        mockMvc.perform(patch("/api/todos/" + idsOf(todos, "团组织关系转接跟进").get(0))
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

        // 6. 清理本测试创建的待办
        for (String title : new String[] {"结课前收齐课程作业", "收齐奖学金申请表", "团组织关系转接跟进", "团组织关系转接（等通知）"}) {
            for (Long id : idsOf(listTodos().get("data"), title)) {
                mockMvc.perform(delete("/api/todos/" + id)).andExpect(status().isOk());
            }
        }
    }

    @Test
    void todoPriorityCategoryAndRepeat() throws Exception {
        ensureWritableView();
        Instant now = Instant.now();
        // 1. 创建带优先级/分类/重复规则的周期待办
        Map<String, Object> body = new HashMap<>();
        body.put("title", "每周班委例会汇报（重复测试）");
        body.put("detail", "每周班会上汇报");
        body.put("dueAt", now.plus(1, ChronoUnit.DAYS).toString());
        body.put("priority", "high");
        body.put("category", "通知");
        body.put("repeatRule", "weekly");
        MvcResult created = mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priority").value("high"))
                .andExpect(jsonPath("$.data.category").value("通知"))
                .andExpect(jsonPath("$.data.repeatRule").value("weekly"))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).get("data").get("id").asLong();

        // 2. 非法值 → 400
        mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"title\":\"x\",\"priority\":\"urgent\"}"))
                .andExpect(status().isBadRequest());

        // 3. 完成周期待办 → 自动生成下期（dueAt 顺延一周，未完成，同优先级/分类/规则）
        Map<String, Object> doneBody = new HashMap<>();
        doneBody.put("done", true);
        mockMvc.perform(patch("/api/todos/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(doneBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.done").value(true));

        JsonNode todos = listTodos().get("data");
        JsonNode next = null;
        for (JsonNode t : todos) {
            if (t.get("id").asLong() != id && "每周班委例会汇报（重复测试）".equals(t.get("title").asText())) {
                next = t;
            }
        }
        assertTrue(next != null, "完成后应自动生成下一期");
        assertEquals("high", next.get("priority").asText());
        assertEquals("weekly", next.get("repeatRule").asText());
        assertTrue(Instant.parse(next.get("dueAt").asText()).isAfter(now.plus(6, ChronoUnit.DAYS)),
                "下期截止时间应顺延约一周");
        assertTrue(!next.get("done").asBoolean());

        // 4. 部分更新：只改优先级，其余字段保持
        Map<String, Object> pBody = new HashMap<>();
        pBody.put("priority", "low");
        mockMvc.perform(patch("/api/todos/" + next.get("id").asLong())
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content(objectMapper.writeValueAsString(pBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priority").value("low"))
                .andExpect(jsonPath("$.data.category").value("通知"));

        // 5. 默认值：不带新字段创建 → mid/其他/none
        MvcResult plain = mockMvc.perform(post("/api/todos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"title\":\"默认字段检查（重复测试）\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priority").value("mid"))
                .andExpect(jsonPath("$.data.category").value("其他"))
                .andExpect(jsonPath("$.data.repeatRule").value("none"))
                .andReturn();
        long plainId = objectMapper.readTree(plain.getResponse()
                .getContentAsString(StandardCharsets.UTF_8)).get("data").get("id").asLong();

        // 6. 清理本测试创建的待办
        mockMvc.perform(delete("/api/todos/" + id)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/todos/" + next.get("id").asLong())).andExpect(status().isOk());
        mockMvc.perform(delete("/api/todos/" + plainId)).andExpect(status().isOk());
    }
}
