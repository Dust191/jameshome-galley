package io.github.jameshome.galley.modules.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 限流器的纯单元测试：不依赖 Spring 与数据库。 */
class RegisterRateLimiterTest {

    @Test
    void 窗口内超过上限即拒绝() {
        RegisterRateLimiter limiter = new RegisterRateLimiter(3, 3600);
        assertThat(limiter.allow("10.0.0.1")).isTrue();
        assertThat(limiter.allow("10.0.0.1")).isTrue();
        assertThat(limiter.allow("10.0.0.1")).isTrue();
        assertThat(limiter.allow("10.0.0.1")).isFalse();
        // 第 5 次仍拒绝：拒绝不重置窗口
        assertThat(limiter.allow("10.0.0.1")).isFalse();
    }

    @Test
    void 窗口滑过后恢复放行() throws InterruptedException {
        // 窗口 1 秒：用尽后等窗口滑过，再次放行
        RegisterRateLimiter limiter = new RegisterRateLimiter(1, 1);
        assertThat(limiter.allow("10.0.0.2")).isTrue();
        assertThat(limiter.allow("10.0.0.2")).isFalse();
        Thread.sleep(1100);
        assertThat(limiter.allow("10.0.0.2")).isTrue();
    }

    @Test
    void 不同IP互不影响() {
        RegisterRateLimiter limiter = new RegisterRateLimiter(1, 3600);
        assertThat(limiter.allow("10.0.0.3")).isTrue();
        assertThat(limiter.allow("10.0.0.3")).isFalse();
        assertThat(limiter.allow("10.0.0.4")).isTrue();
    }

    @Test
    void clear清零计数() {
        RegisterRateLimiter limiter = new RegisterRateLimiter(1, 3600);
        assertThat(limiter.allow("10.0.0.5")).isTrue();
        assertThat(limiter.allow("10.0.0.5")).isFalse();
        limiter.clear();
        assertThat(limiter.allow("10.0.0.5")).isTrue();
    }

    @Test
    void retryAfter至少一秒() {
        RegisterRateLimiter limiter = new RegisterRateLimiter(1, 600);
        limiter.allow("10.0.0.6");
        assertThat(limiter.retryAfterSeconds("10.0.0.6")).isBetween(1L, 600L);
        // 无记录的 IP 也给 1，避免给调用方返回 0 或负数
        assertThat(limiter.retryAfterSeconds("10.0.0.99")).isEqualTo(1L);
    }
}
