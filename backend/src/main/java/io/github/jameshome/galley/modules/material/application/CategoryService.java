package io.github.jameshome.galley.modules.material.application;

import io.github.jameshome.galley.common.error.BusinessException;
import io.github.jameshome.galley.common.error.ErrorCode;
import io.github.jameshome.galley.common.id.UuidV7;
import io.github.jameshome.galley.modules.material.domain.Category;
import io.github.jameshome.galley.modules.material.domain.DictStatus;
import io.github.jameshome.galley.modules.material.infrastructure.CategoryDO;
import io.github.jameshome.galley.modules.material.infrastructure.CategoryMapper;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 分类维护。删除前必须先确认没有物料占用（G3 UAT-8）。 */
@Service
public class CategoryService {

    private final CategoryMapper mapper;

    public CategoryService(CategoryMapper mapper) {
        this.mapper = mapper;
    }

    public List<Category> list(boolean includeInactive) {
        return mapper.selectList(null).stream()
                .filter(r -> includeInactive || DictStatus.ACTIVE.dbValue().equals(r.getStatus()))
                .sorted(
                        Comparator.comparing(CategoryDO::getSortOrder)
                                .thenComparing(CategoryDO::getName))
                .map(CategoryService::toDomain)
                .toList();
    }

    @Transactional
    public Category create(String name, int sortOrder) {
        Instant now = Instant.now();
        CategoryDO row = new CategoryDO();
        row.setId(UuidV7.generate());
        row.setName(name);
        row.setSortOrder((short) sortOrder);
        row.setStatus(DictStatus.ACTIVE.dbValue());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        try {
            mapper.insert(row);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.DUPLICATE_KEY, "分类名已存在", "name");
        }
        return toDomain(row);
    }

    @Transactional
    public Category update(UUID id, String name, Integer sortOrder, DictStatus status) {
        CategoryDO row = require(id);
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
            throw new BusinessException(ErrorCode.DUPLICATE_KEY, "分类名已存在", "name");
        }
        return toDomain(row);
    }

    @Transactional
    public void delete(UUID id) {
        require(id);
        long used = mapper.countMaterials(id);
        if (used > 0) {
            throw new BusinessException(
                    ErrorCode.CATEGORY_IN_USE, "该分类下还有 " + used + " 种物料，不能删除，可改为停用");
        }
        mapper.deleteById(id);
    }

    private CategoryDO require(UUID id) {
        CategoryDO row = mapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分类不存在");
        }
        return row;
    }

    static Category toDomain(CategoryDO row) {
        return new Category(
                row.getId(),
                row.getName(),
                row.getParentId(),
                row.getSortOrder() == null ? 0 : row.getSortOrder(),
                DictStatus.fromDb(row.getStatus()));
    }
}
