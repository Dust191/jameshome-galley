package io.github.jameshome.galley.modules.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 账号（领域对象，不依赖任何框架）。
 *
 * <p>{@code passwordHash} 为单向哈希，红线 5 禁止可逆加密；{@code mustChangePassword} 为真时， 除改密接口外一律拒绝（DES-002
 * §4.1）。
 */
public record AppUser(
        UUID id,
        String username,
        String displayName,
        String passwordHash,
        Role role,
        UserStatus status,
        boolean mustChangePassword,
        Instant passwordUpdatedAt) {

    public boolean canLogin() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean canWrite() {
        return role == Role.ADMIN || role == Role.EDITOR;
    }
}
