package danbank.user.controller;

import danbank.common.dto.ApiResponse;
import danbank.user.dto.TwoFactorCodeRequest;
import danbank.user.dto.TwoFactorSetupResponse;
import danbank.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service TOTP (authenticator app) setup. Flow: POST /setup to get a
 * secret + QR-code URI, add it to an authenticator app, then POST /enable
 * with the code it shows to turn 2FA on.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/2fa")
public class TwoFactorController {

    private final UserService userService;

    public TwoFactorController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/setup")
    @PreAuthorize("@accessPolicy.isSelf(authentication, #userId)")
    public ResponseEntity<ApiResponse<TwoFactorSetupResponse>> setup(@PathVariable long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Scan this into your authenticator app, then confirm with /enable",
                userService.setupTwoFactor(userId)));
    }

    @PostMapping("/enable")
    @PreAuthorize("@accessPolicy.isSelf(authentication, #userId)")
    public ResponseEntity<Void> enable(@PathVariable long userId, @Valid @RequestBody TwoFactorCodeRequest request) {
        userService.enableTwoFactor(userId, request.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/disable")
    @PreAuthorize("@accessPolicy.isSelf(authentication, #userId)")
    public ResponseEntity<Void> disable(@PathVariable long userId, @Valid @RequestBody TwoFactorCodeRequest request) {
        userService.disableTwoFactor(userId, request.code());
        return ResponseEntity.noContent().build();
    }
}
