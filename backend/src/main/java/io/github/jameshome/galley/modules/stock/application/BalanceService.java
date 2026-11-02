package io.github.jameshome.galley.modules.stock.application;

import io.github.jameshome.galley.common.web.PageResult;
import io.github.jameshome.galley.modules.stock.api.dto.StockDtos.BalanceView;
import io.github.jameshome.galley.modules.stock.infrastructure.BalanceListRow;
import io.github.jameshome.galley.modules.stock.infrastructure.StockBalanceMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 结存查询。只读，不负责写入——写入只允许经 {@link MovementService} 的原子增减。 */
@Service
public class BalanceService {

    /**
     * 严重度表达式，与 DES-004 M-9 逐条对应。
     *
     * <p>写成 SQL 而非 Java，是因为排序要在数据库完成：分页后再算严重度会导致跨页顺序错乱。
     */
    private static final String SEVERITY_SQL =
            "(CASE WHEN b.quantity < 0 THEN 3 WHEN b.quantity = 0 THEN 2"
                    + " WHEN b.quantity < m.safety_stock THEN 1 ELSE 0 END)";

    private static final Map<String, String> SORT_COLUMNS =
            Map.of(
                    "name", "m.name",
                    "quantity", "b.quantity",
                    "lastMovementAt", "b.last_movement_at",
                    "severity", SEVERITY_SQL);

    /** 默认与 M-9 的排序一致：严重度降序 → 结存升序 → 名称升序。 */
    private static final String DEFAULT_ORDER = SEVERITY_SQL + " DESC, b.quantity ASC, m.name ASC";

    private final StockBalanceMapper mapper;

    public BalanceService(StockBalanceMapper mapper) {
        this.mapper = mapper;
    }

    public PageResult<BalanceView> search(
            String keyword,
            UUID categoryId,
            String stockState,
            String sort,
            int page,
            int pageSize) {

        int safePage = Math.max(page, 1);
        int safeSize = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        boolean capped = pageSize > 100;

        String keywordParam = blankToNull(keyword);
        String stateParam = blankToNull(stockState);

        List<BalanceListRow> rows =
                mapper.search(
                        keywordParam,
                        categoryId,
                        stateParam,
                        orderBy(sort),
                        safeSize,
                        (safePage - 1) * safeSize);
        long total = mapper.countSearch(keywordParam, categoryId, stateParam);

        return new PageResult<>(
                rows.stream().map(BalanceService::toView).toList(),
                safePage,
                safeSize,
                total,
                capped);
    }

    /** 严重度（DES-004 M-9）：3 负库存、2 断货、1 低于安全线、0 正常。 */
    static int severityOf(BigDecimal quantity, BigDecimal safetyStock) {
        BigDecimal q = quantity == null ? BigDecimal.ZERO : quantity;
        if (q.signum() < 0) {
            return 3;
        }
        if (q.signum() == 0) {
            return 2;
        }
        BigDecimal safe = safetyStock == null ? BigDecimal.ZERO : safetyStock;
        return safe.signum() > 0 && q.compareTo(safe) < 0 ? 1 : 0;
    }

    static String orderBy(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_ORDER;
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
        String column = SORT_COLUMNS.get(key);
        if (column == null) {
            return DEFAULT_ORDER;
        }
        // 追加 material_id 作末级排序：结存相同的物料在分页边界上不会重复或漏掉
        return column + (desc ? " DESC" : " ASC") + ", b.material_id ASC";
    }

    private static BalanceView toView(BalanceListRow row) {
        BigDecimal quantity = row.getQuantity() == null ? BigDecimal.ZERO : row.getQuantity();
        return new BalanceView(
                row.getMaterialId(),
                row.getMaterialName(),
                row.getCode(),
                row.getSpec(),
                row.getCategoryName(),
                row.getUnitName(),
                quantity,
                row.getSafetyStock(),
                row.getLastMovementAt(),
                severityOf(quantity, row.getSafetyStock()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
