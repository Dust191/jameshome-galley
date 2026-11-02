package io.github.jameshome.galley.modules.stock.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 结存列表行：结存 + 物料展示字段，用于库存总览与预警清单。 */
public class BalanceListRow {

    private UUID materialId;
    private String materialName;
    private String code;
    private String spec;
    private String categoryName;
    private String unitName;
    private BigDecimal quantity;
    private BigDecimal safetyStock;
    private Instant lastMovementAt;

    public UUID getMaterialId() {
        return materialId;
    }

    public void setMaterialId(UUID materialId) {
        this.materialId = materialId;
    }

    public String getMaterialName() {
        return materialName;
    }

    public void setMaterialName(String materialName) {
        this.materialName = materialName;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getSafetyStock() {
        return safetyStock;
    }

    public void setSafetyStock(BigDecimal safetyStock) {
        this.safetyStock = safetyStock;
    }

    public Instant getLastMovementAt() {
        return lastMovementAt;
    }

    public void setLastMovementAt(Instant lastMovementAt) {
        this.lastMovementAt = lastMovementAt;
    }
}
