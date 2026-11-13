package dan.bank.bank.auth.dto;

import dan.bank.bank.user.dto.PasswordRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
        @NotBlank(message = "Enter your current password") String currentPassword,
        @NotBlank(message = "Enter a new password") @Pattern(regexp = PasswordRules.REGEX, message = PasswordRules.MESSAGE) String newPassword) { }
