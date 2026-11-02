package io.github.jameshome.galley.modules.material.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 物料状态机（DES-001 §4.3）。
 *
 * <p>重点在"哪些转移必须被拒绝"：归档是终态，允许 active 直达 archived 会让误操作无法回退。
 */
class MaterialStatusTest {

    @Test
    @DisplayName("active 只能停用，不能直接归档")
    void activeCannotArchiveDirectly() {
        assertThat(MaterialStatus.ACTIVE.canTransitionTo(MaterialStatus.INACTIVE)).isTrue();
        assertThat(MaterialStatus.ACTIVE.canTransitionTo(MaterialStatus.ARCHIVED)).isFalse();
    }

    @Test
    @DisplayName("inactive 可以重新启用，也可以归档")
    void inactiveCanReactivateOrArchive() {
        assertThat(MaterialStatus.INACTIVE.canTransitionTo(MaterialStatus.ACTIVE)).isTrue();
        assertThat(MaterialStatus.INACTIVE.canTransitionTo(MaterialStatus.ARCHIVED)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(MaterialStatus.class)
    @DisplayName("归档是终态：除自身外任何目标都不允许")
    void archivedIsTerminal(MaterialStatus target) {
        boolean allowed = MaterialStatus.ARCHIVED.canTransitionTo(target);
        assertThat(allowed).isEqualTo(target == MaterialStatus.ARCHIVED);
    }

    @ParameterizedTest
    @EnumSource(MaterialStatus.class)
    @DisplayName("原地不动（同状态）永远允许，便于 PATCH 只改其他字段时携带 status")
    void sameStatusIsAlwaysAllowed(MaterialStatus status) {
        assertThat(status.canTransitionTo(status)).isTrue();
    }

    @Test
    @DisplayName("与数据库值互转用小写，避免与 API 取值不一致")
    void dbValueIsLowercase() {
        assertThat(MaterialStatus.ACTIVE.dbValue()).isEqualTo("active");
        assertThat(MaterialStatus.fromDb("archived")).isEqualTo(MaterialStatus.ARCHIVED);
    }
}
