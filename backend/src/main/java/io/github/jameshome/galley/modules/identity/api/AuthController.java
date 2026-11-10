package io.github.jameshome.galley.modules.identity.api;

import io.github.jameshome.galley.common.web.ClientIp;
import io.github.jameshome.galley.modules.identity.api.dto.IdentityDtos.ChangePasswordRequest;
import io.github.jameshome.galley.modules.identity.api.dto.IdentityDtos.LoginRequest;
import io.github.jameshome.galley.modules.identity.api.dto.IdentityDtos.MeResponse;
import io.github.jameshome.galley.modules.identity.api.dto.IdentityDtos.RegisterRequest;
import io.github.jameshome.galley.modules.identity.application.AppUserDetailsService;
import io.github.jameshome.galley.modules.identity.application.AuthService;
import io.github.jameshome.galley.modules.identity.domain.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AppUserDetailsService detailsService;
    private final SecurityContextRepository contextRepository;

    public AuthController(
            AuthService authService,
            AppUserDetailsService detailsService,
            SecurityContextRepository contextRepository) {
        this.authService = authService;
        this.detailsService = detailsService;
        this.contextRepository = contextRepository;
    }

    /** 前端先取 CSRF 令牌再发写请求；访问该端点会自动下发 XSRF-TOKEN Cookie。 */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    @PostMapping("/login")
    public MeResponse login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        AppUser user = authService.login(body.username(), body.password(), request, response);
        return MeResponse.from(user);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        authService.logout();
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public MeResponse register(
            @Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        AppUser user =
                authService.register(
                        body.inviteCode(),
                        body.username(),
                        body.password(),
                        body.displayName(),
                        ClientIp.from(request));
        return MeResponse.from(user);
    }

    @PatchMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @Valid @RequestBody ChangePasswordRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        authService.changePassword(username, body.oldPassword(), body.newPassword());

        // 改密后必须刷新会话里的权限，否则 MUST_CHANGE_PASSWORD 标记会一直挂着
        UserDetails refreshed = detailsService.loadUserByUsername(username);
        var newAuth =
                UsernamePasswordAuthenticationToken.authenticated(
                        refreshed, null, refreshed.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(newAuth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        AppUser user = detailsService.findDomain(authentication.getName()).orElseThrow();
        return MeResponse.from(user);
    }
}
