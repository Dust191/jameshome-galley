package io.github.jameshome.galley.modules.identity.domain;

import java.util.Locale;

/** 账号角色。权限矩阵见 DES-002 §5；存储为小写字符串，便于阅读与迁移。 */
public enum Role {
    ADMIN,
    EDITOR,
    VIEWER;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Role fromDb(String value) {
        return Role.valueOf(value.toUpperCase(Locale.ROOT));
    }

    public String authority() {
        return "ROLE_" + name();
    }
}
