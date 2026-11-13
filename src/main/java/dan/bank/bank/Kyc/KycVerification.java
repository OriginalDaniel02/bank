package dan.bank.bank.Kyc;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Verdict of one identity check. Biometric images are never stored. */
@Entity
@Table(name = "kyc_verifications")
@Getter
@Setter
@NoArgsConstructor
public class KycVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false, length = 20)
    private String type = "FACE";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KycStatus status;

    @Column(nullable = false, updatable = false, length = 50)
    private String provider;

    private String providerReference;

    private String reason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public KycVerification(Long userId, KycStatus status, String provider, String providerReference, String reason) {
        this.userId = userId;
        this.status = status;
        this.provider = provider;
        this.providerReference = providerReference;
        this.reason = reason;
    }
}
