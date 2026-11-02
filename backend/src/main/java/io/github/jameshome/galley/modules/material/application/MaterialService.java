package io.github.jameshome.galley.modules.material.application;

import io.github.jameshome.galley.common.error.BusinessException;
import io.github.jameshome.galley.common.error.ErrorCode;
import io.github.jameshome.galley.common.id.UuidV7;
import io.github.jameshome.galley.common.storage.ObjectStorage;
import io.github.jameshome.galley.common.web.PageResult;
import io.github.jameshome.galley.modules.material.api.dto.MaterialDtos.MaterialCreate;
import io.github.jameshome.galley.modules.material.api.dto.MaterialDtos.MaterialPatch;
import io.github.jameshome.galley.modules.material.api.dto.MaterialDtos.MaterialView;
import io.github.jameshome.galley.modules.material.domain.DictStatus;
import io.github.jameshome.galley.modules.material.domain.MaterialStatus;
import io.github.jameshome.galley.modules.material.infrastructure.CategoryDO;
import io.github.jameshome.galley.modules.material.infrastructure.CategoryMapper;
import io.github.jameshome.galley.modules.material.infrastructure.MaterialDO;
import io.github.jameshome.galley.modules.material.infrastructure.MaterialListRow;
import io.github.jameshome.galley.modules.material.infrastructure.MaterialMapper;
import io.github.jameshome.galley.modules.material.infrastructure.UnitDO;
import io.github.jameshome.galley.modules.material.infrastructure.UnitMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 物料主数据。软删、状态机、乐观锁都在这一层收口，Controller 不做业务判断。 */
@Service
public class MaterialService {

    /** 排序白名单：前端只能选这些字段，方向另算。白名单之外一律回落到名称升序。 */
    private static final Map<String, String> SORT_COLUMNS =
            Map.of(
                    "name", "m.name",
                    "code", "m.code",
                    "safetyStock", "m.safety_stock",
                    "currentStock", "current_stock",
                    "updatedAt", "m.updated_at");

    private static final int MAX_PAGE_SIZE = 100;

    private final MaterialMapper mapper;
    private final CategoryMapper categoryMapper;
    private final UnitMapper unitMapper;
    private final ObjectStorage storage;

    public MaterialService(
            MaterialMapper mapper,
            CategoryMapper categoryMapper,
            UnitMapper unitMapper,
            ObjectStorage storage) {
        this.mapper = mapper;
        this.categoryMapper = categoryMapper;
        this.unitMapper = unitMapper;
        this.storage = storage;
    }

    public PageResult<MaterialView> search(
            String keyword,
            UUID categoryId,
            String stockState,
            String status,
            String sort,
            int page,
            int pageSize) {

        int safePage = Math.max(page, 1);
        int safeSize = pageSize <= 0 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        boolean capped = pageSize > MAX_PAGE_SIZE;

        List<MaterialListRow> rows =
                mapper.search(
                        blankToNull(keyword),
                        categoryId,
                        blankToNull(stockState),
                        blankToNull(status),
                        orderBy(sort),
                        safeSize,
                        (safePage - 1) * safeSize);
        long total =
                mapper.countSearch(
                        blankToNull(keyword),
                        categoryId,
                        blankToNull(stockState),
                        blankToNull(status));

        List<MaterialView> items = rows.stream().map(this::toView).toList();
        return new PageResult<>(items, safePage, safeSize, total, capped);
    }

