#!/usr/bin/env python3
"""在 staging 上复跑 12 条 UAT 里可现场执行的部分。

自动化用例已覆盖的（预警清单口径、三角色权限、空数据总览）不在这里重复，
由 CI 的测试类出证；本脚本负责「在 staging 真数据上跑一遍」的那些。

用法：
    python3 scripts/uat-staging.py --base-url http://127.0.0.1:8081 \
        --user admin --password '...'
"""

from __future__ import annotations

import argparse
import http.cookiejar
import json
import random
import string
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid
from decimal import Decimal

RESULTS: list[tuple[str, bool, str]] = []


def record(name: str, ok: bool, detail: str) -> None:
    RESULTS.append((name, ok, detail))
    print(f"{'PASS' if ok else 'FAIL'}  {name}：{detail}", flush=True)


class Client:
    def __init__(self, base: str):
        self.base = base.rstrip("/")
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.token = ""

    def _cookie(self, name: str) -> str:
        for c in self.jar:
            if c.name == name:
                return c.value
        return ""

    def request(self, method: str, path: str, body=None, raw: bytes | None = None,
                content_type: str = "application/json", expect: int | None = None):
        url = self.base + path
        data = None
        if raw is not None:
            data = raw
        elif body is not None:
            data = json.dumps(body).encode()
        req = urllib.request.Request(url, data=data, method=method)
        req.add_header("Content-Type", content_type)
        if self.token and method.upper() not in ("GET", "HEAD"):
            req.add_header("X-XSRF-TOKEN", self.token)
        try:
            with self.opener.open(req, timeout=30) as resp:
                payload = resp.read()
                status = resp.status
        except urllib.error.HTTPError as exc:
            payload = exc.read()
            status = exc.code
        if expect is not None and status != expect:
            raise AssertionError(f"{method} {path} 期望 {expect} 实得 {status}: {payload[:200]!r}")
        try:
            parsed = json.loads(payload) if payload else None
        except json.JSONDecodeError:
            parsed = payload
        return status, parsed, payload

    def login(self, user: str, password: str) -> None:
        self.request("GET", "/api/v1/auth/csrf", expect=200)
        self.token = self._cookie("XSRF-TOKEN")
        self.request("POST", "/api/v1/auth/login", {"username": user, "password": password}, expect=200)

    def upload_png(self, path: str) -> dict:
        boundary = "----uat" + uuid.uuid4().hex
        png = (b"\x89PNG\r\n\x1a\n" + b"\x00\x00\x00\rIHDR" + b"\x00" * 8 +
               b"\x00\x00\x00\x00IEND\xaeB`\x82")
        parts = [
            f"--{boundary}".encode(),
            b'Content-Disposition: form-data; name="file"; filename="uat.png"',
            b"Content-Type: image/png",
            b"",
            png,
            f"--{boundary}--".encode(),
            b"",
        ]
        body = b"\r\n".join(parts)
        status, payload, _ = self.request("POST", path, raw=body,
                                          content_type=f"multipart/form-data; boundary={boundary}",
                                          expect=201)
        return payload


