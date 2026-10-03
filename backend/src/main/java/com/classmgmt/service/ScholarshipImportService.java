package com.classmgmt.service;

import com.classmgmt.domain.Student;
import com.classmgmt.repo.ScholarshipAwardRepository;
import com.classmgmt.repo.StudentRepository;
import com.classmgmt.web.GlobalExceptionHandler;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全院奖学金名单 Excel 识别：定位表头行 → 按表头文字映射列（姓名/学号/奖学金/金额）
 * → 逐行与基准名单匹配（学号精确优先，姓名兜底），产出预览数据。
 */
@Service
public class ScholarshipImportService {

    /** 表头关键词，按优先级排列（先匹配到的高优先级词生效）。 */
    private static final String[] AWARD_KEYS = {
            "奖学金名称", "奖学金等级", "奖学金项目", "获奖名称", "获奖情况", "资助项目",
            "项目名称", "奖学金", "奖项", "等级", "类别"
    };
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final int HEADER_SCAN_LIMIT = 30;

    private final StudentRepository studentRepository;
    private final ScholarshipAwardRepository scholarshipAwardRepository;

    public ScholarshipImportService(StudentRepository studentRepository,
                                    ScholarshipAwardRepository scholarshipAwardRepository) {
        this.studentRepository = studentRepository;
        this.scholarshipAwardRepository = scholarshipAwardRepository;
    }

