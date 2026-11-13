package dan.bank.bank.account.dto;

import dan.bank.bank.account.model.AccountStatus;
import dan.bank.bank.account.model.AccountType;

public record AccountUpdate(
        AccountType type,
        AccountStatus status
) {
}
