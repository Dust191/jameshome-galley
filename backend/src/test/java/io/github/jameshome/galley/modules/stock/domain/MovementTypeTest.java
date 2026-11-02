package io.github.jameshome.galley.modules.stock.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.jameshome.galley.common.error.BusinessException;
import io.github.jameshome.galley.common.error.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 流水类型的符号规则（DES-001 §3.5）。
 *
 * <p>数据库有同样的 CHECK 约束。两边都留是刻意的：应用层给得出字段级提示， 数据库层挡得住绕过应用的脚本与手工 SQL。
 */
class MovementTypeTest {

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("入库必须为正、出库必须为负")
    void inAndOutSigns() {
        assertThatCode(() -> MovementType.IN.requireValidQuantity(n("5.000")))
                .doesNotThrowAnyException();
        assertThatCode(() -> MovementType.OUT.requireValidQuantity(n("-5.000")))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> MovementType.IN.requireValidQuantity(n("-5")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> MovementType.OUT.requireValidQuantity(n("5")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.VALIDATION_FAILED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"3.000", "-3.000"})
    @DisplayName("调整是差额，正负都合法")
    void adjustAcceptsBothSigns(String quantity) {
        assertThatCode(() -> MovementType.ADJUST.requireValidQuantity(n(quantity)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("数量为 0 一律拒绝：零流水既不能表达业务动作，也会撞数据库的 quantity <> 0")
    void zeroIsRejected() {
        for (MovementType type : MovementType.values()) {
            assertThatThrownBy(() -> type.requireValidQuantity(BigDecimal.ZERO))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    @DisplayName("红冲不接受客户端指定，只能走专用接口")
    void reversalIsNotClientAssignable() {
        for (MovementType type : MovementType.values()) {
            boolean expected = type != MovementType.REVERSAL;
            org.assertj.core.api.Assertions.assertThat(type.clientAssignable()).isEqualTo(expected);
        }
    }
}
