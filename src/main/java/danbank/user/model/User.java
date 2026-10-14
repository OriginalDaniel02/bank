package danbank.user.model;

import danbank.address.model.Address;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String firstName;

    @Column(nullable = false, length = 80)
    private String lastName;

    @Column(nullable = false, unique = true, length = 15)
    private String phoneNumber;                 // already in +234... form

    @Column(nullable = false, unique = true, length = 255)
    private String email;                       // lowercase

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, unique = true, length = 64)
    private String bvnHash;

    @Column(nullable = false, length = 4)
    private String bvnLast4;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Builder
    private User(String firstName, String lastName, String phoneNumber, String email,
                 String passwordHash, String bvnHash, String bvnLast4,
                 LocalDate dateOfBirth) {
        this.firstName = firstName.strip();
        this.lastName = lastName.strip();
        this.phoneNumber = phoneNumber;
        this.email = email.strip().toLowerCase(Locale.ROOT);
        this.passwordHash = passwordHash;
        this.bvnHash = bvnHash;
        this.bvnLast4 = bvnLast4;
        this.dateOfBirth = dateOfBirth;
        this.status = UserStatus.ACTIVE;
    }

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

    public void close() {
        if(status == UserStatus.CLOSED){
            throw new IllegalArgumentException("User already closed");
        }
        this.status = UserStatus.CLOSED;
    }

    public void changeNames(String firstName, String lastName){
        requireActive();
        this.firstName = firstName.strip();
        this.lastName =  lastName.strip();

    }

    public void changeEmail(String email){
        requireActive();
        this.email =  email.strip().toLowerCase(Locale.ROOT);
    }

    public void changePassword( String newPwdHash){
        requireActive();
        this.passwordHash = newPwdHash;
    }
    public static String  LastFourDigits(String s){
        return s.substring(s.length()-4);
    }

    public void changePhoneNumber(String newNumber){
        requireActive();
        this.phoneNumber = PhoneNumberNormalize(newNumber);

    }

    public String PhoneNumberNormalize(String phone){
        String num = phone.substring(0,2);
        String[] validProviders =  "081 070 071 091 090 080 80 81 90 91 70 71".split(" ");

        for(String provider : validProviders){
            if(num.equals(provider.strip())){
                return  "+234 "+phone.substring(1);
            }
        }

        return null;
    }


    private void requireActive(){
        if(status != UserStatus.ACTIVE){
            throw  new IllegalArgumentException("User is required to be active");
        }
    }
}