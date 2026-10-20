package danbank.user.exception;

import danbank.common.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Password was correct but the account has 2FA enabled and no TOTP code was
 * supplied. 428 Precondition Required tells the client exactly one more step
 * is needed before the request can be completed, distinct from a plain 401
 * for wrong credentials.
 */
public class TwoFactorRequiredException extends ApiException {
    public TwoFactorRequiredException() {
        super(HttpStatus.PRECONDITION_REQUIRED, "A valid two-factor authentication code is required");
    }
}
