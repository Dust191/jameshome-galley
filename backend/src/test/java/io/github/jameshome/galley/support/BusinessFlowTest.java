package io.github.jameshome.galley.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 业务不变式：结存恒等式、负结存预警、冲正回退、幂等键、并发无丢失更新。
 *
 * <p>对应 UAT-3、UAT-4、UAT-5、UAT-6 与 DEV-003 T-1/T-2/T-3。
 */
class BusinessFlowTest extends IntegrationTestBase {

    @Test
    void 结存恒等于流水求和_含冲正() {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");
        String material = createMaterial(admin, "大米", category, unit, "10");

        createMovement(admin, material, "IN", "100.000");
        createMovement(admin, material, "OUT", "-30.000");
        String overdraw = createMovement(admin, material, "OUT", "-80.000");
        admin.post("/api/v1/movements/" + overdraw + "/reverse", Map.of("note", "超领，冲正"));

        BigDecimal balance =
                new BigDecimal(admin.get("/api/v1/balances?keyword=大米").text("/items/0/quantity"));
        assertThat(balance).isEqualByComparingTo("70.000");

        BigDecimal sum = BigDecimal.ZERO;
        for (var item : admin.get("/api/v1/movements?pageSize=100").node().get("items")) {
            sum = sum.add(new BigDecimal(item.get("quantity").asString()));
        }
        assertThat(sum).isEqualByComparingTo(balance);
    }

    @Test
    void 出库超结存_允许负数并按最高严重度预警() {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");
        String material = createMaterial(admin, "大米", category, unit, "10");
        createMovement(admin, material, "IN", "10.000");
        createMovement(admin, material, "OUT", "-25.000");

        ApiClient.Response balance = admin.get("/api/v1/balances?keyword=大米");
        assertThat(balance.text("/items/0/quantity")).isEqualTo("-15.000");
        assertThat(balance.number("/items/0/severity")).isEqualTo(3);

        ApiClient.Response alerts = admin.get("/api/v1/stats/alerts");
        assertThat(alerts.body()).contains("大米");
        assertThat(alerts.body()).contains("\"severity\":3");
    }

    @Test
    void 幂等键_重复提交只记一次账() {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");
        String material = createMaterial(admin, "大米", category, unit, "5");

        String key = UUID.randomUUID().toString();
        Map<String, Object> body = new HashMap<>();
        body.put("materialId", material);
        body.put("type", "IN");
        body.put("quantity", "10.000");
        body.put("occurredAt", Instant.now().toString());
        body.put("idempotencyKey", key);

        ApiClient.Response first = admin.post("/api/v1/movements", body);
        ApiClient.Response second = admin.post("/api/v1/movements", body);

        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(201);
        assertThat(second.text("/id")).isEqualTo(first.text("/id"));
        assertThat(admin.get("/api/v1/movements").number("/total")).isEqualTo(1);
        assertThat(admin.get("/api/v1/balances?keyword=大米").text("/items/0/quantity"))
                .isEqualTo("10.000");
    }

    @Test
    void 并发十次登记_结存无丢失更新() throws Exception {
        ApiClient admin = asAdmin();
        String category = createCategory(admin, "米面");
        String unit = createUnit(admin, "袋");
        String material = createMaterial(admin, "并发物料", category, unit, "0");

        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(
                    () -> {
                        ApiClient client = asEditor();
                        Map<String, Object> body = new HashMap<>();
                        body.put("materialId", material);
                        body.put("type", "IN");
                        body.put("quantity", "10.000");
                        body.put("occurredAt", Instant.now().toString());
                        return client.post("/api/v1/movements", body).status();
                    });
        }

        List<Future<Integer>> results = pool.invokeAll(tasks);
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        for (Future<Integer> result : results) {
            assertThat(result.get()).isEqualTo(201);
        }

        assertThat(admin.get("/api/v1/movements?pageSize=20").number("/total")).isEqualTo(10);
        assertThat(admin.get("/api/v1/balances?keyword=并发物料").text("/items/0/quantity"))
                .isEqualTo("100.000");
    }
}
