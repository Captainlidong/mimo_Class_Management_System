package com.classmgmt.service;

import com.classmgmt.config.AppProperties;
import com.classmgmt.domain.Announcement;
import com.classmgmt.domain.AnnouncementFile;
import com.classmgmt.domain.AnnouncementVersion;
import com.classmgmt.repo.AnnouncementFileRepository;
import com.classmgmt.repo.AnnouncementRepository;
import com.classmgmt.repo.AnnouncementVersionRepository;
import com.classmgmt.web.GlobalExceptionHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AnnouncementService {

    private static final Set<String> CATEGORIES =
            Set.of(Announcement.CATEGORY_NOTICE, Announcement.CATEGORY_FILE,
                    Announcement.CATEGORY_COPY, Announcement.CATEGORY_OTHER);

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementVersionRepository versionRepository;
    private final AnnouncementFileRepository fileRepository;
    private final AppProperties appProperties;

    public AnnouncementService(AnnouncementRepository announcementRepository,
                               AnnouncementVersionRepository versionRepository,
                               AnnouncementFileRepository fileRepository,
                               AppProperties appProperties) {
        this.announcementRepository = announcementRepository;
        this.versionRepository = versionRepository;
        this.fileRepository = fileRepository;
        this.appProperties = appProperties;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String q, String category, String status) {
        String keyword = q == null ? "" : q.trim().toLowerCase();
        String cat = trimToNull(category);
        String st = trimToNull(status);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Announcement a : announcementRepository.findAll()) {
            if (cat != null && !cat.equals(a.getCategory())) {
                continue;
            }
            if (st != null && !st.equals(a.getStatus())) {
                continue;
            }
            if (!keyword.isEmpty() && !matchesKeyword(a, keyword)) {
                continue;
            }
            result.add(toSummary(a));
        }
        result.sort(Comparator
                .comparing((Map<String, Object> m) -> Boolean.TRUE.equals(m.get("pinned"))).reversed()
                .thenComparing(m -> (Instant) m.get("updatedAt"), Comparator.reverseOrder()));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        Announcement a = find(id);
        Map<String, Object> item = toSummary(a);
        item.put("originalText", a.getOriginalText());
        item.put("editedText", a.getEditedText());
        List<Map<String, Object>> versions = new ArrayList<>();
        for (AnnouncementVersion v : versionRepository.findByAnnouncementIdOrderBySavedAtDescIdDesc(id)) {
            Map<String, Object> vm = new LinkedHashMap<>();
            vm.put("id", v.getId());
            vm.put("content", v.getContent());
            vm.put("savedAt", v.getSavedAt());
            versions.add(vm);
        }
        item.put("versions", versions);
        item.put("files", toFileList(id));
        return item;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        Announcement a = new Announcement();
        applyFields(a, body, true);
        announcementRepository.save(a);
        return detail(a.getId());
    }

    @Transactional
    public Map<String, Object> update(Long id, Map<String, Object> body) {
        Announcement a = find(id);
        applyFields(a, body, false);
        announcementRepository.save(a);
        return detail(id);
    }

    /** 修改稿变化时，旧稿自动存入版本历史。 */
    private void applyFields(Announcement a, Map<String, Object> body, boolean creating) {
        if (body.containsKey("title") || creating) {
            String title = textOrNull(body.get("title"));
            if (title == null || title.isEmpty()) {
                throw GlobalExceptionHandler.badRequest("标题不能为空");
            }
            a.setTitle(clip(title, 128));
        }
        if (body.containsKey("category") || creating) {
            String category = textOrNull(body.get("category"));
            a.setCategory(category == null ? Announcement.CATEGORY_OTHER
                    : (CATEGORIES.contains(category) ? category : Announcement.CATEGORY_OTHER));
        }
        if (body.containsKey("source") || creating) {
            a.setSource(clip(textOrNull(body.get("source")), 64));
        }
        if (body.containsKey("originalText") || creating) {
            String original = body.get("originalText") == null ? null : String.valueOf(body.get("originalText"));
            a.setOriginalText(original == null || original.isBlank() ? null : original);
        }
        if (body.containsKey("editedText") || creating) {
            String next = body.get("editedText") == null ? null : String.valueOf(body.get("editedText"));
            String nextClean = next == null || next.isBlank() ? null : next;
            String current = a.getEditedText();
            boolean changed = creating
                    ? nextClean != null
                    : (nextClean == null ? current != null : !nextClean.equals(current));
            if (changed && !creating && current != null && !current.isBlank()) {
                AnnouncementVersion v = new AnnouncementVersion();
                v.setAnnouncementId(a.getId());
                v.setContent(current);
                v.setSavedAt(Instant.now());
                versionRepository.save(v);
            }
            a.setEditedText(nextClean);
        }
        if (body.containsKey("remark") || creating) {
            a.setRemark(clip(textOrNull(body.get("remark")), 512));
        }
    }

    @Transactional
    public Map<String, Object> markSent(Long id) {
        Announcement a = find(id);
        a.setStatus(Announcement.STATUS_SENT);
        if (a.getSentAt() == null) {
            a.setSentAt(Instant.now());
        }
        announcementRepository.save(a);
        return detail(id);
    }

    @Transactional
    public Map<String, Object> unmarkSent(Long id) {
        Announcement a = find(id);
        a.setStatus(Announcement.STATUS_DRAFT);
        a.setSentAt(null);
        announcementRepository.save(a);
        return detail(id);
    }

    @Transactional
    public Map<String, Object> setPinned(Long id, boolean pinned) {
        Announcement a = find(id);
        a.setPinned(pinned);
        announcementRepository.save(a);
        return detail(id);
    }

    @Transactional
    public Map<String, Object> restoreVersion(Long id, Long versionId) {
        Announcement a = find(id);
        AnnouncementVersion v = versionRepository.findById(versionId)
                .orElseThrow(() -> GlobalExceptionHandler.notFound("历史版本不存在"));
        if (!id.equals(v.getAnnouncementId())) {
            throw GlobalExceptionHandler.badRequest("历史版本与素材不匹配");
        }
        String current = a.getEditedText();
        if (current != null && !current.isBlank()) {
            AnnouncementVersion archive = new AnnouncementVersion();
            archive.setAnnouncementId(id);
            archive.setContent(current);
            archive.setSavedAt(Instant.now());
            versionRepository.save(archive);
        }
        a.setEditedText(v.getContent());
        announcementRepository.save(a);
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        Announcement a = find(id);
        versionRepository.deleteByAnnouncementId(id);
        List<AnnouncementFile> files = fileRepository.findByAnnouncementIdOrderByUploadedAtAscIdAsc(id);
        for (AnnouncementFile f : files) {
            deleteQuietly(storageDir().resolve(f.getStoredName()));
        }
        fileRepository.deleteByAnnouncementId(id);
        announcementRepository.delete(a);
    }

    @Transactional
    public Map<String, Object> addFiles(Long id, List<MultipartFile> uploads) {
        Announcement a = find(id);
        if (uploads == null || uploads.isEmpty()) {
            throw GlobalExceptionHandler.badRequest("请选择要上传的文件");
        }
        Path dir = storageDir();
        for (MultipartFile upload : uploads) {
            if (upload == null || upload.isEmpty()) {
                continue;
            }
            String original = sanitizeFileName(upload.getOriginalFilename());
            AnnouncementFile f = new AnnouncementFile();
            f.setAnnouncementId(id);
            f.setFileName(original);
            f.setStoredName(UUID.randomUUID().toString().replace("-", "") + extensionOf(original));
            f.setFileSize(upload.getSize());
            f.setUploadedAt(Instant.now());
            try {
                Files.createDirectories(dir);
                Path target = dir.resolve(f.getStoredName());
                try (var in = upload.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new IllegalStateException("附件保存失败：" + e.getMessage(), e);
            }
            fileRepository.save(f);
        }
        announcementRepository.save(a);
        return detail(id);
    }

    @Transactional(readOnly = true)
    public AnnouncementFile getFile(Long id, Long fileId) {
        AnnouncementFile f = fileRepository.findById(fileId)
                .orElseThrow(() -> GlobalExceptionHandler.notFound("附件不存在"));
        if (!id.equals(f.getAnnouncementId())) {
            throw GlobalExceptionHandler.badRequest("附件与素材不匹配");
        }
        return f;
    }

    public Path resolveStoredFile(AnnouncementFile f) {
        return storageDir().resolve(f.getStoredName());
    }

    @Transactional
    public void deleteFile(Long id, Long fileId) {
        AnnouncementFile f = getFile(id, fileId);
        fileRepository.delete(f);
        deleteQuietly(storageDir().resolve(f.getStoredName()));
    }

    private boolean matchesKeyword(Announcement a, String keyword) {
        return contains(a.getTitle(), keyword) || contains(a.getSource(), keyword)
                || contains(a.getRemark(), keyword) || contains(a.getOriginalText(), keyword)
                || contains(a.getEditedText(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private Map<String, Object> toSummary(Announcement a) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", a.getId());
        item.put("title", a.getTitle());
        item.put("category", a.getCategory());
        item.put("source", a.getSource());
        item.put("status", a.getStatus());
        item.put("sentAt", a.getSentAt());
        item.put("pinned", Boolean.TRUE.equals(a.getPinned()));
        item.put("remark", a.getRemark());
        item.put("hasOriginal", a.getOriginalText() != null && !a.getOriginalText().isBlank());
        item.put("hasEdited", a.getEditedText() != null && !a.getEditedText().isBlank());
        item.put("attachmentCount", fileRepository.findByAnnouncementIdOrderByUploadedAtAscIdAsc(a.getId()).size());
        item.put("versionCount", versionRepository.findByAnnouncementIdOrderBySavedAtDescIdDesc(a.getId()).size());
        item.put("createdAt", a.getCreatedAt());
        item.put("updatedAt", a.getUpdatedAt());
        return item;
    }

    private List<Map<String, Object>> toFileList(Long id) {
        List<Map<String, Object>> files = new ArrayList<>();
        for (AnnouncementFile f : fileRepository.findByAnnouncementIdOrderByUploadedAtAscIdAsc(id)) {
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("id", f.getId());
            fm.put("fileName", f.getFileName());
            fm.put("fileSize", f.getFileSize());
            fm.put("uploadedAt", f.getUploadedAt());
            files.add(fm);
        }
        return files;
    }

    private Announcement find(Long id) {
        return announcementRepository.findById(id)
                .orElseThrow(() -> GlobalExceptionHandler.notFound("素材不存在"));
    }

    private Path storageDir() {
        String configured = appProperties.getUploadDir();
        return configured == null || configured.isBlank()
                ? Paths.get(System.getProperty("user.dir"), "uploads", "announcements")
                : Paths.get(configured);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 磁盘文件删除失败不阻断业务，行记录已清理
        }
    }

    private String textOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String v = String.valueOf(value).trim();
        return v.isEmpty() ? null : v;
    }

    private String clip(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "未命名附件";
        }
        String base = name.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replaceAll("[\\r\\n\\t]", "_").trim();
        if (base.isEmpty() || ".".equals(base) || "..".equals(base)) {
            return "未命名附件";
        }
        return base.length() > 255 ? base.substring(base.length() - 255) : base;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        String ext = fileName.substring(dot + 1).toLowerCase();
        if (ext.length() > 10 || !ext.matches("[a-z0-9]+")) {
            return "";
        }
        return "." + ext;
    }
}
