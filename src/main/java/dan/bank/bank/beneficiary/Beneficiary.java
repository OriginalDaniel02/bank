package dan.bank.bank.beneficiary;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A saved recipient. The account name is a snapshot taken when it was saved. */
@Entity
@Table(name = "beneficiaries")
@Getter
@Setter
@NoArgsConstructor
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private String accountNumber;

    @Column(nullable = false)
    private String accountName;

    @Column(length = 50)
    private String nickname;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Beneficiary(Long userId, String accountNumber, String accountName, String nickname) {
        this.userId = userId;
        this.accountNumber = accountNumber;
        this.accountName = accountName;
        this.nickname = nickname;
    }
}
