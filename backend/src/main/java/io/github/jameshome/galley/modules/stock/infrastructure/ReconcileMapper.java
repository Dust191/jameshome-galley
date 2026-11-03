package io.github.jameshome.galley.modules.stock.infrastructure;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/** 结存对账（DES-001 I-5）：只读，不修数据。SQL 见 {@code mapper/ReconcileMapper.xml}。 */
@Mapper
public interface ReconcileMapper {

    /** 参与对账的物料数（未删除的全部物料，含停用）。 */
    int countMaterials();

    /**
     * 结存与流水求和不一致的物料。
     *
     * <p>用 {@code left join} 而非 {@code join}：从未入库的物料没有结存行，流水求和为 0， 两边都应按 0 比较；用内连接会让这类物料直接消失，差异被掩盖。
     */
    List<ReconcileRow> findMismatches();
}
