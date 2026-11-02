package io.github.jameshome.galley.modules.identity.application;

import io.github.jameshome.galley.common.id.UuidV7;
import io.github.jameshome.galley.modules.identity.domain.Role;
import io.github.jameshome.galley.modules.identity.domain.UserStatus;
import io.github.jameshome.galley.modules.identity.infrastructure.AppUserDO;
import io.github.jameshome.galley.modules.identity.infrastructure.AppUserMapper;
import java.security.SecureRandom;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 空库时引导出第一个管理员。
 *
 * <p>只在 {@code app_user} 为空时执行一次；之后即使环境变量还在也不会重建——否则删掉的账号会在重启后复活。 口令优先取环境变量；未提供则生成 16 位随机口令并打印一次，且置
 * {@code must_change_password}， 逼首次登录就换掉。
 */
@Component
public class BootstrapAdmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);
    private static final String ALPHABET =
            "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int GENERATED_LENGTH = 16;

    private final AppUserMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String configuredPassword;

    public BootstrapAdmin(
            AppUserMapper mapper,
            PasswordEncoder passwordEncoder,
            @Value("${galley.bootstrap.admin-username:admin}") String username,
            @Value("${galley.bootstrap.admin-password:}") String configuredPassword) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.configuredPassword = configuredPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (mapper.selectCount(null) > 0) {
            return;
        }

        boolean generated = configuredPassword == null || configuredPassword.isBlank();
        String rawPassword = generated ? randomPassword() : configuredPassword;
        if (!generated && rawPassword.length() < 8) {
            throw new IllegalStateException("GALLEY_BOOTSTRAP_ADMIN_PASSWORD 至少 8 位");
        }

        Instant now = Instant.now();
        AppUserDO row = new AppUserDO();
        row.setId(UuidV7.generate());
        row.setUsername(username);
        row.setDisplayName("管理员");
        row.setPasswordHash(passwordEncoder.encode(rawPassword));
        row.setRole(Role.ADMIN.dbValue());
        row.setStatus(UserStatus.ACTIVE.dbValue());
        row.setMustChangePassword(generated);
        row.setPasswordUpdatedAt(now);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        mapper.insert(row);

        if (generated) {
            log.warn("已创建初始管理员 {}，一次性口令：{}（首次登录后必须修改）", username, rawPassword);
        } else {
            log.info("已创建初始管理员 {}（口令来自环境变量）", username);
        }
    }

    private static String randomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
