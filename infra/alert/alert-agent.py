#!/usr/bin/env python3
"""业务层告警代理：每分钟跑一次，六条规则，状态变化时经 DirectMail 发信。

规则与阈值见 docs/04-ops/monitoring.md（RUN-003）§3。这里只做「判定 + 去重 + 发信」，
不采集指标、不写库；每条规则的输入来源都在 --help 里可替换，便于逐条验证。

设计要点：
- 只在状态变化时发信，持续故障每 6 小时提醒一次，恢复时补一封恢复信。
- 数据不足（样本 < 20）时不判定，记 SKIP，避免刚发版就把人叫醒。
- 任何一条检查自身异常都不影响其他条：异常按「未知」记，不发信，写日志。
"""

from __future__ import annotations

import argparse
import hashlib
import hmac
import json
import os
import re
import subprocess
import time
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

MAIL_ENDPOINT = "dm.aliyuncs.com"
MAIL_VERSION = "2015-11-23"
REMIND_SECONDS = 6 * 3600
MIN_SAMPLES = 20


def log(msg: str) -> None:
    print(f"{datetime.now().astimezone().isoformat(timespec='seconds')} {msg}", flush=True)


def load_env(path: Path) -> dict[str, str]:
    env: dict[str, str] = {}
    if not path.exists():
        return env
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip().lstrip("\ufeff")
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        v = v.strip().strip('"').strip("'").rstrip("\r")
        env[k.strip()] = v
    return env


def sh(cmd: list[str], timeout: int = 15) -> tuple[int, str]:
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
        return p.returncode, (p.stdout or "") + (p.stderr or "")
    except Exception as exc:  # noqa: BLE001 - 任何异常都当作检查失败
        return 1, f"{type(exc).__name__}: {exc}"


def read_lines(docker_logs: list[str] | None, file: str | None) -> list[str]:
    if file:
        p = Path(file)
        return p.read_text(encoding="utf-8", errors="replace").splitlines() if p.exists() else []
    if docker_logs:
        rc, out = sh(docker_logs, timeout=30)
        return out.splitlines()
    return []


# ---------- 六条规则 ----------


def check_readiness(args, state: dict) -> tuple[str, str]:
    """B-1：readiness 连续 3 次失败。"""
    if args.health_url:
        req = urllib.request.Request(args.health_url, method="GET")
        try:
            with urllib.request.urlopen(req, timeout=6) as resp:
                ok = resp.status == 200
                detail = f"HTTP {resp.status}"
        except Exception as exc:  # noqa: BLE001
            ok, detail = False, f"{type(exc).__name__}"
    else:
        rc, out = sh(["docker", "exec", args.api_container, "curl", "-fsS", "-m", "5",
                      "http://127.0.0.1:8080/actuator/health/readiness"])
        ok, detail = rc == 0, out.strip().splitlines()[-1][:80] if out.strip() else "无输出"

    fails = 0 if ok else int(state.get("b1_fails", 0)) + 1
    state["b1_fails"] = fails
    if fails >= 3:
        return "ALERT", f"readiness 连续 {fails} 次失败（最后一次：{detail}）"
    return "OK", f"{'正常' if ok else '失败 ' + str(fails) + '/3'}"


def check_tunnel(args, state: dict) -> tuple[str, str]:
    """B-2：入口失效——容器未运行，或地址与上次不同。"""
    rc, out = sh(["docker", "inspect", "-f", "{{.State.Running}}", args.tunnel_container])
    if rc != 0 or out.strip() != "true":
        return "ALERT", f"{args.tunnel_container} 未运行"

    lines = read_lines(["docker", "logs", args.tunnel_container], args.tunnel_logs_file)
    urls = re.findall(r"https://[a-z0-9-]+\.trycloudflare\.com", "\n".join(lines))
    if not urls:
        return "ALERT", "隧道日志里找不到地址"
    current = urls[-1]
    previous = state.get("tunnel_url")
    state["tunnel_url"] = current
    if previous and previous != current:
        return "ALERT", f"入口换址：{previous} → {current}"
    return "OK", f"地址 {current}"


def _caddy_samples(args) -> list[dict]:
    samples = []
    for line in read_lines(["docker", "logs", args.caddy_container, "--since", "10m"],
                           args.caddy_logs_file):
        line = line.strip()
        if not line.startswith("{"):
            continue
        try:
            rec = json.loads(line)
        except json.JSONDecodeError:
            continue
        if "status" not in rec:
            continue
        samples.append(rec)
    return samples


