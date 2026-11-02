package io.github.jameshome.galley.modules.material.domain;

import java.util.Locale;

/** 字典表（分类、单位）的启停状态。停用后不再出现在选择器里，但历史数据保留。 */
public enum DictStatus {
    ACTIVE,
    INACTIVE;

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DictStatus fromDb(String value) {
        return DictStatus.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
