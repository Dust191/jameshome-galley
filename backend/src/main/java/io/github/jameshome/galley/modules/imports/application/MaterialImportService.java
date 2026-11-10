package io.github.jameshome.galley.modules.imports.application;

import io.github.jameshome.galley.common.audit.AuditService;
import io.github.jameshome.galley.common.error.BusinessException;
import io.github.jameshome.galley.common.error.ErrorCode;
import io.github.jameshome.galley.common.id.UuidV7;
import io.github.jameshome.galley.modules.identity.application.CurrentUserService;
import io.github.jameshome.galley.modules.material.domain.MaterialStatus;
import io.github.jameshome.galley.modules.material.infrastructure.CategoryDO;
import io.github.jameshome.galley.modules.material.infrastructure.CategoryMapper;
import io.github.jameshome.galley.modules.material.infrastructure.MaterialDO;
import io.github.jameshome.galley.modules.material.infrastructure.MaterialMapper;
import io.github.jameshome.galley.modules.material.infrastructure.UnitDO;
import io.github.jameshome.galley.modules.material.infrastructure.UnitMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 物料批量导入。
 *
 * <p>采取 <b>全量校验 → 全部通过才写库</b> 的策略：任何一行有问题就整体不写，返回全部错误行。
 * 理由是一份有错的数据导进去一半，比完全没导更麻烦——用户改完再导一次时，已写入的部分会撞唯一键， 于是要在"哪些导过、哪些没导"之间人工比对。
 *
 * <p>分类与单位按名称精确匹配，<b>不自动创建</b>：拼错一个分类名就静默造出一个新分类， 比直接报错更难发现。
 */
@Service
public class MaterialImportService {

    /** 单次导入上限。导入是同步请求，行数太多会让 HTTP 超时。 */
    private static final int MAX_ROWS = 5000;

    private static final int EXPECTED_COLUMNS = 10;

    private final MaterialMapper materialMapper;
    private final CategoryMapper categoryMapper;
    private final UnitMapper unitMapper;
    private final AuditService audit;
    private final CurrentUserService currentUser;

    public MaterialImportService(
            MaterialMapper materialMapper,
            CategoryMapper categoryMapper,
            UnitMapper unitMapper,
            AuditService audit,
            CurrentUserService currentUser) {
        this.materialMapper = materialMapper;
        this.categoryMapper = categoryMapper;
        this.unitMapper = unitMapper;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Transactional
    public ImportResult importMaterials(byte[] content) {
        List<List<String>> rows = CsvParser.parse(new String(content, StandardCharsets.UTF_8));
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "文件为空");
        }

