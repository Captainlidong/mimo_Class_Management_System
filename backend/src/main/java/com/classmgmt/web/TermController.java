package com.classmgmt.web;

import com.classmgmt.service.TermService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 学期档案：列表 / 切换 / 换届（需登录）。 */
@RestController
@RequestMapping("/api/terms")
public class TermController {

    private final TermService termService;

    public TermController(TermService termService) {
        this.termService = termService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list() {
        return ApiResponse.ok(termService.list());
    }

    /** 创建新学期并换届：旧学期归档只读，新学期空白名单。 */
    @PostMapping
    public ApiResponse<Map<String, Object>> createAndSwitch(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(termService.createAndSwitch(body));
    }

    /** 切换浏览学期（历史学期只读）。 */
    @PostMapping("/{id}/switch")
    public ApiResponse<Map<String, Object>> switchTo(@PathVariable Long id) {
        termService.switchTo(id);
        return ApiResponse.ok(termService.list());
    }
}
