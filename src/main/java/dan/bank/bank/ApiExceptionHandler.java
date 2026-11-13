package dan.bank.bank;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error leaves the API in one shape: {"message": "...", "code": "NOT_FOUND", "fields": {...}}.
 * "message" is safe to show to a user, "code" is for programs, "fields" maps invalid input to its problem.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    public record ApiError(String message, String code, Map<String, String> fields) {
        public static ApiError of(HttpStatusCode status, String message) {
            return new ApiError(message, HttpStatus.valueOf(status.value()).name(), Map.of());
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(ResponseStatusException exception) {
        String message = exception.getReason() != null ? exception.getReason()
                : HttpStatus.valueOf(exception.getStatusCode().value()).getReasonPhrase();
        return ResponseEntity.status(exception.getStatusCode()).body(ApiError.of(exception.getStatusCode(), message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        String message = fields.values().stream().findFirst().orElse("Please check the details you entered");
        return ResponseEntity.badRequest().body(new ApiError(message, "VALIDATION_ERROR", fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleMalformed(Exception exception) {
        return ResponseEntity.badRequest().body(ApiError.of(HttpStatus.BAD_REQUEST, "The request could not be understood"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(HttpStatus.FORBIDDEN, "You do not have permission to do that"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT, "Request conflicts with existing data"));
    }
}
