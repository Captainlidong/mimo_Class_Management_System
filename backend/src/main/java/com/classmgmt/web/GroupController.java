package com.classmgmt.web;

import com.classmgmt.service.GroupService;
import com.classmgmt.service.HistoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;
    private final HistoryService historyService;

    public GroupController(GroupService groupService, HistoryService historyService) {
        this.groupService = groupService;
        this.historyService = historyService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(groupService.list());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(groupService.create(body));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id,
                                                   @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(groupService.update(id, body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        groupService.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/check-view")
    public ApiResponse<Map<String, Object>> checkView(@PathVariable Long id,
                                                      @RequestParam Long recordId) {
        return ApiResponse.ok(groupService.checkView(id, recordId, historyService));
    }
}
