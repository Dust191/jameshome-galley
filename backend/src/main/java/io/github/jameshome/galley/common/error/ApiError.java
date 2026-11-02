package io.github.jameshome.galley.common.error;

/** 统一错误体（DES-002 §3）：{@code code} 为大写下划线常量，前端据此映射文案。 */
public record ApiError(String code, String message, String field, String traceId) {}
