package com.classmgmt.service;

import com.classmgmt.web.GlobalExceptionHandler;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class StudentImportService {

    private static final Pattern SEQ = Pattern.compile("^\\d{1,3}[\\.．、\\)）:：\\-—\\s]+");
    private static final Pattern STUDENT_NO = Pattern.compile("^[A-Za-z0-9]{4,32}$");

    public record ImportedRow(String name, String studentNo) {
    }

    public record ParseResult(List<ImportedRow> rows, int rawLines, int skipped, List<String> samples) {
    }

    public ParseResult parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw GlobalExceptionHandler.badRequest("请选择要导入的文件");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            byte[] bytes = file.getBytes();
            if (original.endsWith(".xlsx") || original.endsWith(".xls")) {
                return parseExcel(new ByteArrayInputStream(bytes));
            }
            if (isImageName(original)) {
                throw GlobalExceptionHandler.badRequest("请使用「图片导入」上传截图（jpg/png）");
            }
            return parseText(bytes);
        } catch (GlobalExceptionHandler.ApiException e) {
            throw e;
        } catch (Exception e) {
            throw GlobalExceptionHandler.badRequest("文件解析失败：" + e.getMessage());
        }
    }

    public boolean isImageName(String lowerName) {
        return lowerName != null && (lowerName.endsWith(".png") || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg") || lowerName.endsWith(".bmp")
                || lowerName.endsWith(".webp") || lowerName.endsWith(".gif"));
    }

    /** OCR 识别后的花名册/表格文本 → 名单（支持「学号列 + 姓名列」分块输出）。 */
    public ParseResult parseOcrText(String text) {
        if (text == null || text.isBlank()) {
            throw GlobalExceptionHandler.badRequest("图片未能识别出文字，请换更清晰的截图");
        }
        // 去掉 BOM（U+FEFF）；OCR 结果与复制粘贴常把它当成零宽字符带入
        String normalized = text.replace('\uFEFF', ' ').replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = new ArrayList<>();
        for (String line : normalized.split("\n")) {
            lines.add(line);
        }

        // 路径 A：学号与姓名分行输出（Windows OCR 常见）→ 按序配对
        ParseResult paired = parseSeparatedColumns(lines);
        if (paired != null && !paired.rows().isEmpty()) {
            return paired;
        }

        // 路径 B：同行混合
        return parseOcrLines(lines);
    }

    /**
     * Windows OCR 往往先输出全部学号行，再输出姓名行（字间可有空格）。
     * 学号可能被误识为含 H/I/O 的字符串，需容错；有姓名无可用学号时仍返回姓名。
     */
    private ParseResult parseSeparatedColumns(List<String> lines) {
        List<String> nos = new ArrayList<>();
        List<String> names = new ArrayList<>();
        Pattern noLine = Pattern.compile("^[0-9A-Za-z]{8,14}$");
        Pattern nameLine = Pattern.compile("^[\\u4e00-\\u9fff\\s·\\-]{1,12}$");

        for (String raw : lines) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String line = raw.trim();
            if (isNoiseHeaderText(line)) {
                continue;
            }
            // 学号行：允许 OCR 混入字母
            if (noLine.matcher(line).matches()) {
                String fixed = normalizeStudentNo(line);
                if (!fixed.isEmpty()) {
                    nos.add(fixed);
                }
                continue;
            }
            String stripped = line.replaceAll("\\s+", "");
            if (!stripped.isEmpty() && nameLine.matcher(line).matches()
                    && stripped.chars().allMatch(c -> c >= 0x4E00 && c <= 0x9FFF)
                    && !isNoiseName(stripped)
                    && !looksLikeMajor(stripped)
                    && stripped.length() >= 1 && stripped.length() <= 6) {
                names.add(stripped);
            }
        }

        // 有姓名即可出结果；学号尽量配对
        if (names.isEmpty()) {
            return null;
        }
        int n = names.size();
        List<ImportedRow> rows = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<String> samples = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String name = names.get(i);
            if (isNoiseName(name) || looksLikeMajor(name)) {
                continue;
            }
            if (!seen.add(name)) {
                continue;
            }
            String no = (i < nos.size()) ? nos.get(i) : null;
            rows.add(new ImportedRow(name, no));
            if (samples.size() < 5) {
                samples.add(no == null ? name : (name + " / " + no));
            }
        }
        if (rows.isEmpty()) {
            return null;
        }
        int skipped = Math.max(0, lines.size() - rows.size());
        return new ParseResult(rows, lines.size(), skipped, samples);
    }

    /** OCR 学号容错：常见把 11 识成 H、1 识成 I/l，再抽数字。 */
    private String normalizeStudentNo(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        // Windows OCR 常把漏识的 "11" 读成 H：2H2616151 → 2112616151
        if (raw.indexOf('H') >= 0 || raw.indexOf('h') >= 0) {
            String expand = raw.replace("H", "11").replace("h", "11")
                    .replace('I', '1').replace('l', '1').replace('L', '1')
                    .replace('O', '0').replace('o', '0');
            String dExpand = expand.replaceAll("[^0-9]", "");
            if (dExpand.length() >= 9 && dExpand.length() <= 14) {
                return dExpand;
            }
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() >= 9 && digits.length() <= 14) {
            return digits;
        }
        String mapped = raw
                .replace('H', '1').replace('h', '1')
                .replace('I', '1').replace('l', '1').replace('L', '1').replace('|', '1')
                .replace('O', '0').replace('o', '0')
                .replace('S', '5').replace('B', '8').replace('G', '6');
        String d2 = mapped.replaceAll("[^0-9]", "");
        if (d2.length() >= 8 && d2.length() <= 14) {
            return d2;
        }
        if (digits.length() >= 8) {
            return digits;
        }
        return digits.isEmpty() ? raw.replaceAll("[^0-9A-Za-z]", "") : digits;
    }

    private boolean isNoiseHeaderText(String line) {
        return line.contains("学号") || line.contains("姓名") || line.contains("序号")
                || line.contains("专业") || line.contains("班级") || line.contains("性别");
    }

    private ParseResult parseOcrLines(List<String> lines) {
        List<ImportedRow> rows = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int skipped = 0;
        List<String> samples = new ArrayList<>();

        Pattern attached = Pattern.compile("(\\d{6,14})[\\s,，、]*([\\u4e00-\\u9fff]{2,4})");
        Pattern withGender = Pattern.compile(
                "(\\d{6,14})[\\s\\S]*?([\\u4e00-\\u9fff]{2,4})(?:[\\s\\S]*?([\\u4e00-\\u9fff]{2,6}))?\\s*[男女]");
        Pattern onlyName = Pattern.compile("^[\\u4e00-\\u9fff]{2,4}$");

        for (String raw : lines) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String line = NameNormalizer.normalize(raw);
            if (NameNormalizer.isBlankOrSymbol(line)) {
                skipped++;
                continue;
            }
            if (isNoiseHeaderText(line) && !line.matches(".*\\d{6,}.*")) {
                skipped++;
                continue;
            }

            boolean hasGender = line.matches(".*[男女]$");
            if (hasGender && line.matches(".*\\d{6,}.*")) {
                var mg = withGender.matcher(line);
                if (mg.find()) {
                    String name = pickNameNearGender(mg.group(2), mg.group(3));
                    if (name != null && addRow(rows, seen, samples, name, mg.group(1))) {
                        continue;
                    }
                    skipped++;
                    continue;
                }
            }

            var m = attached.matcher(line);
            boolean found = false;
            while (m.find()) {
                String name = m.group(2);
                if (isNoiseName(name) || looksLikeMajor(name)) {
                    skipped++;
                    continue;
                }
                if (addRow(rows, seen, samples, name, m.group(1))) {
                    found = true;
                } else {
                    skipped++;
                }
            }
            if (found) {
                continue;
            }

            String[] parts = line.split("[\\s\\t,，、;；|]+");
            String no = null;
            String name = null;
            for (String p : parts) {
                String t = NameNormalizer.normalize(p);
                if (t.isEmpty()) {
                    continue;
                }
                if (no == null && isStudentNoToken(t) && t.length() >= 6) {
                    no = t;
                    continue;
                }
                if (name == null && onlyName.matcher(t).matches() && !isNoiseName(t) && !looksLikeMajor(t)) {
                    name = t;
                }
            }
            if (name != null && addRow(rows, seen, samples, name, no)) {
                continue;
            }
            skipped++;
        }

        if (rows.isEmpty()) {
            throw GlobalExceptionHandler.badRequest("未能从图片中识别出有效姓名，请使用更清晰的名单截图，或改用文件导入");
        }
        return new ParseResult(rows, lines.size(), skipped, samples);
    }

    private boolean addRow(List<ImportedRow> rows, Set<String> seen, List<String> samples, String name, String no) {
        if (NameNormalizer.isBlankOrSymbol(name) || isNoiseName(name)) {
            return false;
        }
        if (!seen.add(name)) {
            return false;
        }
        rows.add(new ImportedRow(name, no));
        if (samples.size() < 5) {
            samples.add(no == null ? name : (name + " / " + no));
        }
        return true;
    }

    private boolean isNoiseName(String name) {
        if (name == null || name.length() > 4) {
            return true;
        }
        return switch (name) {
            case "男", "女", "班级", "专业", "学号", "姓名", "序号", "备注", "学院", "年级",
                 "电话", "号码", "性别", "民族", "籍贯", "政治", "面貌" -> true;
            default -> false;
        };
    }

    /** 专业名常被 OCR 进姓名栏，用后缀启发过滤。 */
    private boolean looksLikeMajor(String s) {
        if (s == null || s.length() < 3) {
            return false;
        }
        return s.endsWith("工程") || s.endsWith("技术") || s.endsWith("科学")
                || s.endsWith("管理") || s.endsWith("学院") || s.endsWith("学部")
                || s.equals("计算机") || s.equals("土木");
    }

    /**
     * 行含男/女时：两个候选则第一个是姓名、第二个是专业；
     * 仅一个时，若是专业形态则返回 null（该行姓名 OCR 失败）。
     */
    private String pickNameNearGender(String a, String b) {
        String c1 = a == null ? null : a.trim();
        String c2 = b == null ? null : b.trim();
        if (c1 != null && c2 != null && !c1.equals(c2)) {
            if (!isNoiseName(c1) && !looksLikeMajor(c1)) {
                return c1;
            }
            if (!isNoiseName(c2) && !looksLikeMajor(c2)) {
                return c2;
            }
            return null;
        }
        if (c1 != null && !isNoiseName(c1) && !looksLikeMajor(c1)) {
            return c1;
        }
        return null;
    }

    private ParseResult parseText(byte[] bytes) {
        String content = decodeText(bytes);
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') { // 去 UTF-8 BOM
            content = content.substring(1);
        }
        String[] lines = content.split("\\R");
        return buildFromTokens(List.of(lines));
    }

    private String decodeText(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        // U+FFFD 为解码失败产生的替换字符，数量越多说明该编码猜测越不可信
        long bad = utf8.chars().filter(c -> c == '\uFFFD').count();
        if (bad > 0) {
            try {
                String gbk = new String(bytes, Charset.forName("GBK"));
                long gbkBad = gbk.chars().filter(c -> c == '\uFFFD').count();
                if (gbkBad < bad) {
                    return gbk;
                }
            } catch (Exception ignored) {
                // keep utf-8
            }
        }
        return utf8;
    }

    private ParseResult parseExcel(java.io.InputStream in) throws Exception {
        List<String> tokens = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row == null) {
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                short last = row.getLastCellNum();
                for (int i = 0; i < Math.max(last, 0); i++) {
                    Cell cell = row.getCell(i);
                    if (cell == null) {
                        continue;
                    }
                    if (cell.getCellType() == CellType.FORMULA) {
                        try {
                            sb.append(formatter.formatCellValue(cell)).append(' ');
                        } catch (Exception e) {
                            sb.append(cell.toString()).append(' ');
                        }
                    } else {
                        sb.append(formatter.formatCellValue(cell)).append(' ');
                    }
                }
                String line = sb.toString().trim();
                if (!line.isEmpty()) {
                    tokens.add(line);
                }
            }
        }
        return buildFromTokens(tokens);
    }

    private ParseResult buildFromTokens(List<String> rawLines) {
        List<ImportedRow> rows = new ArrayList<>();
        Set<String> seenNames = new LinkedHashSet<>();
        int skipped = 0;
        List<String> samples = new ArrayList<>();

        for (String raw : rawLines) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String normalizedLine = NameNormalizer.normalize(raw);
            if (NameNormalizer.isBlankOrSymbol(normalizedLine)) {
                skipped++;
                continue;
            }

            String[] parts = normalizedLine.split("[\\s\\t,，、;；|]+");
            List<String> cleaned = new ArrayList<>();
            for (String part : parts) {
                String p = NameNormalizer.normalize(part);
                if (!p.isEmpty()) {
                    cleaned.add(p);
                }
            }
            if (cleaned.isEmpty()) {
                skipped++;
                continue;
            }
            // 整行都是表头
            if (cleaned.stream().allMatch(this::isHeader)) {
                skipped++;
                continue;
            }

            String studentNo = null;
            List<String> nameParts = new ArrayList<>();

            for (String part : cleaned) {
                if (isHeader(part)) {
                    continue;
                }
                if (isStudentNoToken(part)) {
                    if (studentNo == null) {
                        studentNo = part;
                    } else {
                        skipped++;
                    }
                    continue;
                }
                String name = SEQ.matcher(part).replaceFirst("").trim();
                if (NameNormalizer.isBlankOrSymbol(name) || isHeader(name)) {
                    continue;
                }
                nameParts.add(name);
            }

            // 整行只有学号/序号、没有姓名 → 丢弃
            if (nameParts.isEmpty()) {
                skipped++;
                continue;
            }

            // 同一行多个姓名时，学号只挂在第一个
            for (int i = 0; i < nameParts.size(); i++) {
                String name = nameParts.get(i);
                if (!seenNames.add(name)) {
                    skipped++;
                    continue;
                }
                String no = (i == 0) ? studentNo : null;
                rows.add(new ImportedRow(name, no));
                if (samples.size() < 5) {
                    samples.add(no == null ? name : (name + " / " + no));
                }
            }
        }

        if (rows.isEmpty()) {
            throw GlobalExceptionHandler.badRequest("未能从文件中解析出有效姓名，请检查文件格式（支持 txt/csv/xlsx，可含学号列）");
        }
        return new ParseResult(rows, rawLines.size(), skipped, samples);
    }

    private static boolean containsCjk(String s) {
        if (s == null) {
            return false;
        }
        return s.codePoints().anyMatch(c -> c >= 0x4E00 && c <= 0x9FFF);
    }

    /** 含数字的学号形态：202401 / A0001；纯字母姓名（Alice/Bob）不算学号。 */
    private static boolean isStudentNoToken(String token) {
        return token != null
                && STUDENT_NO.matcher(token).matches()
                && token.length() >= 4
                && token.chars().anyMatch(Character::isDigit)
                && !containsCjk(token);
    }

    private boolean isHeader(String token) {
        String t = token.toLowerCase(Locale.ROOT).replace(" ", "");
        return t.equals("姓名") || t.equals("名字") || t.equals("name")
                || t.equals("学号") || t.equals("studentno") || t.equals("student_no")
                || t.equals("序号") || t.equals("no") || t.equals("编号")
                || t.equals("班级") || t.equals("备注") || t.equals("联系方式")
                || t.equals("手机号") || t.equals("电话");
    }
}
