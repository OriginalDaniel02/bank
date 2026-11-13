package dan.bank.bank.auth;

import dan.bank.bank.auth.dto.AuthResponse;
import dan.bank.bank.auth.dto.ChangePasswordRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class PasswordController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    public PasswordController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    @PostMapping("/password")
    public ResponseEntity<AuthResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken) {
        return ResponseEntity.ok(authService.changePassword(currentUser.id(), request.currentPassword(), request.newPassword(), deviceToken));
    }
}
