package io.github.jameshome.galley.modules.identity.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Optional;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUserDO> {

    @Select("SELECT * FROM app_user WHERE username = #{username}")
    Optional<AppUserDO> findByUsername(@Param("username") String username);

    @Select("SELECT * FROM app_user WHERE id = #{id}")
    Optional<AppUserDO> findById(@Param("id") UUID id);
}
