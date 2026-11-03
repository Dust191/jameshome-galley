package io.github.jameshome.galley.modules.stock.api.dto;

import io.github.jameshome.galley.modules.stock.domain.MovementType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 库存模块的出入参。 */
public final class StockDtos {

    private StockDtos() {}

    /**
     * 登记流水。
     *
     * <p>{@code quantity} 带符号：{@code IN} 正、{@code OUT} 负、{@code ADJUST} 可正可负。
     * 符号与类型不符属于参数错误（400），不是业务拒绝（422）——调用方写错了，不是规则不允许。
     */
    public record MovementCreate(
            @NotNull(message = "必须选择物料") UUID materialId,
            @NotNull(message = "必须选择类型") MovementType type,
            @NotNull(message = "数量不能为空") @Digits(integer = 11, fraction = 3, message = "数量最多三位小数")
                    BigDecimal quantity,
            @NotNull(message = "发生时间不能为空") Instant occurredAt,
            @Size(max = 200, message = "备注不超过 200 字") String note,
            @Size(max = 64, message = "幂等键不超过 64 字符") String idempotencyKey) {}

    /** 红冲。原因必填——半年后回头看，没有原因的红冲等于没有记录。 */
    public record ReversalRequest(
            @NotBlank(message = "冲正原因不能为空") @Size(max = 200, message = "原因不超过 200 字")
                    String note) {}

    /** 结存对账结果：checked 为参与核对的物料数，items 只列不一致的。 */
    public record ReconcileReport(
            int checked, int mismatched, Instant checkedAt, List<Mismatch> items) {}

    public record Mismatch(String materialId, String name, String balance, String movementSum) {}

    public record MovementView(
            UUID id,
            UUID materialId,
            String materialName,
            String unitName,
            String type,
            BigDecimal quantity,
            Instant occurredAt,
            String operatorName,
            String note,
            boolean reversed,
            UUID reversalOf) {}

    /** 结存视图。{@code severity} 按 DES-004 M-9：3 负库存 / 2 断货 / 1 低于安全线 / 0 正常或呆滞。 */
    public record BalanceView(
            UUID materialId,
            String materialName,
            String code,
            String spec,
            String categoryName,
            String unitName,
            BigDecimal quantity,
            BigDecimal safetyStock,
            Instant lastMovementAt,
            int severity) {}
}
