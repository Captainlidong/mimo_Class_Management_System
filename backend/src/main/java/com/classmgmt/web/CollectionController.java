package com.classmgmt.web;

import com.classmgmt.service.CollectionService;
import com.classmgmt.service.ExportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/collections")
public class CollectionController {

    private final CollectionService collectionService;
    private final ExportService exportService;

    public CollectionController(CollectionService collectionService, ExportService exportService) {
        this.collectionService = collectionService;
        this.exportService = exportService;
    }

    @GetMapping("/tasks")
    public ApiResponse<List<Map<String, Object>>> tasks() {
        return ApiResponse.ok(collectionService.listTasks());
    }

    @PostMapping("/tasks")
    public ApiResponse<Map<String, Object>> createTask(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(collectionService.createTask(body));
    }

    @PatchMapping("/tasks/{id}")
    public ApiResponse<Map<String, Object>> updateTask(@PathVariable Long id,
                                                       @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(collectionService.updateTask(id, body));
    }

    @DeleteMapping("/tasks/{id}")
    public ApiResponse<Void> deleteTask(@PathVariable Long id) {
        collectionService.deleteTask(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse<Map<String, Object>> taskDetail(@PathVariable Long id) {
        return ApiResponse.ok(collectionService.getTask(id));
    }

    /** 点击状态标签切换交表状态，body = {submitted: true/false}。 */
    @PatchMapping("/entries/{id}")
    public ApiResponse<Map<String, Object>> setSubmitted(@PathVariable Long id,
                                                         @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(collectionService.setEntrySubmitted(id, body));
    }

    /** 向已有任务补充人员，body = {studentIds: []}；新人员默认未交，已在名单的自动跳过。 */
    @PostMapping("/tasks/{id}/students")
    public ApiResponse<Map<String, Object>> addStudents(@PathVariable Long id,
                                                        @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(collectionService.addStudents(id, body));
    }

    /** 从任务名单移除一位同学。 */
    @DeleteMapping("/tasks/{id}/students/{studentId}")
    public ApiResponse<Void> removeStudent(@PathVariable Long id, @PathVariable Long studentId) {
        collectionService.removeStudent(id, studentId);
        return ApiResponse.ok(null);
    }

    /** 粘贴名单批量标记，body = {text: "张三，李四", submitted: true}。 */
    @PostMapping("/tasks/{id}/mark")
    public ApiResponse<Map<String, Object>> markByText(@PathVariable Long id,
                                                       @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(collectionService.markByText(id, body));
    }

    @GetMapping("/tasks/{id}/export")
    public ResponseEntity<byte[]> exportTask(@PathVariable Long id,
                                             @RequestParam(defaultValue = "xlsx") String format,
                                             @RequestParam(defaultValue = "all") String scope) {
        return exportService.exportCollection(id, format, scope);
    }
}
