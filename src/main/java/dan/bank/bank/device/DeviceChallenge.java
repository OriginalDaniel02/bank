package dan.bank.bank.device;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "device_challenges")
@Getter
@Setter
@NoArgsConstructor
public class DeviceChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, unique = true, updatable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, updatable = false, length = 120)
    private String label;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public DeviceChallenge(Long userId, String tokenHash, String label, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.label = label;
        this.expiresAt = expiresAt;
    }
}
