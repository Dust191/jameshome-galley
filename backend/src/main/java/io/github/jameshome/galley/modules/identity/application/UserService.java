package io.github.jameshome.galley.modules.identity.application;

import io.github.jameshome.galley.common.audit.AuditService;
import io.github.jameshome.galley.common.error.BusinessException;
import io.github.jameshome.galley.common.error.ErrorCode;
import io.github.jameshome.galley.modules.identity.domain.AppUser;
import io.github.jameshome.galley.modules.identity.domain.Role;
import io.github.jameshome.galley.modules.identity.domain.UserStatus;
import io.github.jameshome.galley.modules.identity.infrastructure.AppUserDO;
import io.github.jameshome.galley.modules.identity.infrastructure.AppUserMapper;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 账号管理（仅 admin）：列角色、改显示名/角色/状态、重置口令。 */
@Service
public class UserService {

    private static final String TEMP_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

    private final AppUserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final CurrentUserService currentUser;
    private final SecureRandom random = new SecureRandom();

    public UserService(
            AppUserMapper mapper,
            PasswordEncoder passwordEncoder,
            AuditService audit,
            CurrentUserService currentUser) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    public List<AppUser> list() {
        return mapper.selectList(null).stream()
                .sorted(Comparator.comparing(AppUserDO::getUsername))
                .map(AppUserDetailsService::toDomain)
                .toList();
    }

    @Transactional
    public AppUser update(UUID id, String displayName, Role role, UserStatus status) {
        AppUserDO row = mapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        String before = audit.snapshot(sanitizedUser(row));
        Instant now = Instant.now();
        if (displayName != null && !displayName.isBlank()) {
            row.setDisplayName(displayName);
        }
        if (role != null) {
            row.setRole(role.dbValue());
        }
        if (status != null) {
            row.setStatus(status.dbValue());
        }
        row.setUpdatedAt(now);
        mapper.updateById(row);
        audit.record(
                currentUser.requireId(),
                "UPDATE",
                "app_user",
                id,
                before,
                audit.snapshot(sanitizedUser(row)));
        return AppUserDetailsService.toDomain(row);
    }

    /** 账号快照：只含账号名、显示名、角色、状态。绝对不能把 {@code AppUserDO} 整个序列化—— 那会把 password_hash 写进审计（红线 5）。 */
    static Map<String, Object> sanitizedUser(AppUserDO row) {
        return Map.of(
                "username", row.getUsername(),
                "displayName", row.getDisplayName() == null ? "" : row.getDisplayName(),
                "role", row.getRole(),
                "status", row.getStatus());
    }

    /** 重置为 12 位临时口令，并置 {@code must_change_password}，用户下次登录必须先改密。 */
    @Transactional
    public String resetPassword(UUID id) {
        AppUserDO row = mapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号不存在");
        }
        String temp = randomPassword();
        row.setPasswordHash(passwordEncoder.encode(temp));
        row.setMustChangePassword(true);
        row.setPasswordUpdatedAt(Instant.now());
        row.setUpdatedAt(Instant.now());
        mapper.updateById(row);
        // 快照只记"要求改密"这个事实；临时口令本身绝不入审计
        audit.record(
                currentUser.requireId(),
                "RESET_PASSWORD",
                "app_user",
                id,
                audit.snapshot(sanitizedUser(row)),
                audit.snapshot(Map.of("mustChangePassword", true)));
        return temp;
    }

    private String randomPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(TEMP_ALPHABET.charAt(random.nextInt(TEMP_ALPHABET.length())));
        }
        return sb.toString();
    }
}
