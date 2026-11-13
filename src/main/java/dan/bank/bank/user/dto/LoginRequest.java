package dan.bank.bank.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email address is invalid")
        String email,
        @NotBlank(message = "Password is required")
        String password) {
}
