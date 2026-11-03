package io.github.jameshome.galley.modules.identity.application;

import io.github.jameshome.galley.common.error.BusinessException;
import io.github.jameshome.galley.common.error.ErrorCode;
import io.github.jameshome.galley.common.id.UuidV7;
import io.github.jameshome.galley.modules.identity.domain.AppUser;
import io.github.jameshome.galley.modules.identity.domain.InviteStatus;
import io.github.jameshome.galley.modules.identity.domain.Role;
import io.github.jameshome.galley.modules.identity.domain.UserStatus;
import io.github.jameshome.galley.modules.identity.infrastructure.AppUserDO;
import io.github.jameshome.galley.modules.identity.infrastructure.AppUserMapper;
import io.github.jameshome.galley.modules.identity.infrastructure.InviteCodeDO;
import io.github.jameshome.galley.modules.identity.infrastructure.InviteCodeMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 登录、注册、改密。会话由 Spring Session JDBC 持久化，不引入 JWT（ADR-0001 §2.6）。 */
@Service
public class AuthService {

    /** 连续失败 5 次锁定 15 分钟（DES-002 §4.1）。进程内计数，单实例部署够用。 */
    private static final int MAX_FAILURES = 5;

    private static final Duration LOCK = Duration.ofMinutes(15);
    private static final String INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final AppUserMapper userMapper;
    private final InviteCodeMapper inviteMapper;
    private final PasswordEncoder passwordEncoder;
    private final AppUserDetailsService detailsService;
    private final Map<String, Failure> failures = new ConcurrentHashMap<>();

    public AuthService(
            AuthenticationManager authenticationManager,
            SecurityContextRepository contextRepository,
            AppUserMapper userMapper,
            InviteCodeMapper inviteMapper,
            PasswordEncoder passwordEncoder,
            AppUserDetailsService detailsService) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.userMapper = userMapper;
        this.inviteMapper = inviteMapper;
        this.passwordEncoder = passwordEncoder;
        this.detailsService = detailsService;
    }

    public AppUser login(
            String username,
            String rawPassword,
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response) {
        String key = username.toLowerCase(java.util.Locale.ROOT);
        Failure f = failures.get(key);
        if (f != null && f.lockedUntil != null && f.lockedUntil.isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "失败次数过多，请稍后再试");
        }

        try {
            Authentication auth =
                    authenticationManager.authenticate(
                            UsernamePasswordAuthenticationToken.unauthenticated(
                                    username, rawPassword));
            failures.remove(key);

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, request, response);

            return detailsService
                    .findDomain(username)
                    .orElseThrow(
                            () ->
                                    new BusinessException(
                                            ErrorCode.AUTH_INVALID_CREDENTIALS, "账号或密码不正确"));
        } catch (BadCredentialsException ex) {
            failures.merge(
                    key,
                    new Failure(1, null),
                    (old, add) -> {
                        int count = old.count + 1;
                        Instant until = count >= MAX_FAILURES ? Instant.now().plus(LOCK) : null;
                        return new Failure(count, until);
                    });
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "账号或密码不正确");
        }
    }

    public void logout() {
        SecurityContextHolder.clearContext();
    }

    /** 注册。三种无效邀请码对外返回同一错误码，不区分"不存在/已使用/已禁用"（D-18）。 */
    @Transactional
    public AppUser register(
            String inviteCode, String username, String rawPassword, String displayName) {
        InviteCodeDO invite =
                inviteMapper
                        .findByCode(inviteCode)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.INVITE_CODE_INVALID, "邀请码无效"));

        if (InviteStatus.fromDb(invite.getStatus()) != InviteStatus.UNUSED) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID, "邀请码无效");
        }
        if (userMapper.findByUsername(username).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_KEY, "账号名已被占用", "username");
        }

        Instant now = Instant.now();
        AppUserDO row = new AppUserDO();
        row.setId(UuidV7.generate());
        row.setUsername(username);
        // 显示名未填时以账号名代替：注册表单只收账号名与密码两栏
        row.setDisplayName(
                displayName == null || displayName.isBlank() ? username : displayName.trim());
        row.setPasswordHash(passwordEncoder.encode(rawPassword));
        row.setRole(Role.VIEWER.dbValue());
        row.setStatus(UserStatus.ACTIVE.dbValue());
        row.setMustChangePassword(false);
        row.setPasswordUpdatedAt(now);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        userMapper.insert(row);

        invite.setStatus(InviteStatus.USED.dbValue());
        invite.setUsedBy(row.getId());
        invite.setUsedAt(now);
        invite.setUpdatedAt(now);
        inviteMapper.updateById(invite);

        return AppUserDetailsService.toDomain(row);
    }

    @Transactional
    public void changePassword(String username, String oldPassword, String newPassword) {
        AppUserDO row =
                userMapper
                        .findByUsername(username)
                        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED, "会话已失效"));

        if (!passwordEncoder.matches(oldPassword, row.getPasswordHash())) {
            throw new BusinessException(
                    ErrorCode.AUTH_INVALID_CREDENTIALS, "原密码不正确", "oldPassword");
        }

        Instant now = Instant.now();
        row.setPasswordHash(passwordEncoder.encode(newPassword));
        row.setMustChangePassword(false);
        row.setPasswordUpdatedAt(now);
        row.setUpdatedAt(now);
        userMapper.updateById(row);
    }

    /** 生成 8 位加密随机码。去掉易混字符 I/O/0/1，避免口头传递出错。 */
    public static String randomCode(java.security.SecureRandom random) {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(INVITE_ALPHABET.charAt(random.nextInt(INVITE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private record Failure(int count, Instant lockedUntil) {}
}
