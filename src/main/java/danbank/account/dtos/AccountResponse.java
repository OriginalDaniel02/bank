package danbank.account.dtos;

import danbank.account.model.Account;
import danbank.account.model.AccountStatus;
import danbank.account.model.AccountType;
import danbank.common.dto.ApiResponse;

import java.math.BigDecimal;

public record AccountResponse (
        long id,
        long userId,
        String accountName,
        String accountNumber,
        BigDecimal balance,
        AccountType type,
        AccountStatus status
) {

    public static AccountResponse from(Account a){
        return new AccountResponse(
               a.getId(), a.getUser().getId(), a.getAccountName(),a.getAccountNumber(),a.getBalance()
                , a.getType(),a.getStatus()
        );
    }
}
