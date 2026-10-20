package danbank.user.exception;

import danbank.common.exception.NotFoundException;

public class EmailNotFoundException extends NotFoundException {
    public EmailNotFoundException() {
        super("No user found with that email address");
    }
}
