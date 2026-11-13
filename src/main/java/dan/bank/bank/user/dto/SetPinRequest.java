package dan.bank.bank.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SetPinRequest(
        @NotBlank @Pattern(regexp = "^\\d{4}$", message = "PIN must be exactly 4 digits") String pin,
        @NotBlank(message = "Password is required") String password) { }
