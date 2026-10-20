package danbank.account.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TransferRequest(
        @NotNull Long fromAccountId,
        @NotNull Long toAccountId,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2, message = "Amount must have at most 2 decimal places")
        BigDecimal amount,
        String description,
        // Optional client-supplied key shared by both ledger legs. Retrying with the
        // same key returns the original transfer instead of moving money twice.
        @Size(max = 255)
        String idempotencyKey
) {
}
