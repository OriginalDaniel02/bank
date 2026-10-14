package danbank.user.exception;

import danbank.common.exception.NotFoundException;

public class EmalNotFOundException extends NotFoundException {
    public EmalNotFOundException() {
        super("Email not found");
    }
}