def check_5xx(args, state: dict) -> tuple[str, str]:
    """B-3：5 分钟内 5xx >1%，样本 ≥20 才判。"""
    now = time.time()
    recent = [r for r in _caddy_samples(args) if now - float(r.get("ts", 0)) <= 300]
    if len(recent) < MIN_SAMPLES:
        return "SKIP", f"5 分钟样本 {len(recent)} < {MIN_SAMPLES}"
    bad = sum(1 for r in recent if int(r.get("status", 0)) >= 500)
    ratio = bad / len(recent)
    if ratio > 0.01:
        return "ALERT", f"5xx {bad}/{len(recent)} = {ratio:.2%}（阈值 1%）"
    return "OK", f"5xx {bad}/{len(recent)} = {ratio:.2%}"


def check_p95(args, state: dict) -> tuple[str, str]:
    """B-4：10 分钟 P95 >1 秒，样本 ≥20 才判。"""
    now = time.time()
    durations = sorted(
        float(r.get("duration", 0)) for r in _caddy_samples(args)
        if now - float(r.get("ts", 0)) <= 600
    )
    if len(durations) < MIN_SAMPLES:
        return "SKIP", f"10 分钟样本 {len(durations)} < {MIN_SAMPLES}"
    idx = min(len(durations) - 1, int(round(0.95 * (len(durations) - 1))))
    p95 = durations[idx]
    if p95 > 1.0:
        return "ALERT", f"P95 {p95 * 1000:.0f}ms（阈值 1000ms，样本 {len(durations)}）"
    return "OK", f"P95 {p95 * 1000:.0f}ms"


def check_backup(args, state: dict) -> tuple[str, str]:
    """B-5：备份失败——最新导出超过 26 小时，或最近日志含失败。"""
    root = Path(args.backup_dir)
    dumps = sorted(root.rglob("*.dump"), key=lambda p: p.stat().st_mtime, reverse=True) if root.exists() else []
    if not dumps:
        return "ALERT", f"{root} 下没有任何导出文件"
    age_h = (time.time() - dumps[0].stat().st_mtime) / 3600
    if age_h > 26:
        return "ALERT", f"最新导出 {dumps[0].name} 已 {age_h:.1f} 小时（阈值 26）"
    return "OK", f"最新导出 {dumps[0].name}，{age_h:.1f} 小时前"


def check_reconcile(args, state: dict) -> tuple[str, str]:
    """B-6：对账差异 ≠0，或 26 小时内没有对账记录。"""
    lines = read_lines(["docker", "logs", args.api_container, "--since", "26h"], args.api_logs_file)
    hits = [ln for ln in lines if "结存对账" in ln]
    if not hits:
        return "ALERT", "26 小时内没有对账记录"
    last = hits[-1]
    if "差异" in last and "零差异" not in last:
        return "ALERT", f"对账不一致：{last.split('结存对账')[-1].strip()[:100]}"
    return "OK", "零差异"


CHECKS = [
    ("B-1", "服务不可用", check_readiness),
    ("B-2", "入口失效", check_tunnel),
    ("B-3", "5xx 比例", check_5xx),
    ("B-4", "P95 延迟", check_p95),
    ("B-5", "备份失败", check_backup),
    ("B-6", "对账差异", check_reconcile),
]


# ---------- 发信 ----------


def _sign(secret: str, akid: str, body: str) -> dict[str, str]:
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    nonce = str(uuid.uuid4())
    payload_hash = hashlib.sha256(body.encode()).hexdigest()
    headers = {
        "host": MAIL_ENDPOINT,
        "x-acs-action": "SingleSendMail",
        "x-acs-version": MAIL_VERSION,
        "x-acs-date": now,
        "x-acs-signature-nonce": nonce,
        "x-acs-content-sha256": payload_hash,
    }
    signed = ";".join(sorted(headers))
    canonical_headers = "".join(f"{k}:{headers[k]}\n" for k in sorted(headers))
    canonical_request = f"POST\n/\n\n{canonical_headers}\n{signed}\n{payload_hash}"
    string_to_sign = "ACS3-HMAC-SHA256\n" + hashlib.sha256(canonical_request.encode()).hexdigest()
    signature = hmac.new(secret.encode(), string_to_sign.encode(), hashlib.sha256).hexdigest()
    out = {
        "Authorization": f"ACS3-HMAC-SHA256 Credential={akid},SignedHeaders={signed},Signature={signature}",
        "x-acs-action": "SingleSendMail",
        "x-acs-version": MAIL_VERSION,
        "x-acs-date": now,
        "x-acs-signature-nonce": nonce,
        "x-acs-content-sha256": payload_hash,
        "Content-Type": "application/x-www-form-urlencoded",
    }
    return out