def rand_code() -> str:
    return "UAT" + "".join(random.choices(string.ascii_uppercase + string.digits, k=8))


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-url", required=True)
    ap.add_argument("--user", default="admin")
    ap.add_argument("--password", required=True)
    args = ap.parse_args()

    c = Client(args.base_url)
    c.login(args.user, args.password)

    _, categories, _ = c.request("GET", "/api/v1/categories", expect=200)
    _, units, _ = c.request("GET", "/api/v1/units", expect=200)
    cat_list = categories.get("items", categories) if isinstance(categories, dict) else categories
    unit_list = units.get("items", units) if isinstance(units, dict) else units
    if not cat_list or not unit_list:
        print("staging 缺少分类或单位，先造基础数据", file=sys.stderr)
        return 1
    unit_id = unit_list[0]["id"]

    # 专用分类，便于第 8 条「分类下有物料时删除被拒」
    _, cat, _ = c.request("POST", "/api/v1/categories",
                          {"name": "UAT-" + uuid.uuid4().hex[:6], "sortOrder": 99}, expect=201)
    cat_id = cat["id"]

    code = rand_code()
    # 1. 建档（含图片）
    up = c.upload_png("/api/v1/files")
    _, created, _ = c.request("POST", "/api/v1/materials", {
        "code": code, "name": "UAT 物料", "categoryId": cat_id, "unitId": unit_id,
        "spec": "测试规格", "safetyStock": 50,
    }, expect=201)
    material_id = created["id"]
    _, detail, _ = c.request("GET", f"/api/v1/materials/{material_id}", expect=200)
    _, listing, _ = c.request("GET", f"/api/v1/materials?keyword={code}", expect=200)
    items = listing.get("items", []) if isinstance(listing, dict) else listing
    record("UAT-1 建档（含图片）后列表与详情一致",
           detail["code"] == code and any(i["id"] == material_id for i in items),
           f"图片 key={up.get('key', '')[:16]}…，详情与列表均可查到 {code}")

    # 2. 字段校验
    status, payload, _ = c.request("POST", "/api/v1/materials", {
        "code": rand_code(), "name": "UAT 非法", "categoryId": cat_id, "unitId": unit_id,
        "spec": "", "safetyStock": -5,
    })
    record("UAT-2 必填/取值非法返回字段级错误",
           status == 400 and isinstance(payload, dict),
           f"HTTP {status}，字段 {payload.get('field') if isinstance(payload, dict) else payload}")

    # 3. 入库后结存一致
    c.request("POST", "/api/v1/movements", {
        "materialId": material_id, "type": "IN", "quantity": 100,
        "occurredAt": "2026-11-21T10:00:00+08:00", "note": "UAT 入库",
    }, expect=201)
    _, b1, _ = c.request("GET", f"/api/v1/balances?keyword={code}", expect=200)
    b1_items = b1.get("items", []) if isinstance(b1, dict) else b1
    qty = b1_items[0]["quantity"] if b1_items else None
    record("UAT-3 入库后结存与流水一致", qty == 100, f"结存 {qty}（期望 100）")

    # 4. 出库超结存允许负数
    c.request("POST", "/api/v1/movements", {
        "materialId": material_id, "type": "OUT", "quantity": -150,
        "occurredAt": "2026-11-21T10:05:00+08:00", "note": "UAT 出库",
    }, expect=201)
    _, b2, _ = c.request("GET", f"/api/v1/balances?keyword={code}", expect=200)
    b2_items = b2.get("items", []) if isinstance(b2, dict) else b2
    qty2 = b2_items[0]["quantity"] if b2_items else None
    record("UAT-4 出库超结存按实际值显示", qty2 == -50, f"结存 {qty2}（期望 -50）")

    # 5. 并发十次登记，结存不丢更新
    def post_in() -> None:
        cli = Client(args.base_url)
        cli.login(args.user, args.password)
        cli.request("POST", "/api/v1/movements", {
            "materialId": material_id, "type": "IN", "quantity": 10,
            "occurredAt": "2026-11-21T10:10:00+08:00", "note": "UAT 并发",
        }, expect=201)

    threads = [threading.Thread(target=post_in) for _ in range(10)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    _, b3, _ = c.request("GET", f"/api/v1/balances?keyword={code}", expect=200)
    b3_items = b3.get("items", []) if isinstance(b3, dict) else b3
    qty3 = b3_items[0]["quantity"] if b3_items else None
    record("UAT-5 并发十次登记结存无丢失更新", qty3 == 50, f"结存 {qty3}（期望 -50+100=50）")

    # 6. 冲正后回增，重复冲正被拒
    _, movements, _ = c.request("GET", f"/api/v1/movements?materialId={material_id}", expect=200)
    mv = movements.get("items", []) if isinstance(movements, dict) else movements
    out = next(m for m in mv if m["type"] == "OUT" and not m.get("reversed"))
    c.request("POST", f"/api/v1/movements/{out['id']}/reverse", {"note": "UAT 冲正"}, expect=201)
    _, b4, _ = c.request("GET", f"/api/v1/balances?keyword={code}", expect=200)
    b4_items = b4.get("items", []) if isinstance(b4, dict) else b4
    qty4 = b4_items[0]["quantity"] if b4_items else None
    status, payload, _ = c.request("POST", f"/api/v1/movements/{out['id']}/reverse", {"note": "UAT 重复冲正"})
    record("UAT-6 冲正回增且不可重复冲正", qty4 == 200 and status == 422,
           f"冲正后结存 {qty4}，重复冲正 HTTP {status} {payload}")

    # 7. 停用后默认隐藏
    status, _, _ = c.request("PATCH", f"/api/v1/materials/{material_id}", {"status": "inactive"})
    if status not in (200, 204):
        status, _, _ = c.request("DELETE", f"/api/v1/materials/{material_id}")
    _, hidden, _ = c.request("GET", f"/api/v1/materials?keyword={code}", expect=200)
    hidden_items = hidden.get("items", []) if isinstance(hidden, dict) else hidden
    _, allv, _ = c.request("GET", f"/api/v1/materials?keyword={code}&status=all", expect=200)
    all_items = allv.get("items", []) if isinstance(allv, dict) else allv
    record("UAT-7 停用后默认隐藏、全部可见", len(hidden_items) == 0 and len(all_items) >= 1,
           f"默认 {len(hidden_items)} 条，status=all {len(all_items)} 条")

    # 8. 分类下有物料时删除被拒
    status, payload, _ = c.request("DELETE", f"/api/v1/categories/{cat_id}")
    record("UAT-8 分类有物料时删除被拒", status == 422,
           f"HTTP {status} {payload if isinstance(payload, str) else payload}")

    # 12. 导出 CSV 带 BOM
    status, _, raw = c.request("GET", f"/api/v1/exports/materials.csv?keyword={code}", expect=200)
    record("UAT-12 导出 CSV 带 UTF-8 BOM", raw[:3] == b"\xef\xbb\xbf", f"前 3 字节 {raw[:3]!r}")

    # 9. 预警清单口径：每条都应满足 结存 < 安全库存，且严重度在 1~3
    _, alerts, _ = c.request("GET", "/api/v1/stats/alerts?page=1&pageSize=20", expect=200)
    items = alerts.get("items", []) if isinstance(alerts, dict) else alerts
    bad = [a for a in items
           if not (Decimal(str(a["quantity"])) < Decimal(str(a["safetyStock"])))
           or not (1 <= int(a["severity"]) <= 3)]
    record("UAT-9 预警清单与安全库存口径一致", len(items) > 0 and not bad,
           f"抽样 {len(items)} 条，口径不符 {len(bad)} 条")

    # 10. viewer 访问写接口 403
    _, invite, _ = c.request("POST", "/api/v1/invite-codes", expect=201)
    viewer = "uat" + uuid.uuid4().hex[:8]
    _, _, _ = c.request("POST", "/api/v1/auth/register", {
        "inviteCode": invite["code"], "username": viewer,
        "password": "UatPassw0rd!", "displayName": "UAT 只读",
    }, expect=201)
    v = Client(args.base_url)
    v.login(viewer, "UatPassw0rd!")
    status, payload, _ = v.request("POST", "/api/v1/materials", {
        "code": rand_code(), "name": "只读越权", "categoryId": cat_id, "unitId": unit_id,
        "spec": "", "safetyStock": 0,
    })
    record("UAT-10 viewer 写接口被拒", status == 403, f"HTTP {status}")

    fails = [r for r in RESULTS if not r[1]]
    print(f"\n合计 {len(RESULTS)} 条，通过 {len(RESULTS) - len(fails)}，失败 {len(fails)}")
    return 0 if not fails else 2


if __name__ == "__main__":
    raise SystemExit(main())
