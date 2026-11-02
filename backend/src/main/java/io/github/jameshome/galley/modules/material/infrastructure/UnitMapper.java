package io.github.jameshome.galley.modules.material.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UnitMapper extends BaseMapper<UnitDO> {

    @Select("SELECT count(*) FROM material WHERE unit_id = #{id} AND deleted_at IS NULL")
    long countMaterials(@Param("id") UUID id);
}
