package dan.bank.bank.auth;

import dan.bank.bank.user.model.User;
import dan.bank.bank.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Resolves the authenticated caller (from the JWT) so controllers can enforce ownership. */
@Component
public class CurrentUser {

    private final UserRepository userRepository;

    public CurrentUser(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public long id() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return userRepository.findByEmail(authentication.getName())
                .map(User::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required"));
    }

    /** Fails with 403 unless the path/user id is the caller's own. */
    public void requireSelf(long userId) {
        if (id() != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only access your own data");
        }
    }
}
