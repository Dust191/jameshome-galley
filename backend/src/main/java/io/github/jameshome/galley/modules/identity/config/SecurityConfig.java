package io.github.jameshome.galley.modules.identity.config;

import io.github.jameshome.galley.common.error.ApiError;
import io.github.jameshome.galley.common.error.ErrorCode;
import io.github.jameshome.galley.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * 安全配置：会话存库、Cookie + CSRF、JSON 错误体。
 *
 * <p>权限规则与 DES-002 §5 的矩阵一一对应；这里只做"能不能进"，"能改哪些字段"由服务层判断。
 */
@Configuration
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            io.github.jameshome.galley.modules.identity.application.AppUserDetailsService uds,
            PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(uds);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            SecurityContextRepository contextRepository,
            MustChangePasswordFilter mustChangePasswordFilter)
            throws Exception {

        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookiePath("/");
        csrfRepo.setCookieName("XSRF-TOKEN");
        csrfRepo.setHeaderName("X-XSRF-TOKEN");

        http.csrf(
                        csrf ->
                                csrf.csrfTokenRepository(csrfRepo)
                                        .csrfTokenRequestHandler(
                                                new CsrfTokenRequestAttributeHandler())
                                        // 登录与注册也要校验 CSRF：先 GET /auth/csrf 取令牌
                                        .ignoringRequestMatchers("/actuator/**"))
                .securityContext(c -> c.securityContextRepository(contextRepository))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/actuator/health/**", "/actuator/info")
                                        .permitAll()
                                        .requestMatchers(
                                                "/api/v1/auth/login",
                                                "/api/v1/auth/register",
                                                "/api/v1/auth/csrf")
                                        .permitAll()
                                        .requestMatchers(
                                                "/api/v1/users/**", "/api/v1/invite-codes/**")
                                        .hasRole("ADMIN")
                                        // 读接口对所有已登录角色开放：viewer 的职责就是"能看不能改"
                                        // （DES-002 §5、UAT-10）。先列 GET，再列写方法，顺序不能反。
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/categories/**",
                                                "/api/v1/units/**",
                                                "/api/v1/materials/**",
                                                "/api/v1/movements/**",
                                                "/api/v1/balances/**",
                                                "/api/v1/stats/**",
                                                "/api/v1/exports/**")
                                        .authenticated()
                                        // 单位只有 admin 能改（DES-002 §4.3）
                                        .requestMatchers("/api/v1/units/**")
                                        .hasRole("ADMIN")
                                        // 删除物料是 admin 独有
                                        .requestMatchers(HttpMethod.DELETE, "/api/v1/materials/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers(
                                                "/api/v1/categories/**",
                                                "/api/v1/materials/**",
                                                "/api/v1/movements/**",
                                                "/api/v1/files/**",
                                                "/api/v1/imports/**")
                                        .hasAnyRole("ADMIN", "EDITOR")
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(
                                                (req, res, e) ->
                                                        write(res, ErrorCode.AUTH_REQUIRED, "请先登录"))
                                        .accessDeniedHandler(
                                                (req, res, e) ->
                                                        write(
                                                                res,
                                                                ErrorCode.PERMISSION_DENIED,
                                                                "没有这个权限")))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterAfter(
                        mustChangePasswordFilter,
                        org.springframework.security.web.authentication
                                .AnonymousAuthenticationFilter.class);

        return http.build();
    }

    private void write(HttpServletResponse res, ErrorCode code, String message) throws IOException {
        res.setStatus(code.status().value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                res.getWriter(),
                new ApiError(code.name(), message, null, MDC.get(TraceIdFilter.MDC_KEY)));
    }
}
