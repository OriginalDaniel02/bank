package dan.bank.bank.auth.dto;

import dan.bank.bank.user.model.User;

/** The signed-in user as the client needs to see it (never the password or PIN hash). */
public record UserSummary(Long id, String firstName, String lastName, String email,
                          String phoneNumber, int kycTier, String role) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
                user.getPhoneNumber(), user.getKycTier(), user.getRole().name());
    }
}
