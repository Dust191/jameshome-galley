package io.github.jameshome.galley.modules.stats.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 统计出参。
 *
 * <p>数量一律以字符串传输（DES-002 §2），避免 JSON 数字在前端变成浮点后丢精度； 比率单独用字符串，保留 4 位小数。
 */
public final class StatsDtos {

    private StatsDtos() {}

    /** 总览的五个标量（M-1、M-2、M-3、M-7、M-8）。 */
    public record OverviewView(
            long materialCount,
            long lowStockCount,
            long zeroStockCount,
            long staleCount,
            String completenessRate) {}

    /** M-4 的一行；{@code ratio} 为占 M-1 的比例，4 位小数。 */
    public record CategoryShare(String categoryName, long materialCount, String ratio) {}

    /** M-5 的一个点，{@code date} 形如 {@code 2026-11-02}。 */
    public record TrendPoint(LocalDate date, long inCount, long outCount) {}

    /** M-6 的一行。{@code totalQuantity} 为负数之和，表示净出库量。 */
    public record TopOutItem(
            String materialName, String unitName, long outCount, BigDecimal totalQuantity) {}

    /** M-9 的响应体，逐条与结存视图一致，另附总数便于前端显示"共 N 条待处理"。 */
    public record AlertList(List<AlertItem> items, int total) {}

    public record AlertItem(
            String materialName,
            String unitName,
            String quantity,
            String safetyStock,
            int severity,
            String reason) {}
}
