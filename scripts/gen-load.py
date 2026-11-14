#!/usr/bin/env python3
"""为 staging 造压测数据：5 万物料 / 200 万流水（DEV-009 §7.6）。

与 `gen-seed.py` 的分工：那个造小样本给开发与测试用，这个造目标量级给性能实测用。

设计要点：

- **期初结存是流水，不是结存表**。每个物料在基准日（默认 2026-01-01）有一条
  `ADJUST` 流水作为建账，结存由全量流水求和推出（红线 7：结存不得直接改）。
- 账期从基准日到今天，流水按 IN 六成、OUT 三成半、ADJUST 半成分布；符号与类型
  必须符合 `ck_movement_sign`，否则导入会被约束拒绝。
- 产出 TSV 用 `psql \\copy` 导入，不走接口：200 万条走 API 不是一个晚上的事。
- 随机种子固定，同样参数重复执行结果一致；需要不同数据就换 `--seed`。

用法见 `docs/03-dev/testing.md` §4。
"""

from __future__ import annotations

import argparse
import random
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

CATEGORIES = ["米面", "油料", "调味", "干货", "罐头", "冷冻", "蔬果", "清洁"]
UNITS = ["袋", "桶", "瓶", "箱", "包", "斤"]
STEMS = [
    "大米", "面粉", "糯米", "小米", "玉米面", "挂面", "意面", "燕麦",
    "花生油", "菜籽油", "大豆油", "橄榄油", "芝麻油", "猪油", "椰子油",
    "食盐", "白糖", "红糖", "酱油", "陈醋", "料酒", "蚝油", "豆瓣酱",
    "木耳", "香菇", "黄花菜", "粉丝", "腐竹", "紫菜", "虾皮", "红枣",
    "番茄罐头", "午餐肉", "玉米罐头", "蘑菇罐头", "黄桃罐头",
    "速冻水饺", "速冻汤圆", "冷冻鸡翅", "冷冻鱼片",
    "土豆", "白菜", "胡萝卜", "洋葱", "生姜",
]
SPECS = ["500g/袋", "1kg/袋", "5kg/袋", "25kg/袋", "500ml/瓶", "5L/桶", "10kg/箱", ""]
NOTES = ["", "", "", "常用", "备用", "节日采购"]

TZ = timezone(timedelta(hours=8))


def ts(moment: datetime) -> str:
    return moment.strftime("%Y-%m-%d %H:%M:%S+08")


def main() -> None:
    parser = argparse.ArgumentParser(description="生成压测数据（TSV）")
    parser.add_argument("--materials", type=int, default=50000)
    parser.add_argument("--movements", type=int, default=2000000)
    parser.add_argument("--base-date", default="2026-01-01", help="期初结存基准日，YYYY-MM-DD")
    parser.add_argument("--end-date", default="2026-11-13", help="账期截止日，YYYY-MM-DD")
    parser.add_argument("--operator", required=True, help="operator_id，取 staging 库 app_user.id")
    parser.add_argument("--out", default="./load-data", help="输出目录")
    parser.add_argument("--seed", type=int, default=20261114)
    args = parser.parse_args()

    rng = random.Random(args.seed)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    base = datetime.strptime(args.base_date, "%Y-%m-%d").replace(tzinfo=TZ)
    end = datetime.strptime(args.end_date, "%Y-%m-%d").replace(tzinfo=TZ)
    span_seconds = int((end - base).total_seconds())
    if span_seconds <= 0:
        raise SystemExit("截止日必须晚于基准日")

    # 分类与单位：固定 id，供物料引用
    categories = [(str(uuid.UUID(int=rng.getrandbits(128), version=4)), name, i)
                  for i, name in enumerate(CATEGORIES)]
    units = [(str(uuid.UUID(int=rng.getrandbits(128), version=4)), name, i)
             for i, name in enumerate(UNITS)]

    with (out / "category.tsv").open("w", encoding="utf-8") as fh:
        for cid, name, order in categories:
            fh.write(f"{cid}\t{name}\t{order}\tactive\t{ts(base)}\t{ts(base)}\n")

    with (out / "unit.tsv").open("w", encoding="utf-8") as fh:
        for uid, name, order in units:
            fh.write(f"{uid}\t{name}\t{order}\tactive\t{ts(base)}\t{ts(base)}\n")

    # 物料：编码唯一，名称带序号避免全同名
    material_ids: list[str] = []
    with (out / "material.tsv").open("w", encoding="utf-8") as fh:
        for i in range(args.materials):
            mid = str(uuid.UUID(int=rng.getrandbits(128), version=4))
            material_ids.append(mid)
            code = f"M{i + 1:05d}"
            name = f"{STEMS[i % len(STEMS)]}-{i + 1:05d}"
            cid = categories[i % len(categories)][0]
            uid = units[i % len(units)][0]
            spec = SPECS[i % len(SPECS)]
            safety = rng.randint(0, 50) * 1.0
            fh.write(
                f"{mid}\t{code}\t{name}\t{cid}\t{uid}\t{spec or chr(92) + 'N'}"
                f"\t{safety:.3f}\tactive\t0\t{ts(base)}\t{ts(base)}\t{args.operator}\n"
            )

    # 流水：首日一条期初 ADJUST，其余随机；IN 正、OUT 负、ADJUST 可正可负
    opening = max(args.materials, min(args.movements // 10, args.materials))
    remaining = args.movements - opening
    if remaining < 0:
        raise SystemExit("流水条数少于物料数，无法给每个物料建期初")

    with (out / "movement.tsv").open("w", encoding="utf-8") as fh:
        for i in range(opening):
            mid = material_ids[i % len(material_ids)]
            mid_qty = f"{rng.randint(50, 5000) / 10:.3f}"
            fh.write(
                f"{uuid.uuid4()}\t{mid}\tADJUST\t{mid_qty}\t{ts(base)}\t{args.operator}"
                f"\t\tfalse\t{ts(base)}\t{args.operator}\n"
            )

        for _ in range(remaining):
            mid = material_ids[rng.randrange(len(material_ids))]
            moment = base + timedelta(seconds=rng.randrange(span_seconds))
            roll = rng.random()
            if roll < 0.60:
                kind, qty = "IN", rng.randint(1, 500) / 10
            elif roll < 0.95:
                kind, qty = "OUT", -rng.randint(1, 300) / 10
            else:
                kind = "ADJUST"
                qty = rng.choice([-1, 1]) * rng.randint(1, 200) / 10
            note = NOTES[rng.randrange(len(NOTES))]
            fh.write(
                f"{uuid.uuid4()}\t{mid}\t{kind}\t{qty:.3f}\t{ts(moment)}\t{args.operator}"
                f"\t{note}\tfalse\t{ts(moment)}\t{args.operator}\n"
            )

    print(f"已生成到 {out.resolve()}")
    print(f"  分类 {len(categories)} / 单位 {len(units)} / 物料 {args.materials} / 流水 {args.movements}")
    print(f"  期初 {opening} 条（{args.base_date}），日常 {remaining} 条（{args.base_date} ~ {args.end_date}）")


if __name__ == "__main__":
    main()
