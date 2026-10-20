package danbank.user.exception;

import danbank.common.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.time.Instant;

public class UserLockedException extends ApiException {

    public UserLockedException(Instant lockedUntil) {
        super(HttpStatus.LOCKED, "Too many failed login attempts. Try again after " + lockedUntil);
    }
}
