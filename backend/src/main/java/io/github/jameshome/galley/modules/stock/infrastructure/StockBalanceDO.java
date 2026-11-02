package io.github.jameshome.galley.modules.stock.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** stock_balance 的持久化对象。主键就是 material_id，一物料一行。 */
@TableName("stock_balance")
public class StockBalanceDO {

    @TableId(type = IdType.INPUT)
    private UUID materialId;

    private BigDecimal quantity;
    private Instant lastMovementAt;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getMaterialId() {
        return materialId;
    }

    public void setMaterialId(UUID materialId) {
        this.materialId = materialId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public Instant getLastMovementAt() {
        return lastMovementAt;
    }

    public void setLastMovementAt(Instant lastMovementAt) {
        this.lastMovementAt = lastMovementAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
