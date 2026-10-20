package danbank.account.dtos;

import danbank.account.model.Account;
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
) {

    public static AccountDto from(Account account) {
        return new AccountDto(
                account.getId(),
                account.getUser().getId(),
                account.getAccountName(),
                account.getAccountNumber(),
                account.getBalance(),
                account.getType(),
                account.getStatus()
        );
    }
}
