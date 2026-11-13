package dan.bank.bank.auth.dto;

/** What a sign-in returns: either the tokens, or a request to verify a new device first. */
public sealed interface LoginResult permits AuthResponse, DeviceChallengeResponse { }
