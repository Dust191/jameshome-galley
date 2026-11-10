package io.github.jameshome.galley.common.audit;

import java.util.UUID;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 审计日志写入（`DES-001` §3.9）。
 *
 * <p>不用 {@code BaseMapper}：{@code before} / {@code after} 是 jsonb 列，MyBatis 按字符串 绑定会被 PG
 * 拒绝（character varying → jsonb），这里显式 {@code ::jsonb} 转换。
 */
@Mapper
public interface AuditLogMapper {

    @Insert(
            """
            insert into audit_log
                (id, actor_id, action, entity, entity_id, before, after)
            values
                (#{id}, #{actorId}, #{action}, #{entity}, #{entityId},
                 #{before,jdbcType=VARCHAR}::jsonb, #{after,jdbcType=VARCHAR}::jsonb)
            """)
    int insert(
            @Param("id") UUID id,
            @Param("actorId") UUID actorId,
            @Param("action") String action,
            @Param("entity") String entity,
            @Param("entityId") UUID entityId,
            @Param("before") String before,
            @Param("after") String after);
}
