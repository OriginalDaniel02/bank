package dan.bank.bank.Account.dtos;
import dan.bank.bank.Account.model.AccountStatus;
import dan.bank.bank.Account.model.AccountType;
import java.math.BigDecimal;
public record AccountDto(long id, long userId, String accountName, String accountNumber, BigDecimal balance, AccountType type, AccountStatus status) { }
