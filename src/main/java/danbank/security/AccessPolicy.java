package danbank.security;

import danbank.account.repository.AccountRepo;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Ownership rules used from {@code @PreAuthorize("@accessPolicy...")} expressions.
 */
@Component("accessPolicy")
public class AccessPolicy {

    private final AccountRepo accountRepository;

    public AccessPolicy(AccountRepo accountRepository) {
        this.accountRepository = accountRepository;
    }

    public boolean isSelfOrAdmin(Authentication authentication, long userId) {
        AuthUser caller = caller(authentication);
        return caller != null && (caller.isAdmin() || caller.getId() == userId);
    }

    public boolean isSelf(Authentication authentication, long userId) {
        AuthUser caller = caller(authentication);
        return caller != null && caller.getId() == userId;
    }

    public boolean ownsAccount(Authentication authentication, long accountId) {
        AuthUser caller = caller(authentication);
        if (caller == null) {
            return false;
        }
        return caller.isAdmin() || accountRepository.findByIdAndUser_Id(accountId, caller.getId()).isPresent();
    }

    private AuthUser caller(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        return null;
    }
}
