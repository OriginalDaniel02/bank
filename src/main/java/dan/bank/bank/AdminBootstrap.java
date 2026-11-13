package dan.bank.bank;

import dan.bank.bank.user.model.Role;
import dan.bank.bank.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Promotes the user whose email is in ADMIN_EMAIL (if they exist) to ADMIN at startup. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final String adminEmail;

    public AdminBootstrap(UserRepository userRepository, @Value("${bank.admin-email:}") String adminEmail) {
        this.userRepository = userRepository;
        this.adminEmail = adminEmail;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank()) return;
        userRepository.findByEmail(adminEmail).ifPresentOrElse(user -> {
            if (user.getRole() != Role.ADMIN) log.info("Promoted the account for ADMIN_EMAIL to ADMIN");
            user.setRole(Role.ADMIN);
        }, () -> log.warn("ADMIN_EMAIL is set but no user has signed up with that email yet. Sign up, then restart."));
    }
}
