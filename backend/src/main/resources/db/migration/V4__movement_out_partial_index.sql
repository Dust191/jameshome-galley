-- M-7 呆滞物料查询的部分索引（ITER-20261114-08）。
--
-- 原查询对 90 天内全部流水做 Bitmap Heap Scan，再按 type / reversed 过滤；
-- 200 万流水、5 万物料时该查询耗时 2877 ms，占去总览 3 秒里的绝大部分。
-- 只索引「未红冲的出库」后，扫描范围从全部流水缩到出库那部分，
-- 过滤条件下移到索引本身，NOT EXISTS 不必回表判断 type。
--
-- 用 IF NOT EXISTS：staging 已于 2026-11-14 手工建过同名索引用于验证，
-- 下次由 Flyway 执行时跳过创建、只登记版本，不会因「已存在」而失败。

CREATE INDEX IF NOT EXISTS idx_movement_out_by_material
    ON stock_movement (material_id, occurred_at DESC)
    WHERE type = 'OUT' AND reversed = false;
