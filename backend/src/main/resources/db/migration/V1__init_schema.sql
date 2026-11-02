-- V1 基线：DES-001 八表模型
-- 约定：主键由应用侧生成 UUID v7；gen_random_uuid() 仅作兜底。
-- 数量统一 numeric(14,3)，禁止浮点；时间统一 timestamptz 存 UTC。

-- ---------------------------------------------------------------- 账号与邀请码

CREATE TABLE app_user (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    username              varchar(32)  NOT NULL,
    display_name          varchar(50)  NOT NULL,
    password_hash         varchar(100) NOT NULL,
    role                  varchar(16)  NOT NULL,
    status                varchar(16)  NOT NULL DEFAULT 'active',
    must_change_password  boolean      NOT NULL DEFAULT false,
    password_updated_at   timestamptz,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    created_by            uuid,
    updated_by            uuid,
    CONSTRAINT uq_app_user_username UNIQUE (username),
    CONSTRAINT ck_app_user_role   CHECK (role   IN ('admin', 'editor', 'viewer')),
    CONSTRAINT ck_app_user_status CHECK (status IN ('active', 'disabled')),
    CONSTRAINT ck_app_user_username_format CHECK (username ~ '^[a-z0-9_]{3,32}$')
);

CREATE TABLE invite_code (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code        varchar(8) NOT NULL,
    status      varchar(16) NOT NULL DEFAULT 'unused',
    created_by  uuid NOT NULL REFERENCES app_user (id),
    used_by     uuid REFERENCES app_user (id),
    used_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_invite_code_code UNIQUE (code),
    CONSTRAINT ck_invite_code_status CHECK (status IN ('unused', 'used', 'disabled')),
    -- 已使用必须有使用者与时间；未使用必须都没有
    CONSTRAINT ck_invite_code_used CHECK (
        (status = 'used' AND used_by IS NOT NULL AND used_at IS NOT NULL)
        OR (status <> 'used' AND used_by IS NULL AND used_at IS NULL)
    )
);

-- ---------------------------------------------------------------- 主数据

CREATE TABLE material_category (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name        varchar(50) NOT NULL,
    parent_id   uuid REFERENCES material_category (id),
    sort_order  smallint NOT NULL DEFAULT 0,
    status      varchar(16) NOT NULL DEFAULT 'active',
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_by  uuid,
    CONSTRAINT ck_material_category_status CHECK (status IN ('active', 'inactive'))
);

-- parent_id 可为空，普通 UNIQUE 会把 NULL 视作互不相等，故用 coalesce 归一
CREATE UNIQUE INDEX uq_material_category_parent_name
    ON material_category (coalesce(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), name);

CREATE TABLE unit (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name        varchar(20) NOT NULL,
    sort_order  smallint NOT NULL DEFAULT 0,
    status      varchar(16) NOT NULL DEFAULT 'active',
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_by  uuid,
    CONSTRAINT uq_unit_name UNIQUE (name),
    CONSTRAINT ck_unit_status CHECK (status IN ('active', 'inactive'))
);

CREATE TABLE material (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code             varchar(32),
    name             varchar(80) NOT NULL,
    category_id      uuid NOT NULL REFERENCES material_category (id),
    unit_id          uuid NOT NULL REFERENCES unit (id),
    spec             varchar(50),
    safety_stock     numeric(14,3) NOT NULL DEFAULT 0,
    shelf_life_days  integer,
    image_url        varchar(255),
    note             varchar(200),
    status           varchar(16) NOT NULL DEFAULT 'active',
    version          integer NOT NULL DEFAULT 0,
    deleted_at       timestamptz,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    created_by       uuid,
    updated_by       uuid,
    CONSTRAINT ck_material_status       CHECK (status IN ('active', 'inactive', 'archived')),
    CONSTRAINT ck_material_safety_stock CHECK (safety_stock >= 0),
    CONSTRAINT ck_material_shelf_life   CHECK (shelf_life_days IS NULL OR shelf_life_days > 0)
);

