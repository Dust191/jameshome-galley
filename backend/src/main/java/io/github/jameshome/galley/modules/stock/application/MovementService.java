package io.github.jameshome.galley.modules.stock.application;

import io.github.jameshome.galley.common.error.BusinessException;
import io.github.jameshome.galley.common.error.ErrorCode;
import io.github.jameshome.galley.common.id.UuidV7;
import io.github.jameshome.galley.common.web.PageResult;
import io.github.jameshome.galley.modules.identity.domain.AppUser;
import io.github.jameshome.galley.modules.material.domain.MaterialStatus;
import io.github.jameshome.galley.modules.material.infrastructure.MaterialDO;
import io.github.jameshome.galley.modules.material.infrastructure.MaterialMapper;
import io.github.jameshome.galley.modules.stock.api.dto.StockDtos.MovementCreate;
import io.github.jameshome.galley.modules.stock.api.dto.StockDtos.MovementView;
import io.github.jameshome.galley.modules.stock.domain.MovementType;
import io.github.jameshome.galley.modules.stock.infrastructure.MovementListRow;
import io.github.jameshome.galley.modules.stock.infrastructure.StockBalanceMapper;
import io.github.jameshome.galley.modules.stock.infrastructure.StockMovementDO;
import io.github.jameshome.galley.modules.stock.infrastructure.StockMovementMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 出入库登记与红冲。
 *
 * <p>两条硬规则在这里落地：
 *
 * <ul>
 *   <li><b>流水不可改删</b>：登记后错误只能靠红冲抵消，数据库触发器同样拒绝 UPDATE/DELETE。
 *   <li><b>结存原子增减</b>：用 {@code INSERT ... ON CONFLICT DO UPDATE} 在一条语句里完成， 不做"读—算—写"，否则并发下会丢更新。
 * </ul>
 */
@Service
public class MovementService {

    private final StockMovementMapper movementMapper;
    private final StockBalanceMapper balanceMapper;
    private final MaterialMapper materialMapper;

    public MovementService(
            StockMovementMapper movementMapper,
            StockBalanceMapper balanceMapper,
            MaterialMapper materialMapper) {
        this.movementMapper = movementMapper;
        this.balanceMapper = balanceMapper;
        this.materialMapper = materialMapper;
    }

    public PageResult<MovementView> search(
            UUID materialId, String type, Instant from, Instant to, int page, int pageSize) {

        int safePage = Math.max(page, 1);
        int safeSize = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        boolean capped = pageSize > 100;

        String typeParam = type == null || type.isBlank() ? null : type.toUpperCase();
        List<MovementListRow> rows =
                movementMapper.search(
                        materialId, typeParam, from, to, safeSize, (safePage - 1) * safeSize);
        long total = movementMapper.countSearch(materialId, typeParam, from, to);

        return new PageResult<>(
                rows.stream().map(MovementService::toView).toList(),
                safePage,
                safeSize,
                total,
                capped);
    }

    /**
     * 登记一条流水。
     *
     * <p>带 {@code idempotencyKey} 时，同键重复提交返回首次结果而不是再记一笔。 幂等判断放在事务内、依赖唯一索引兜底：并发同键时后到的那个会撞索引，捕获后再查一次。
     */
    @Transactional
    public MovementView create(MovementCreate body, AppUser operator) {
        if (!body.type().clientAssignable()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "红冲只能通过红冲接口发起", "type");
        }
        body.type().requireValidQuantity(body.quantity());

        String key = blankToNull(body.idempotencyKey());
        if (key != null) {
            var existing = movementMapper.findByIdempotencyKey(key);
            if (existing.isPresent()) {
                return toView(existing.get());
            }
        }

        MaterialDO material = requireUsableMaterial(body.materialId());

