package io.github.jameshome.galley.modules.identity.api.dto;

import io.github.jameshome.galley.modules.identity.domain.AppUser;
import io.github.jameshome.galley.modules.identity.domain.InviteCode;
import io.github.jameshome.galley.modules.identity.domain.Role;
import io.github.jameshome.galley.modules.identity.domain.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** identity 模块的入参与出参。Controller 不得直接暴露 DO（ArchUnit 规则）。 */
public final class IdentityDtos {

    private IdentityDtos() {}

    public record LoginRequest(
            @NotBlank(message = "账号名不能为空") String username,
            @NotBlank(message = "口令不能为空") String password) {}

    public record RegisterRequest(
            @NotBlank(message = "邀请码不能为空") String inviteCode,
            @NotBlank(message = "账号名不能为空")
                    @Pattern(regexp = "^[a-z0-9_]{3,32}$", message = "账号名只能用小写字母、数字与下划线，3~32 位")
                    String username,
            @NotBlank(message = "口令不能为空") @Size(min = 8, max = 64, message = "口令长度 8~64 位")
                    String password,
            @NotBlank(message = "显示名不能为空") @Size(max = 50, message = "显示名不超过 50 字")
                    String displayName) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "原口令不能为空") String oldPassword,
            @NotBlank(message = "新口令不能为空") @Size(min = 8, max = 64, message = "口令长度 8~64 位")
                    String newPassword) {}

    public record UpdateUserRequest(String displayName, Role role, UserStatus status) {}

    public record MeResponse(
            UUID id,
            String username,
            String displayName,
            String role,
            String status,
            boolean mustChangePassword) {

        public static MeResponse from(AppUser u) {
            return new MeResponse(
                    u.id(),
                    u.username(),
                    u.displayName(),
                    u.role().dbValue(),
                    u.status().dbValue(),
                    u.mustChangePassword());
        }
    }

    public record UserView(
            UUID id,
            String username,
            String displayName,
            String role,
            String status,
            Instant passwordUpdatedAt) {

        public static UserView from(AppUser u) {
            return new UserView(
                    u.id(),
                    u.username(),
                    u.displayName(),
                    u.role().dbValue(),
                    u.status().dbValue(),
                    u.passwordUpdatedAt());
        }
    }

    public record InviteView(
            UUID id, String code, String status, UUID usedBy, Instant usedAt, Instant createdAt) {

        public static InviteView from(InviteCode c) {
            return new InviteView(
                    c.id(), c.code(), c.status().dbValue(), c.usedBy(), c.usedAt(), null);
        }
    }

    public record ResetPasswordResponse(String temporaryPassword) {}
}