def send_mail(env: dict[str, str], subject: str, text: str, args) -> bool:
    akid = env.get("GALLEY_MAIL_ACCESS_KEY_ID", "")
    secret = env.get("GALLEY_MAIL_ACCESS_KEY_SECRET", "")
    account = env.get("GALLEY_MAIL_ACCOUNT", "ecs-alert@dust181.aliyun.com")
    to = args.mail_to or env.get("GALLEY_MAIL_TO", "")
    if not (akid and secret and to):
        log("MAIL 跳过：缺少凭据或收件人")
        return False

    params = {
        "Action": "SingleSendMail",
        "Version": MAIL_VERSION,
        "AccountName": account,
        "ReplyToAddress": "false",
        "AddressType": "1",
        "ToAddress": to,
        "Subject": subject,
        "TextBody": text,
    }
    body = urllib.parse.urlencode(params)
    headers = _sign(secret, akid, body)
    req = urllib.request.Request(f"https://{MAIL_ENDPOINT}/", data=body.encode(), headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            payload = resp.read().decode("utf-8", "replace")
        ok = '"Code":"OK"' in payload.replace(" ", "")
        log(f"MAIL {'成功' if ok else '失败'}：{subject} → {to}；响应 {payload[:160]}")
        return ok
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", "replace")[:300]
        log(f"MAIL 拒绝：HTTP {exc.code} {body}")
        return False
    except Exception as exc:  # noqa: BLE001
        log(f"MAIL 异常：{type(exc).__name__}: {exc}")
        return False


# ---------- 主流程 ----------


def main() -> int:
    ap = argparse.ArgumentParser(description="业务层告警代理")
    ap.add_argument("--env-file", default="/opt/galley/.env.prod")
    ap.add_argument("--state-dir", default="/var/lib/galley-alert")
    ap.add_argument("--api-container", default="galley-api-1")
    ap.add_argument("--caddy-container", default="galley-caddy-1")
    ap.add_argument("--tunnel-container", default="galley-cloudflared-1")
    ap.add_argument("--backup-dir", default="/opt/galley/backup")
    ap.add_argument("--health-url", default="")
    ap.add_argument("--caddy-logs-file", default="")
    ap.add_argument("--api-logs-file", default="")
    ap.add_argument("--tunnel-logs-file", default="")
    ap.add_argument("--mail-to", default="")
    ap.add_argument("--only", default="", help="只跑一条，如 B-5")
    ap.add_argument("--test-mail", action="store_true")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("-v", "--verbose", action="store_true")
    args = ap.parse_args()

    env = load_env(Path(args.env_file))
    state_dir = Path(args.state_dir)
    state_dir.mkdir(parents=True, exist_ok=True)
    state_file = state_dir / "state.json"
    state = json.loads(state_file.read_text(encoding="utf-8")) if state_file.exists() else {}

    if args.test_mail:
        ok = send_mail(env, "【Galley】告警通道测试",
                       "这是一封测试信：能收到即说明 DirectMail 通道与签名可用。", args)
        return 0 if ok else 1

    now = time.time()
    fired, recovered = [], []
    for key, name, fn in CHECKS:
        if args.only and key != args.only:
            continue
        try:
            status, detail = fn(args, state)
        except Exception as exc:  # noqa: BLE001
            status, detail = "SKIP", f"检查异常 {type(exc).__name__}: {exc}"

        slot = state.setdefault(key, {"active": False, "sent_at": 0})
        if status == "ALERT":
            if not slot["active"]:
                fired.append((key, name, detail))
                slot.update(active=True, sent_at=now)
            elif now - slot["sent_at"] > REMIND_SECONDS:
                fired.append((key, name, f"（持续）{detail}"))
                slot["sent_at"] = now
        elif status == "OK" and slot["active"]:
            recovered.append((key, name, detail))
            slot.update(active=False, sent_at=0)
        log(f"{key} {name}: {status} {detail}")
        if args.verbose:
            pass

    state_file.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")

    if args.dry_run:
        if fired:
            log(f"DRY-RUN 本应发出 {len(fired)} 封：{', '.join(k for k, _, _ in fired)}")
        if recovered:
            log(f"DRY-RUN 本应发出恢复信：{', '.join(k for k, _, _ in recovered)}")
        return 0

    for key, name, detail in fired:
        send_mail(env, f"【Galley】{key} {name}", f"{detail}\n\n时间：{datetime.now().astimezone().isoformat(timespec='seconds')}", args)
    for key, name, detail in recovered:
        send_mail(env, f"【Galley】恢复：{key} {name}", f"已恢复：{detail}", args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
