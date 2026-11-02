package io.github.jameshome.galley.modules.material.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CategoryMapper extends BaseMapper<CategoryDO> {

    /** 该分类下的在用物料数，用于删除前的占用校验。 */
    @Select("SELECT count(*) FROM material WHERE category_id = #{id} AND deleted_at IS NULL")
    long countMaterials(@Param("id") UUID id);
}
