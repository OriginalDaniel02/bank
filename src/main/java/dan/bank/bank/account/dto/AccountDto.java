package dan.bank.bank.account.dto;
import dan.bank.bank.account.model.AccountStatus;
import dan.bank.bank.account.model.AccountType;
import java.math.BigDecimal;
public record AccountDto(long id, long userId, String accountName, String accountNumber, BigDecimal balance, AccountType type, AccountStatus status) { }
