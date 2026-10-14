package danbank.account.exception;

import danbank.common.exception.NotFoundException;

public class AccountOwnerNotFoundException extends NotFoundException {
    public AccountOwnerNotFoundException() {
        super("User for this account not found");
    }
}
