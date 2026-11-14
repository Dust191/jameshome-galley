package io.github.jameshome.galley.modules.stats.infrastructure;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 统计查询。全部为只读聚合，口径逐条对应 DES-004 §3。
 *
 * <p>每条 SQL 都显式写了"只统计 active 且未软删的物料"——统计不写默认值， 让每处口径在 SQL 里可见，比集中在一个视图里更容易核对。
 */
@Mapper
public interface StatsMapper {

    /** M-1 在管物料种类数。 */
    @Select("SELECT count(*) FROM material WHERE status = 'active' AND deleted_at IS NULL")
    long countActiveMaterials();

    /**
     * M-1 / M-2 / M-3 / M-7 / M-8 合并为一次查询。
     *
     * <p>五项指标同源于「在管物料」这一个集合，原先分五次查会把同一张表扫五遍； 5 万物料、200 万流水下这是总览的主要 CPU 开销（`ITER-20261114-10`）。每个
     * {@code FILTER} 的条件与原先单条 SQL 的 WHERE 逐字对应，改口径时两处必须同步，`DES-004` §3 是唯一依据。
     */
    @Select(
            """
            SELECT count(*) AS materials,
                   count(*) FILTER (WHERE coalesce(b.quantity, 0) < m.safety_stock) AS low,
                   count(*) FILTER (WHERE coalesce(b.quantity, 0) = 0) AS zero,
                   count(*) FILTER (WHERE NOT EXISTS (
                       SELECT 1 FROM stock_movement sm
                       WHERE sm.material_id = m.id
                         AND sm.type = 'OUT' AND sm.reversed = false
                         AND sm.occurred_at > now() - interval '90 days')) AS stale,
                   count(*) FILTER (
                       WHERE m.spec IS NOT NULL AND m.spec <> '' AND m.safety_stock > 0) AS complete
            FROM material m
            LEFT JOIN stock_balance b ON b.material_id = m.id
            WHERE m.status = 'active' AND m.deleted_at IS NULL
            """)
    OverviewCountsRow overviewCounts();

    /** M-4 分类分布：停用分类若仍有物料，单列一行由调用方加后缀。 */
    @Select(
            """
            SELECT c.name AS category_name,
                   (c.status <> 'active') AS inactive,
                   count(m.id) AS material_count
            FROM material m
            JOIN material_category c ON c.id = m.category_id
            WHERE m.status = 'active' AND m.deleted_at IS NULL
            GROUP BY c.id, c.name, c.status
            ORDER BY count(m.id) DESC, c.name ASC
            """)
    List<CategoryRow> categoryDistribution();

    /**
     * M-5 近 30 天出入库笔数。
     *
     * <p>用 {@code generate_series} 生成完整日期序列再左连流水，保证缺日为 0 而不是缺行—— 补齐放在 SQL 里，避免 Java
     * 端再写一遍"哪些日子没有数据"的逻辑。
     *
     * <p>有效流水口径：排除 {@code REVERSAL}，排除 {@code reversed = true} 的原流水（DES-004 §4）。
     */
    @Select(
            """
            WITH days AS (
              SELECT (date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai')
                      - (n || ' day')::interval)::date AS day
              FROM generate_series(0, 29) AS n
            )
            SELECT d.day AS day,
                   count(sm.id) FILTER (WHERE sm.type = 'IN')  AS in_count,
                   count(sm.id) FILTER (WHERE sm.type = 'OUT') AS out_count
            FROM days d
            LEFT JOIN stock_movement sm
              ON (sm.occurred_at AT TIME ZONE 'Asia/Shanghai')::date = d.day
             AND sm.type IN ('IN', 'OUT')
             AND sm.reversed = false
            GROUP BY d.day
            ORDER BY d.day ASC
            """)
    List<TrendRow> trend();

    /** M-6 近 30 天出库频次 Top10；并列按名称升序保证输出稳定。 */
    @Select(
            """
            SELECT m.name AS material_name,
                   u.name AS unit_name,
                   count(*) AS out_count,
                   sum(sm.quantity) AS total_quantity
            FROM stock_movement sm
            JOIN material m ON m.id = sm.material_id
            JOIN unit u ON u.id = m.unit_id
            WHERE sm.type = 'OUT' AND sm.reversed = false
              AND sm.occurred_at > now() - interval '30 days'
            GROUP BY m.id, m.name, u.name
            ORDER BY count(*) DESC, m.name ASC
            LIMIT #{limit}
            """)
    List<TopOutRow> topOut(@Param("limit") int limit);
}
