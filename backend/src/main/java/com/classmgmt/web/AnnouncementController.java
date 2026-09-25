package com.classmgmt.web;

import com.classmgmt.domain.AnnouncementFile;
import com.classmgmt.service.AnnouncementService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String q,
                                                       @RequestParam(required = false) String category,
                                                       @RequestParam(required = false) String status) {
        return ApiResponse.ok(announcementService.list(q, category, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(announcementService.detail(id));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(announcementService.create(body));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id,
                                                   @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(announcementService.update(id, body));
    }

    @PostMapping("/{id}/mark-sent")
    public ApiResponse<Map<String, Object>> markSent(@PathVariable Long id) {
        return ApiResponse.ok(announcementService.markSent(id));
    }

    @PostMapping("/{id}/unmark-sent")
    public ApiResponse<Map<String, Object>> unmarkSent(@PathVariable Long id) {
        return ApiResponse.ok(announcementService.unmarkSent(id));
    }

    @PatchMapping("/{id}/pin")
    public ApiResponse<Map<String, Object>> setPinned(@PathVariable Long id,
                                                      @RequestBody Map<String, Object> body) {
        boolean pinned = Boolean.parseBoolean(String.valueOf(body.get("pinned")));
        return ApiResponse.ok(announcementService.setPinned(id, pinned));
    }

    @PostMapping("/{id}/versions/{versionId}/restore")
    public ApiResponse<Map<String, Object>> restoreVersion(@PathVariable Long id,
                                                           @PathVariable Long versionId) {
        return ApiResponse.ok(announcementService.restoreVersion(id, versionId));
    }

    @PostMapping("/{id}/files")
    public ApiResponse<Map<String, Object>> uploadFiles(@PathVariable Long id,
                                                        @RequestParam("files") List<MultipartFile> files) {
        return ApiResponse.ok(announcementService.addFiles(id, files));
    }

    @GetMapping("/{id}/files/{fileId}")
    public ResponseEntity<ByteArrayResource> downloadFile(@PathVariable Long id, @PathVariable Long fileId) {
        AnnouncementFile f = announcementService.getFile(id, fileId);
        Path path = announcementService.resolveStoredFile(f);
        if (!Files.exists(path)) {
            throw GlobalExceptionHandler.notFound("附件文件已丢失：" + f.getFileName());
        }
        try {
            byte[] body = Files.readAllBytes(path);
            String encoded = URLEncoder.encode(f.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new ByteArrayResource(body));
        } catch (Exception e) {
            throw new IllegalStateException("附件读取失败：" + e.getMessage(), e);
        }
    }

    @DeleteMapping("/{id}/files/{fileId}")
    public ApiResponse<Map<String, Object>> deleteFile(@PathVariable Long id, @PathVariable Long fileId) {
        announcementService.deleteFile(id, fileId);
        return ApiResponse.ok(announcementService.detail(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return ApiResponse.ok(null);
    }
}
