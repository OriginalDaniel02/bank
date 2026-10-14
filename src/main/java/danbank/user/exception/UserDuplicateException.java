package danbank.user.exception;

import danbank.common.exception.NotFoundException;

public class UserDuplicateException extends NotFoundException {
    public UserDuplicateException() {
        super("An account with these details already exists");
    }
}
