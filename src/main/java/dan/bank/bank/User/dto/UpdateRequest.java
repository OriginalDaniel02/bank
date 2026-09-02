package dan.bank.bank.User.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record UpdateRequest(
        @Size(min = 2, max = 50, message = "FirstName should not exceed 200 characters" )
        String firstName,
        @Size(min = 2, max = 50, message = "LastName should not exceed 200 characters" )
        String lastName,
        @Pattern(
                regexp = "^\\+?[0-9]{10,15}$",
                message = "Phone number is invalid"
        )
        String phoneNumber,
        @Size(min = 2, max = 200, message = "FirstName should not exceed 200 characters" )
        String address,
        @Email(message = "Enter valid email")
        String email,
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9\\s])\\S{8,64}$",
                message = "Password must contain uppercase, lowercase, number and special character"
        )
        String password,

        @Past(message = "DOB  must no be in the past")
        LocalDate dob
) {


}
