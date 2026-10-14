package danbank.user.exception;

import danbank.common.exception.NotFoundException;

public class InvalidCredentialsException extends NotFoundException {
    public InvalidCredentialsException() {
        super("Invalid Credentials");
    }
}
