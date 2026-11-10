package io.github.jameshome.galley.common.web;

import jakarta.servlet.http.HttpServletRequest;

/** 客户端 IP 的统一取法，供限流与审计使用。 */
public final class ClientIp {

    private ClientIp() {}

    /**
     * 取客户端 IP。
     *
     * <p>生产环境经过 Caddy 反向代理，真实地址在 {@code X-Forwarded-For} 首跳（Caddy 默认追加）。
     * 这里只取首跳而不信任整条链：链条由调用方控制，只有第一跳不可伪造。 直连（本地开发、集成测试）时回退到 {@link
     * HttpServletRequest#getRemoteAddr()}。
     */
    public static String from(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
