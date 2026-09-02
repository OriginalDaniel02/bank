package dan.bank.bank.User.dto;

public record UserResponse<T>(
        String message,
        T data) {
}
