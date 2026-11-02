package io.github.jameshome.galley.modules.material.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 物料持久化。
 *
 * <p>列表查询放在 XML（{@code mapper/MaterialMapper.xml}）：动态条件多，注解里写 {@code <script>} 会难以阅读。{@code
 * orderBy} 由 Service 从白名单拼出，不接受前端原样传入。
 */
@Mapper
public interface MaterialMapper extends BaseMapper<MaterialDO> {

    List<MaterialListRow> search(
            @Param("keyword") String keyword,
            @Param("categoryId") UUID categoryId,
            @Param("stockState") String stockState,
            @Param("status") String status,
            @Param("orderBy") String orderBy,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countSearch(
            @Param("keyword") String keyword,
            @Param("categoryId") UUID categoryId,
            @Param("stockState") String stockState,
            @Param("status") String status);

    /** 单条详情，含分类名、单位名与当前结存。 */
    MaterialListRow findRowById(@Param("id") UUID id);

    /** 该物料是否已有流水；软删前的占用校验用。 */
    long countMovements(@Param("id") UUID id);
}
