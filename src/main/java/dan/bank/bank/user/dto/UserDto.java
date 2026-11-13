package dan.bank.bank.user.dto;

import java.time.LocalDate;

public record UserDto(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String  address,
        LocalDate dateOfBirth
) {
}
