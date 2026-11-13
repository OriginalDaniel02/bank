package dan.bank.bank.auth;

import dan.bank.bank.auth.dto.AuthResponse;
import dan.bank.bank.account.service.AccountService;
import dan.bank.bank.auth.dto.SignUpRequest;
import dan.bank.bank.auth.dto.UserSummary;
import dan.bank.bank.user.dto.UserDto;
import dan.bank.bank.user.dto.UserRequest;
import dan.bank.bank.user.service.UserService;
import dan.bank.bank.auth.model.RefreshToken;
import dan.bank.bank.auth.repository.RefreshTokenRepository;
import dan.bank.bank.user.dto.LoginRequest;
import dan.bank.bank.user.model.User;
import dan.bank.bank.user.repository.UserRepository;
import dan.bank.bank.auth.dto.DeviceChallengeResponse;
import dan.bank.bank.auth.dto.LoginResult;
import dan.bank.bank.device.DeviceLabel;
import dan.bank.bank.device.DeviceService;
import dan.bank.bank.device.DeviceVerification;
import dan.bank.bank.kyc.FaceImages;
import dan.bank.bank.notification.NotificationService;
import dan.bank.bank.notification.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final long refreshTokenDays;
    private final UserService userService;
    private final AccountService accountService;
    private final LoginThrottle loginThrottle;
    private final DeviceService deviceService;
    private final DeviceVerification deviceVerification;
    private final NotificationService notifications;
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final String unknownUserHash;

    public AuthService(
            UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder, TokenService tokenService,
                       @Value("${jwt.refresh-token-days}") long refreshTokenDays,
                       UserService userService, AccountService accountService, LoginThrottle loginThrottle,
                       DeviceService deviceService, DeviceVerification deviceVerification, NotificationService notifications) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokenDays = refreshTokenDays;
        this.userService = userService;
        this.accountService = accountService;
        this.loginThrottle = loginThrottle;
        this.deviceService = deviceService;
        this.deviceVerification = deviceVerification;
        this.notifications = notifications;
        this.unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResult login(LoginRequest request, String deviceToken, String userAgent) {
        String identifier = request.identifier().strip();
        loginThrottle.check(identifier);
        User user = (identifier.contains("@")
                ? userRepository.findByEmail(identifier)
                : userRepository.findByPhoneNumber(identifier.replaceAll("\\s", "")))
                .orElse(null);
        // Always do one password check, even for an unknown identifier, so the response time does not reveal
        // whether an account exists.
        boolean passwordMatches = passwordEncoder.matches(request.password(), user != null ? user.getPassword() : unknownUserHash);
        if (user == null || !passwordMatches) {
            loginThrottle.recordFailure(identifier);
            throw invalidCredentials();
        }
        loginThrottle.clear(identifier);
        return continueSignIn(user, deviceToken, DeviceLabel.from(userAgent));
    }

    /** Creates the user and their first (savings) account together, so a new user can bank immediately. */
    @Transactional
    public UserSummary signUp(SignUpRequest request) {
        UserDto created = userService.signUp(new UserRequest(
                request.firstName().strip(), request.lastName().strip(), request.phoneNumber().replaceAll("\\s", ""),
                null, request.email().strip(), request.password(), null)).data();
        accountService.createAccount(created.id());
        return UserSummary.from(userRepository.getReferenceById(created.id()));
    }

    /** The password is right. Now decide whether this device may come in, or must prove it is the owner first. */
    private LoginResult continueSignIn(User user, String deviceToken, String label) {
        DeviceService.Check check = deviceService.check(user.getId(), deviceToken);
        switch (check.decision()) {
            case TRUSTED -> {
                return createTokenPair(user, check.deviceId(), null);
            }
            case ENROLL_FIRST -> {
                DeviceService.Enrollment first = deviceService.enroll(user.getId(), label);
                return createTokenPair(user, first.deviceId(), first.deviceToken());
            }
            case UNVERIFIED_ALLOWED -> {
                // No face provider is configured, so we cannot check. Let the owner know instead.
                DeviceService.Enrollment added = deviceService.enroll(user.getId(), label);
                alert(user.getId(), "New device signed in", label + " just signed in to your account. If this was not you, change your password now.");
                return createTokenPair(user, added.deviceId(), added.deviceToken());
            }
            default -> {
                String secret = deviceService.createChallenge(user.getId(), label);
                alert(user.getId(), "New device needs verification", label + " tried to sign in with your password and is waiting for a face check. If this was not you, change your password now.");
                return new DeviceChallengeResponse("DEVICE_VERIFICATION_REQUIRED", secret,
                        "This looks like a new device. Verify your face to finish signing in.", (int) DeviceService.CHALLENGE_LIFETIME.toSeconds());
            }
        }
    }

    /** Finishes a new-device sign-in once the face check has passed. */
    public AuthResponse completeDeviceChallenge(String challengeToken, FaceImages images) {
        DeviceVerification.Passed passed = deviceVerification.verify(challengeToken, images);
        User user = userRepository.findById(passed.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This verification has expired. Please sign in again."));
        alert(user.getId(), "New device verified", "A new device passed the face check and can now sign in to your account. You can remove it any time in Settings.");
        return createTokenPair(user, passed.enrollment().deviceId(), passed.enrollment().deviceToken());
    }

    private void alert(long userId, String title, String message) {
        try {
            notifications.notifyIndependently(userId, NotificationType.SECURITY, title, message);
        } catch (RuntimeException e) {
            log.warn("Could not create a security alert", e);
        }
    }

    /**
     * Changes the password, signs the user out everywhere else (all refresh tokens are revoked) and returns a fresh
     * token pair so the device that made the change stays signed in.
     */
    @Transactional
    public AuthResponse changePassword(long userId, String currentPassword, String newPassword, String deviceToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a password you have not used before");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        refreshTokenRepository.revokeAllForUser(userId);
        alert(userId, "Password changed", "Your password was changed and every other device was signed out. If this was not you, contact support right away.");
        return createTokenPair(user, deviceService.activeDeviceId(userId, deviceToken), null);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (storedToken.isRevoked() || storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has expired or was revoked");
        }

        // Rotation means each refresh token can be used only once.
        storedToken.setRevoked(true);
        return createTokenPair(storedToken.getUser(), storedToken.getDeviceId(), null);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken)).ifPresent(token -> token.setRevoked(true));
    }

    private AuthResponse createTokenPair(User user, Long deviceId, String newDeviceToken) {
        String accessToken = tokenService.createAccessToken(user.getEmail());
        String refreshToken = UUID.randomUUID().toString();
        Instant refreshExpiration = Instant.now().plus(refreshTokenDays, ChronoUnit.DAYS);
        RefreshToken stored = new RefreshToken(hashToken(refreshToken), user, refreshExpiration);
        stored.setDeviceId(deviceId);
        refreshTokenRepository.save(stored);

        return new AuthResponse(accessToken, refreshToken, "Bearer", tokenService.accessTokenExpiration(),
                tokenService.accessTokenSeconds(), UserSummary.from(user), newDeviceToken);
    }

    private String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
