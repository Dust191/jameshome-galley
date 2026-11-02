package io.github.jameshome.galley.modules.material.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 排序参数解析。
 *
 * <p>这是全项目唯一的 SQL 字符串拼接点，因此测试重点不是"排序对不对"，而是 **任何输入都不会把非法内容带进 SQL**——包括注入片段。
 */
class MaterialServiceOrderByTest {

    @Test
    @DisplayName("空排序回落到名称升序，并带 id 作次级排序保证分页稳定")
    void defaultSort() {
        assertThat(MaterialService.orderBy(null)).isEqualTo("m.name ASC, m.id ASC");
        assertThat(MaterialService.orderBy("  ")).isEqualTo("m.name ASC, m.id ASC");
    }

    @Test
    @DisplayName("前缀减号表示降序")
    void minusPrefixMeansDesc() {
        assertThat(MaterialService.orderBy("-updatedAt")).isEqualTo("m.updated_at DESC, m.id ASC");
    }

    @Test
    @DisplayName("逗号写法也可表达方向")
    void commaForm() {
        assertThat(MaterialService.orderBy("safetyStock,desc"))
                .isEqualTo("m.safety_stock DESC, m.id ASC");
        assertThat(MaterialService.orderBy("code,asc")).isEqualTo("m.code ASC, m.id ASC");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "name; DROP TABLE material",
                "m.name --",
                "1=1",
                "(select 1)",
                "name) OR 1=1 --"
            })
    @DisplayName("白名单之外的输入一律回落到默认列，绝不进入 SQL")
    void rejectsInjection(String malicious) {
        String orderBy = MaterialService.orderBy(malicious);
        assertThat(orderBy).isEqualTo("m.name ASC, m.id ASC");
    }

    @Test
    @DisplayName("未知字段回落到名称，而不是报错——排序是可选参数，不该让列表打不开")
    void unknownFieldFallsBack() {
        assertThat(MaterialService.orderBy("nonexistent")).isEqualTo("m.name ASC, m.id ASC");
    }
}
