package danbank.user.dto;

public record TwoFactorSetupResponse(String secret, String otpAuthUri) {
}
