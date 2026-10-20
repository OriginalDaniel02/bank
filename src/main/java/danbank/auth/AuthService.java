package danbank.auth;

import danbank.auth.dto.AuthResponse;
import danbank.auth.model.RefreshToken;
import danbank.auth.repository.RefreshTokenRepository;
import danbank.common.audit.Audit;
import danbank.notification.service.NotificationService;
import danbank.security.TotpService;
import danbank.user.dto.LoginRequest;
import danbank.user.exception.InvalidCredentialsException;
import danbank.user.exception.TwoFactorRequiredException;
import danbank.user.exception.UserLockedException;
import danbank.user.model.User;
import danbank.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final TotpService totpService;
    private final NotificationService notificationService;
    private final long refreshTokenDays;
    private final int maxFailedLoginAttempts;
    private final Duration lockoutDuration;

    public AuthService(
            UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder, TokenService tokenService,
            TotpService totpService, NotificationService notificationService,
            @Value("${jwt.refresh-token-days}") long refreshTokenDays,
            @Value("${bank.login.max-failed-attempts}") int maxFailedLoginAttempts,
            @Value("${bank.login.lockout-minutes}") long lockoutMinutes) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.totpService = totpService;
        this.notificationService = notificationService;
        this.refreshTokenDays = refreshTokenDays;
        this.maxFailedLoginAttempts = maxFailedLoginAttempts;
        this.lockoutDuration = Duration.ofMinutes(lockoutMinutes);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().strip().toLowerCase();
        User user = userRepository.findByEmail(email).orElseThrow(this::invalidCredentials);

        if (user.isLocked()) {
            Audit.log("LOGIN_BLOCKED_LOCKED", "userId={}", user.getId());
            throw new UserLockedException(user.getLockedUntil());
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            recordFailedLoginAndMaybeNotify(user);
            throw invalidCredentials();
        }

        if (user.isTwoFactorEnabled()) {
            if (request.totpCode() == null || request.totpCode().isBlank()) {
                throw new TwoFactorRequiredException();
            }
            if (!totpService.verifyCode(user.getTwoFactorSecret(), request.totpCode())) {
                recordFailedLoginAndMaybeNotify(user);
                throw invalidCredentials();
            }
        }

        user.recordSuccessfulLogin();
        Audit.log("LOGIN_SUCCESS", "userId={}", user.getId());
        return createTokenPair(user);
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
        return createTokenPair(storedToken.getUser());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken)).ifPresent(token -> token.setRevoked(true));
    }

    private void recordFailedLoginAndMaybeNotify(User user) {
        user.recordFailedLogin(maxFailedLoginAttempts, lockoutDuration);
        Audit.log("LOGIN_FAILURE", "userId={} failedAttempts={}", user.getId(), user.getFailedLoginAttempts());
        if (user.isLocked()) {
            notificationService.notify(user.getId(), "SECURITY_ALERT",
                    "Your account was locked after too many failed login attempts.");
        }
    }

    private AuthResponse createTokenPair(User user) {
        String accessToken = tokenService.createAccessToken(user.getEmail());
        String refreshToken = UUID.randomUUID().toString();
        Instant refreshExpiration = Instant.now().plus(refreshTokenDays, ChronoUnit.DAYS);
        refreshTokenRepository.save(new RefreshToken(hashToken(refreshToken), user, refreshExpiration));

        return new AuthResponse(accessToken, refreshToken, "Bearer", tokenService.accessTokenExpiration());
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

    private InvalidCredentialsException invalidCredentials() {
        return new InvalidCredentialsException();
    }
}
