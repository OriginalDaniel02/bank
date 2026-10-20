package danbank.user.exception;

import danbank.common.exception.ConflictException;

public class UserDuplicateException extends ConflictException {
    public UserDuplicateException() {
        super("An account with these details already exists");
    }
}
