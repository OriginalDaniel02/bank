package dan.bank.bank.User.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record UserRequest(
        @NotBlank(message = "First_name is required")
        String firstName,
        @NotBlank(message = "last_name is required")
        String lastName,
        @NotBlank(message = "phone_number is required")
        @Pattern(
                regexp = "^\\+?[0-9]{10,15}$",
                message = "Phone number is invalid"
        )
        String phoneNumber,

        @NotBlank(message = "Address is required")
        String address,

        @NotBlank(message = "Email is required")
        @Email(message = "Email address is invalid")
        String email,

        @NotBlank(message = "Password is required")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%&*!])\\S{8,64}$",
                message = "Password must be 8–64 characters and contain uppercase, lowercase, number and special character"
        )
        String password,
        @JsonFormat(pattern = "dd-MM-yyyy")
        LocalDate dob) {}
