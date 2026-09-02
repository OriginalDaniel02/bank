package dan.bank.bank.Account.model;

import dan.bank.bank.User.model.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
@Setter @Getter @AllArgsConstructor @NoArgsConstructor
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_account_user"))
    private User user;
    @Column(nullable = false) private String accountName;
    @Column(nullable = false, unique = true) private String accountNumber;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal balance;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AccountType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AccountStatus status;
    @Version private long version;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @PrePersist protected void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate protected void onUpdate() { updatedAt = Instant.now(); }
}
