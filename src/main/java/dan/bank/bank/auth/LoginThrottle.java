package dan.bank.bank.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Slows down password guessing: after {@value #MAX_FAILURES} wrong passwords for one email/phone, sign-in is refused for
 * {@code LOCK_MINUTES} minutes, even with the right password. Works the same for identifiers that do not exist, so the
 * response never reveals whether an account is real. Every method commits on its own, so a failed sign-in (which ends in
 * an exception and a rollback) still counts.
 */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final long LOCK_MINUTES = 15;
    private static final Duration LOCK = Duration.ofMinutes(LOCK_MINUTES);
    private static final Logger log = LoggerFactory.getLogger(LoginThrottle.class);

    private final LoginAttemptRepository attempts;

    public LoginThrottle(LoginAttemptRepository attempts) {
        this.attempts = attempts;
    }

    /** Throws 429 if this identifier is currently locked. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void check(String identifier) {
        attempts.findById(key(identifier)).ifPresent(attempt -> {
            Instant until = attempt.getLockedUntil();
            if (until != null && until.isAfter(Instant.now())) {
                long minutes = Math.max(1, Duration.between(Instant.now(), until).plusSeconds(59).toMinutes());
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Too many failed sign-in attempts. Please try again in " + minutes + (minutes == 1 ? " minute." : " minutes."));
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String identifier) {
        try {
            Instant now = Instant.now();
            LoginAttempt attempt = attempts.findWithLockByKey(key(identifier)).orElseGet(() -> new LoginAttempt(key(identifier)));
            if (attempt.getUpdatedAt().isBefore(now.minus(LOCK))) attempt.setFailures(0);   // old failures are forgiven
            attempt.setFailures(attempt.getFailures() + 1);
            attempt.setUpdatedAt(now);
            if (attempt.getFailures() >= MAX_FAILURES) {
                attempt.setLockedUntil(now.plus(LOCK));
                attempt.setFailures(0);
                log.warn("Sign-in locked for {} minutes after repeated failures", LOCK_MINUTES);
            }
            attempts.saveAndFlush(attempt);
        } catch (RuntimeException e) {
            // Two failures arriving at the very same instant for a brand-new identifier can collide. One lost count is fine.
            log.debug("Could not record a failed sign-in", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clear(String identifier) {
        attempts.deleteById(key(identifier));
    }

    /** Hashed so the table holds no emails or phone numbers. */
    static String key(String identifier) {
        String normal = identifier.strip().toLowerCase().replaceAll("\\s", "");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normal.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
