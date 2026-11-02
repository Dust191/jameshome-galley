package io.github.jameshome.galley.modules.material.domain;

import java.util.UUID;

/** 计量单位（领域对象）。不同单位的数量不可相加（红线 9）。 */
public record Unit(UUID id, String name, int sortOrder, DictStatus status) {}
