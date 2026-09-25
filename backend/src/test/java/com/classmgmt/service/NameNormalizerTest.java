package com.classmgmt.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NameNormalizerTest {

    @Test
    void stripsSequencePrefix() {
        assertEquals("张三", NameNormalizer.normalize("1. 张三"));
        assertEquals("张三", NameNormalizer.normalize("2、张三"));
        assertEquals("张三", NameNormalizer.normalize("3) 张三"));
        assertEquals("张三", NameNormalizer.normalize("０１、张三"));
    }

    @Test
    void fullWidthToHalfWidth() {
        assertEquals("abc123", NameNormalizer.normalize("ａｂｃ１２３"));
        assertEquals("李四", NameNormalizer.normalize("　李四　"));
    }

    @Test
    void parseMultipleSeparators() {
        List<String> tokens = NameNormalizer.parseTokens("张三, 李四、王五\n赵六;钱七");
        assertEquals(List.of("张三", "李四", "王五", "赵六", "钱七"), tokens);
    }

    @Test
    void blankAndSymbolDetected() {
        assertTrue(NameNormalizer.isBlankOrSymbol(""));
        assertTrue(NameNormalizer.isBlankOrSymbol("  "));
        assertTrue(NameNormalizer.isBlankOrSymbol("!!!"));
        assertTrue(NameNormalizer.isBlankOrSymbol("——"));
        assertFalse(NameNormalizer.isBlankOrSymbol("张三"));
    }
}
