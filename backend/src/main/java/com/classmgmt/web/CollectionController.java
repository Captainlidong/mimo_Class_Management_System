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

    /** 更新条目：body = {submitted: true/false} 切换状态，或 {remark: "..."} 更新备注（可同时传）。 */
    @PatchMapping("/entries/{id}")
    public ApiResponse<Map<String, Object>> updateEntry(@PathVariable Long id,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(collectionService.updateEntry(id, body));
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

    /** 一键标记全部条目，body = {submitted: true/false}；返回 {changed, total, received}。 */
    @PostMapping("/tasks/{id}/mark-all")
    public ApiResponse<Map<String, Object>> markAll(@PathVariable Long id,
                                                    @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(collectionService.markAll(id, body));
    }

    /** 批量移除任务名单成员，body = {studentIds: []}；不在名单里的跳过。返回 {removed, skipped}。 */
    @PostMapping("/tasks/{id}/students/batch-remove")
    public ApiResponse<Map<String, Object>> removeStudentsBatch(@PathVariable Long id,
                                                                @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(collectionService.removeStudentsBatch(id, body));
    }

    /** 导出任务名单。columns 可选（no,name,studentNo,status,time，remark），缺省全量；summary=true 时任务名/统计行附在名单末尾。 */
    @GetMapping("/tasks/{id}/export")
    public ResponseEntity<byte[]> exportTask(@PathVariable Long id,
                                             @RequestParam(defaultValue = "xlsx") String format,
                                             @RequestParam(defaultValue = "all") String scope,
                                             @RequestParam(required = false) String columns,
                                             @RequestParam(defaultValue = "false") boolean summary) {
        return exportService.exportCollection(id, format, scope, columns, summary);
    }
}
