package dan.bank.bank.User.service;

import dan.bank.bank.User.model.User;
import dan.bank.bank.User.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;

/** Transaction PIN: set/change it (re-authenticating with the password) and verify it with lockout. */
@Service
public class PinService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PinService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void setPin(long userId, String pin, String password) {
        User user = load(userId);
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Incorrect password");
        }
        user.setTransactionPinHash(passwordEncoder.encode(pin));
        user.setPinFailedAttempts(0);
        user.setPinLockedUntil(null);
    }

    /** Failed attempts must persist, so a wrong PIN does not roll the counter back. */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void verify(long userId, String pin) {
        User user = load(userId);
        if (user.getTransactionPinHash() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Set a transaction PIN first");
        }
        Instant now = Instant.now();
        if (user.getPinLockedUntil() != null && user.getPinLockedUntil().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "PIN locked after too many attempts. Try again later");
        }
        if (pin == null || !passwordEncoder.matches(pin, user.getTransactionPinHash())) {
            int attempts = user.getPinFailedAttempts() + 1;
            if (attempts >= MAX_ATTEMPTS) {
                user.setPinLockedUntil(now.plus(LOCK_DURATION));
                user.setPinFailedAttempts(0);
            } else {
                user.setPinFailedAttempts(attempts);
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Incorrect transaction PIN");
        }
        user.setPinFailedAttempts(0);
        user.setPinLockedUntil(null);
    }

    private User load(long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
