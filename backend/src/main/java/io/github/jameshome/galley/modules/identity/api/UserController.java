package io.github.jameshome.galley.modules.identity.api;

import io.github.jameshome.galley.modules.identity.api.dto.IdentityDtos.ResetPasswordResponse;
import io.github.jameshome.galley.modules.identity.api.dto.IdentityDtos.UpdateUserRequest;
import io.github.jameshome.galley.modules.identity.api.dto.IdentityDtos.UserView;
import io.github.jameshome.galley.modules.identity.application.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserView> list() {
        return userService.list().stream().map(UserView::from).toList();
    }

    @PatchMapping("/{id}")
    public UserView update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest body) {
        return UserView.from(
                userService.update(id, body.displayName(), body.role(), body.status()));
    }

    @PostMapping("/{id}/reset-password")
    public ResetPasswordResponse resetPassword(@PathVariable UUID id) {
        return new ResetPasswordResponse(userService.resetPassword(id));
    }
}
