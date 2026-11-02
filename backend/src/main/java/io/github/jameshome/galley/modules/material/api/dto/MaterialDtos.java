package io.github.jameshome.galley.modules.material.api.dto;

import io.github.jameshome.galley.modules.material.domain.Category;
import io.github.jameshome.galley.modules.material.domain.DictStatus;
import io.github.jameshome.galley.modules.material.domain.MaterialStatus;
import io.github.jameshome.galley.modules.material.domain.Unit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** material 模块的出入参。分类与单位先落，物料的 DTO 在 T3 后半段补。 */
public final class MaterialDtos {

    private MaterialDtos() {}

    public record CategoryView(UUID id, String name, UUID parentId, int sortOrder, String status) {
        public static CategoryView from(Category c) {
            return new CategoryView(
                    c.id(), c.name(), c.parentId(), c.sortOrder(), c.status().dbValue());
        }
    }

    public record CategoryCreate(
            @NotBlank(message = "分类名不能为空") @Size(max = 50, message = "分类名不超过 50 字") String name,
            @Min(value = -32768, message = "排序值过小") @Max(value = 32767, message = "排序值过大")
                    Integer sortOrder) {}

    public record CategoryPatch(String name, Integer sortOrder, DictStatus status) {}

    public record UnitView(UUID id, String name, int sortOrder, String status) {
        public static UnitView from(Unit u) {
            return new UnitView(u.id(), u.name(), u.sortOrder(), u.status().dbValue());
        }
    }

    public record UnitCreate(
            @NotBlank(message = "单位名不能为空") @Size(max = 20, message = "单位名不超过 20 字") String name,
            @Min(value = -32768, message = "排序值过小") @Max(value = 32767, message = "排序值过大")
                    Integer sortOrder) {}

    public record UnitPatch(String name, Integer sortOrder, DictStatus status) {}

    // ---------- 物料 ----------

    /**
     * 建档入参。
     *
     * <p>{@code safetyStock} 默认 0，语义是"不设预警线"而非"库存下限为零"（DES-004 M-2）。 数量用 {@link BigDecimal}：{@code
     * numeric(14,3)} 对应的整数位最多 11 位、小数 3 位，超出即字段级错误。
     */
    public record MaterialCreate(
            @Size(max = 32, message = "自编码不超过 32 字") String code,
            @NotBlank(message = "物料名不能为空") @Size(max = 80, message = "物料名不超过 80 字") String name,
            @NotNull(message = "必须选择分类") UUID categoryId,
            @NotNull(message = "必须选择单位") UUID unitId,
            @Size(max = 50, message = "规格不超过 50 字") String spec,
            @NotNull(message = "安全库存不能为空")
                    @DecimalMin(value = "0", message = "安全库存不能为负")
                    @Digits(integer = 11, fraction = 3, message = "数量最多三位小数")
                    BigDecimal safetyStock,
            @Min(value = 1, message = "保质期须大于 0 天") Integer shelfLifeDays,
            @Size(max = 255, message = "图片 key 过长") String imageKey,
            @Size(max = 200, message = "备注不超过 200 字") String note) {}

    /**
     * 局部更新入参：字段为 {@code null} 表示不改。
     *
     * <p>{@code version} 用于乐观锁；不传则视为放弃并发保护，服务层会拒绝——宁可让调用方显式传， 也不想在并发写时默默覆盖别人的修改。
     */
    public record MaterialPatch(
            @Size(max = 32, message = "自编码不超过 32 字") String code,
            @Size(max = 80, message = "物料名不超过 80 字") String name,
            UUID categoryId,
            UUID unitId,
            @Size(max = 50, message = "规格不超过 50 字") String spec,
            @DecimalMin(value = "0", message = "安全库存不能为负")
                    @Digits(integer = 11, fraction = 3, message = "数量最多三位小数")
                    BigDecimal safetyStock,
            @Min(value = 1, message = "保质期须大于 0 天") Integer shelfLifeDays,
            @Size(max = 255, message = "图片 key 过长") String imageKey,
            @Size(max = 200, message = "备注不超过 200 字") String note,
            MaterialStatus status,
            Integer version) {}

    /** 列表与详情共用：{@code imageUrl} 由服务层按存储实现拼出，库里只存 key。 */
    public record MaterialView(
            UUID id,
            String code,
            String name,
            UUID categoryId,
            String categoryName,
            UUID unitId,
            String unitName,
            String spec,
            BigDecimal safetyStock,
            Integer shelfLifeDays,
            String imageKey,
            String imageUrl,
            String note,
            String status,
            int version,
            BigDecimal currentStock) {}
}
