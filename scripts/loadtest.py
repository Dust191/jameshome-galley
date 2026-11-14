#!/usr/bin/env python3
"""对 staging 做只读压测，并在云监控阈值前收手（DEV-009 §7.6）。

为什么不是 ab / wrk：这里的停止条件不是「跑满 N 秒」，而是「任一主机指标逼近
云监控告警线就停」。通用压测工具不读主机指标，也不认这条规则。

三条约束：

1. 只发 GET，不写数据；压测期间 staging 与 prod 同机，写操作会污染结账口径。
2. 每 2 秒采一次 CPU / 内存 / 磁盘；对应指标达到「告警阈值 − 5」即置停止标志，
   各线程在下一个请求前退出。默认阈值 85，因此停在 80。
3. 停止时如实输出「停在哪个指标、当时多少数据、跑了多久」，不做外推。

用法见 `docs/03-dev/testing.md` §4。
"""

from __future__ import annotations

import argparse
import http.client
import json
import shutil
import statistics
import threading
import time
import urllib.parse
from dataclasses import dataclass, field


@dataclass
class Stats:
    latencies: list[float] = field(default_factory=list)
    status: dict[int, int] = field(default_factory=dict)
    errors: int = 0
    lock: threading.Lock = field(default_factory=threading.Lock)

    def record(self, ms: float, code: int) -> None:
        with self.lock:
            self.latencies.append(ms)
            self.status[code] = self.status.get(code, 0) + 1
            if code >= 400:
                self.errors += 1


