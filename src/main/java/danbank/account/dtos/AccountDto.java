package danbank.account.dtos;
import danbank.account.model.AccountStatus;
import danbank.account.model.AccountType;
import java.math.BigDecimal;
public record AccountDto(
        long id,
        long userId,
        String accountName,
        String accountNumber,
        BigDecimal balance,
        AccountType type,
        AccountStatus status

) { }
