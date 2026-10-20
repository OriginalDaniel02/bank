package danbank.account.dtos;

import danbank.account.model.Account;
import danbank.account.model.AccountStatus;
import danbank.account.model.AccountType;
import danbank.account.model.Currency;

import java.math.BigDecimal;

public record AccountDto(
        long id,
        long userId,
        String accountName,
        String accountNumber,
        BigDecimal balance,
        Currency currency,
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
                account.getCurrency(),
                account.getType(),
                account.getStatus()
        );
    }
}
