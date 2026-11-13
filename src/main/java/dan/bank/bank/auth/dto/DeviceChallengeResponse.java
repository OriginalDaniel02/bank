package dan.bank.bank.auth.dto;

/** The password was right but this device is new: prove it is you with your face to finish signing in. */
public record DeviceChallengeResponse(String status, String challengeToken, String message, int expiresInSeconds) implements LoginResult { }
