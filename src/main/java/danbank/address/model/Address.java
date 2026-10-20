package danbank.address.model;

import danbank.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * An address is immutable once created. "Changing" a user's address closes the
 * current record (sets validTo) and inserts a new one, so the full history is kept.
 */
@Entity
@Table(name = "addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 25)
    private String state;

    @Column(nullable = false, length = 80)
    private String lga;

    @Column(length = 12)
    private String postalCode;

    @Column(length = 120)
    private String landmark;

    @Column(nullable = false, length = 200)
    private String address1;

    @Column(length = 200)
    private String address2;

    @Column(nullable = false, updatable = false)
    private Instant validFrom;

    private Instant validTo;

    @Builder
    public Address(User user, String state, String lga, String postalCode,
                    String landmark, String address1, String address2) {
        this.user = Objects.requireNonNull(user, "User is required");
        this.state = state;
        this.lga = lga;
        this.postalCode = postalCode;
        this.landmark = landmark;
        this.address1 = Objects.requireNonNull(address1, "Address line 1 is required");
        this.address2 = address2;
        this.validFrom = Instant.now();
    }

    public void close(Instant at) {
        if (validTo != null) {
            throw new IllegalStateException("Address is already closed");
        }
        this.validTo = at;
    }
}
