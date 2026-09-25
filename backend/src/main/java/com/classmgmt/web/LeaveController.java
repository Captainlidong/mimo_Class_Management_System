package com.classmgmt.web;

import com.classmgmt.service.ExportService;
import com.classmgmt.service.LeaveService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leaves")
public class LeaveController {

    private final LeaveService leaveService;
    private final ExportService exportService;

    public LeaveController(LeaveService leaveService, ExportService exportService) {
        this.leaveService = leaveService;
        this.exportService = exportService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) Long studentId,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String type,
                                                       @RequestParam(required = false) String from,
                                                       @RequestParam(required = false) String to) {
        return ApiResponse.ok(leaveService.list(studentId, status, type,
                from == null ? null : leaveService.parseTime(from, "开始时间"),
                to == null ? null : leaveService.parseTime(to, "结束时间")));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(leaveService.stats());
    }

    @PostMapping
    public ApiResponse<List<Map<String, Object>>> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(leaveService.create(body));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id,
                                                   @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(leaveService.update(id, body));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<Map<String, Object>> close(@PathVariable Long id,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(leaveService.close(id, body));
    }

    @PostMapping("/{id}/reopen")
    public ApiResponse<Map<String, Object>> reopen(@PathVariable Long id) {
        return ApiResponse.ok(leaveService.reopen(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        leaveService.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "xlsx") String format) {
        return exportService.exportLeaves(format);
    }
}
