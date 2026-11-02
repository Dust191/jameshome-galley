package io.github.jameshome.galley.modules.stock.domain;

import io.github.jameshome.galley.common.error.BusinessException;
import io.github.jameshome.galley.common.error.ErrorCode;
import java.math.BigDecimal;
import java.util.Locale;

/**
 * 流水类型（DES-001 §3.5）。
 *
 * <p>符号规则：{@code IN} 必正、{@code OUT} 必负、{@code ADJUST} 为差额可正可负、 {@code REVERSAL}
 * 由红冲接口生成且必为原流水的相反数。数据库有同样的 CHECK 约束， 这里是应用层的第一道拦截——两条都留着，因为绕过应用直连数据库的脚本也要被挡住。
 */
public enum MovementType {
    IN,
    OUT,
    ADJUST,
    REVERSAL;

    public String dbValue() {
        return name();
    }

    public static MovementType fromDb(String value) {
        return MovementType.valueOf(value.toUpperCase(Locale.ROOT));
    }

    /** 客户端可指定的类型：红冲只能走专用接口，不允许直接登记。 */
    public boolean clientAssignable() {
        return this != REVERSAL;
    }

    /** 校验数量符号；不符时按 DES-002 §4.4 返回 400 而非业务拒绝。 */
    public void requireValidQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "数量不能为 0", "quantity");
        }
        switch (this) {
            case IN -> {
                if (quantity.signum() < 0) {
                    throw new BusinessException(
                            ErrorCode.VALIDATION_FAILED, "入库数量必须为正数", "quantity");
                }
            }
            case OUT -> {
                if (quantity.signum() > 0) {
                    throw new BusinessException(
                            ErrorCode.VALIDATION_FAILED, "出库数量必须为负数", "quantity");
                }
            }
            default -> {
                // ADJUST 可正可负；REVERSAL 的符号由服务端按原流水取反，这里不校验
            }
        }
    }
}
