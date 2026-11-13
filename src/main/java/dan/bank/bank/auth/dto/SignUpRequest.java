package dan.bank.bank.auth.dto;

import dan.bank.bank.user.dto.PasswordRules;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank(message = "First name is required") @Size(max = 50, message = "First name is too long") String firstName,
        @NotBlank(message = "Last name is required") @Size(max = 50, message = "Last name is too long") String lastName,
        @NotBlank(message = "Email is required") @Email(message = "Email address is invalid") String email,
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Phone number is invalid") String phoneNumber,
        @NotBlank(message = "Password is required")
        @Pattern(regexp = PasswordRules.REGEX, message = PasswordRules.MESSAGE) String password,
        @AssertTrue(message = "You must accept the terms to open an account") boolean termsAccepted
) { }
