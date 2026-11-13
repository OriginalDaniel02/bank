package dan.bank.bank.account.dto;

public record AccountResponse<T>(
        String message, T data
) {
}