def pct(values: list[float], q: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    idx = min(len(ordered) - 1, int(round((len(ordered) - 1) * q)))
    return ordered[idx]


def read_cpu() -> tuple[int, int]:
    with open("/proc/stat", encoding="utf-8") as fh:
        parts = fh.readline().split()[1:]
    nums = [int(x) for x in parts]
    idle = nums[3] + (nums[4] if len(nums) > 4 else 0)
    return sum(nums), idle


def read_mem_percent() -> float:
    info = {}
    with open("/proc/meminfo", encoding="utf-8") as fh:
        for line in fh:
            key, _, rest = line.partition(":")
            info[key] = int(rest.split()[0])
    total = info.get("MemTotal", 1)
    available = info.get("MemAvailable", 0)
    return (total - available) * 100.0 / total


def read_disk_percent(path: str) -> float:
    usage = shutil.disk_usage(path)
    return usage.used * 100.0 / usage.total


def _cookie_pairs(resp: http.client.HTTPResponse) -> dict[str, str]:
    """一条响应可能带多个 Set-Cookie，getheader 只给第一个，必须遍历 getheaders。"""
    jar: dict[str, str] = {}
    for name, value in resp.getheaders():
        if name.lower() != "set-cookie":
            continue
        pair = value.split(";", 1)[0].strip()
        if "=" in pair:
            key, val = pair.split("=", 1)
            jar[key] = val
    return jar


def login(base: str, username: str, password: str) -> str:
    parts = urllib.parse.urlsplit(base)
    conn = http.client.HTTPConnection(parts.hostname, parts.port or 80, timeout=15)

    conn.request("GET", "/api/v1/auth/csrf")
    resp = conn.getresponse()
    resp.read()
    jar = _cookie_pairs(resp)
    token = jar.get("XSRF-TOKEN", "")
    if not token:
        raise SystemExit("未取到 XSRF-TOKEN，检查 base-url 是否指向应用")

    body = json.dumps({"username": username, "password": password})
    cookie_header = "; ".join(f"{k}={v}" for k, v in jar.items())
    conn.request(
        "POST",
        "/api/v1/auth/login",
        body=body,
        headers={
            "Content-Type": "application/json",
            "X-XSRF-TOKEN": token,
            "Cookie": cookie_header,
        },
    )
    resp = conn.getresponse()
    payload = resp.read().decode("utf-8", "replace")
    if resp.status != 200:
        raise SystemExit(f"登录失败 HTTP {resp.status}: {payload[:200]}")
    jar.update(_cookie_pairs(resp))

    # 会话 cookie 名随实现而变：文档写 SESSION，实际下发的是 JSESSIONID。
    # 这里不写死名字——取登录后新增的、不是 XSRF 的那一个。
    session = None
    for key, value in jar.items():
        if key.upper() != "XSRF-TOKEN":
            session = f"{key}={value}"
            break
    if not session:
        raise SystemExit("登录成功但未拿到会话 cookie")
    conn.close()
    return session


def main() -> None:
    parser = argparse.ArgumentParser(description="staging 只读压测")
    parser.add_argument("--base-url", default="http://127.0.0.1:8081")
    parser.add_argument("--username", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--concurrency", type=int, default=8)
    parser.add_argument("--duration", type=int, default=60, help="最长运行秒数")
    parser.add_argument("--stop-threshold", type=float, default=85.0, help="云监控告警阈值")
    parser.add_argument("--margin", type=float, default=5.0, help="提前停止的百分点")
    parser.add_argument("--sample-seconds", type=float, default=2.0)
    parser.add_argument("--warmup", type=int, default=20, help="预热请求数，不计入统计")
    parser.add_argument("--out", default="loadtest.json")
    args = parser.parse_args()

    stop_at = args.stop_threshold - args.margin
    parts = urllib.parse.urlsplit(args.base_url)
    host, port = parts.hostname, parts.port or 80

    # 预检：同机还有 prod 在跑，整机负载可能本来就高。若已越过停止点，
    # 压出来的数字说明不了任何问题——先让机器空下来。
    pre_total, pre_idle = read_cpu()
    time.sleep(1.0)
    now_total, now_idle = read_cpu()
    dt, di = now_total - pre_total, now_idle - pre_idle
    pre_cpu = 100.0 * (1 - di / dt) if dt > 0 else 0.0
    pre_mem = read_mem_percent()
    if pre_cpu >= stop_at or pre_mem >= stop_at:
        raise SystemExit(
            f"预检未通过：整机 CPU {pre_cpu:.1f}%、内存 {pre_mem:.1f}%，"
            f"已达或超过停止点 {stop_at:.0f}%。先确认没有其他任务在跑再压。"
        )

    session = login(args.base_url, args.username, args.password)
    base_headers = {"Cookie": session, "Connection": "keep-alive"}

    paths = ["/api/v1/stats/overview", "/api/v1/materials?page=1&pageSize=20&sort=-updatedAt"]

    # 预热：JIT 编译、连接建立、首个查询的缓冲都在前几次请求上，算进统计只会失真
    if args.warmup > 0:
        warm = http.client.HTTPConnection(host, port, timeout=15)
        for i in range(args.warmup):
            try:
                warm.request("GET", paths[i % len(paths)], headers=base_headers)
                warm.getresponse().read()
            except Exception:
                warm.close()
                warm = http.client.HTTPConnection(host, port, timeout=15)
        warm.close()
        print(f"预热 {args.warmup} 次完成，开始采样")

    stats = Stats()
    stop = threading.Event()
    reason = {"value": "到达时长上限"}
    peak = {"cpu": 0.0, "mem": 0.0, "disk": 0.0}

    def worker(index: int) -> None:
        conn = http.client.HTTPConnection(host, port, timeout=15)
        turn = index
        while not stop.is_set():
            path = paths[turn % len(paths)]
            turn += 1
            started = time.perf_counter()
            try:
                conn.request("GET", path, headers=base_headers)
                resp = conn.getresponse()
                resp.read()
                code = resp.status
            except Exception:
                code = 0
                conn.close()
                conn = http.client.HTTPConnection(host, port, timeout=15)
            stats.record((time.perf_counter() - started) * 1000.0, code)
        conn.close()

    def monitor() -> None:
        prev_total, prev_idle = read_cpu()
        while not stop.is_set():
            time.sleep(args.sample_seconds)
            total, idle = read_cpu()
            dt, di = total - prev_total, idle - prev_idle
            cpu = 100.0 * (1 - di / dt) if dt > 0 else 0.0
            prev_total, prev_idle = total, idle
            mem = read_mem_percent()
            disk = read_disk_percent("/")
            peak["cpu"] = max(peak["cpu"], cpu)
            peak["mem"] = max(peak["mem"], mem)
            peak["disk"] = max(peak["disk"], disk)
            for name, value in (("CPU", cpu), ("内存", mem), ("磁盘", disk)):
                if value >= stop_at:
                    reason["value"] = f"{name} 达到 {value:.1f}%，触及停止点 {stop_at:.0f}%"
                    stop.set()
                    return

    threads = [threading.Thread(target=worker, args=(i,), daemon=True) for i in range(args.concurrency)]
    mon = threading.Thread(target=monitor, daemon=True)
    started_at = time.time()
    for t in threads:
        t.start()
    mon.start()
    # 用 wait 而不是 sleep：停止点一旦触发就立刻结束计时，别把等待时间算进压测时长
    stop.wait(timeout=args.duration)
    stop.set()
    stopped_at = time.time()
    for t in threads:
        t.join(timeout=20)
    elapsed = stopped_at - started_at

    lat = stats.latencies
    result = {
        "base_url": args.base_url,
        "concurrency": args.concurrency,
        "elapsed_seconds": round(elapsed, 1),
        "requests": len(lat),
        "rps": round(len(lat) / elapsed, 1) if elapsed > 0 else 0,
        "errors": stats.errors,
        "status_codes": {str(k): v for k, v in sorted(stats.status.items())},
        "latency_ms": {
            "p50": round(pct(lat, 0.50), 1),
            "p95": round(pct(lat, 0.95), 1),
            "p99": round(pct(lat, 0.99), 1),
            "max": round(max(lat), 1) if lat else 0,
            "mean": round(statistics.fmean(lat), 1) if lat else 0,
        },
        "peak_percent": {k: round(v, 1) for k, v in peak.items()},
        "stop_threshold": args.stop_threshold,
        "stop_at": stop_at,
        "stopped_because": reason["value"],
    }
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(result, fh, ensure_ascii=False, indent=2)

    print(json.dumps(result, ensure_ascii=False, indent=2))
    print(f"\n结果已写入 {args.out}")


if __name__ == "__main__":
    main()
