package io.github.jameshome.galley.modules.stats.application;

import io.github.jameshome.galley.common.web.PageResult;
import io.github.jameshome.galley.modules.stats.api.dto.StatsDtos.AlertItem;
import io.github.jameshome.galley.modules.stats.api.dto.StatsDtos.AlertList;
import io.github.jameshome.galley.modules.stats.api.dto.StatsDtos.CategoryShare;
import io.github.jameshome.galley.modules.stats.api.dto.StatsDtos.OverviewView;
import io.github.jameshome.galley.modules.stats.api.dto.StatsDtos.TopOutItem;
import io.github.jameshome.galley.modules.stats.api.dto.StatsDtos.TrendPoint;
import io.github.jameshome.galley.modules.stats.infrastructure.OverviewCountsRow;
import io.github.jameshome.galley.modules.stats.infrastructure.StatsMapper;
import io.github.jameshome.galley.modules.stock.api.dto.StockDtos.BalanceView;
import io.github.jameshome.galley.modules.stock.application.BalanceService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 九项指标的组装（口径见 DES-004）。
 *
 * <p>计算尽量下沉到 SQL，本类只做三件事：把标量拼成响应、把比率算成 4 位小数、 给分类与预警补上人类可读的说明。比率统一用 {@link RoundingMode#HALF_UP}——
 * 与文档里"四舍五入"的字面含义一致，避免银行家舍入带来的对不上账。
 */
@Service
public class StatsService {

    private final StatsMapper mapper;
    private final BalanceService balanceService;

    public StatsService(StatsMapper mapper, BalanceService balanceService) {
        this.mapper = mapper;
        this.balanceService = balanceService;
    }

    public OverviewView overview() {
        // 五项计数一次查出：分五次查会把物料表扫五遍，2 并发就把 2 vCPU 打满
        OverviewCountsRow counts = mapper.overviewCounts();
        long materials = nz(counts.getMaterials());
        return new OverviewView(
                materials,
                nz(counts.getLow()),
                nz(counts.getZero()),
                nz(counts.getStale()),
                rate(nz(counts.getComplete()), materials));
    }

    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    public List<CategoryShare> categoryDistribution() {
        long total = mapper.countActiveMaterials();
        return mapper.categoryDistribution().stream()
                .map(
                        row -> {
                            String name =
                                    Boolean.TRUE.equals(row.getInactive())
                                            ? row.getCategoryName() + "（已停用）"
                                            : row.getCategoryName();
                            long count =
                                    row.getMaterialCount() == null ? 0 : row.getMaterialCount();
                            return new CategoryShare(name, count, rate(count, total));
                        })
                .toList();
    }

    public List<TrendPoint> trend() {
        return mapper.trend().stream()
                .map(
                        row ->
                                new TrendPoint(
                                        row.getDay(),
                                        row.getInCount() == null ? 0 : row.getInCount(),
                                        row.getOutCount() == null ? 0 : row.getOutCount()))
                .toList();
    }

    public List<TopOutItem> topOut(int limit) {
        int safeLimit = limit <= 0 ? 10 : Math.min(limit, 50);
        return mapper.topOut(safeLimit).stream()
                .map(
                        row ->
                                new TopOutItem(
                                        row.getMaterialName(),
                                        row.getUnitName(),
                                        row.getOutCount() == null ? 0 : row.getOutCount(),
                                        row.getTotalQuantity() == null
                                                ? BigDecimal.ZERO
                                                : row.getTotalQuantity()))
                .toList();
    }

    /** M-9 预警清单：直接复用结存的 alert 筛选，保证与预警数同源、不会两处口径打架。 */
    public AlertList alerts(int page, int pageSize) {
        PageResult<BalanceView> pageResult =
                balanceService.search(null, null, "alert", "severity,desc", page, pageSize);
        List<AlertItem> items =
                pageResult.items().stream()
                        .map(
                                v ->
                                        new AlertItem(
                                                v.materialName(),
                                                v.unitName(),
                                                v.quantity().toPlainString(),
                                                v.safetyStock() == null
                                                        ? "0"
                                                        : v.safetyStock().toPlainString(),
                                                v.severity(),
                                                reasonOf(v)))
                        .toList();
        return new AlertList(items, (int) pageResult.total());
    }

    /** 与 M-9 的四个严重度一一对应；0 档正常物料不会出现在清单里。 */
    private static String reasonOf(BalanceView view) {
        return switch (view.severity()) {
            case 3 -> "负库存，账实已不符";
            case 2 -> "断货";
            case 1 -> "低于安全线";
            default -> "呆滞：90 天内无出库";
        };
    }

    /** 比率为 0 到 1 之间的小数，保留 4 位；分母为 0 时返回 0，避免除零。 */
    static String rate(long part, long total) {
        if (total <= 0) {
            return "0.0000";
        }
        return BigDecimal.valueOf(part)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .toPlainString();
    }
}
