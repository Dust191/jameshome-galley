package io.github.jameshome.galley.modules.imports.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CSV 解析。
 *
 * <p>这些用例全部来自"用户真的会这么存文件"： Excel 另存带 BOM、规格里写逗号、备注里换行、老系统给 CRLF。任何一条处理错，
 * 表现都是"某一列整体错位"，而用户只会说"导入乱掉了"。
 */
class CsvParserTest {

    @Test
    @DisplayName("普通行按逗号切分")
    void simpleRows() {
        List<List<String>> rows = CsvParser.parse("a,b,c\nd,e,f");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsExactly("a", "b", "c");
        assertThat(rows.get(1)).containsExactly("d", "e", "f");
    }

    @Test
    @DisplayName("CRLF 与 LF 都能正确分行")
    void handlesBothLineEndings() {
        assertThat(CsvParser.parse("a,b\r\nc,d")).hasSize(2);
        assertThat(CsvParser.parse("a,b\nc,d")).hasSize(2);
    }

    @Test
    @DisplayName("去掉 UTF-8 BOM，否则第一个表头会带上不可见字符")
    void stripsBom() {
        List<List<String>> rows = CsvParser.parse("\uFEFF自编码,物料名");
        assertThat(rows.get(0).get(0)).isEqualTo("自编码");
    }

    @Test
    @DisplayName("引号内的逗号不切列")
    void quotedComma() {
        List<List<String>> rows = CsvParser.parse("大米,\"25kg/袋, 加厚\",袋");
        assertThat(rows.get(0)).containsExactly("大米", "25kg/袋, 加厚", "袋");
    }

    @Test
    @DisplayName("引号内的换行不切行")
    void quotedNewline() {
        List<List<String>> rows = CsvParser.parse("大米,\"第一行\n第二行\",袋");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(1)).isEqualTo("第一行\n第二行");
    }

    @Test
    @DisplayName("连续两个引号表示一个引号字符")
    void escapedQuote() {
        List<List<String>> rows = CsvParser.parse("a,\"他说\"\"好\"\"\",c");
        assertThat(rows.get(0).get(1)).isEqualTo("他说\"好\"");
    }

    @Test
    @DisplayName("末尾空行被丢弃，不算一条失败记录")
    void dropsTrailingBlankLine() {
        List<List<String>> rows = CsvParser.parse("a,b\nc,d\n\n");
        assertThat(rows).hasSize(2);
    }

    @Test
    @DisplayName("空字段保留为空串，不塌陷成少一列")
    void keepsEmptyCells() {
        List<List<String>> rows = CsvParser.parse("a,,c");
        assertThat(rows.get(0)).hasSize(3);
        assertThat(rows.get(0).get(1)).isEmpty();
    }

    @Test
    @DisplayName("取列越界时返回空串，不抛下标越界")
    void cellOutOfRange() {
        assertThat(CsvParser.cell(List.of("a"), 5)).isEmpty();
    }
}
