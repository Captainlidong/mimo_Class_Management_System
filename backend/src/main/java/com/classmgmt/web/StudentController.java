package com.classmgmt.web;

import com.classmgmt.dto.StudentListDTO;
import com.classmgmt.dto.StudentsReplaceRequest;
import com.classmgmt.service.ImageOcrService;
import com.classmgmt.service.StudentImportService;
import com.classmgmt.service.StudentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentService studentService;
    private final StudentImportService studentImportService;
    private final ImageOcrService imageOcrService;

    public StudentController(StudentService studentService,
                             StudentImportService studentImportService,
                             ImageOcrService imageOcrService) {
        this.studentService = studentService;
        this.studentImportService = studentImportService;
        this.imageOcrService = imageOcrService;
    }

    @GetMapping
    public ApiResponse<StudentListDTO> list() {
        return ApiResponse.ok(studentService.list());
    }

    @PutMapping
    public ApiResponse<StudentListDTO> replace(@RequestBody StudentsReplaceRequest request) {
        return ApiResponse.ok(studentService.replace(request));
    }

    @PostMapping("/lock")
    public ApiResponse<StudentListDTO> lock() {
        studentService.setLocked(true);
        return ApiResponse.ok(studentService.list());
    }

    @PostMapping("/unlock")
    public ApiResponse<StudentListDTO> unlock() {
        studentService.setLocked(false);
        return ApiResponse.ok(studentService.list());
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        StudentListDTO list = studentService.list();
        return ApiResponse.ok(Map.of("locked", list.isLocked(), "count", list.getCount()));
    }

    /**
     * 解析上传的名单文件（txt/csv/xlsx），返回预览，不直接落库。
     */
    @PostMapping("/parse-import")
    public ApiResponse<Map<String, Object>> parseImport(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(toParseData(studentImportService.parse(file)));
    }

    /**
     * 图片 OCR 导入：识别花名册/表格截图 → 预览名单（不直接落库）。
     */
    @PostMapping("/parse-image")
    public ApiResponse<Map<String, Object>> parseImage(@RequestParam("file") MultipartFile file) {
        String text = imageOcrService.ocrToText(file);
        StudentImportService.ParseResult result = studentImportService.parseOcrText(text);
        Map<String, Object> data = toParseData(result);
        data.put("ocrText", text);
        return ApiResponse.ok(data);
    }

    private Map<String, Object> toParseData(StudentImportService.ParseResult result) {
        List<Map<String, Object>> rows = result.rows().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", r.name());
            m.put("studentNo", r.studentNo());
            return m;
        }).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("rows", rows);
        data.put("count", rows.size());
        data.put("rawLines", result.rawLines());
        data.put("skipped", result.skipped());
        data.put("samples", result.samples());
        return data;
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        String content = """
                # 班级名单导入模板（UTF-8 文本 / CSV / Excel 均可）
                # 规则：一行一人；或「学号,姓名」/「姓名,学号」；可含表头「学号,姓名」
                # 学号可选；系统会自动忽略空行、序号前缀与表头

                202401,张三
                202402,李四
                202403,王五
                """;
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''%E7%8F%AD%E7%BA%A7%E5%90%8D%E5%8D%95%E6%A8%A1%E6%9D%BF.txt")
                .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
                .body(body);
    }
}
