package io.github.jameshome.galley.modules.material.application;

import io.github.jameshome.galley.common.audit.AuditService;
import io.github.jameshome.galley.common.error.BusinessException;
import io.github.jameshome.galley.common.error.ErrorCode;
import io.github.jameshome.galley.common.id.UuidV7;
import io.github.jameshome.galley.modules.identity.application.CurrentUserService;
import io.github.jameshome.galley.modules.material.domain.DictStatus;
import io.github.jameshome.galley.modules.material.domain.Unit;
import io.github.jameshome.galley.modules.material.infrastructure.UnitDO;
import io.github.jameshome.galley.modules.material.infrastructure.UnitMapper;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 单位维护。已被物料引用的单位不得停用或删除（DES-001 §3.3）。 */
@Service
public class UnitService {

    private final UnitMapper mapper;
    private final AuditService audit;
    private final CurrentUserService currentUser;

    public UnitService(UnitMapper mapper, AuditService audit, CurrentUserService currentUser) {
        this.mapper = mapper;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    public List<Unit> list(boolean includeInactive) {
        return mapper.selectList(null).stream()
                .filter(r -> includeInactive || DictStatus.ACTIVE.dbValue().equals(r.getStatus()))
                .sorted(Comparator.comparing(UnitDO::getSortOrder).thenComparing(UnitDO::getName))
                .map(UnitService::toDomain)
                .toList();
    }

    @Transactional
    public Unit create(String name, int sortOrder) {
        Instant now = Instant.now();
        UnitDO row = new UnitDO();
        row.setId(UuidV7.generate());
        row.setName(name);
        row.setSortOrder((short) sortOrder);
        row.setStatus(DictStatus.ACTIVE.dbValue());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        try {
            mapper.insert(row);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_KEY, "单位名已存在", "name");
        }
        audit.record(
                currentUser.requireId(), "CREATE", "unit", row.getId(), null, audit.snapshot(row));
        return toDomain(row);
    }

    @Transactional
    public Unit update(UUID id, String name, Integer sortOrder, DictStatus status) {
        UnitDO row = require(id);
        String before = audit.snapshot(row);
        long used = mapper.countMaterials(id);

        if (status == DictStatus.INACTIVE && used > 0) {
            throw new BusinessException(ErrorCode.UNIT_IN_USE, "该单位已被 " + used + " 种物料使用，不能停用");
        }
        if (name != null && !name.isBlank()) {
            row.setName(name);
        }
        if (sortOrder != null) {
            row.setSortOrder((short) sortOrder.intValue());
        }
        if (status != null) {
            row.setStatus(status.dbValue());
        }
        row.setUpdatedAt(Instant.now());
        try {
            mapper.updateById(row);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_KEY, "单位名已存在", "name");
        }
        audit.record(currentUser.requireId(), "UPDATE", "unit", id, before, audit.snapshot(row));
        return toDomain(row);
    }

    @Transactional
    public void delete(UUID id) {
        UnitDO row = require(id);
        long used = mapper.countMaterials(id);
        if (used > 0) {
            throw new BusinessException(ErrorCode.UNIT_IN_USE, "该单位已被 " + used + " 种物料使用，不能删除");
        }
        mapper.deleteById(id);
        audit.record(currentUser.requireId(), "DELETE", "unit", id, audit.snapshot(row), null);
    }

    private UnitDO require(UUID id) {
        UnitDO row = mapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "单位不存在");
        }
        return row;
    }

    static Unit toDomain(UnitDO row) {
        return new Unit(
                row.getId(),
                row.getName(),
                row.getSortOrder() == null ? 0 : row.getSortOrder(),
                DictStatus.fromDb(row.getStatus()));
    }
}
