package danbank.user.dto;

import danbank.address.dto.addressResponse;
import danbank.address.model.Address;
import danbank.user.model.User;
import danbank.user.model.UserStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

public record UserResponse(

        Long id,
        String firstName,
        String lastName,
        String phoneNumber,
        String email,
        String passwordHash,
        String bvnHash,
        String bvnLast4,
        LocalDate dateOfBirth,
        UserStatus status

        ) {
        public static UserResponse from(User a) {
                return new UserResponse(
                        a.getId(), a.getFirstName(), a.getLastName(), a.getPhoneNumber(),a.getEmail(),a.getPasswordHash(),
                        a.getBvnHash(), a.getBvnLast4(), a.getDateOfBirth(),a.getStatus());
        }
}
