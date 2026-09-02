package dan.bank.bank.Account.dtos;

public record AccountResponse<T>(
        String message, T data
) {
}
