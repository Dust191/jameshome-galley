package io.github.jameshome.galley.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 结存对账（DES-001 I-5）：正常数据零差异；人为改坏结存必须被抓出来；只对管理员开放。 */
class ReconcileEndpointTest extends IntegrationTestBase {

    @Test
    void 对账_正常数据零差异() {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");
        String material = createMaterial(admin, "大米", category, unit, "5");
        createMovement(admin, material, "IN", "10.000");

        ApiClient.Response report = admin.get("/api/v1/reconcile");
        assertThat(report.status()).isEqualTo(200);
        assertThat(report.number("/checked")).isEqualTo(1);
        assertThat(report.number("/mismatched")).isZero();
    }

    @Test
    void 对账_人为改坏结存被发现() {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");
        String material = createMaterial(admin, "大米", category, unit, "5");
        createMovement(admin, material, "IN", "10.000");
        jdbc.update(
                "update stock_balance set quantity = 999 where material_id = ?",
                UUID.fromString(material));

        ApiClient.Response report = admin.get("/api/v1/reconcile");
        assertThat(report.number("/mismatched")).isEqualTo(1);
        assertThat(report.text("/items/0/balance")).isEqualTo("999.000");
        assertThat(report.text("/items/0/movementSum")).isEqualTo("10.000");
    }

    @Test
    void 对账_仅管理员可访问() {
        assertThat(asEditor().get("/api/v1/reconcile").status()).isEqualTo(403);
        assertThat(asViewer().get("/api/v1/reconcile").status()).isEqualTo(403);
    }
}
