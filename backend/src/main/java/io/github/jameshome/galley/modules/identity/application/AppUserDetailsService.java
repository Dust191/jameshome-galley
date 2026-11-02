package io.github.jameshome.galley.modules.identity.application;

import io.github.jameshome.galley.modules.identity.domain.AppUser;
import io.github.jameshome.galley.modules.identity.domain.Role;
import io.github.jameshome.galley.modules.identity.domain.UserStatus;
import io.github.jameshome.galley.modules.identity.infrastructure.AppUserDO;
import io.github.jameshome.galley.modules.identity.infrastructure.AppUserMapper;
import java.util.Optional;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** 把库里的账号翻译成 Spring Security 能认的 {@link UserDetails}。 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    /** 登录后必须先改密的标记；由 {@code MustChangePasswordFilter} 拦截除改密外的请求。 */
    public static final String MUST_CHANGE_PASSWORD = "MUST_CHANGE_PASSWORD";

    private final AppUserMapper mapper;

    public AppUserDetailsService(AppUserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUserDO row =
                mapper.findByUsername(username)
                        .orElseThrow(() -> new UsernameNotFoundException("账号不存在"));

        boolean enabled = UserStatus.fromDb(row.getStatus()) == UserStatus.ACTIVE;
        java.util.List<String> authorities = new java.util.ArrayList<>();
        authorities.add(Role.fromDb(row.getRole()).authority());
        if (Boolean.TRUE.equals(row.getMustChangePassword())) {
            authorities.add(MUST_CHANGE_PASSWORD);
        }
        return User.withUsername(row.getUsername())
                .password(row.getPasswordHash())
                .authorities(authorities.toArray(String[]::new))
                .disabled(!enabled)
                .build();
    }

    /** 供业务层按账号名取领域对象，避免各处重复做 DO → 领域对象的转换。 */
    public Optional<AppUser> findDomain(String username) {
        return mapper.findByUsername(username).map(AppUserDetailsService::toDomain);
    }

    static AppUser toDomain(AppUserDO row) {
        return new AppUser(
                row.getId(),
                row.getUsername(),
                row.getDisplayName(),
                row.getPasswordHash(),
                Role.fromDb(row.getRole()),
                UserStatus.fromDb(row.getStatus()),
                Boolean.TRUE.equals(row.getMustChangePassword()),
                row.getPasswordUpdatedAt());
    }
}