-- 编码唯一，但软删行不占用编码
CREATE UNIQUE INDEX uq_material_code ON material (code)
    WHERE code IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX idx_material_category_status ON material (category_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_material_name ON material (name);
CREATE INDEX idx_material_unit ON material (unit_id);

-- ---------------------------------------------------------------- 流水与结存

CREATE TABLE stock_movement (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    material_id   uuid NOT NULL REFERENCES material (id),
    type          varchar(16) NOT NULL,
    quantity      numeric(14,3) NOT NULL,
    occurred_at   timestamptz NOT NULL,
    operator_id   uuid NOT NULL REFERENCES app_user (id),
    note          varchar(200),
    reversed      boolean NOT NULL DEFAULT false,
    reversal_of   uuid REFERENCES stock_movement (id),
    created_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    CONSTRAINT ck_movement_type     CHECK (type IN ('IN', 'OUT', 'ADJUST', 'REVERSAL')),
    CONSTRAINT ck_movement_quantity CHECK (quantity <> 0),
    -- 符号与类型必须一致：入库为正、出库为负，调整可正可负
    CONSTRAINT ck_movement_sign CHECK (
        (type = 'IN'  AND quantity > 0)
        OR (type = 'OUT' AND quantity < 0)
        OR (type = 'ADJUST')
        OR (type = 'REVERSAL')
    ),
    -- 红冲必须指向原流水，其它类型不得带 reversal_of
    CONSTRAINT ck_movement_reversal_ref CHECK (
        (type = 'REVERSAL' AND reversal_of IS NOT NULL)
        OR (type <> 'REVERSAL' AND reversal_of IS NULL)
    )
);

-- 一条原流水最多被红冲一次
CREATE UNIQUE INDEX uq_movement_reversal_of ON stock_movement (reversal_of)
    WHERE reversal_of IS NOT NULL;

CREATE INDEX idx_movement_material_time ON stock_movement (material_id, occurred_at DESC);
CREATE INDEX idx_movement_time ON stock_movement (occurred_at);

CREATE TABLE stock_balance (
    material_id       uuid PRIMARY KEY REFERENCES material (id),
    quantity          numeric(14,3) NOT NULL DEFAULT 0,
    last_movement_at  timestamptz,
    version           integer NOT NULL DEFAULT 0,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);

-- 零库存与负库存查询（预警清单高频路径）
CREATE INDEX idx_balance_nonpositive ON stock_balance (quantity) WHERE quantity <= 0;

-- ---------------------------------------------------------------- 审计

CREATE TABLE audit_log (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    uuid REFERENCES app_user (id),
    action      varchar(32) NOT NULL,
    entity      varchar(32) NOT NULL,
    entity_id   uuid NOT NULL,
    before      jsonb,
    after       jsonb,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_entity ON audit_log (entity, entity_id, created_at DESC);

-- ---------------------------------------------------------------- 不变式触发器

-- 流水只增不改：update 仅允许改 reversed；delete 一律拒绝（I-1、I-2）
CREATE OR REPLACE FUNCTION fn_movement_immutable() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION '流水不可删除（DES-001 I-2）' USING ERRCODE = '23514';
    END IF;

    IF NEW.quantity    <> OLD.quantity
       OR NEW.type        <> OLD.type
       OR NEW.material_id <> OLD.material_id
       OR NEW.occurred_at <> OLD.occurred_at
       OR NEW.reversal_of IS DISTINCT FROM OLD.reversal_of THEN
        RAISE EXCEPTION '流水的数量/类型/物料/发生时间不可修改，修正须走红冲（DES-001 I-1）'
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_movement_immutable
    BEFORE UPDATE OR DELETE ON stock_movement
    FOR EACH ROW EXECUTE FUNCTION fn_movement_immutable();

-- 红冲符号校验：红冲数量必须等于原流水相反数（I-4）
CREATE OR REPLACE FUNCTION fn_movement_reversal_sign() RETURNS trigger AS $$
DECLARE
    orig_qty    numeric(14,3);
    orig_reversed boolean;
BEGIN
    IF NEW.type <> 'REVERSAL' THEN
        RETURN NEW;
    END IF;

    SELECT quantity, reversed INTO orig_qty, orig_reversed
    FROM stock_movement WHERE id = NEW.reversal_of;

    IF NOT FOUND THEN
        RAISE EXCEPTION '红冲指向的原流水不存在' USING ERRCODE = '23503';
    END IF;

    IF NEW.quantity <> -orig_qty THEN
        RAISE EXCEPTION '红冲数量 % 必须等于原流水相反数 %', NEW.quantity, -orig_qty
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_movement_reversal_sign
    BEFORE INSERT ON stock_movement
    FOR EACH ROW EXECUTE FUNCTION fn_movement_reversal_sign();
