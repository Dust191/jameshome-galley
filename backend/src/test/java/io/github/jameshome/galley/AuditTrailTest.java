package io.github.jameshome.galley;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jameshome.galley.support.ApiClient;
import io.github.jameshome.galley.support.IntegrationTestBase;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 写操作审计的端到端验证（`DEV-007` DVT-08 收回）。
 *
 * <p>断言方式：直接查 {@code audit_log} 表（审计没有读取接口，表即事实）。 账号类快照必须脱敏——password / passwordHash /
 * password_hash 都不许出现。
 */
class AuditTrailTest extends IntegrationTestBase {

    @Autowired private JdbcTemplate auditJdbc;

    private UUID adminId() {
        return auditJdbc.queryForObject(
                "select id from app_user where username = 'admin'", UUID.class);
    }

    private List<Map<String, Object>> auditRows(String where, Object... args) {
        return auditJdbc.queryForList(
                "select actor_id, action, entity, entity_id, "
                        + "before::text as before_json, after::text as after_json "
                        + "from audit_log where "
                        + where,
                args);
    }

    private String createMaterial(String name) {
        String category = createCategory(asAdmin(), "审计分类-" + name);
        String unit = createUnit(asAdmin(), "审计单位");
        ApiClient.Response response =
                asAdmin()
                        .post(
                                "/api/v1/materials",
                                Map.of(
                                        "name",
                                        name,
                                        "categoryId",
                                        category,
                                        "unitId",
                                        unit,
                                        "safetyStock",
                                        "5"));
        assertThat(response.status()).as(response.body()).isEqualTo(201);
        return response.text("/id");
    }

    @Test
    void 建档落CREATE审计且回填created_by() {
        String materialId = createMaterial("审计大米");
        UUID id = UUID.fromString(materialId);

        var rows = auditRows("entity = 'material' and action = 'CREATE' and entity_id = ?", id);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("actor_id")).isEqualTo(adminId());
        assertThat(rows.get(0).get("before_json")).isNull();
        assertThat((String) rows.get(0).get("after_json")).contains("审计大米");

        UUID createdBy =
                auditJdbc.queryForObject(
                        "select created_by from material where id = ?", UUID.class, id);
        assertThat(createdBy).isEqualTo(adminId());
    }

    @Test
    void 编辑落UPDATE审计且前后快照不同() {
        String materialId = createMaterial("审计可改物料");
        UUID id = UUID.fromString(materialId);

        var response = asAdmin().patch("/api/v1/materials/" + id, Map.of("name", "审计改后物料"));
        assertThat(response.status()).as(response.body()).isEqualTo(200);

        var rows = auditRows("entity = 'material' and action = 'UPDATE' and entity_id = ?", id);
        assertThat(rows).hasSize(1);
        assertThat((String) rows.get(0).get("before_json")).contains("审计可改物料");
        assertThat((String) rows.get(0).get("after_json")).contains("审计改后物料");
    }

    @Test
    void 软删除落DELETE审计() {
        String materialId = createMaterial("审计待删物料");
        UUID id = UUID.fromString(materialId);

        assertThat(asAdmin().delete("/api/v1/materials/" + id).status()).isEqualTo(204);

        var rows = auditRows("entity = 'material' and action = 'DELETE' and entity_id = ?", id);
        assertThat(rows).hasSize(1);
        assertThat((String) rows.get(0).get("before_json")).contains("审计待删物料");
    }

    @Test
    void 流水登记与红冲落PLACE与REVERSE审计() {
        String materialId = createMaterial("审计流水物料");

        var place =
                asAdmin()
                        .post(
                                "/api/v1/movements",
                                Map.of(
                                        "materialId",
                                        materialId,
                                        "type",
                                        "IN",
                                        "quantity",
                                        "5",
                                        "occurredAt",
                                        "2026-11-10T02:00:00Z"));
        assertThat(place.status()).as(place.body()).isEqualTo(201);
        String movementId = place.text("/id");

        var rows =
                auditRows(
                        "entity = 'stock_movement' and action = 'PLACE' and entity_id = ?",
                        UUID.fromString(movementId));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("actor_id")).isEqualTo(adminId());

        var reverse =
                asAdmin()
                        .post(
                                "/api/v1/movements/" + movementId + "/reverse",
                                Map.of("note", "审计冲正"));
        assertThat(reverse.status()).as(reverse.body()).isEqualTo(201);

        var reversed =
                auditRows(
                        "entity = 'stock_movement' and action = 'REVERSE' and entity_id = ?",
                        UUID.fromString(reverse.text("/id")));
        assertThat(reversed).hasSize(1);
        assertThat((String) reversed.get(0).get("before_json")).contains("\"reversed\": false");
        assertThat((String) reversed.get(0).get("after_json")).contains("REVERSAL");
    }

    @Test
    void 注册与登录落审计且快照不含口令字段() {
        String code = asAdmin().post("/api/v1/invite-codes", Map.of()).text("/code");
        var registered =
                anonymous()
                        .post(
                                "/api/v1/auth/register",
                                Map.of(
                                        "inviteCode",
                                        code,
                                        "username",
                                        "audituser",
                                        "password",
                                        "audit-pass-2026"));
        assertThat(registered.status()).as(registered.body()).isEqualTo(201);

        UUID userId =
                auditJdbc.queryForObject(
                        "select id from app_user where username = 'audituser'", UUID.class);

        var creates =
                auditRows("entity = 'app_user' and action = 'CREATE' and entity_id = ?", userId);
        assertThat(creates).hasSize(1);
        String after = (String) creates.get(0).get("after_json");
        assertThat(after).contains("audituser");
        // 脱敏：注册快照里不许出现任何口令字段
        assertThat(after).doesNotContain("password");

        anonymous().login("audituser", "audit-pass-2026");
        var logins =
                auditRows("entity = 'app_user' and action = 'LOGIN' and entity_id = ?", userId);
        assertThat(logins).hasSize(1);
        assertThat(logins.get(0).get("actor_id")).isEqualTo(userId);
    }

    @Test
    void 导入落IMPORT审计() {
        String category = createCategory(asAdmin(), "导入分类");
        String unit = createUnit(asAdmin(), "导入单位");
        String csv =
                "自编码,物料名,分类,单位,规格,安全库存,保质期(天),当前结存,状态,备注\n"
                        + "A0001,导入物料一,导入分类,导入单位,,1,,,active,\n"
                        + "A0002,导入物料二,导入分类,导入单位,,2,,,active,\n";
        var response =
                asAdmin()
                        .upload(
                                "/api/v1/imports/materials",
                                "audit.csv",
                                csv.getBytes(StandardCharsets.UTF_8),
                                "text/csv");
        assertThat(response.status()).as(response.body()).isEqualTo(200);

        var rows =
                auditRows(
                        "entity = 'material' and action = 'IMPORT' and after::text like ?",
                        "%\"created\": 2%");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("actor_id")).isEqualTo(adminId());
    }
}
