package io.github.jameshome.galley.modules.material.domain;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 物料（领域对象）。
 *
 * <p>{@code safetyStock} 用 {@link BigDecimal}：数量统一 {@code numeric(14,3)}，用 double 会在累加时
 * 引入二进制舍入误差，而库存对不上账正是最难查的一类问题。
 *
 * <p>{@code imageUrl} 存的是对象存储的 key，不是完整 URL（ADR-0005）。
 */
public record Material(
        UUID id,
        String code,
        String name,
        UUID categoryId,
        UUID unitId,
        String spec,
        BigDecimal safetyStock,
        Integer shelfLifeDays,
        String imageUrl,
        String note,
        MaterialStatus status,
        int version) {

    /** 安全库存为 0 视为不设预警线（DES-004 M-2 口径）。 */
    public boolean hasSafetyStock() {
        return safetyStock != null && safetyStock.signum() > 0;
    }

    public boolean editable() {
        return status != MaterialStatus.ARCHIVED;
    }
}
