package io.github.jameshome.galley.modules.identity.domain;

import java.util.Locale;

/** 邀请码状态机（DES-001 §4.5）：unused → used / disabled，后两者为终态。 */
public enum InviteStatus {
    UNUSED,
    USED,
    DISABLED;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static InviteStatus fromDb(String value) {
        return InviteStatus.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
