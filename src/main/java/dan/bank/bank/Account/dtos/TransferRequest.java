package dan.bank.bank.Account.dtos;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
public record TransferRequest(@NotNull Long fromAccountId,
                              @NotNull Long toAccountId,
                              @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
                              String description) { }
