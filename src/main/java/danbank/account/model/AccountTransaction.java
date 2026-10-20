package danbank.account.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "account_transactions")
@Getter
@NoArgsConstructor
public class AccountTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    // Unique identifier for this one ledger row (idempotency key).
    @Column(nullable = false, unique = true, updatable = false)
    private String reference;

    // Shared by both legs of a transfer so they can be looked up as a pair.
    // Null for plain credits/debits, which only ever produce a single row.
    @Column(updatable = false)
    private String transferGroup;

    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public AccountTransaction(Account account, TransactionType type, BigDecimal amount, String reference, String description) {
        this(account, type, amount, reference, null, description);
    }

    public AccountTransaction(Account account, TransactionType type, BigDecimal amount, String reference, String transferGroup, String description) {
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.reference = reference;
        this.transferGroup = transferGroup;
        this.description = description;
    }
}
