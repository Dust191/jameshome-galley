package io.github.jameshome.galley.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 权限矩阵（DEV-003 T-10）：关键路由 × 三种角色。
 *
 * <p>只读账号"能看不能改"是产品的一等约束（GOV-000 §1.2），所以每条写入口都要有 403 证据。
 */
class PermissionMatrixTest extends IntegrationTestBase {

    @Test
    void 匿名_受保护接口一律401() {
        ApiClient guest = anonymous();
        assertThat(guest.get("/api/v1/materials").status()).isEqualTo(401);
        assertThat(guest.get("/api/v1/stats/overview").status()).isEqualTo(401);
        assertThat(guest.get("/api/v1/balances").status()).isEqualTo(401);
    }

    @Test
    void viewer_可读不可写() {
        String category = createCategory(asAdmin(), "米面");
        String unit = createUnit(asAdmin(), "袋");
        String material = createMaterial(asAdmin(), "大米", category, unit, "5");

        ApiClient viewer = asViewer();
        assertThat(viewer.get("/api/v1/materials").status()).isEqualTo(200);
        assertThat(viewer.get("/api/v1/balances").status()).isEqualTo(200);
        assertThat(viewer.get("/api/v1/stats/overview").status()).isEqualTo(200);

        assertThat(viewer.post("/api/v1/materials", Map.of()).status()).isEqualTo(403);
        assertThat(viewer.patch("/api/v1/materials/" + material, Map.of()).status()).isEqualTo(403);
        assertThat(viewer.delete("/api/v1/materials/" + material).status()).isEqualTo(403);
        assertThat(viewer.post("/api/v1/movements", Map.of()).status()).isEqualTo(403);
        assertThat(viewer.post("/api/v1/files", Map.of()).status()).isEqualTo(403);
        assertThat(viewer.post("/api/v1/imports/materials", Map.of()).status()).isEqualTo(403);
    }

    @Test
    void viewer_可看概览统计_不可看统计详情() {
        ApiClient viewer = asViewer();
        assertThat(viewer.get("/api/v1/stats/overview").status()).isEqualTo(200);
        assertThat(viewer.get("/api/v1/stats/trend").status()).isEqualTo(200);
        assertThat(viewer.get("/api/v1/stats/alerts").status()).isEqualTo(200);
        // 统计详情页（DES-003 /stats）仅管理员
        assertThat(viewer.get("/api/v1/stats/category-distribution").status()).isEqualTo(403);
        assertThat(viewer.get("/api/v1/stats/top-out").status()).isEqualTo(403);
        assertThat(asAdmin().get("/api/v1/stats/category-distribution").status()).isEqualTo(200);
    }

    @Test
    void viewer_管理接口一律403() {
        ApiClient viewer = asViewer();
        assertThat(viewer.get("/api/v1/users").status()).isEqualTo(403);
        assertThat(viewer.get("/api/v1/invite-codes").status()).isEqualTo(403);
        assertThat(viewer.post("/api/v1/invite-codes", Map.of()).status()).isEqualTo(403);
    }

    @Test
    void editor_可维护分类与物料_不可管单位与账号() {
        ApiClient editor = asEditor();
        String category = createCategory(editor, "米面");
        String unit = createUnit(asAdmin(), "袋");
        assertThat(createMaterial(editor, "大米", category, unit, "5")).isNotBlank();

        assertThat(editor.post("/api/v1/units", Map.of("name", "箱")).status()).isEqualTo(403);
        assertThat(editor.get("/api/v1/users").status()).isEqualTo(403);
        assertThat(editor.post("/api/v1/invite-codes", Map.of()).status()).isEqualTo(403);
    }

    @Test
    void admin_全部放行() {
        ApiClient admin = asAdmin();
        assertThat(admin.get("/api/v1/users").status()).isEqualTo(200);
        assertThat(admin.get("/api/v1/invite-codes").status()).isEqualTo(200);
        assertThat(admin.post("/api/v1/units", Map.of("name", "箱")).status()).isEqualTo(201);
    }
}
