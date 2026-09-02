package dan.bank.bank.Account.dtos;

import dan.bank.bank.Account.model.AccountStatus;
import dan.bank.bank.Account.model.AccountType;
import jakarta.validation.constraints.Size;

public record AccountUpdate(
        AccountType type,
        AccountStatus status
) {
}
