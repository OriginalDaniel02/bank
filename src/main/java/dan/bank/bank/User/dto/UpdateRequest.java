package dan.bank.bank.User.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateRequest(
        @Size(min = 2, max = 50, message = "First name must be 2-50 characters")
        String firstName,
        @Size(min = 2, max = 50, message = "Last name must be 2-50 characters")
        String lastName,
        @Pattern(
                regexp = "^\\+?[0-9]{10,15}$",
                message = "Phone number is invalid"
        )
        String phoneNumber,
        @Size(min = 2, max = 200, message = "Address must be 2-200 characters")
        String address,
        @Email(message = "Enter valid email")
        String email,
        // Same rule as sign-up, so a password can't be weakened through an update.
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%&*!])\\S{8,64}$",
                message = "Password must be 8–64 characters and contain uppercase, lowercase, number and special character"
        )
        String password,

        @Past(message = "Date of birth must be in the past")
        LocalDate dob
) { }
