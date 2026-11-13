package dan.bank.bank.auth;

import dan.bank.bank.auth.dto.AuthResponse;
import dan.bank.bank.auth.dto.DeviceChallengeResponse;
import dan.bank.bank.auth.dto.LoginResult;
import dan.bank.bank.kyc.FaceImages;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import dan.bank.bank.auth.dto.SignUpRequest;
import dan.bank.bank.auth.dto.UserSummary;
import org.springframework.http.HttpStatus;
import dan.bank.bank.auth.dto.RefreshTokenRequest;
import dan.bank.bank.user.dto.LoginRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<UserSummary> signUp(@Valid @RequestBody SignUpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signUp(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResult> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Device-Token", required = false) String deviceToken,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        LoginResult result = authService.login(request, deviceToken, userAgent);
        // 202: the password was right, but the sign-in is not finished until the new device passes a face check.
        return result instanceof DeviceChallengeResponse ? ResponseEntity.accepted().body(result) : ResponseEntity.ok(result);
    }

    @PostMapping(value = "/device/verify", consumes = "multipart/form-data")
    public ResponseEntity<AuthResponse> verifyDevice(
            @RequestParam("challengeToken") String challengeToken,
            @RequestParam("selfie") MultipartFile selfie,
            @RequestParam(value = "liveness", required = false) List<MultipartFile> liveness) throws IOException {
        return ResponseEntity.ok(authService.completeDeviceChallenge(challengeToken, FaceImages.of(selfie, liveness)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
