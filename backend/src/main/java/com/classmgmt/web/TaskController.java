package com.classmgmt.web;

import com.classmgmt.domain.LongTask;
import com.classmgmt.dto.CheckRecordSummaryDTO;
import com.classmgmt.repo.CheckRecordRepository;
import com.classmgmt.repo.LongTaskRepository;
import com.classmgmt.service.HistoryService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final LongTaskRepository longTaskRepository;
    private final HistoryService historyService;
    private final CheckRecordRepository checkRecordRepository;

    public TaskController(LongTaskRepository longTaskRepository,
                          HistoryService historyService,
                          CheckRecordRepository checkRecordRepository) {
        this.longTaskRepository = longTaskRepository;
        this.historyService = historyService;
        this.checkRecordRepository = checkRecordRepository;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        List<Map<String, Object>> items = longTaskRepository.findAll().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("name", t.getName());
            m.put("type", t.getType());
            m.put("remark", t.getRemark());
            m.put("recordCount", checkRecordRepository.countByTaskId(t.getId()));
            return m;
        }).toList();
        return ApiResponse.ok(items);
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").trim();
        if (name.isEmpty()) {
            return ApiResponse.fail(400, "任务名称不能为空");
        }
        LongTask task = new LongTask();
        task.setName(name);
        task.setType(body.getOrDefault("type", "自定义"));
        task.setRemark(body.get("remark"));
        longTaskRepository.save(task);
        return ApiResponse.ok(Map.of(
                "id", task.getId(),
                "name", task.getName(),
                "type", task.getType(),
                "remark", task.getRemark() == null ? "" : task.getRemark(),
                "recordCount", 0
        ));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id,
                                                   @RequestBody Map<String, String> body) {
        LongTask task = longTaskRepository.findById(id)
                .orElseThrow(() -> GlobalExceptionHandler.notFound("任务不存在"));
        if (body.containsKey("name")) {
            String name = body.get("name") == null ? "" : body.get("name").trim();
            if (name.isEmpty()) {
                return ApiResponse.fail(400, "任务名称不能为空");
            }
            task.setName(name);
        }
        if (body.containsKey("type")) {
            task.setType(body.get("type"));
        }
        if (body.containsKey("remark")) {
            task.setRemark(body.get("remark"));
        }
        longTaskRepository.save(task);
        return ApiResponse.ok(Map.of(
                "id", task.getId(),
                "name", task.getName(),
                "type", task.getType() == null ? "" : task.getType(),
                "remark", task.getRemark() == null ? "" : task.getRemark(),
                "recordCount", checkRecordRepository.countByTaskId(task.getId())
        ));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        if (!longTaskRepository.existsById(id)) {
            return ApiResponse.fail(404, "任务不存在");
        }
        longTaskRepository.deleteById(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/records")
    public ApiResponse<List<CheckRecordSummaryDTO>> records(@PathVariable Long id) {
        return ApiResponse.ok(historyService.search(id, null));
    }
}