    public MaterialView detail(UUID id) {
        MaterialListRow row = mapper.findRowById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "物料不存在");
        }
        return toView(row);
    }

    @Transactional
    public MaterialView create(MaterialCreate body) {
        requireUsableCategory(body.categoryId());
        requireUsableUnit(body.unitId());

        Instant now = Instant.now();
        MaterialDO row = new MaterialDO();
        row.setId(UuidV7.generate());
        row.setCode(blankToNull(body.code()));
        row.setName(body.name().trim());
        row.setCategoryId(body.categoryId());
        row.setUnitId(body.unitId());
        row.setSpec(blankToNull(body.spec()));
        row.setSafetyStock(body.safetyStock());
        row.setShelfLifeDays(body.shelfLifeDays());
        row.setImageUrl(blankToNull(body.imageKey()));
        row.setNote(blankToNull(body.note()));
        row.setStatus(MaterialStatus.ACTIVE.dbValue());
        row.setVersion(0);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);

        try {
            mapper.insert(row);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_KEY, "自编码已被占用", "code");
        }
        return detail(row.getId());
    }

    /**
     * 局部更新。
     *
     * <p>{@code version} 用于乐观锁：调用方不传时取当前值，等于"读到什么就改什么"， 并发下的第二个请求会拿到 409 而不是默默覆盖。
     */
    @Transactional
    public MaterialView update(UUID id, MaterialPatch body) {
        MaterialDO row = require(id);
        MaterialStatus current = MaterialStatus.fromDb(row.getStatus());

        if (body.status() != null && !current.canTransitionTo(body.status())) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "状态不能从 " + current.dbValue() + " 变为 " + body.status().dbValue());
        }
        if (current == MaterialStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "已归档的物料不可修改");
        }

        if (body.categoryId() != null) {
            requireUsableCategory(body.categoryId());
            row.setCategoryId(body.categoryId());
        }
        if (body.unitId() != null) {
            requireUsableUnit(body.unitId());
            row.setUnitId(body.unitId());
        }
        if (body.name() != null && !body.name().isBlank()) {
            row.setName(body.name().trim());
        }
        if (body.code() != null) {
            row.setCode(blankToNull(body.code()));
        }
        if (body.spec() != null) {
            row.setSpec(blankToNull(body.spec()));
        }
        if (body.safetyStock() != null) {
            row.setSafetyStock(body.safetyStock());
        }
        if (body.shelfLifeDays() != null) {
            row.setShelfLifeDays(body.shelfLifeDays());
        }
        if (body.imageKey() != null) {
            row.setImageUrl(blankToNull(body.imageKey()));
        }
        if (body.note() != null) {
            row.setNote(blankToNull(body.note()));
        }
        if (body.status() != null) {
            row.setStatus(body.status().dbValue());
        }

        row.setVersion(body.version() == null ? row.getVersion() : body.version());
        row.setUpdatedAt(Instant.now());

        int affected;
        try {
            affected = mapper.updateById(row);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_KEY, "自编码已被占用", "code");
        }
        if (affected == 0) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "该物料已被他人修改，请刷新后重试");
        }
        return detail(id);
    }

    /**
     * 软删。
     *
     * <p>有流水的物料不允许删除：删掉它会让历史流水指向不存在的物料，结存与统计都会失真。 这种情况应当"停用"而非删除。
     */
    @Transactional
    public void softDelete(UUID id) {
        MaterialDO row = require(id);
        long movements = mapper.countMovements(id);
        if (movements > 0) {
            throw new BusinessException(
                    ErrorCode.MATERIAL_HAS_MOVEMENTS, "该物料已有 " + movements + " 条流水，不能删除，可改为停用或归档");
        }
        row.setDeletedAt(Instant.now());
        row.setUpdatedAt(Instant.now());
        row.setVersion(row.getVersion());
        if (mapper.updateById(row) == 0) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT, "该物料已被他人修改，请刷新后重试");
        }
    }

    private MaterialDO require(UUID id) {
        MaterialDO row = mapper.selectById(id);
        if (row == null || row.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "物料不存在");
        }
        return row;
    }

    private void requireUsableCategory(UUID id) {
        CategoryDO row = categoryMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "分类不存在", "categoryId");
        }
        if (!DictStatus.ACTIVE.dbValue().equals(row.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "分类已停用，不能选用", "categoryId");
        }
    }

    private void requireUsableUnit(UUID id) {
        UnitDO row = unitMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "单位不存在", "unitId");
        }
        if (!DictStatus.ACTIVE.dbValue().equals(row.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "单位已停用，不能选用", "unitId");
        }
    }

    private MaterialView toView(MaterialListRow row) {
        String key = row.getImageUrl();
        return new MaterialView(
                row.getId(),
                row.getCode(),
                row.getName(),
                row.getCategoryId(),
                row.getCategoryName(),
                row.getUnitId(),
                row.getUnitName(),
                row.getSpec(),
                row.getSafetyStock(),
                row.getShelfLifeDays(),
                key,
                key == null || key.isBlank() ? null : storage.publicUrl(key),
                row.getNote(),
                row.getStatus(),
                row.getVersion() == null ? 0 : row.getVersion(),
                row.getCurrentStock() == null ? BigDecimal.ZERO : row.getCurrentStock());
    }

    /** 排序串形如 {@code name}、{@code -updatedAt} 或 {@code safetyStock,desc}。 */
    static String orderBy(String sort) {
        if (sort == null || sort.isBlank()) {
            return "m.name ASC, m.id ASC";
        }
        String[] parts = sort.split(",");
        String key = parts[0].trim();
        boolean desc = key.startsWith("-");
        if (desc) {
            key = key.substring(1);
        }
        if (parts.length > 1) {
            desc = "desc".equalsIgnoreCase(parts[1].trim());
        }
        String column = SORT_COLUMNS.getOrDefault(key, "m.name");
        // 追加 id 作次级排序：同名物料在分页边界上不会重复出现或漏掉
        return column + (desc ? " DESC" : " ASC") + ", m.id ASC";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
