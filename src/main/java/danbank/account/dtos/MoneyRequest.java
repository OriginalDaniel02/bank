package danbank.account.dtos;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
public record MoneyRequest(
        @NotNull @DecimalMin(value = "0.01")
        BigDecimal amount,
        String description) { }
