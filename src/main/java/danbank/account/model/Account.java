package danbank.account.model;

import danbank.user.model.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_account_user"))
    private User user;

    @Column(nullable = false)
    private String accountName;

    @Column(nullable = false, unique = true)
    private String accountNumber;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal accountBalance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus accountStatus;

    @Version
    private long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist protected void onCreate(){
        Instant now = Instant.now();
        createdAt = now; updatedAt = now;
    }

    @PreUpdate protected void onUpdate() {
        updatedAt = Instant.now();
    }


    @Builder
    public Account(
            long id, User user,String accountName, String accountNumber, BigDecimal accountBalance,
            AccountType accountType, AccountStatus accountStatus, Long version
    ){
        this.id = id;
        this.user =  user;
        this.accountName = accountName;
        this.accountNumber = accountNumber;
        this.accountBalance = accountBalance;
        this.accountType =  accountType;
        this.accountStatus = accountStatus;
        this.version =  version;
    }
}
