package danbank.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email address is invalid")
        String email,
        @NotBlank(message = "Email is required")
        String password,
        // Required only when the account has two-factor authentication enabled.
        String totpCode
) {
}
