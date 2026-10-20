package danbank.user.dto;

import danbank.user.model.User;
import danbank.user.model.UserStatus;

import java.time.Instant;
import java.time.LocalDate;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String phoneNumber,
        String email,
        String bvnLast4,
        LocalDate dateOfBirth,
        UserStatus status,
        boolean twoFactorEnabled,
        Instant lastLoginAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber(),
                user.getEmail(),
                user.getBvnLast4(),
                user.getDateOfBirth(),
                user.getStatus(),
                user.isTwoFactorEnabled(),
                user.getLastLoginAt()
        );
    }
}
