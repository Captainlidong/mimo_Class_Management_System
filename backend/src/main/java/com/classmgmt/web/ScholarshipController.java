package com.classmgmt.web;

import com.classmgmt.service.ExportService;
import com.classmgmt.service.ScholarshipImportService;
import com.classmgmt.service.ScholarshipService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scholarships")
public class ScholarshipController {

    private final ScholarshipService scholarshipService;
    private final ScholarshipImportService scholarshipImportService;
    private final ExportService exportService;

    public ScholarshipController(ScholarshipService scholarshipService,
                                 ScholarshipImportService scholarshipImportService,
                                 ExportService exportService) {
        this.scholarshipService = scholarshipService;
        this.scholarshipImportService = scholarshipImportService;
        this.exportService = exportService;
    }

    // —— 批次 ——

    @GetMapping("/batches")
    public ApiResponse<List<Map<String, Object>>> batches() {
        return ApiResponse.ok(scholarshipService.listBatches());
    }

    @PostMapping("/batches")
    public ApiResponse<Map<String, Object>> createBatch(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(scholarshipService.createBatch(body));
    }

    @PatchMapping("/batches/{id}")
    public ApiResponse<Map<String, Object>> updateBatch(@PathVariable Long id,
                                                        @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(scholarshipService.updateBatch(id, body));
    }

    @DeleteMapping("/batches/{id}")
    public ApiResponse<Void> deleteBatch(@PathVariable Long id) {
        scholarshipService.deleteBatch(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/batches/{id}")
    public ApiResponse<Map<String, Object>> batchDetail(@PathVariable Long id) {
        return ApiResponse.ok(scholarshipService.getBatch(id));
    }

    // —— 获奖记录 ——

    @PostMapping("/batches/{id}/awards")
    public ApiResponse<List<Map<String, Object>>> addAwards(@PathVariable Long id,
                                                            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(scholarshipService.addAward(id, body));
    }

    @PatchMapping("/awards/{id}")
    public ApiResponse<Map<String, Object>> updateAward(@PathVariable Long id,
                                                        @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(scholarshipService.updateAward(id, body));
    }

    @DeleteMapping("/awards/{id}")
    public ApiResponse<Void> deleteAward(@PathVariable Long id) {
        scholarshipService.deleteAward(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/awards/{id}/mark-received")
    public ApiResponse<Map<String, Object>> markReceived(@PathVariable Long id,
                                                         @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(scholarshipService.markReceived(id, body));
    }

    // —— 名单识别 ——

    @PostMapping(value = "/batches/{id}/import/parse", consumes = "multipart/form-data")
    public ApiResponse<Map<String, Object>> parseImport(@PathVariable Long id,
                                                        @RequestParam("file") MultipartFile file,
                                                        @RequestParam(required = false) Integer headerRow,
                                                        @RequestParam(required = false) Integer nameCol,
                                                        @RequestParam(required = false) Integer studentNoCol,
                                                        @RequestParam(required = false) Integer levelCol,
                                                        @RequestParam(required = false) Integer amountCol) {
        Map<String, Object> result = scholarshipImportService.parse(id, file,
                headerRow, nameCol, studentNoCol, levelCol, amountCol);
        result.put("fileName", file.getOriginalFilename());
        return ApiResponse.ok(result);
    }

    /** 确认导入：body = {rows: [{studentId, awardName, amount}], sourceFile} */
    @PostMapping("/batches/{id}/import/confirm")
    public ApiResponse<List<Map<String, Object>>> confirmImport(@PathVariable Long id,
                                                                @RequestBody Map<String, Object> body) {
        String sourceFile = body.get("sourceFile") == null ? null : String.valueOf(body.get("sourceFile"));
        return ApiResponse.ok(scholarshipService.addAward(id, body, sourceFile));
    }

    // —— 导出 ——

    @GetMapping("/batches/{id}/export")
    public ResponseEntity<byte[]> exportBatch(@PathVariable Long id,
                                              @RequestParam(defaultValue = "xlsx") String format) {
        return exportService.exportScholarships(id, format);
    }
}
