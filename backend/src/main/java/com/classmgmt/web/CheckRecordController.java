package com.classmgmt.web;

import com.classmgmt.dto.CheckRecordSummaryDTO;
import com.classmgmt.dto.CheckResultDTO;
import com.classmgmt.service.CheckService;
import com.classmgmt.service.ExportService;
import com.classmgmt.service.HistoryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/check-records")
public class CheckRecordController {

    private final HistoryService historyService;
    private final ExportService exportService;
    private final CheckService checkService;

    public CheckRecordController(HistoryService historyService, ExportService exportService,
                                 CheckService checkService) {
        this.historyService = historyService;
        this.exportService = exportService;
        this.checkService = checkService;
    }

    @GetMapping
    public ApiResponse<List<CheckRecordSummaryDTO>> list(@RequestParam(required = false) Long taskId,
                                                         @RequestParam(required = false) String q) {
        return ApiResponse.ok(historyService.search(taskId, q));
    }

    @GetMapping("/{id}")
    public ApiResponse<CheckResultDTO> detail(@PathVariable Long id) {
        return ApiResponse.ok(historyService.detail(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<CheckRecordSummaryDTO> patch(@PathVariable Long id,
                                                    @RequestBody Map<String, String> body) {
        return ApiResponse.ok(historyService.patch(id, body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        historyService.delete(id);
        return ApiResponse.ok(null);
    }

    /** 未参与名单一键转收集任务（补交收集）。 */
    @PostMapping("/{id}/convert-to-collection")
    public ApiResponse<Map<String, Object>> convertToCollection(@PathVariable Long id) {
        return ApiResponse.ok(checkService.convertRecordToCollection(id));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id,
                                         @RequestParam(defaultValue = "csv") String format) {
        return exportService.exportRecord(id, format.toLowerCase());
    }
}
