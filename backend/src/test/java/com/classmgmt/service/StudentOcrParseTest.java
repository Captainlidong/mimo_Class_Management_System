package com.classmgmt.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StudentOcrParseTest {

    private final StudentImportService service = new StudentImportService();

    @Test
    void parsesAttachedRosterLine() {
        String text = """
                2112616151绳涵戈 土木工程 男 1
                2112616152孟庆成 土木工程 男 2
                2112616161姚璐 土木工程 女 9
                """;
        var result = service.parseOcrText(text);
        assertEquals(3, result.rows().size());
        assertEquals("绳涵戈", result.rows().get(0).name());
        assertEquals("2112616151", result.rows().get(0).studentNo());
        assertEquals("孟庆成", result.rows().get(1).name());
        assertEquals("姚璐", result.rows().get(2).name());
    }

    @Test
    void parsesSpacedHeaderAndNames() {
        String text = """
                学号 姓名 专业 性别
                202401 张三 土木工程 男
                202402 李四 土木工程 女
                """;
        var result = service.parseOcrText(text);
        assertTrue(result.rows().size() >= 2);
        assertEquals("张三", result.rows().get(0).name());
        assertEquals("202401", result.rows().get(0).studentNo());
    }

    @Test
    void skipsMajorWhenNameOcrFailed() {
        String text = "2112616151 ie 土木工程 男\n2112616161 姚璐 土木工程 女";
        var result = service.parseOcrText(text);
        assertEquals(1, result.rows().size());
        assertEquals("姚璐", result.rows().get(0).name());
        assertEquals("2112616161", result.rows().get(0).studentNo());
    }

    @Test
    void parsesSeparatedWindowsOcrColumns() {
        String text = """
                2112616151
                2112616152
                2112616155
                绳 涵 戈
                孟 庆 成
                徐 吴
                土 木 工 程
                """;
        var result = service.parseOcrText(text);
        assertEquals(3, result.rows().size());
        assertEquals("绳涵戈", result.rows().get(0).name());
        assertEquals("2112616151", result.rows().get(0).studentNo());
        assertEquals("孟庆成", result.rows().get(1).name());
        assertEquals("徐吴", result.rows().get(2).name());
        assertEquals("2112616155", result.rows().get(2).studentNo());
    }

    @Test
    void parsesAlphanumericOcrIdsAndSpacedNames() {
        String text = """
                2H2616151
                2H2616152
                绳 涵 戈
                孟 庆 成
                """;
        var result = service.parseOcrText(text);
        assertEquals(2, result.rows().size());
        assertEquals("绳涵戈", result.rows().get(0).name());
        assertEquals("孟庆成", result.rows().get(1).name());
        // 学号经容错修正
        assertEquals("2112616151", result.rows().get(0).studentNo());
    }

    @Test
    void namesWithoutStudentNoStillParse() {
        String text = "绳 涵 戈\n孟 庆 成\n徐 昊";
        var result = service.parseOcrText(text);
        assertEquals(3, result.rows().size());
        assertEquals("绳涵戈", result.rows().get(0).name());
        assertTrue(result.rows().get(0).studentNo() == null || result.rows().get(0).studentNo().isEmpty());
    }

    /**
     * 回归测试：OCR 文本/复制粘贴常带 BOM（U+FEFF）。它必须先被清掉，
     * 否则会粘在姓名或学号上导致比对失败；同时保证源码里用的是 '\uFEFF'
     * 转义而不是不可见字面量。
     */
    @Test
    void stripsBomFromOcrText() {
        String withLeadingBom = "\uFEFF2112616151绳涵戈 土木工程 男\n2112616152孟庆成 土木工程 男";
        var leading = service.parseOcrText(withLeadingBom);
        assertEquals(2, leading.rows().size());
        assertEquals("绳涵戈", leading.rows().get(0).name());
        assertEquals("2112616151", leading.rows().get(0).studentNo());
        assertFalse(leading.rows().get(0).name().contains("\uFEFF"));

        String withInnerBom = "2112616151\uFEFF绳涵戈 土木工程 男\n2112616152孟庆成 土木工程 男";
        var inner = service.parseOcrText(withInnerBom);
        assertEquals(2, inner.rows().size());
        assertEquals("绳涵戈", inner.rows().get(0).name());
        assertEquals("2112616151", inner.rows().get(0).studentNo());
    }

    @Test
    void emptyOcrRejected() {
        assertThrows(RuntimeException.class, () -> service.parseOcrText("   \n  "));
    }
}
