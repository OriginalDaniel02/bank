package dan.bank.bank.user.dto;

public record UserResponse<T>(
        String message,
        T data) {
}
