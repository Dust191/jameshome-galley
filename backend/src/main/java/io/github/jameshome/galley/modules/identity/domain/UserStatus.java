package io.github.jameshome.galley.modules.identity.domain;

import java.util.Locale;

/** 账号状态机（DES-001 §4.4）：active ⇄ disabled。 */
public enum UserStatus {
    ACTIVE,
    DISABLED;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static UserStatus fromDb(String value) {
        return UserStatus.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
