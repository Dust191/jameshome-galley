package io.github.jameshome.galley.common.audit;

import io.github.jameshome.galley.common.id.UuidV7;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 写操作审计（红线 7：写操作必须写审计；`SCOPE-001` In 项）。
 *
 * <p>用法：先 {@link #snapshot(Object)} 取变更前快照，再改对象，写库成功后 {@link #record} 落一条审计。{@code record}
 * 与业务写入同一事务——审计失败， 业务一起回滚；反过来说，业务提交了审计必然已写入，不存在"改了但没记"。
 *
 * <p>敏感字段约束：账号类的快照必须由调用方手工构造（只含账号名、显示名、角色、状态）， 禁止把 {@code AppUserDO} 整个序列化——那会把 password_hash
 * 写进审计（红线 5）。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogMapper mapper;
    private final ObjectMapper json;

    public AuditService(AuditLogMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    /** 把任意对象序列化成 JSON 快照文本；{@code null} 原样返回。 */
    public String snapshot(Object value) {
        if (value == null) {
            return null;
        }
        return json.writeValueAsString(value);
    }

    /**
     * 落一条审计。
     *
     * @param actorId 操作者；系统任务为 null
     * @param action 动作（CREATE / UPDATE / DELETE / PLACE / REVERSE / IMPORT / LOGIN / …）
     * @param entity 实体表名
     * @param entityId 目标行 id；批量操作传一个代表本批次的随机 id
     */
    public void record(
            UUID actorId,
            String action,
            String entity,
            UUID entityId,
            String before,
            String after) {
        mapper.insert(UuidV7.generate(), actorId, action, entity, entityId, before, after);
        if (log.isDebugEnabled()) {
            log.debug("audit {} {} {}", action, entity, entityId);
        }
    }
}
