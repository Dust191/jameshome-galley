package io.github.jameshome.galley.modules.identity.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InviteCodeMapper extends BaseMapper<InviteCodeDO> {

    @Select("SELECT * FROM invite_code WHERE code = #{code}")
    Optional<InviteCodeDO> findByCode(@Param("code") String code);
}
