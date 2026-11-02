package io.github.jameshome.galley.common.web;

import java.util.List;

/** 分页响应（DES-002 §2）：{@code pageSize} 上限 100，超出由调用方截断并置 {@code capped}。 */
public record PageResult<T>(List<T> items, int page, int pageSize, long total, boolean capped) {

    public static <T> PageResult<T> of(List<T> items, int page, int pageSize, long total) {
        return new PageResult<>(items, page, pageSize, total, false);
    }
}
