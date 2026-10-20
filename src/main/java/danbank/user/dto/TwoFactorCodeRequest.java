package danbank.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TwoFactorCodeRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "Code must be exactly 6 digits")
        String code
) {
}
