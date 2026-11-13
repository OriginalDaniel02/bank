package dan.bank.bank.device;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "trusted_devices")
@Getter
@Setter
@NoArgsConstructor
public class TrustedDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, unique = true, updatable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant lastUsedAt = Instant.now();

    @Column(nullable = false)
    private boolean revoked;

    public TrustedDevice(Long userId, String tokenHash, String label) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.label = label;
    }
}
