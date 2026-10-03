package com.classmgmt.service;

import com.classmgmt.dto.CheckResultDTO;
import com.classmgmt.web.GlobalExceptionHandler;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class ExportService {

    private final HistoryService historyService;
    private final LeaveService leaveService;
    private final ScholarshipService scholarshipService;

    public ExportService(HistoryService historyService, LeaveService leaveService,
                         ScholarshipService scholarshipService) {
        this.historyService = historyService;
        this.leaveService = leaveService;
        this.scholarshipService = scholarshipService;
    }

    public ResponseEntity<byte[]> exportRecord(Long id, String format) {
        CheckResultDTO dto = historyService.detail(id);
        String base = sanitizeFileName(dto.getTitle() == null ? "核对结果" : dto.getTitle());
        return switch (format) {
            case "xlsx", "excel" -> xlsx(dto, base);
            case "txt" -> text(dto, base, "txt");
            case "csv" -> text(dto, base, "csv");
            default -> throw GlobalExceptionHandler.badRequest("不支持的导出格式：" + format);
        };
    }

    public ResponseEntity<byte[]> exportLeaves(String format) {
        List<Map<String, Object>> rows = leaveService.list(null, null, null, null, null);
        String base = "请假记录_" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        return switch (format) {
            case "xlsx", "excel" -> leavesXlsx(rows, base);
            case "csv" -> leavesCsv(rows, base);
            default -> throw GlobalExceptionHandler.badRequest("不支持的导出格式：" + format);
        };
    }

    private static final List<String> LEAVE_COLUMNS = List.of(
            "序号", "姓名", "学号", "类型", "开始时间", "结束时间",
            "状态", "返校时间", "批假人", "请假原因", "备注", "登记时间");

    private ResponseEntity<byte[]> leavesXlsx(List<Map<String, Object>> rows, String base) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("请假记录");
            CellStyle header = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            header.setFont(font);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row head = sheet.createRow(0);
            for (int i = 0; i < LEAVE_COLUMNS.size(); i++) {
                Cell c = head.createCell(i);
                c.setCellValue(LEAVE_COLUMNS.get(i));
                c.setCellStyle(header);
            }
            int r = 1;
            for (Map<String, Object> row : rows) {
                Row line = sheet.createRow(r++);
                List<String> cells = leaveCells(row);
                for (int i = 0; i < cells.size(); i++) {
                    line.createCell(i).setCellValue(cells.get(i));
                }
            }
            for (int i = 0; i < LEAVE_COLUMNS.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return file(base + ".xlsx",
                    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                    out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("导出 Excel 失败：" + e.getMessage(), e);
        }
    }

    private ResponseEntity<byte[]> leavesCsv(List<Map<String, Object>> rows, String base) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // BOM, 便于 Excel 直接打开中文 CSV
        sb.append(String.join(",", LEAVE_COLUMNS)).append("\n");
        for (Map<String, Object> row : rows) {
            List<String> cells = leaveCells(row);
            for (int i = 0; i < cells.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(csvEscape(cells.get(i)));
            }
            sb.append("\n");
        }
        return file(base + ".csv", MediaType.parseMediaType("text/csv;charset=UTF-8"),
                sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private List<String> leaveCells(Map<String, Object> row) {
        return List.of(
                String.valueOf(row.get("id")),
                String.valueOf(row.get("name")),
                row.get("studentNo") == null ? "" : String.valueOf(row.get("studentNo")),
                leaveService.typeLabel((String) row.get("leaveType")),
                leaveService.display((java.time.Instant) row.get("startTime")),
                leaveService.display((java.time.Instant) row.get("endTime")),
                leaveService.statusLabel((String) row.get("status")),
                leaveService.display((java.time.Instant) row.get("returnedAt")),
                row.get("approver") == null ? "" : String.valueOf(row.get("approver")),
                row.get("reason") == null ? "" : String.valueOf(row.get("reason")),
                row.get("remark") == null ? "" : String.valueOf(row.get("remark")),
                leaveService.display((java.time.Instant) row.get("createdAt")));
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static final List<String> SCHOLARSHIP_COLUMNS = List.of(
            "序号", "姓名", "学号", "奖学金名称", "金额（元）", "申请表状态", "交表时间", "备注");

    public ResponseEntity<byte[]> exportScholarships(Long batchId, String format) {
        Map<String, Object> batch = scholarshipService.getBatch(batchId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> awards = (List<Map<String, Object>>) batch.get("awards");
        String base = sanitizeFileName(String.valueOf(batch.get("name"))) + "_获奖名单";
        return switch (format) {
            case "xlsx", "excel" -> scholarshipXlsx(batch, awards, base);
            case "csv" -> scholarshipCsv(batch, awards, base);
            default -> throw GlobalExceptionHandler.badRequest("不支持的导出格式：" + format);
        };
    }

    private ResponseEntity<byte[]> scholarshipXlsx(Map<String, Object> batch,
                                                   List<Map<String, Object>> awards, String base) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("获奖名单");
            CellStyle header = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            header.setFont(font);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row meta = sheet.createRow(0);
            meta.createCell(0).setCellValue("批次");
            meta.createCell(1).setCellValue(String.valueOf(batch.get("name")));
            Row counts = sheet.createRow(1);
            counts.createCell(0).setCellValue("统计");
            counts.createCell(1).setCellValue("获奖 " + awards.size()
                    + " 人 / 已交表 " + batch.get("receivedCount") + " 人");

            Row head = sheet.createRow(3);
            for (int i = 0; i < SCHOLARSHIP_COLUMNS.size(); i++) {
                Cell c = head.createCell(i);
                c.setCellValue(SCHOLARSHIP_COLUMNS.get(i));
                c.setCellStyle(header);
            }
            int r = 4;
            for (Map<String, Object> row : awards) {
                Row line = sheet.createRow(r++);
                List<String> cells = scholarshipCells(row);
                for (int i = 0; i < cells.size(); i++) {
                    line.createCell(i).setCellValue(cells.get(i));
                }
            }
            for (int i = 0; i < SCHOLARSHIP_COLUMNS.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return file(base + ".xlsx",
                    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                    out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("导出 Excel 失败：" + e.getMessage(), e);
        }
    }

    private ResponseEntity<byte[]> scholarshipCsv(Map<String, Object> batch,
                                                  List<Map<String, Object>> awards, String base) {
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("批次,").append(csvEscape(String.valueOf(batch.get("name")))).append("\n");
        sb.append(String.join(",", SCHOLARSHIP_COLUMNS)).append("\n");
        for (Map<String, Object> row : awards) {
            List<String> cells = scholarshipCells(row);
            for (int i = 0; i < cells.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(csvEscape(cells.get(i)));
            }
            sb.append("\n");
        }
        return file(base + ".csv", MediaType.parseMediaType("text/csv;charset=UTF-8"),
                sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private List<String> scholarshipCells(Map<String, Object> row) {
        boolean received = Boolean.TRUE.equals(row.get("formReceived"));
        return List.of(
                String.valueOf(row.get("id")),
                String.valueOf(row.get("name")),
                row.get("studentNo") == null ? "" : String.valueOf(row.get("studentNo")),
                row.get("awardName") == null ? "未注明" : String.valueOf(row.get("awardName")),
                row.get("amount") == null ? "" : String.valueOf(row.get("amount")),
                received ? "已交表" : "未交表",
                leaveService.display((java.time.Instant) row.get("receivedAt")),
                row.get("remark") == null ? "" : String.valueOf(row.get("remark")));
    }

    private ResponseEntity<byte[]> xlsx(CheckResultDTO dto, String base) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("核对结果");
            CellStyle header = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            header.setFont(font);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            int r = 0;
            Row meta = sheet.createRow(r++);
            meta.createCell(0).setCellValue("任务名称");
            meta.createCell(1).setCellValue(dto.getTitle());
            Row time = sheet.createRow(r++);
            time.createCell(0).setCellValue("核对时间");
            time.createCell(1).setCellValue(dto.getCheckedAt());
            Row counts = sheet.createRow(r++);
            counts.createCell(0).setCellValue("统计");
            counts.createCell(1).setCellValue("已参与 " + dto.getParticipated().size()
                    + " / 未参与 " + dto.getAbsent().size()
                    + " / 无效 " + dto.getInvalid().size()
                    + " / 去重 " + dto.getDuplicates().size()
                    + " / 基准 " + dto.getCounts().getOrDefault("baseline", 0));
            r++;
            r = writeColumn(sheet, r, "已参与", dto.getParticipated(), header);
            r = writeColumn(sheet, r, "未参与", dto.getAbsent(), header);
            r = writeColumn(sheet, r, "无效/异常", dto.getInvalid(), header);
            r = writeColumn(sheet, r, "重复姓名", dto.getDuplicates(), header);

            for (int i = 0; i < 4; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return file(base + ".xlsx",
                    MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                    out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("导出 Excel 失败：" + e.getMessage(), e);
        }
    }

    private int writeColumn(Sheet sheet, int startRow, String title, List<String> names, CellStyle header) {
        Row titleRow = sheet.createRow(startRow++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(title);
        titleCell.setCellStyle(header);
        if (names == null || names.isEmpty()) {
            sheet.createRow(startRow++).createCell(0).setCellValue("（无）");
        } else {
            for (String name : names) {
                sheet.createRow(startRow++).createCell(0).setCellValue(name);
            }
        }
        return startRow + 1;
    }

    private ResponseEntity<byte[]> text(CheckResultDTO dto, String base, String ext) {
        StringBuilder sb = new StringBuilder();
        sb.append("任务名称,").append(dto.getTitle()).append("\n");
        sb.append("核对时间,").append(dto.getCheckedAt()).append("\n");
        sb.append("已参与人数,").append(dto.getParticipated().size()).append("\n");
        sb.append("未参与人数,").append(dto.getAbsent().size()).append("\n");
        sb.append("无效条数,").append(dto.getInvalid().size()).append("\n");
        sb.append("重复人数,").append(dto.getDuplicates().size()).append("\n\n");
        appendSection(sb, "已参与", dto.getParticipated(), "csv".equals(ext));
        appendSection(sb, "未参与", dto.getAbsent(), "csv".equals(ext));
        appendSection(sb, "无效/异常", dto.getInvalid(), "csv".equals(ext));
        appendSection(sb, "重复姓名", dto.getDuplicates(), "csv".equals(ext));
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        MediaType media = "csv".equals(ext)
                ? MediaType.parseMediaType("text/csv;charset=UTF-8")
                : MediaType.parseMediaType("text/plain;charset=UTF-8");
        return file(base + "." + ext, media, bytes);
    }

    private void appendSection(StringBuilder sb, String title, List<String> names, boolean csv) {
        sb.append(title).append("\n");
        if (names == null || names.isEmpty()) {
            sb.append("（无）\n");
        } else {
            for (String name : names) {
                sb.append(name).append("\n");
            }
        }
        sb.append("\n");
    }

    private ResponseEntity<byte[]> file(String filename, MediaType mediaType, byte[] body) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(mediaType)
                .body(body);
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
    }
}
