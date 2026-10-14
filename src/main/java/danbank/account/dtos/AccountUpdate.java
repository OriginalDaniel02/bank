package danbank.account.dtos;

import danbank.account.model.AccountStatus;
import danbank.account.model.AccountType;

public record AccountUpdate(
        AccountType type,
        AccountStatus status
) {
}
