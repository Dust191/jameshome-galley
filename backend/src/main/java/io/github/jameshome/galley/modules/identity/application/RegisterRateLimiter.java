package io.github.jameshome.galley.modules.identity.application;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 注册端点的按 IP 固定窗口限流（`SCOPE-001` In 项、决策 D-20、`DEV-007` DVT-09）。
 *
 * <p>注册页对未登录用户开放，无限制即可被高频尝试邀请码。每个 IP 在窗口内的**全部尝试** （无论成败）都计数，超限后返回 429；窗口滑过自动恢复。进程内计数，单实例部署够用——
 * 与登录失败锁定的口径一致（{@code AuthService} 注释）。
 */
@Component
public class RegisterRateLimiter {

    private final int maxAttempts;
    private final Duration window;
    private final ConcurrentHashMap<String, Window> hits = new ConcurrentHashMap<>();

    public RegisterRateLimiter(
            @Value("${galley.register.rate-limit.max-attempts:10}") int maxAttempts,
            @Value("${galley.register.rate-limit.window-seconds:600}") long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    /**
     * 记一次尝试并判断是否放行。
     *
     * @return {@code true} 表示窗口内未超限；{@code false} 表示已超限，调用方应返回 429
     */
    public boolean allow(String ip) {
        Instant now = Instant.now();
        Window current =
                hits.compute(
                        ip,
                        (key, existing) -> {
                            if (existing == null || now.isAfter(existing.start().plus(window))) {
                                return Window.startingAt(now);
                            }
                            return existing;
                        });
        return current.count().incrementAndGet() <= maxAttempts;
    }

    /** 距窗口重置还剩多少秒，供 429 响应的 {@code Retry-After} 头使用（恒 ≥1）。 */
    public long retryAfterSeconds(String ip) {
        Window current = hits.get(ip);
        if (current == null) {
            return 1;
        }
        long seconds =
                window.toSeconds() - Duration.between(current.start(), Instant.now()).toSeconds();
        return Math.max(1, seconds);
    }

    /** 窗口计数清零。集成测试在每个用例前调用，避免跨用例累积。 */
    public void clear() {
        hits.clear();
    }

    private record Window(Instant start, AtomicInteger count) {

        private static Window startingAt(Instant now) {
            return new Window(now, new AtomicInteger());
        }
    }
}
