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
    private final String unknownUserHash;

    public AuthService(
            UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder, TokenService tokenService,
                       @Value("${jwt.refresh-token-days}") long refreshTokenDays,
                       UserService userService, AccountService accountService, LoginThrottle loginThrottle) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.refreshTokenDays = refreshTokenDays;
        this.userService = userService;
        this.accountService = accountService;
        this.loginThrottle = loginThrottle;
        this.unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
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
        return createTokenPair(user);
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

    /**
     * Changes the password, signs the user out everywhere else (all refresh tokens are revoked) and returns a fresh
     * token pair so the device that made the change stays signed in.
     */
    @Transactional
    public AuthResponse changePassword(long userId, String currentPassword, String newPassword) {
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

    private AuthResponse createTokenPair(User user) {
        String accessToken = tokenService.createAccessToken(user.getEmail());
        String refreshToken = UUID.randomUUID().toString();
        Instant refreshExpiration = Instant.now().plus(refreshTokenDays, ChronoUnit.DAYS);
        refreshTokenRepository.save(new RefreshToken(hashToken(refreshToken), user, refreshExpiration));

        return new AuthResponse(accessToken, refreshToken, "Bearer", tokenService.accessTokenExpiration(),
                tokenService.accessTokenSeconds(), UserSummary.from(user));
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
