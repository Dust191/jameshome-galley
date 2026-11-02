package io.github.jameshome.galley.modules.material.domain;

import java.util.UUID;

/** 物料分类（领域对象）。MVP 只允许一级，{@code parentId} 为后续层级预留。 */
public record Category(UUID id, String name, UUID parentId, int sortOrder, DictStatus status) {}
