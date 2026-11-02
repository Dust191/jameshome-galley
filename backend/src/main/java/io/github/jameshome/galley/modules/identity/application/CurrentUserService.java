package io.github.jameshome.galley.modules.identity.application;

import io.github.jameshome.galley.common.error.BusinessException;
import io.github.jameshome.galley.common.error.ErrorCode;
import io.github.jameshome.galley.modules.identity.domain.AppUser;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * 当前登录人。
 *
 * <p>审计列（{@code operator_id}、{@code created_by}）需要的是用户 id 而不是用户名，
 * 但会话里只有用户名。集中在一处换算，避免每个模块各写一遍、各查一次库。
 */
@Service
public class CurrentUserService {

    private final AppUserDetailsService detailsService;

    public CurrentUserService(AppUserDetailsService detailsService) {
        this.detailsService = detailsService;
    }

    public AppUser require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED, "请先登录");
        }
        return detailsService
                .findDomain(auth.getName())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED, "会话已失效，请重新登录"));
    }

    public UUID requireId() {
        return require().id();
    }
}
