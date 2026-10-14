package danbank.address.model;

import danbank.user.model.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "addresses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch =FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 25)
    private String state;

    @Column(nullable = false, length = 20)
    private String lga;

    @Column( length = 12)
    private String postalCode;

    @Column(length = 60)
    private String landmark;

    @Column(nullable = false, length = 200)
    private String address1;

    @Column(length = 200)
    private String address2;

    @Column(nullable = false, updatable = false)
    private Instant validFrom;

    @Column(nullable = false)
    private Instant validTo;


    @Builder
    public Address(Instant validTo, Instant validFrom, String address2, String address1,
                   String landmark, String postalCode, String lga, String state, User user) {
        this.validFrom = Instant.now();
        this.address2 = address2;
        this.address1 = address1;
        this.landmark = landmark;
        this.postalCode = postalCode;
        this.lga = lga;
        this.state = state;
        this.user = user;
    }

    public void close(Instant at) {
        if (validTo != null) {
            throw new IllegalStateException("Address is already closed");
        }
        this.validTo = at;
    }

}
