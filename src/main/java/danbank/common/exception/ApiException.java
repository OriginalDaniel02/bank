package danbank.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for all domain exceptions that should be translated into a structured
 * JSON error response by {@link danbank.common.web.GlobalExceptionHandler}.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
