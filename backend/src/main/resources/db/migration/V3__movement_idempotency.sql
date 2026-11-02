-- 登记幂等键（DES-002 §2 的 Idempotency-Key）。
--
-- 移动端网络抖动时，客户端重发同一次登记会导致重复记账——这是库存系统里最难人工发现的错误之一：
-- 数量翻倍、流水多一条，且两条都"合法"。用客户端提供的键做唯一约束，重复请求返回首次结果。
--
-- 只对非空键建唯一索引：未传键的调用不做幂等（显式选择"接受可能重复"）。

ALTER TABLE stock_movement ADD COLUMN idempotency_key varchar(64);

CREATE UNIQUE INDEX uk_movement_idempotency
    ON stock_movement (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

COMMENT ON COLUMN stock_movement.idempotency_key IS '客户端幂等键；同键重复提交返回首次结果';
