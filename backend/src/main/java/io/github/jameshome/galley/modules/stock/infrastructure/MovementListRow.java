package io.github.jameshome.galley.modules.stock.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 流水列表行：流水 + 物料名 + 单位名 + 操作人显示名。 */
public class MovementListRow {

    private UUID id;
    private UUID materialId;
    private String materialName;
    private String unitName;
    private String type;
    private BigDecimal quantity;
    private Instant occurredAt;
    private String operatorName;
    private String note;
    private Boolean reversed;
    private UUID reversalOf;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

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

    public String getUnitName() {
        return unitName;
    }

    public void setUnitName(String unitName) {
        this.unitName = unitName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Boolean getReversed() {
        return reversed;
    }

    public void setReversed(Boolean reversed) {
        this.reversed = reversed;
    }

    public UUID getReversalOf() {
        return reversalOf;
    }

    public void setReversalOf(UUID reversalOf) {
        this.reversalOf = reversalOf;
    }
}
