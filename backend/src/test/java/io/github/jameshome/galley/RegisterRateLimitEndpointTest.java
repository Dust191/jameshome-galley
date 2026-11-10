package io.github.jameshome.galley;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jameshome.galley.support.IntegrationTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 注册端点限流的端到端验证（`DEV-007` DVT-09 收回）。
 *
 * <p>默认窗口 10 次 / 600 秒（`application.yml`）。测试客户端全部来自 127.0.0.1， 与其他用例共享同一 IP，所以 {@code
 * resetDatabase} 会同时清零限流计数。
 */
class RegisterRateLimitEndpointTest extends IntegrationTestBase {

    @Test
    void 注册超限返回429并携带RetryAfter() {
        // 前 10 次：邀请码无效（422），但成败都计数
        for (int i = 0; i < 10; i++) {
            var response =
                    anonymous()
                            .post(
                                    "/api/v1/auth/register",
                                    Map.of(
                                            "inviteCode",
                                            "ZZZZZZZZ",
                                            "username",
                                            "rate" + i,
                                            "password",
                                            "rate-pass-2026"));
            assertThat(response.status())
                    .as("第 %d 次：%s".formatted(i + 1, response.body()))
                    .isEqualTo(422);
        }
        // 第 11 次：429 + Retry-After
        var blocked =
                anonymous()
                        .post(
                                "/api/v1/auth/register",
                                Map.of(
                                        "inviteCode",
                                        "ZZZZZZZZ",
                                        "username",
                                        "rateover",
                                        "password",
                                        "rate-pass-2026"));
        assertThat(blocked.status()).isEqualTo(429);
        assertThat(blocked.text("/code")).isEqualTo("RATE_LIMITED");
        assertThat(blocked.header("Retry-After")).isNotNull();
        assertThat(Long.parseLong(blocked.header("Retry-After"))).isBetween(1L, 600L);
    }

    @Test
    void 未超限的正常注册不受影响() {
        String code = asAdmin().post("/api/v1/invite-codes", Map.of()).text("/code");
        var response =
                anonymous()
                        .post(
                                "/api/v1/auth/register",
                                Map.of(
                                        "inviteCode",
                                        code,
                                        "username",
                                        "rateok",
                                        "password",
                                        "rate-pass-2026"));
        assertThat(response.status()).isEqualTo(201);
        assertThat(response.text("/username")).isEqualTo("rateok");
    }
}
