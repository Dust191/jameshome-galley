package io.github.jameshome.galley.modules.material.domain;

import java.util.Locale;

/**
 * 物料状态机（DES-001 §4.3）：{@code active ⇄ inactive → archived}。
 *
 * <p>{@code archived} 为终态。刻意不允许 {@code active → archived} 直达：归档前必须先停用， 让"还能不能恢复"这一步显式发生，避免误操作后无法回头。
 */
public enum MaterialStatus {
    ACTIVE,
    INACTIVE,
    ARCHIVED;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MaterialStatus fromDb(String value) {
        return MaterialStatus.valueOf(value.toUpperCase(Locale.ROOT));
    }

    public boolean canTransitionTo(MaterialStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case ACTIVE -> target == INACTIVE;
            case INACTIVE -> target == ACTIVE || target == ARCHIVED;
            case ARCHIVED -> false;
        };
    }
}
