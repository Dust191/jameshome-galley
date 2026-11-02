package io.github.jameshome.galley.modules.imports.application;

import io.github.jameshome.galley.common.error.BusinessException;
import io.github.jameshome.galley.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;

/**
 * 最小 CSV 解析器（RFC 4180）。
 *
 * <p>不引第三方库的理由：只有这一处用到，且规则简单。但要处理三件容易被忽略的事：
 *
 * <ul>
 *   <li><b>BOM</b>：Excel 另存的 CSV 带 UTF-8 BOM，不去掉会让第一个表头变成 {@code ﻿自编码}。
 *   <li><b>引号内的逗号与换行</b>：规格里写 {@code 25kg/袋, 加厚} 是常事，按逗号硬切会把一行切错。
 *   <li><b>双引号转义</b>：{@code ""} 表示一个引号字符。
 * </ul>
 */
final class CsvParser {

    private CsvParser() {}

    /** 解析为行列表。末尾空行会被丢弃——Excel 保存时经常留一个空行，不该算作一条失败记录。 */
    static List<List<String>> parse(String content) {
        String text = content;
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }

        List<List<String>> rows = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    boolean doubled = i + 1 < text.length() && text.charAt(i + 1) == '"';
                    if (doubled) {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }
            switch (c) {
                case '"' -> inQuotes = true;
                case ',' -> {
                    current.add(field.toString());
                    field.setLength(0);
                }
                case '\r' -> {
                    // 交给 \n 处理；单独的 \r 视作换行（老 Mac 格式）
                    if (i + 1 >= text.length() || text.charAt(i + 1) != '\n') {
                        current.add(field.toString());
                        field.setLength(0);
                        rows.add(current);
                        current = new ArrayList<>();
                    }
                }
                case '\n' -> {
                    current.add(field.toString());
                    field.setLength(0);
                    rows.add(current);
                    current = new ArrayList<>();
                }
                default -> field.append(c);
            }
        }

        if (field.length() > 0 || !current.isEmpty()) {
            current.add(field.toString());
            rows.add(current);
        }

        rows.removeIf(row -> row.size() == 1 && row.get(0).isBlank());
        return rows;
    }

    /** 单行拆列；列数不符时给出明确的行号，便于用户直接定位到 Excel 的哪一行。 */
    static List<String> requireColumns(List<String> row, int expected, int lineNo) {
        if (row.size() < expected) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "第 " + lineNo + " 行只有 " + row.size() + " 列，应为 " + expected + " 列");
        }
        return row;
    }

    static String cell(List<String> row, int index) {
        if (index >= row.size()) {
            return "";
        }
        String value = row.get(index);
        return value == null ? "" : value.trim();
    }
}
