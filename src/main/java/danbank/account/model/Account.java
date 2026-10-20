package danbank.account.model;

import danbank.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_account_user"))
    private User user;

    @Column(nullable = false)
    private String accountName;

    @Column(nullable = false, unique = true)
    private String accountNumber;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Version
    private long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    @Builder
    public Account(User user, String accountName, String accountNumber) {
        this.user = Objects.requireNonNull(user, "User is required");
        this.accountName = Objects.requireNonNull(accountName, "Account name is required").strip();
        this.accountNumber = Objects.requireNonNull(accountNumber, "Account number is required");
        this.balance = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        this.type = AccountType.SAVINGS;
        this.status = AccountStatus.ACTIVE;
        this.currency = "NGN";
    }

    public void credit(BigDecimal amount) {
        requireActive();
        requirePositive(amount);
        this.balance = this.balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        requireActive();
        requirePositive(amount);
        if (this.balance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds");
        }
        this.balance = this.balance.subtract(amount);
    }

    public void activate() {
        if (status == AccountStatus.CLOSED) {
            throw new IllegalStateException("A closed account cannot be reactivated");
        }
        this.status = AccountStatus.ACTIVE;
    }

    public void deactivate() {
        if (status == AccountStatus.CLOSED) {
            throw new IllegalStateException("Account has already been closed");
        }
        this.status = AccountStatus.NOT_ACTIVE;
    }

    public void changeType(AccountType newType) {
        this.type = Objects.requireNonNull(newType, "Account type is required");
    }

    private void requireActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active");
        }
    }

    private void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}
