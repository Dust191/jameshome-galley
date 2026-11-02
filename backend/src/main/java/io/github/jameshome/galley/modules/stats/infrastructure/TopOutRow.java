package io.github.jameshome.galley.modules.stats.infrastructure;

import java.math.BigDecimal;

/** M-6 出库频次的一行。 */
public class TopOutRow {

    private String materialName;
    private String unitName;
    private Long outCount;
    private BigDecimal totalQuantity;

    public String getMaterialName() {
        return materialName;
    }

    public void setMaterialName(String materialName) {
        this.materialName = materialName;
    }

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public Long getOutCount() {
        return outCount;
    }

    public void setOutCount(Long outCount) {
        this.outCount = outCount;
    }

    public BigDecimal getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(BigDecimal totalQuantity) {
        this.totalQuantity = totalQuantity;
    }
}
