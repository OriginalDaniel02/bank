package danbank.account.exception;

import danbank.common.exception.NotFoundException;

public class AccountNotFoundException extends NotFoundException {
    public AccountNotFoundException() {
        super("Account does not exist");
    }
}