    /**
     * 解析全院名单 Excel。manual 参数为人工指定的列映射（0 基），用于表头识别失败后的兜底。
     * 返回 {recognized, matched[], ambiguous[], unmatchedCount, totalRows, columns, samples[]}。
     */
    public Map<String, Object> parse(Long batchId, MultipartFile file,
                                     Integer manualHeaderRow, Integer manualNameCol,
                                     Integer manualNoCol, Integer manualLevelCol, Integer manualAmountCol) {
        List<List<String>> grid = readGrid(file);
        if (grid.isEmpty()) {
            throw GlobalExceptionHandler.badRequest("Excel 内容为空或无法读取");
        }

        ColumnMapping mapping;
        if (manualNameCol != null) {
            // 手动模式：headerRow 允许 -1（无表头文件，首行即数据）
            int headerRow = manualHeaderRow == null ? 0 : manualHeaderRow;
            mapping = new ColumnMapping(headerRow, manualNameCol, manualNoCol, manualLevelCol, manualAmountCol);
        } else {
            mapping = detectHeader(grid);
        }
        if (mapping.nameCol < 0) {
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("recognized", false);
            fail.put("totalRows", grid.size());
            fail.put("samples", previewGrid(grid));
            fail.put("message", "未能自动识别表头（需要至少包含\"姓名\"列），请根据下方预览手动指定各列位置");
            return fail;
        }

        List<Map<String, Object>> matched = new ArrayList<>();
        List<Map<String, Object>> ambiguous = new ArrayList<>();
        int unmatchedCount = 0;
        int noAwardCount = 0;
        int totalRows = 0;

        Map<String, Student> noMap = new HashMap<>();
        Map<String, List<Student>> nameMap = new LinkedHashMap<>();
        for (Student s : studentRepository.findAll()) {
            String no = cleanStudentNo(s.getStudentNo());
            if (no != null) {
                noMap.put(no, s);
            }
            nameMap.computeIfAbsent(NameNormalizer.normalize(s.getName()), k -> new ArrayList<>()).add(s);
        }
        var existing = batchId == null ? List.<Long>of()
                : scholarshipAwardRepository.findByBatchIdOrderByCreatedAtAscIdAsc(batchId).stream()
                        .map(a -> a.getStudentId()).collect(java.util.stream.Collectors.toSet());

        for (int r = mapping.headerRow + 1; r < grid.size(); r++) {
            List<String> row = grid.get(r);
            String name = NameNormalizer.normalize(cellAt(row, mapping.nameCol));
            if (NameNormalizer.isBlankOrSymbol(name)) {
                continue;
            }
            totalRows++;
            String studentNo = cleanStudentNo(mapping.noCol >= 0 ? cellAt(row, mapping.noCol) : null);
            String rawAward = mapping.levelCol >= 0 ? cellAt(row, mapping.levelCol) : null;
            // 等级列为空 = 该生未获奖（全院名单里全班同学都在列），直接跳过
            if (mapping.levelCol >= 0 && isNoAwardMarker(rawAward)) {
                noAwardCount++;
                continue;
            }
            String awardName = normalizeAwardName(rawAward);
            BigDecimal amount = mapping.amountCol >= 0 ? parseAmount(cellAt(row, mapping.amountCol)) : null;

            Student student = null;
            String matchedBy = null;
            if (studentNo != null && noMap.containsKey(studentNo)) {
                student = noMap.get(studentNo);
                matchedBy = "学号";
            } else {
                List<Student> sameName = nameMap.get(name);
                if (sameName != null && sameName.size() == 1) {
                    student = sameName.get(0);
                    matchedBy = "姓名";
                } else if (sameName != null && sameName.size() > 1) {
                    Map<String, Object> amb = baseRow(name, studentNo, awardName, amount);
                    amb.put("status", "ambiguous");
                    amb.put("reason", "班内有 " + sameName.size() + " 位同名同学：" +
                            sameName.stream().map(Student::getName).distinct().collect(java.util.stream.Collectors.joining("、"))
                            + "，请手动添加");
                    ambiguous.add(amb);
                    continue;
                }
            }

            if (student == null) {
                unmatchedCount++;
                if (unmatchedCount <= 20) {
                    Map<String, Object> row0 = baseRow(name, studentNo, awardName, amount);
                    row0.put("status", "unmatched");
                    row0.put("reason", studentNo == null ? "未匹配到本班同学（按姓名）" : "学号与姓名均未匹配到本班同学");
                    ambiguous.add(row0);
                }
                continue;
            }

            Map<String, Object> item = baseRow(name, studentNo, awardName, amount);
            item.put("status", "matched");
            item.put("matchedBy", matchedBy);
            item.put("studentId", student.getId());
            item.put("studentName", student.getName());
            item.put("studentStudentNo", student.getStudentNo());
            item.put("duplicate", existing.contains(student.getId()));
            matched.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recognized", true);
        result.put("headerRow", mapping.headerRow);
        result.put("columns", mapping.toMap());
        result.put("totalRows", totalRows);
        result.put("matched", matched);
        result.put("others", ambiguous);
        result.put("unmatchedCount", unmatchedCount);
        result.put("noAwardCount", noAwardCount);
        return result;
    }

    /** 人工指定列映射后重新解析（表头识别失败兜底）。 */
    private static String cellAt(List<String> row, int idx) {
        if (idx < 0 || idx >= row.size()) {
            return "";
        }
        return row.get(idx);
    }

    private ColumnMapping detectHeader(List<List<String>> grid) {
        int scanLimit = Math.min(grid.size(), HEADER_SCAN_LIMIT);
        for (int r = 0; r < scanLimit; r++) {
            List<String> row = grid.get(r);
            int nameCol = -1;
            int noCol = -1;
            int levelCol = -1;
            int amountCol = -1;
            for (int c = 0; c < row.size(); c++) {
                String head = normalizeHeader(row.get(c));
                if (head.isEmpty()) {
                    continue;
                }
                if (nameCol < 0 && head.contains("姓名")) {
                    nameCol = c;
                } else if (noCol < 0 && (head.contains("学号") || head.replace(" ", "").contains("studentno"))) {
                    noCol = c;
                } else if (levelCol < 0 && matchesAny(head, AWARD_KEYS)) {
                    levelCol = c;
                } else if (amountCol < 0 && (head.contains("金额") || head.contains("标准") || head.contains("额度"))) {
                    amountCol = c;
                }
            }
            if (nameCol >= 0 && (noCol >= 0 || levelCol >= 0)) {
                return new ColumnMapping(r, nameCol, noCol, levelCol, amountCol);
            }
        }
        return new ColumnMapping(-1, -1, -1, -1, -1);
    }

    private boolean matchesAny(String head, String[] keys) {
        for (String key : keys) {
            if (head.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeHeader(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        StringBuilder sb = new StringBuilder();
        for (char ch : v.toCharArray()) {
            if (ch >= 0xFF01 && ch <= 0xFF5E) {
                sb.append((char) (ch - 0xFEE0));
            } else if (ch == 0x3000) {
                sb.append(' ');
            } else {
                sb.append(ch);
            }
        }
        return sb.toString().replace("（", "(").replace("）", ")");
    }

    private List<List<String>> readGrid(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            List<List<String>> grid = new ArrayList<>();
            for (int r = sheet.getFirstRowNum(); r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                List<String> cells = new ArrayList<>();
                if (row == null) {
                    grid.add(cells);
                    continue;
                }
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String v;
                    try {
                        v = formatter.formatCellValue(row.getCell(c)).trim();
                    } catch (Exception e) {
                        v = row.getCell(c) == null ? "" : String.valueOf(row.getCell(c)).trim();
                    }
                    cells.add(v);
                }
                grid.add(cells);
            }
            // 去掉末尾连续空行
            while (!grid.isEmpty() && grid.get(grid.size() - 1).isEmpty()) {
                grid.remove(grid.size() - 1);
            }
            return grid;
        } catch (IOException e) {
            throw GlobalExceptionHandler.badRequest("文件读取失败：" + e.getMessage());
        } catch (Exception e) {
            throw GlobalExceptionHandler.badRequest("Excel 解析失败，请确认上传的是 .xlsx / .xls 文件");
        }
    }

    private List<List<String>> previewGrid(List<List<String>> grid) {
        return new ArrayList<>(grid.subList(0, Math.min(grid.size(), 8)));
    }

    private Map<String, Object> baseRow(String name, String studentNo, String awardName, BigDecimal amount) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("studentNo", studentNo);
        item.put("awardName", awardName);
        item.put("amount", amount);
        return item;
    }

    public String cleanStudentNo(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (char ch : raw.trim().toCharArray()) {
            if (ch == ' ' || ch == 0x3000 || ch == '\t' || ch == '-' || ch == '—' || ch == '－') {
                continue;
            }
            if (ch >= 0xFF01 && ch <= 0xFF5E) {
                ch = (char) (ch - 0xFEE0);
            }
            sb.append(Character.toUpperCase(ch));
        }
        String v = sb.toString();
        return v.isEmpty() ? null : v;
    }

    private String normalizeAwardName(String raw) {
        String v = NameNormalizer.normalize(raw);
        return v.isEmpty() ? "未注明" : v;
    }

    /** 等级列出现这些内容视为"未获奖"（空、横杠、"无"）。 */
    private boolean isNoAwardMarker(String raw) {
        if (raw == null) {
            return true;
        }
        String v = raw.trim();
        return v.isEmpty() || "-".equals(v) || "—".equals(v) || "－".equals(v) || "无".equals(v);
    }

    public BigDecimal parseAmount(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = AMOUNT_PATTERN.matcher(raw.replace(",", ""));
        if (m.find()) {
            try {
                return new BigDecimal(m.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 列映射结果。 */
    private static class ColumnMapping {
        final int headerRow;
        final int nameCol;
        final int noCol;
        final int levelCol;
        final int amountCol;

        ColumnMapping(int headerRow, int nameCol, int noCol, int levelCol, int amountCol) {
            this.headerRow = headerRow;
            this.nameCol = nameCol;
            this.noCol = noCol;
            this.levelCol = levelCol;
            this.amountCol = amountCol;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("headerRow", headerRow);
            m.put("nameCol", nameCol);
            m.put("studentNoCol", noCol);
            m.put("levelCol", levelCol);
            m.put("amountCol", amountCol);
            return m;
        }
    }
}
