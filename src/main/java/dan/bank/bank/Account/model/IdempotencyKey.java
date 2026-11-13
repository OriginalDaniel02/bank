package dan.bank.bank.Account.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false, length = 100)
    private String keyValue;

    @Column(nullable = false, updatable = false, length = 64)
    private String requestHash;

    /** Null while the first request is still running; set once the transfer committed. */
    private String transferId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public IdempotencyKey(Long userId, String keyValue, String requestHash) {
        this.userId = userId;
        this.keyValue = keyValue;
        this.requestHash = requestHash;
    }
}
