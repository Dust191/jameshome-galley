package io.github.jameshome.galley.modules.stock.infrastructure;

import java.math.BigDecimal;
import java.util.UUID;

/** 对账的一行：某物料的结存与流水求和。 */
public class ReconcileRow {

    private UUID materialId;
    private String name;
    private BigDecimal balance;
    private BigDecimal movementSum;

    public UUID getMaterialId() {
        return materialId;
    }

    public void setMaterialId(UUID materialId) {
        this.materialId = materialId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getMovementSum() {
        return movementSum;
    }

    public void setMovementSum(BigDecimal movementSum) {
        this.movementSum = movementSum;
    }
}
