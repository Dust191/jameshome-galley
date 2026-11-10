package io.github.jameshome.galley.support;

import io.github.jameshome.galley.modules.identity.application.RegisterRateLimiter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

/**
 * 集成测试基座。
 *
 * <p>按 DEV-003：集成测试跑真实 PostgreSQL 18 容器，Flyway 从零迁移（迁移脚本本身也顺带被测）。 测试通过真实 HTTP 端口访问应用，覆盖过滤器链、会话与
 * CSRF，而不是绕过它们。
 *
 * <p>每个测试前清空业务表并重建三个账号（{@code admin / editor / viewer}，密码 {@code test-pass-2026}），测试之间互不影响。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    /**
     * 整个 JVM 共用一个容器。
     *
     * <p>不能每个测试类各起一个：Spring 会按配置缓存上下文，第二个类复用第一个类的上下文， 却指向已经停掉的容器端口（Connection refused）。共用一个容器，JDBC
     * URL 恒定，缓存才成立。
     *
     * <p>与开发环境同一镜像，避免为测试单独拉取。
     */
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:18.3")
                    .withDatabaseName("galley")
                    .withUsername("galley")
                    .withPassword("galley");

    static {
        POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop));
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    protected static final String PASSWORD = "test-pass-2026";

    @Value("${local.server.port}")
    protected int port;

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected ObjectMapper json;

    @Autowired private RegisterRateLimiter registerRateLimiter;

    @BeforeEach
    void resetDatabase() {
        // 流水表有"不可改删"行级触发器（DES-001 I-2），DELETE 会被拒；TRUNCATE 不触发行级触发器。
        jdbc.execute(
                "truncate table stock_movement, stock_balance, audit_log, material, "
                        + "material_category, unit, invite_code, app_user cascade");
        // 进程内的限流计数不属于数据库，但同样会跨用例累积，逐用例清零
        registerRateLimiter.clear();

        insertUser("admin", "管理员", "admin");
        insertUser("editor", "录入员", "editor");
        insertUser("viewer", "查看者", "viewer");
    }

    private void insertUser(String username, String displayName, String role) {
        jdbc.update(
                """
                insert into app_user
                    (id, username, display_name, password_hash, role, status, must_change_password)
                values (?, ?, ?, ?, ?, 'active', false)
                """,
                UUID.randomUUID(),
                username,
                displayName,
                passwordEncoder.encode(PASSWORD),
                role);
    }

    protected ApiClient anonymous() {
        return new ApiClient(port, json);
    }

    protected ApiClient asAdmin() {
        return anonymous().login("admin", PASSWORD);
    }

    protected ApiClient asEditor() {
        return anonymous().login("editor", PASSWORD);
    }

    protected ApiClient asViewer() {
        return anonymous().login("viewer", PASSWORD);
    }

    // ---------- 常用夹具 ----------

    protected String createCategory(ApiClient client, String name) {
        ApiClient.Response response =
                client.post("/api/v1/categories", java.util.Map.of("name", name, "sortOrder", 1));
        if (response.status() != 201) {
            throw new IllegalStateException("建分类失败：" + response.status() + " " + response.body());
        }
        return response.text("/id");
    }

    protected String createUnit(ApiClient client, String name) {
        ApiClient.Response response =
                client.post("/api/v1/units", java.util.Map.of("name", name, "sortOrder", 1));
        if (response.status() != 201) {
            throw new IllegalStateException("建单位失败：" + response.status() + " " + response.body());
        }
        return response.text("/id");
    }

    protected String createMaterial(
            ApiClient client, String name, String categoryId, String unitId, String safetyStock) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", name);
        body.put("categoryId", categoryId);
        body.put("unitId", unitId);
        body.put("safetyStock", safetyStock);
        ApiClient.Response response = client.post("/api/v1/materials", body);
        if (response.status() != 201) {
            throw new IllegalStateException("建物料失败：" + response.status() + " " + response.body());
        }
        return response.text("/id");
    }

    protected String createMovement(
            ApiClient client, String materialId, String type, String quantity) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("materialId", materialId);
        body.put("type", type);
        body.put("quantity", quantity);
        body.put("occurredAt", java.time.Instant.now().toString());
        ApiClient.Response response = client.post("/api/v1/movements", body);
        if (response.status() != 201) {
            throw new IllegalStateException("登记流水失败：" + response.status() + " " + response.body());
        }
        return response.text("/id");
    }
}
