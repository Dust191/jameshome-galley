package io.github.jameshome.galley.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 端点覆盖：40 个端点逐个走到，含正常、校验失败与业务拒绝三类。
 *
 * <p>这里只断言"接口行为"，业务不变式（结存恒等式、并发、幂等）在 {@link BusinessFlowTest}。
 */
class EndpointCoverageTest extends IntegrationTestBase {

    // ---------- 认证 ----------

    @Test
    void csrf_匿名下发令牌() {
        ApiClient.Response response = anonymous().get("/api/v1/auth/csrf");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.text("/token")).isNotBlank();
    }

    @Test
    void login_成功返回当前用户() {
        ApiClient.Response response = asAdmin().get("/api/v1/auth/me");
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.text("/username")).isEqualTo("admin");
    }

    @Test
    void login_密码错误返回401与错误码() {
        ApiClient client = anonymous();
        client.get("/api/v1/auth/csrf");
        ApiClient.Response response =
                client.post(
                        "/api/v1/auth/login",
                        Map.of("username", "admin", "password", "wrong-password"));
        assertThat(response.status()).isEqualTo(401);
        assertThat(response.text("/code")).isEqualTo("AUTH_INVALID_CREDENTIALS");
    }

    @Test
    void logout_会话立即失效() {
        ApiClient client = asAdmin();
        assertThat(client.post("/api/v1/auth/logout", Map.of()).status()).isEqualTo(204);
        assertThat(client.get("/api/v1/auth/me").status()).isEqualTo(401);
    }

    @Test
    void register_凭邀请码注册后可直接登录() {
        String code = asAdmin().post("/api/v1/invite-codes", Map.of()).text("/code");

        ApiClient guest = anonymous();
        assertThat(guest.post("/api/v1/invite-codes/validate", Map.of("code", code)).bool("/valid"))
                .isTrue();

        ApiClient.Response created =
                guest.post(
                        "/api/v1/auth/register",
                        Map.of(
                                "inviteCode", code,
                                "username", "newbie",
                                "password", "newbie-pass-2026"));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.text("/displayName")).isEqualTo("newbie");

        assertThat(anonymous().login("newbie", "newbie-pass-2026").get("/api/v1/auth/me").status())
                .isEqualTo(200);
    }

    @Test
    void password_改密后旧密码失效() {
        ApiClient client = asAdmin();
        ApiClient.Response changed =
                client.patch(
                        "/api/v1/auth/password",
                        Map.of("oldPassword", PASSWORD, "newPassword", "new-pass-2026"));
        assertThat(changed.status()).isEqualTo(204);

        ApiClient fresh = anonymous();
        fresh.get("/api/v1/auth/csrf");
        assertThat(
                        fresh.post(
                                        "/api/v1/auth/login",
                                        Map.of("username", "admin", "password", PASSWORD))
                                .status())
                .isEqualTo(401);
        assertThat(anonymous().login("admin", "new-pass-2026").get("/api/v1/auth/me").status())
                .isEqualTo(200);
    }

    // ---------- 分类 ----------

    @Test
    void categories_增改查删() {
        ApiClient admin = asAdmin();
        String id = createCategory(admin, "米面");

        assertThat(admin.patch("/api/v1/categories/" + id, Map.of("name", "粮油")).status())
                .isEqualTo(200);

        ApiClient.Response list = admin.get("/api/v1/categories");
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body()).contains("粮油");

        assertThat(admin.delete("/api/v1/categories/" + id).status()).isEqualTo(204);
    }

    @Test
    void categories_有物料时删除被拒() {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");
        createMaterial(admin, "大米", category, unit, "5");

        ApiClient.Response response = admin.delete("/api/v1/categories/" + category);
        assertThat(response.status()).isEqualTo(422);
        assertThat(response.text("/code")).isEqualTo("CATEGORY_IN_USE");
    }

    // ---------- 单位 ----------

    @Test
    void units_增改查删() {
        ApiClient admin = asAdmin();
        String id = createUnit(admin, "袋");

        assertThat(admin.patch("/api/v1/units/" + id, Map.of("name", "包")).status()).isEqualTo(200);
        assertThat(admin.get("/api/v1/units").body()).contains("包");
        assertThat(admin.delete("/api/v1/units/" + id).status()).isEqualTo(204);
    }

    @Test
    void units_被物料引用时删除被拒() {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");
        createMaterial(admin, "大米", category, unit, "5");

        ApiClient.Response response = admin.delete("/api/v1/units/" + unit);
        assertThat(response.status()).isEqualTo(422);
        assertThat(response.text("/code")).isEqualTo("UNIT_IN_USE");
    }

    // ---------- 物料 ----------

    @Test
    void materials_建档后列表与详情一致() {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");

        Map<String, Object> body = new HashMap<>();
        body.put("code", "M001");
        body.put("name", "大米");
        body.put("categoryId", category);
        body.put("unitId", unit);
        body.put("spec", "25kg/袋");
        body.put("safetyStock", "10.000");
        body.put("shelfLifeDays", 180);
        body.put("note", "常用");
        ApiClient.Response created = admin.post("/api/v1/materials", body);
        assertThat(created.status()).isEqualTo(201);
        String id = created.text("/id");

        ApiClient.Response detail = admin.get("/api/v1/materials/" + id);
        assertThat(detail.status()).isEqualTo(200);
        assertThat(detail.text("/name")).isEqualTo("大米");
        assertThat(detail.text("/safetyStock")).isEqualTo("10.000");

        ApiClient.Response list = admin.get("/api/v1/materials?keyword=大米");
        assertThat(list.number("/total")).isEqualTo(1);
        assertThat(list.text("/items/0/id")).isEqualTo(id);
    }

    @Test
    void materials_字段校验返回400() {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");

        Map<String, Object> body = new HashMap<>();
        body.put("name", "坏数据");
        body.put("categoryId", category);
        body.put("unitId", unit);
        body.put("safetyStock", "-1");

        ApiClient.Response response = admin.post("/api/v1/materials", body);
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.text("/code")).isEqualTo("VALIDATION_FAILED");
        assertThat(response.text("/field")).isEqualTo("safetyStock");
    }

    @Test
    void materials_状态机拒绝非法跃迁() {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");
        String id = createMaterial(admin, "大米", category, unit, "5");

        ApiClient.Response response =
                admin.patch("/api/v1/materials/" + id, Map.of("status", "archived", "version", 0));
        assertThat(response.status()).isEqualTo(422);
        assertThat(response.text("/code")).isEqualTo("INVALID_STATUS_TRANSITION");
    }

    @Test
    void materials_版本冲突返回409() {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");
        String id = createMaterial(admin, "大米", category, unit, "5");

        assertThat(
                        admin.patch("/api/v1/materials/" + id, Map.of("note", "第一次", "version", 0))
                                .status())
                .isEqualTo(200);
        ApiClient.Response stale =
                admin.patch("/api/v1/materials/" + id, Map.of("note", "第二次", "version", 0));
        assertThat(stale.status()).isEqualTo(409);
        assertThat(stale.text("/code")).isEqualTo("VERSION_CONFLICT");
    }

    @Test
    void materials_有流水时删除被拒_无流水可软删() {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");
        String used = createMaterial(admin, "有流水", category, unit, "5");
        String unused = createMaterial(admin, "无流水", category, unit, "5");
        createMovement(admin, used, "IN", "10.000");

        ApiClient.Response rejected = admin.delete("/api/v1/materials/" + used);
        assertThat(rejected.status()).isEqualTo(422);
        assertThat(rejected.text("/code")).isEqualTo("MATERIAL_HAS_MOVEMENTS");

        assertThat(admin.delete("/api/v1/materials/" + unused).status()).isEqualTo(204);
        assertThat(admin.get("/api/v1/materials/" + unused).status()).isEqualTo(404);
    }

    // ---------- 流水与结存 ----------

    @Test
    void movements_登记与筛选() {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");
        String material = createMaterial(admin, "大米", category, unit, "5");

        String movement = createMovement(admin, material, "IN", "10.000");
        assertThat(movement).isNotBlank();

        ApiClient.Response list = admin.get("/api/v1/movements?materialId=" + material);
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.number("/total")).isEqualTo(1);
        assertThat(list.text("/items/0/type")).isEqualTo("IN");
    }

    @Test
    void movements_符号与类型不符返回400() {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");
        String material = createMaterial(admin, "大米", category, unit, "5");

        Map<String, Object> body = new HashMap<>();
        body.put("materialId", material);
        body.put("type", "OUT");
        body.put("quantity", "5.000");
        body.put("occurredAt", java.time.Instant.now().toString());

        ApiClient.Response response = admin.post("/api/v1/movements", body);
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.text("/field")).isEqualTo("quantity");
    }

    @Test
    void movements_冲正后结存回退且不可重复冲正() {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");
        String material = createMaterial(admin, "大米", category, unit, "5");
        String movement = createMovement(admin, material, "IN", "10.000");

        ApiClient.Response reversed =
                admin.post("/api/v1/movements/" + movement + "/reverse", Map.of("note", "录错"));
        assertThat(reversed.status()).isEqualTo(201);
        assertThat(reversed.text("/type")).isEqualTo("REVERSAL");
        assertThat(reversed.text("/quantity")).isEqualTo("-10.000");

        ApiClient.Response again =
                admin.post("/api/v1/movements/" + movement + "/reverse", Map.of("note", "再来"));
        assertThat(again.status()).isEqualTo(422);
        assertThat(again.text("/code")).isEqualTo("MOVEMENT_ALREADY_REVERSED");
    }

    @Test
    void balances_结存与严重度() {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");
        String material = createMaterial(admin, "大米", category, unit, "10");
        createMovement(admin, material, "IN", "5.000");

        ApiClient.Response list = admin.get("/api/v1/balances?keyword=大米");
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.number("/total")).isEqualTo(1);
        assertThat(list.text("/items/0/quantity")).isEqualTo("5.000");
        assertThat(list.number("/items/0/severity")).isEqualTo(1);
    }

    // ---------- 统计 ----------

    @Test
    void stats_五项指标均可读() {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");
        String material = createMaterial(admin, "大米", category, unit, "10");
        createMovement(admin, material, "IN", "5.000");

        assertThat(admin.get("/api/v1/stats/overview").status()).isEqualTo(200);
        assertThat(admin.get("/api/v1/stats/category-distribution").number("/0/materialCount"))
                .isEqualTo(1);
        assertThat(admin.get("/api/v1/stats/trend").body()).contains("inCount");
        assertThat(admin.get("/api/v1/stats/top-out").status()).isEqualTo(200);
        assertThat(admin.get("/api/v1/stats/alerts").status()).isEqualTo(200);
    }

    @Test
    void stats_趋势固定30个点且缺日为0() {
        ApiClient.Response trend = asAdmin().get("/api/v1/stats/trend");
        assertThat(trend.node().size()).isEqualTo(30);
        assertThat(trend.number("/0/inCount")).isZero();
    }

    // ---------- 管理 ----------

    @Test
    void inviteCodes_创建_列表_禁用() {
        ApiClient admin = asAdmin();
        ApiClient.Response created = admin.post("/api/v1/invite-codes", Map.of());
        assertThat(created.status()).isEqualTo(201);
        String id = created.text("/id");
        String code = created.text("/code");

        assertThat(admin.get("/api/v1/invite-codes").body()).contains(code);
        assertThat(admin.patch("/api/v1/invite-codes/" + id, Map.of()).text("/status"))
                .isEqualTo("disabled");
        assertThat(admin.post("/api/v1/invite-codes/validate", Map.of("code", code)).bool("/valid"))
                .isFalse();
    }

    @Test
    void users_列表_改角色_重置密码() {
        ApiClient admin = asAdmin();
        String userId = admin.get("/api/v1/users").text("/0/id");

        ApiClient.Response updated =
                admin.patch(
                        "/api/v1/users/" + userId,
                        Map.of("displayName", "管理员甲", "role", "admin", "status", "active"));
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.text("/displayName")).isEqualTo("管理员甲");

        ApiClient.Response reset = admin.post("/api/v1/users/" + userId + "/reset-password", null);
        assertThat(reset.status()).isEqualTo(200);
        String temporary = reset.text("/temporaryPassword");
        assertThat(temporary).hasSize(12);
    }

    // ---------- 导入导出 ----------

    @Test
    void imports_整批写入与逐行错误() {
        ApiClient admin = asAdmin();
        createCategory(admin, "米面");
        createUnit(admin, "袋");

        String csv =
                "自编码,物料名,分类,单位,规格,安全库存,保质期(天),当前结存,状态,备注\r\n"
                        + "I001,糯米,米面,袋,25kg/袋,5,90,0,active,导入\r\n"
                        + "I002,,米面,袋,500g,1,180,0,active,缺名称\r\n";
        ApiClient.Response response =
                admin.upload(
                        "/api/v1/imports/materials",
                        "materials.csv",
                        csv.getBytes(StandardCharsets.UTF_8),
                        "text/csv");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.number("/created")).isZero();
        assertThat(response.number("/errors/0/line")).isEqualTo(3);
        assertThat(admin.get("/api/v1/materials?keyword=糯米").number("/total")).isZero();
    }

    @Test
    void exports_两个CSV都带BOM() {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");
        createMaterial(admin, "大米", category, unit, "5");

        ApiClient.Response materials = admin.get("/api/v1/exports/materials.csv");
        assertThat(materials.status()).isEqualTo(200);
        assertThat(materials.body()).startsWith("\uFEFF");

        ApiClient.Response movements = admin.get("/api/v1/exports/movements.csv");
        assertThat(movements.status()).isEqualTo(200);
        assertThat(movements.body()).startsWith("\uFEFF");
    }

    // ---------- 其他 ----------

    @Test
    void 空数据时总览返回零而不报错() {
        ApiClient.Response overview = asAdmin().get("/api/v1/stats/overview");
        assertThat(overview.status()).isEqualTo(200);
        assertThat(overview.number("/materialCount")).isZero();
        assertThat(overview.number("/lowStockCount")).isZero();
        assertThat(overview.number("/zeroStockCount")).isZero();
        assertThat(overview.number("/staleCount")).isZero();
    }

    @Test
    void 预警清单与列表筛选口径一致() {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");
        String zero = createMaterial(admin, "缺货物料", category, unit, "10");
        String low = createMaterial(admin, "低库存物料", category, unit, "10");
        String normal = createMaterial(admin, "正常物料", category, unit, "0");
        createMovement(admin, low, "IN", "5.000");
        createMovement(admin, normal, "IN", "20.000");
        // 正常物料必须有近期出库，否则会以"90 天未动"进入预警（M-9 呆滞）
        createMovement(admin, normal, "OUT", "-1.000");

        ApiClient.Response alerts = admin.get("/api/v1/stats/alerts");
        ApiClient.Response filtered = admin.get("/api/v1/materials?stockState=alert&pageSize=50");

        assertThat(alerts.number("/total")).isEqualTo(2);
        assertThat(filtered.number("/total")).isEqualTo(2);
        assertThat(filtered.body()).contains("缺货物料", "低库存物料").doesNotContain("正常物料");
        assertThat(zero).isNotBlank();
    }

    @Test
    void 停用物料默认隐藏_全部可见_流水仍可追溯() {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");
        String material = createMaterial(admin, "大米", category, unit, "5");
        createMovement(admin, material, "IN", "10.000");

        assertThat(
                        admin.patch(
                                        "/api/v1/materials/" + material,
                                        Map.of("status", "inactive", "version", 0))
                                .status())
                .isEqualTo(200);

        assertThat(admin.get("/api/v1/materials").number("/total")).isZero();
        assertThat(admin.get("/api/v1/materials?status=all").number("/total")).isEqualTo(1);
        assertThat(admin.get("/api/v1/movements?materialId=" + material).number("/total"))
                .isEqualTo(1);
    }

    @Test
    void materials_不存在返回404() {
        ApiClient admin = asAdmin();
        assertThat(admin.get("/api/v1/materials/01a19a9d-0000-7000-8000-000000000000").status())
                .isEqualTo(404);
    }

    @Test
    void materials_列表分页字段齐全() {
        ApiClient admin = asAdmin();
        ApiClient.Response list = admin.get("/api/v1/materials?page=1&pageSize=20");
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.node().has("items")).isTrue();
        assertThat(list.number("/page")).isEqualTo(1);
        assertThat(list.number("/pageSize")).isEqualTo(20);
    }

    @Test
    void users_列表包含三个账号() {
        ApiClient admin = asAdmin();
        List<String> names = admin.get("/api/v1/users").node().findValuesAsString("username");
        assertThat(names).contains("admin", "editor", "viewer");
    }
}
