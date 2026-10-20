package danbank.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePassword(
        @NotBlank String currentPassword,
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%&*!])\\S{8,64}$",
                message = "Password must be 8-64 characters and contain uppercase, lowercase, number and special character"
        )
        String newPassword
) {
    @Override
    public String toString() {
        return "ChangePassword[hidden]";
    }
}
