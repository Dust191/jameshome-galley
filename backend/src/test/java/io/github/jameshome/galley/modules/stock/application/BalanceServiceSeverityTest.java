package io.github.jameshome.galley.modules.stock.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 严重度判定（DES-004 M-9）。
 *
 * <p>边界值决定预警清单的准确性：安全库存为 0 表示"不设预警线"，此时任何正结存都不该算低库存； 结存恰好等于安全线也不算，规则是"低于"而非"不高于"。
 */
class BalanceServiceSeverityTest {

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("负结存最高严重度 3")
    void negativeIsSevere() {
        assertThat(BalanceService.severityOf(n("-0.001"), n("10"))).isEqualTo(3);
        assertThat(BalanceService.severityOf(n("-100"), n("0"))).isEqualTo(3);
    }

    @Test
    @DisplayName("结存为 0 是断货，严重度 2")
    void zeroIsOutOfStock() {
        assertThat(BalanceService.severityOf(BigDecimal.ZERO, n("10"))).isEqualTo(2);
        assertThat(BalanceService.severityOf(n("0.000"), n("0"))).isEqualTo(2);
    }

    @Test
    @DisplayName("低于安全线才是 1，恰好等于不算")
    void belowSafetyStock() {
        assertThat(BalanceService.severityOf(n("9.999"), n("10"))).isEqualTo(1);
        assertThat(BalanceService.severityOf(n("10.000"), n("10"))).isZero();
        assertThat(BalanceService.severityOf(n("10.001"), n("10"))).isZero();
    }

    @Test
    @DisplayName("安全库存为 0 视为不设预警线，正结存一律正常")
    void zeroSafetyStockMeansNoAlert() {
        assertThat(BalanceService.severityOf(n("0.001"), n("0"))).isZero();
        assertThat(BalanceService.severityOf(n("999"), BigDecimal.ZERO)).isZero();
    }

    @Test
    @DisplayName("空值按 0 处理，不抛异常")
    void nullsAreTreatedAsZero() {
        assertThat(BalanceService.severityOf(null, null)).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "quantity", "lastMovementAt", "severity"})
    @DisplayName("四个白名单字段都能排序，且都追加 material_id 保证分页稳定")
    void allowedSortColumns(String key) {
        assertThat(BalanceService.orderBy(key)).endsWith("b.material_id ASC");
    }

    @ParameterizedTest
    @ValueSource(strings = {"quantity; DROP TABLE stock_balance", "1=1", "(select 1)"})
    @DisplayName("白名单外一律回落到默认排序，不进 SQL")
    void rejectsInjection(String malicious) {
        String orderBy = BalanceService.orderBy(malicious);
        assertThat(orderBy).contains("CASE WHEN coalesce(b.quantity");
    }

    @Test
    @DisplayName("默认排序即 M-9 的顺序：严重度降序 → 结存升序 → 名称升序")
    void defaultOrderMatchesM9() {
        assertThat(BalanceService.orderBy(null))
                .startsWith("(CASE WHEN coalesce(b.quantity")
                .contains("DESC, coalesce(b.quantity, 0) ASC, m.name ASC");
    }
}