        UUID actorId = currentUser.requireId();
        UUID batchId = UuidV7.generate();
        int start = isHeader(rows.get(0)) ? 1 : 0;
        int total = rows.size() - start;
        if (total <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "文件只有表头，没有数据行");
        }
        if (total > MAX_ROWS) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED, "单次最多导入 " + MAX_ROWS + " 行，当前 " + total + " 行");
        }

        // 分类与单位通常只有几十条，一次全查在内存建索引比逐行回查更省也更快
        Map<String, UUID> categories = new HashMap<>();
        for (CategoryDO c : categoryMapper.selectList(null)) {
            categories.put(c.getName(), c.getId());
        }
        Map<String, UUID> units = new HashMap<>();
        for (UnitDO u : unitMapper.selectList(null)) {
            units.put(u.getName(), u.getId());
        }

        List<RowError> errors = new ArrayList<>();
        List<MaterialDO> pending = new ArrayList<>();
        Set<String> codesInFile = new HashSet<>();

        for (int i = start; i < rows.size(); i++) {
            int lineNo = i + 1;
            List<String> row = rows.get(i);

            String code = CsvParser.cell(row, 0);
            String name = CsvParser.cell(row, 1);
            String categoryName = CsvParser.cell(row, 2);
            String unitName = CsvParser.cell(row, 3);
            String spec = CsvParser.cell(row, 4);
            String safetyStockRaw = CsvParser.cell(row, 5);
            String shelfLifeRaw = CsvParser.cell(row, 6);
            String note = CsvParser.cell(row, 9);

            if (name.isEmpty()) {
                errors.add(new RowError(lineNo, "物料名", "不能为空", describe(row)));
                continue;
            }
            UUID categoryId = categories.get(categoryName);
            if (categoryId == null) {
                errors.add(
                        new RowError(
                                lineNo,
                                "分类",
                                categoryName.isEmpty() ? "不能为空" : "「" + categoryName + "」不存在",
                                describe(row)));
                continue;
            }
            UUID unitId = units.get(unitName);
            if (unitId == null) {
                errors.add(
                        new RowError(
                                lineNo,
                                "单位",
                                unitName.isEmpty() ? "不能为空" : "「" + unitName + "」不存在",
                                describe(row)));
                continue;
            }

            BigDecimal safetyStock;
            try {
                safetyStock =
                        safetyStockRaw.isEmpty() ? BigDecimal.ZERO : new BigDecimal(safetyStockRaw);
            } catch (NumberFormatException ex) {
                errors.add(new RowError(lineNo, "安全库存", "不是合法数字：" + safetyStockRaw, describe(row)));
                continue;
            }
            if (safetyStock.signum() < 0) {
                errors.add(new RowError(lineNo, "安全库存", "不能为负数", describe(row)));
                continue;
            }
            if (safetyStock.stripTrailingZeros().scale() > 3) {
                errors.add(new RowError(lineNo, "安全库存", "最多三位小数", describe(row)));
                continue;
            }

            Integer shelfLife = null;
            if (!shelfLifeRaw.isEmpty()) {
                try {
                    shelfLife = Integer.valueOf(shelfLifeRaw);
                    if (shelfLife <= 0) {
                        errors.add(new RowError(lineNo, "保质期", "须大于 0 天", describe(row)));
                        continue;
                    }
                } catch (NumberFormatException ex) {
                    errors.add(new RowError(lineNo, "保质期", "不是整数：" + shelfLifeRaw, describe(row)));
                    continue;
                }
            }

            if (!code.isEmpty() && !codesInFile.add(code)) {
                errors.add(new RowError(lineNo, "自编码", "文件内重复：" + code, describe(row)));
                continue;
            }

            Instant now = Instant.now();
            MaterialDO entity = new MaterialDO();
            entity.setId(UuidV7.generate());
            entity.setCreatedBy(actorId);
            entity.setUpdatedBy(actorId);
            entity.setCode(code.isEmpty() ? null : code);
            entity.setName(name);
            entity.setCategoryId(categoryId);
            entity.setUnitId(unitId);
            entity.setSpec(spec.isEmpty() ? null : spec);
            entity.setSafetyStock(safetyStock);
            entity.setShelfLifeDays(shelfLife);
            entity.setNote(note.isEmpty() ? null : note);
            entity.setStatus(MaterialStatus.ACTIVE.dbValue());
            entity.setVersion(0);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            pending.add(entity);
        }

        if (!errors.isEmpty()) {
            return new ImportResult(total, 0, errors);
        }

        try {
            for (MaterialDO entity : pending) {
                materialMapper.insert(entity);
            }
        } catch (DuplicateKeyException ex) {
            // 走到这里说明自编码与库里已有的冲突；整体回滚，让用户改完重导
            throw new BusinessException(ErrorCode.DUPLICATE_KEY, "自编码与已有物料冲突，请检查后重新导入", "code");
        }

        // entityId 存批次 id：一批导入了多少条在 after 里，行级明细以物料自己的 CREATE 之外由批次号关联
        audit.record(
                actorId,
                "IMPORT",
                "material",
                batchId,
                null,
                audit.snapshot(
                        Map.of(
                                "total",
                                total,
                                "created",
                                pending.size(),
                                "batch",
                                batchId.toString())));
        return new ImportResult(total, pending.size(), List.of());
    }

    private static boolean isHeader(List<String> first) {
        return !first.isEmpty() && CsvParser.cell(first, 0).contains("自编码");
    }

    private static String describe(List<String> row) {
        return String.join(",", row);
    }

    /** 逐行错误。{@code line} 是文件里的物理行号（含表头），用户照着 Excel 的行号就能定位。 */
    public record RowError(int line, String field, String message, String raw) {}

    /** 导入结果；{@code created} 为 0 且 {@code errors} 非空表示整批未写入。 */
    public record ImportResult(int total, int created, List<RowError> errors) {}
}
