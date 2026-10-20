package danbank.account.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record MoneyRequest(
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2, message = "Amount must have at most 2 decimal places")
        BigDecimal amount,
        String description,
        // Optional client-supplied key. Retrying a request with the same key returns
        // the original result instead of moving money twice.
        @Size(max = 255)
        String idempotencyKey
) {
}
