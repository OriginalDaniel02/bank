package dan.bank.bank.User.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email address is invalid")
        String email,
        @NotBlank(message = "Email is required")
        String password) {
}