        StockMovementDO row = new StockMovementDO();
        row.setId(UuidV7.generate());
        row.setMaterialId(material.getId());
        row.setType(body.type().dbValue());
        row.setQuantity(body.quantity());
        row.setOccurredAt(body.occurredAt());
        row.setOperatorId(operator.id());
        row.setNote(blankToNull(body.note()));
        row.setReversed(false);
        row.setIdempotencyKey(key);
        row.setCreatedAt(Instant.now());
        row.setCreatedBy(operator.id());

        try {
            movementMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            // 并发同键：另一笔已写入，返回它的结果
            if (key != null) {
                return movementMapper
                        .findByIdempotencyKey(key)
                        .map(MovementService::toView)
                        .orElseThrow(() -> ex);
            }
            throw ex;
        }

        balanceMapper.addQuantity(row.getMaterialId(), row.getQuantity(), row.getOccurredAt());
        return detail(row.getId());
    }

    /**
     * 红冲：生成一条数量相反的 {@code REVERSAL} 流水，并把原流水标记为已冲正。
     *
     * <p>用 {@code markReversed} 的条件更新做并发闸门：两个请求同时红冲同一条时， 只有一个能把它从 {@code false} 翻成 {@code
     * true}，另一个立即失败。
     */
    @Transactional
    public MovementView reverse(UUID movementId, String note, AppUser operator) {
        StockMovementDO original =
                movementMapper
                        .findById(movementId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "流水不存在"));

        if (MovementType.REVERSAL.dbValue().equals(original.getType())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "红冲流水不能再被红冲", "movementId");
        }
        if (Boolean.TRUE.equals(original.getReversed())) {
            throw new BusinessException(ErrorCode.MOVEMENT_ALREADY_REVERSED, "该流水已被红冲");
        }
        if (movementMapper.markReversed(movementId) == 0) {
            throw new BusinessException(ErrorCode.MOVEMENT_ALREADY_REVERSED, "该流水已被红冲");
        }

        Instant now = Instant.now();
        StockMovementDO row = new StockMovementDO();
        row.setId(UuidV7.generate());
        row.setMaterialId(original.getMaterialId());
        row.setType(MovementType.REVERSAL.dbValue());
        row.setQuantity(original.getQuantity().negate());
        row.setOccurredAt(now);
        row.setOperatorId(operator.id());
        row.setNote(note.trim());
        row.setReversed(false);
        row.setReversalOf(movementId);
        row.setCreatedAt(now);
        row.setCreatedBy(operator.id());

        movementMapper.insert(row);
        balanceMapper.addQuantity(row.getMaterialId(), row.getQuantity(), now);
        return detail(row.getId());
    }

    public MovementView detail(UUID id) {
        MovementListRow row = movementMapper.findRowById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "流水不存在");
        }
        return toView(row);
    }

    /** 按流水重算结存，用于对账（RUN-002 恢复后校验、M-8 之外的完整性检查）。 */
    public BigDecimal recomputedQuantity(UUID materialId) {
        BigDecimal sum = movementMapper.sumQuantity(materialId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    private MaterialDO requireUsableMaterial(UUID id) {
        MaterialDO material = materialMapper.selectById(id);
        if (material == null || material.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "物料不存在");
        }
        if (MaterialStatus.ARCHIVED.dbValue().equals(material.getStatus())) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION, "已归档的物料不能登记流水", "materialId");
        }
        return material;
    }

    private static MovementView toView(StockMovementDO row) {
        return new MovementView(
                row.getId(),
                row.getMaterialId(),
                null,
                null,
                row.getType(),
                row.getQuantity(),
                row.getOccurredAt(),
                null,
                row.getNote(),
                Boolean.TRUE.equals(row.getReversed()),
                row.getReversalOf());
    }

    private static MovementView toView(MovementListRow row) {
        return new MovementView(
                row.getId(),
                row.getMaterialId(),
                row.getMaterialName(),
                row.getUnitName(),
                row.getType(),
                row.getQuantity(),
                row.getOccurredAt(),
                row.getOperatorName(),
                row.getNote(),
                Boolean.TRUE.equals(row.getReversed()),
                row.getReversalOf());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
