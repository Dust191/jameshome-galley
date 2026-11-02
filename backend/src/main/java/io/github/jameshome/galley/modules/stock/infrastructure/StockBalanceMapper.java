package io.github.jameshome.galley.modules.stock.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 结存持久化。
 *
 * <p>结存的更新只有一种方式：{@link #addQuantity} 的原子增减。不用"先读再写"， 因为两个并发登记会读到同一个旧值，后写的那个把前一个的增量吃掉——这就是丢失更新。
 */
@Mapper
public interface StockBalanceMapper extends BaseMapper<StockBalanceDO> {

    /**
     * 原子增减结存；没有该物料的行时插入。
     *
     * <p>{@code GREATEST} 处理补录：{@code occurred_at} 可能早于已有的最后流水时间， 直接覆盖会让"最后变动时间"倒退。
     */
    @Update(
            """
            INSERT INTO stock_balance (material_id, quantity, last_movement_at, updated_at)
            VALUES (#{materialId}, #{delta}, #{occurredAt}, now())
            ON CONFLICT (material_id) DO UPDATE
            SET quantity = stock_balance.quantity + EXCLUDED.quantity,
                last_movement_at = GREATEST(
                    coalesce(stock_balance.last_movement_at, EXCLUDED.last_movement_at),
                    EXCLUDED.last_movement_at),
                updated_at = now()
            """)
    int addQuantity(
            @Param("materialId") UUID materialId,
            @Param("delta") BigDecimal delta,
            @Param("occurredAt") Instant occurredAt);

    @Select("SELECT * FROM stock_balance WHERE material_id = #{materialId}")
    Optional<StockBalanceDO> findByMaterialId(@Param("materialId") UUID materialId);

    List<BalanceListRow> search(
            @Param("keyword") String keyword,
            @Param("categoryId") UUID categoryId,
            @Param("stockState") String stockState,
            @Param("orderBy") String orderBy,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countSearch(
            @Param("keyword") String keyword,
            @Param("categoryId") UUID categoryId,
            @Param("stockState") String stockState);
}
