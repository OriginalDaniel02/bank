package dan.bank.bank.auth.dto;

import java.time.Instant;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant accessTokenExpiresAt,
        long expiresInSeconds,
        UserSummary user
) { }
