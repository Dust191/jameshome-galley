package io.github.jameshome.galley.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 验证集成测试基座本身：容器能起、Flyway 能迁、登录态能保持。 */
class IntegrationSmokeTest extends IntegrationTestBase {

    @Test
    void healthz_匿名可访问() {
        assertThat(anonymous().get("/healthz").status()).isEqualTo(200);
    }

    @Test
    void me_登录后返回当前用户() {
        ApiClient.Response response = asAdmin().get("/api/v1/auth/me");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.text("/username")).isEqualTo("admin");
        assertThat(response.text("/role")).isEqualTo("admin");
    }

    @Test
    void me_未登录返回401() {
        assertThat(anonymous().get("/api/v1/auth/me").status()).isEqualTo(401);
    }
}
