package dan.bank.bank.auth.dto;

import java.time.Instant;

/** deviceToken is only present when this sign-in just trusted a new device: the client must keep it. */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant accessTokenExpiresAt,
        long expiresInSeconds,
        UserSummary user,
        String deviceToken
) implements LoginResult { }
