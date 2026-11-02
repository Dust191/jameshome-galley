package io.github.jameshome.galley.modules.stock.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 流水持久化。写入只有 insert；修改仅限 {@code reversed} 一个字段，由专用语句完成。 */
@Mapper
public interface StockMovementMapper extends BaseMapper<StockMovementDO> {

    @Select("SELECT * FROM stock_movement WHERE id = #{id}")
    Optional<StockMovementDO> findById(@Param("id") UUID id);

    @Select("SELECT * FROM stock_movement WHERE idempotency_key = #{key}")
    Optional<StockMovementDO> findByIdempotencyKey(@Param("key") String key);

    /** 只翻 reversed 标志：流水其余字段受触发器保护，本语句是唯一的例外通道。 */
    @org.apache.ibatis.annotations.Update(
            "UPDATE stock_movement SET reversed = true WHERE id = #{id} AND reversed = false")
    int markReversed(@Param("id") UUID id);

    /** 单条流水，带物料名、单位名与操作人——创建与红冲后用它回填返回体。 */
    MovementListRow findRowById(@Param("id") UUID id);

    List<MovementListRow> search(
            @Param("materialId") UUID materialId,
            @Param("type") String type,
            @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countSearch(
            @Param("materialId") UUID materialId,
            @Param("type") String type,
            @Param("from") java.time.Instant from,
            @Param("to") java.time.Instant to);

    /** 对账用：按流水重算结存，与 stock_balance 比对（RUN-002 的恢复后校验）。 */
    @org.apache.ibatis.annotations.Select(
            "SELECT coalesce(sum(quantity), 0) FROM stock_movement WHERE material_id = #{materialId}")
    java.math.BigDecimal sumQuantity(@Param("materialId") UUID materialId);
}
