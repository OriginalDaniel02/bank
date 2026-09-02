package dan.bank.bank.Account.model;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity
@Table(name = "account_transactions")
@Getter
@Setter
@NoArgsConstructor
public class AccountTransaction {

 @Id
 @GeneratedValue(strategy = GenerationType.IDENTITY)
 private Long id;

 @ManyToOne(fetch = FetchType.LAZY, optional = false)
 @JoinColumn(name = "account_id")
 private Account account;

 @Enumerated(EnumType.STRING)
 @Column(nullable = false)
 private TransactionType type;

 @Column(nullable = false, precision = 19, scale = 2)
 private BigDecimal amount;

 @Column(nullable = false, unique = true, updatable = false) private String reference;
 private String description;

 @Column(nullable = false, updatable = false)
 private Instant createdAt;

 @PrePersist void onCreate() {
  createdAt = Instant.now(); }

 public AccountTransaction(Account account, TransactionType type, BigDecimal amount, String reference, String description) {
  this.account=account;
  this.type=type;
  this.amount=amount;
  this.reference=reference;
  this.description=description; }
}
