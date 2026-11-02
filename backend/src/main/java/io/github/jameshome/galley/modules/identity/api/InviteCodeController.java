package io.github.jameshome.galley.modules.identity.api;

import io.github.jameshome.galley.modules.identity.api.dto.IdentityDtos.InviteView;
import io.github.jameshome.galley.modules.identity.application.AppUserDetailsService;
import io.github.jameshome.galley.modules.identity.application.InviteCodeService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invite-codes")
public class InviteCodeController {

    private final InviteCodeService inviteService;
    private final AppUserDetailsService detailsService;

    public InviteCodeController(
            InviteCodeService inviteService, AppUserDetailsService detailsService) {
        this.inviteService = inviteService;
        this.detailsService = detailsService;
    }

    /** 注册前的可用性校验；只回布尔值，不区分无效原因（D-18）。 */
    @PostMapping("/validate")
    public Map<String, Boolean> validate(@RequestBody Map<String, String> body) {
        return Map.of("valid", inviteService.usable(body.get("code")));
    }

    @GetMapping
    public List<InviteView> list(@RequestParam(required = false) String status) {
        return inviteService.list(status).stream().map(InviteView::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InviteView create(Authentication authentication) {
        UUID creator = detailsService.findDomain(authentication.getName()).orElseThrow().id();
        return InviteView.from(inviteService.create(creator));
    }

    @PatchMapping("/{id}")
    public InviteView disable(@PathVariable UUID id) {
        return InviteView.from(inviteService.disable(id));
    }
}
