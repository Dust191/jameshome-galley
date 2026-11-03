package io.github.jameshome.galley.modules.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 身份领域规则：角色能力、状态机与邀请码可用性。 */
class IdentityDomainTest {

    @Test
    void role_数据库取值与权限名往返一致() {
        for (Role role : Role.values()) {
            assertThat(role.dbValue()).isEqualTo(role.name().toLowerCase());
            assertThat(Role.fromDb(role.dbValue())).isEqualTo(role);
            assertThat(role.authority()).isEqualTo("ROLE_" + role.name());
        }
    }

    @Test
    void userStatus_数据库取值往返一致() {
        for (UserStatus status : UserStatus.values()) {
            assertThat(UserStatus.fromDb(status.dbValue())).isEqualTo(status);
        }
    }

    @Test
    void inviteStatus_数据库取值往返一致() {
        for (InviteStatus status : InviteStatus.values()) {
            assertThat(InviteStatus.fromDb(status.dbValue())).isEqualTo(status);
        }
    }

    @Test
    void appUser_能力随角色与状态变化() {
        assertThat(user(Role.ADMIN, UserStatus.ACTIVE).isAdmin()).isTrue();
        assertThat(user(Role.ADMIN, UserStatus.ACTIVE).canWrite()).isTrue();
        assertThat(user(Role.EDITOR, UserStatus.ACTIVE).canWrite()).isTrue();
        assertThat(user(Role.VIEWER, UserStatus.ACTIVE).canWrite()).isFalse();
        assertThat(user(Role.VIEWER, UserStatus.ACTIVE).isAdmin()).isFalse();

        assertThat(user(Role.ADMIN, UserStatus.ACTIVE).canLogin()).isTrue();
        assertThat(user(Role.ADMIN, UserStatus.DISABLED).canLogin()).isFalse();
    }

    @Test
    void inviteCode_仅未使用可用() {
        assertThat(invite(InviteStatus.UNUSED).usable()).isTrue();
        assertThat(invite(InviteStatus.USED).usable()).isFalse();
        assertThat(invite(InviteStatus.DISABLED).usable()).isFalse();
    }

    private static AppUser user(Role role, UserStatus status) {
        return new AppUser(
                UUID.randomUUID(), "tester", "测试", "hash", role, status, false, Instant.now());
    }

    private static InviteCode invite(InviteStatus status) {
        return new InviteCode(UUID.randomUUID(), "CODE1234", status, null, null, null);
    }
}
