package dan.bank.bank.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record TransferByNumberRequest(
        @NotNull Long fromAccountId,
        @NotBlank @Pattern(regexp = "^\\d{10}$", message = "Account number must be 10 digits") String toAccountNumber,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        String description) { }
