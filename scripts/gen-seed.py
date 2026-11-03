#!/usr/bin/env python3
"""生成种子数据（DEV-003 §3）。

固定随机种子，重复执行结果完全一致。产出两类文件，都在 backend/src/test/resources/seed/：

- *.csv：给人看、给测试读的结构化数据；
- seed.sql：可直接灌入开发库/性能库的完整脚本（含结存汇总）。

账号密码统一 seed-pass-2026，哈希由 PostgreSQL 的 pgcrypto 在导入时生成，
不在仓库里存死哈希——否则改了密码策略还得回来重算。
"""

import csv
import random
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

SEED = 20261103
OUT = Path(__file__).resolve().parent.parent / "backend/src/test/resources/seed"

CATEGORIES = ["米面", "油料", "调味", "干货", "罐头", "冷冻", "蔬果", "清洁"]
UNITS = ["袋", "桶", "瓶", "箱", "包", "斤"]
MATERIAL_NAMES = [
    "大米", "面粉", "糯米", "小米", "玉米面", "挂面", "意面", "燕麦",
    "花生油", "菜籽油", "大豆油", "橄榄油", "芝麻油", "猪油", "椰子油", "玉米油",
    "食盐", "白糖", "红糖", "酱油", "陈醋", "料酒", "蚝油", "豆瓣酱", "花椒", "八角",
    "木耳", "香菇", "黄花菜", "粉丝", "腐竹", "紫菜", "虾皮", "红枣", "枸杞",
    "番茄罐头", "午餐肉", "玉米罐头", "蘑菇罐头", "黄桃罐头",
    "速冻水饺", "速冻汤圆", "冷冻鸡翅", "冷冻鱼片",
    "土豆", "白菜", "胡萝卜", "洋葱", "生姜",
    "洗洁精",
]
SPECS = ["500g/袋", "1kg/袋", "5kg/袋", "25kg/袋", "500ml/瓶", "5L/桶", "10kg/箱", ""]
NOTES = ["", "常用", "备用", "节日采购", "低值易耗"]


def uuid7_like(rng):
    """确定性 UUID（版本位设为 7，与生产主键形态一致）。"""
    value = rng.getrandbits(128)
    value &= ~(0xF << 76)
    value |= 7 << 76
    value &= ~(0x3 << 62)
    value |= 0x2 << 62
    return str(uuid.UUID(int=value))


def main():
    rng = random.Random(SEED)
    OUT.mkdir(parents=True, exist_ok=True)

    categories = [
        {"id": uuid7_like(rng), "name": name, "sort_order": i + 1}
        for i, name in enumerate(CATEGORIES)
    ]
    units = [
        {"id": uuid7_like(rng), "name": name, "sort_order": i + 1}
        for i, name in enumerate(UNITS)
    ]

    materials = []
    for i, name in enumerate(MATERIAL_NAMES):
        category = categories[i % len(categories)]
        unit = units[i % len(units)]
        materials.append(
            {
                "id": uuid7_like(rng),
                "code": f"S{i + 1:03d}",
                "name": f"{name}-{i + 1:02d}",
                "category_id": category["id"],
                "unit_id": unit["id"],
                "spec": rng.choice(SPECS),
                "safety_stock": f"{rng.choice([0, 2, 5, 10, 20, 50])}.000",
                "shelf_life_days": rng.choice(["", "90", "180", "365", "730"]),
                "note": rng.choice(NOTES),
            }
        )

    users = [
        {"id": uuid7_like(rng), "username": "admin", "display_name": "管理员", "role": "admin"},
        {"id": uuid7_like(rng), "username": "editor", "display_name": "录入员", "role": "editor"},
        {"id": uuid7_like(rng), "username": "viewer", "display_name": "查看者", "role": "viewer"},
    ]

    start = datetime(2026, 7, 1, tzinfo=timezone.utc)
    movements = []
    # 475 条常规流水 + 25 条冲正 = 500（D3-6 要求 500 条）
    for i in range(475):
        material = materials[rng.randrange(len(materials))]
        kind = rng.choices(["IN", "OUT", "ADJUST"], weights=[4, 5, 1])[0]
        if kind == "IN":
            quantity = f"{rng.randint(1, 40)}.000"
        elif kind == "OUT":
            quantity = f"-{rng.randint(1, 12)}.000"
        else:
            quantity = f"{rng.choice([-3, -2, -1, 1, 2, 3])}.000"
        occurred = start + timedelta(
            days=rng.randrange(120), hours=rng.randrange(6, 20), minutes=rng.randrange(60)
        )
        movements.append(
            {
                "id": uuid7_like(rng),
                "material_id": material["id"],
                "type": kind,
                "quantity": quantity,
                "occurred_at": occurred.isoformat().replace("+00:00", "Z"),
                "operator_id": users[1]["id"],
                "note": rng.choice(["", "日常领用", "补货", "盘点"]),
                "reversed": "false",
                "reversal_of": "",
            }
        )

    # 每 20 条抽一条做冲正：原流水标记 reversed，并补一条数量相反的 REVERSAL
    reversals = []
    for index in range(0, len(movements), 19):
        original = movements[index]
        quantity = float(original["quantity"])
        if quantity == 0:
            continue
        original["reversed"] = "true"
        occurred = datetime.fromisoformat(
            original["occurred_at"].replace("Z", "+00:00")
        ) + timedelta(hours=2)
        reversals.append(
            {
                "id": uuid7_like(rng),
                "material_id": original["material_id"],
                "type": "REVERSAL",
                "quantity": f"{-quantity:.3f}",
                "occurred_at": occurred.isoformat().replace("+00:00", "Z"),
                "operator_id": users[0]["id"],
                "note": "冲正",
                "reversed": "false",
                "reversal_of": original["id"],
            }
        )
    movements.extend(reversals)

    write_csv("categories.csv", ["id", "name", "sort_order"], categories)
    write_csv("units.csv", ["id", "name", "sort_order"], units)
    write_csv(
        "materials.csv",
        [
            "id", "code", "name", "category_id", "unit_id",
            "spec", "safety_stock", "shelf_life_days", "note",
        ],
        materials,
    )
    write_csv("users.csv", ["id", "username", "display_name", "role"], users)
    write_csv(
        "movements.csv",
        ["id", "material_id", "type", "quantity", "occurred_at",
         "operator_id", "note", "reversed", "reversal_of"],
        movements,
    )
    write_sql(categories, units, materials, users, movements)

    print(f"分类 {len(categories)}，单位 {len(units)}，物料 {len(materials)}，"
          f"账号 {len(users)}，流水 {len(movements)}（其中冲正 {len(reversals)}）")
    print(f"输出目录：{OUT}")


