package dan.bank.bank.deposit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** One attempt to add money by card. Becomes SUCCESS only after the payment provider confirms it. */
@Entity
@Table(name = "deposits")
@Getter
@Setter
@NoArgsConstructor
public class Deposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private Long accountId;

    @Column(nullable = false, unique = true, updatable = false, length = 64)
    private String reference;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency = "NGN";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DepositStatus status = DepositStatus.PENDING;

    @Column(nullable = false, updatable = false, length = 20)
    private String provider = "PAYSTACK";

    private String failureReason;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant completedAt;

    public Deposit(Long userId, Long accountId, String reference, BigDecimal amount) {
        this.userId = userId;
        this.accountId = accountId;
        this.reference = reference;
        this.amount = amount;
    }
}
