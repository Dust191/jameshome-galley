package io.github.jameshome.galley.modules.identity.config;

import io.github.jameshome.galley.common.error.ApiError;
import io.github.jameshome.galley.common.error.ErrorCode;
import io.github.jameshome.galley.common.web.TraceIdFilter;
import io.github.jameshome.galley.modules.identity.application.AppUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code must_change_password} 为真时，只放行改密、登出、查询自己三条路径。
 *
 * <p>不在每个接口里写判断，是因为漏一个就等于没做——放在过滤器里，新增接口不会意外绕过。
 */
@Component
public class MustChangePasswordFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED =
            Set.of(
                    "/api/v1/auth/password",
                    "/api/v1/auth/logout",
                    "/api/v1/auth/me",
                    "/api/v1/auth/csrf");

    private final ObjectMapper objectMapper;

    public MustChangePasswordFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && isFlagged(auth)) {
            String path = request.getRequestURI();
            if (!ALLOWED.contains(path)) {
                response.setStatus(ErrorCode.PASSWORD_CHANGE_REQUIRED.status().value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                objectMapper.writeValue(
                        response.getWriter(),
                        new ApiError(
                                ErrorCode.PASSWORD_CHANGE_REQUIRED.name(),
                                "首次登录或密码被重置后必须先改密",
                                null,
                                MDC.get(TraceIdFilter.MDC_KEY)));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean isFlagged(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(AppUserDetailsService.MUST_CHANGE_PASSWORD::equals);
    }
}