def write_csv(name, header, rows):
    with (OUT / name).open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=header)
        writer.writeheader()
        for row in rows:
            writer.writerow({key: row.get(key, "") for key in header})


def sql_quote(value):
    if value is None or value == "":
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


def write_sql(categories, units, materials, users, movements):
    lines = [
        "-- 由 scripts/gen-seed.py 生成，请勿手工修改；随机种子 20261103。",
        "-- 用途：开发库/性能库的固定种子数据（DEV-003 §3）。",
        "CREATE EXTENSION IF NOT EXISTS pgcrypto;",
        "",
        "-- 清空业务数据，保留表结构与迁移历史",
        "DELETE FROM stock_movement;",
        "DELETE FROM stock_balance;",
        "DELETE FROM audit_log;",
        "DELETE FROM material;",
        "DELETE FROM material_category;",
        "DELETE FROM unit;",
        "DELETE FROM invite_code;",
        "DELETE FROM app_user;",
        "",
        "-- 分类",
    ]
    for row in categories:
        lines.append(
            "INSERT INTO material_category (id, name, parent_id, sort_order, status) VALUES "
            f"({sql_quote(row['id'])}, {sql_quote(row['name'])}, NULL, {row['sort_order']}, 'active');"
        )
    lines.append("")
    lines.append("-- 单位")
    for row in units:
        lines.append(
            "INSERT INTO unit (id, name, sort_order, status) VALUES "
            f"({sql_quote(row['id'])}, {sql_quote(row['name'])}, {row['sort_order']}, 'active');"
        )
    lines.append("")
    lines.append("-- 物料")
    for row in materials:
        lines.append(
            "INSERT INTO material (id, code, name, category_id, unit_id, spec, safety_stock, "
            "shelf_life_days, image_url, note, status, version) VALUES "
            f"({sql_quote(row['id'])}, {sql_quote(row['code'])}, {sql_quote(row['name'])}, "
            f"{sql_quote(row['category_id'])}, {sql_quote(row['unit_id'])}, {sql_quote(row['spec'])}, "
            f"{row['safety_stock']}, {row['shelf_life_days'] or 'NULL'}, NULL, "
            f"{sql_quote(row['note'])}, 'active', 0);"
        )
    lines.append("")
    lines.append("-- 账号：密码统一 seed-pass-2026，哈希在导入时生成")
    for row in users:
        lines.append(
            "INSERT INTO app_user (id, username, display_name, password_hash, role, status, "
            "must_change_password) VALUES "
            f"({sql_quote(row['id'])}, {sql_quote(row['username'])}, {sql_quote(row['display_name'])}, "
            f"crypt('seed-pass-2026', gen_salt('bf', 10)), {sql_quote(row['role'])}, 'active', false);"
        )
    lines.append("")
    lines.append("-- 流水（不可改删，由触发器保证）")
    for row in movements:
        reversal = sql_quote(row["reversal_of"]) if row["reversal_of"] else "NULL"
        lines.append(
            "INSERT INTO stock_movement (id, material_id, type, quantity, occurred_at, operator_id, "
            "note, reversed, reversal_of) VALUES "
            f"({sql_quote(row['id'])}, {sql_quote(row['material_id'])}, {sql_quote(row['type'])}, "
            f"{row['quantity']}, {sql_quote(row['occurred_at'])}, {sql_quote(row['operator_id'])}, "
            f"{sql_quote(row['note'])}, {row['reversed']}, {reversal});"
        )
    lines.append("")
    lines.append("-- 结存：按全部流水求和（含被冲正的原流水与冲正本身）")
    lines.append(
        "INSERT INTO stock_balance (material_id, quantity, last_movement_at, version, created_at, updated_at) "
        "SELECT material_id, sum(quantity), max(occurred_at), 0, now(), now() "
        "FROM stock_movement GROUP BY material_id;"
    )
    (OUT / "seed.sql").write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
