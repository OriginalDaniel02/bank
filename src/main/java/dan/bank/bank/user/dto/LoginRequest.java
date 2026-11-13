package dan.bank.bank.user.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

/** Log in with an email address or a phone number. The JSON field may be "identifier" or "email". */
public record LoginRequest(
        @JsonAlias("email")
        @NotBlank(message = "Email or phone number is required")
        String identifier,
        @NotBlank(message = "Password is required")
        String password) {
}
