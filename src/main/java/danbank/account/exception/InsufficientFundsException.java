package danbank.account.exception;

import danbank.common.exception.ConflictException;

public class InsufficientFundsException extends ConflictException {
    public InsufficientFundsException() {
        super("Insufficient funds");
    }
}
