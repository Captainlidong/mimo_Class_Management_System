package com.classmgmt.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class NameNormalizer {

    private static final Pattern SEQ_PREFIX = Pattern.compile("^(\\d{1,3})[\\.．、\\)）:：\\-—\\s]+");
    private static final Pattern SYMBOL_ONLY = Pattern.compile("^[\\p{P}\\p{S}\\s]+$");

    private NameNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = toHalfWidth(raw).trim();
        s = s.replace("　", " ").trim();
        s = SEQ_PREFIX.matcher(s).replaceFirst("").trim();
        s = s.replaceAll("[\\r\\n\\t]+", " ").trim();
        // 去掉姓名尾部句读（粘贴时常带句号）
        s = s.replaceAll("[。．\\.]+$", "").trim();
        return s;
    }

    public static boolean isBlankOrSymbol(String s) {
        return s == null || s.isBlank() || SYMBOL_ONLY.matcher(s).matches();
    }

    public static List<String> parseTokens(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        String half = toHalfWidth(text);
        String[] parts = half.split("[\\s,，、;；/|\\n\\r\\t]+");
        for (String part : parts) {
            String n = normalize(part);
            if (!n.isEmpty()) {
                tokens.add(n);
            }
        }
        return tokens;
    }

    public static Set<String> uniqueNonBlank(List<String> names) {
        Set<String> set = new LinkedHashSet<>();
        for (String name : names) {
            String n = normalize(name);
            if (!isBlankOrSymbol(n)) {
                set.add(n);
            }
        }
        return set;
    }

    /** 学号后两位数字，用于名单排序；无学号或无数字时返回 Integer.MAX_VALUE（排最后）。 */
    public static int trailingDigits(String studentNo) {
        if (studentNo == null) {
            return Integer.MAX_VALUE;
        }
        String digits = toHalfWidth(studentNo).replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        String last2 = digits.length() <= 2 ? digits : digits.substring(digits.length() - 2);
        return Integer.parseInt(last2);
    }

    private static String toHalfWidth(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch >= 0xFF01 && ch <= 0xFF5E) {
                sb.append((char) (ch - 0xFEE0));
            } else if (ch == 0x3000) {
                sb.append(' ');
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
