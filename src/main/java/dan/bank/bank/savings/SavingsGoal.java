package dan.bank.bank.savings;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** A pot of money set aside for a goal. The money leaves the account's balance while it is saved. */
@Entity
@Table(name = "savings_goals")
@Getter
@Setter
@NoArgsConstructor
public class SavingsGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private SavingsType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal targetAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal savedAmount = BigDecimal.ZERO.setScale(2);

    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SavingsStatus status = SavingsStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Instant closedAt;

    public SavingsGoal(Long userId, String name, SavingsType type, BigDecimal targetAmount, LocalDate deadline) {
        this.userId = userId;
        this.name = name;
        this.type = type;
        this.targetAmount = targetAmount;
        this.deadline = deadline;
    }
}
