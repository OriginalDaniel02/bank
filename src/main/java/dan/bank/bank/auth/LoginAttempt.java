package dan.bank.bank.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "login_attempts")
@Getter
@Setter
@NoArgsConstructor
public class LoginAttempt {

    @Id
    @Column(name = "attempt_key", length = 64)
    private String key;

    @Column(nullable = false)
    private int failures;

    private Instant lockedUntil;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public LoginAttempt(String key) {
        this.key = key;
    }
}
