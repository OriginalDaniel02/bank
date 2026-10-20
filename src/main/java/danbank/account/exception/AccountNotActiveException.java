package danbank.account.exception;

import danbank.common.exception.ConflictException;

public class AccountNotActiveException extends ConflictException {
    public AccountNotActiveException() {
        super("Account is not active");
    }
}
