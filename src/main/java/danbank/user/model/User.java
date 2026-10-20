package danbank.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    private static final String[] VALID_PHONE_PREFIXES = "070 071 080 081 090 091".split(" ");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String firstName;

    @Column(nullable = false, length = 80)
    private String lastName;

    @Column(nullable = false, unique = true, length = 15)
    private String phoneNumber;                 // stored normalized as "+234 ..."

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private int failedLoginAttempts;

    private Instant lockedUntil;

    private Instant lastLoginAt;

    @Column(length = 32)
    private String twoFactorSecret;

    @Column(nullable = false)
    private boolean twoFactorEnabled;

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
        this.phoneNumber = normalizePhoneNumber(phoneNumber);
        this.email = email.strip().toLowerCase(Locale.ROOT);
        this.passwordHash = passwordHash;
        this.bvnHash = bvnHash;
        this.bvnLast4 = bvnLast4;
        this.dateOfBirth = dateOfBirth;
        this.status = UserStatus.ACTIVE;
        this.role = Role.USER;
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
        if (status == UserStatus.CLOSED) {
            throw new IllegalStateException("User is already closed");
        }
        this.status = UserStatus.CLOSED;
    }

    public void changeNames(String firstName, String lastName) {
        requireActive();
        this.firstName = firstName.strip();
        this.lastName = lastName.strip();
    }

    public void changeEmail(String email) {
        requireActive();
        this.email = email.strip().toLowerCase(Locale.ROOT);
    }

    public void changePassword(String newPasswordHash) {
        requireActive();
        this.passwordHash = newPasswordHash;
    }

    public void changePhoneNumber(String newNumber) {
        requireActive();
        this.phoneNumber = normalizePhoneNumber(newNumber);
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public void recordSuccessfulLogin() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
    }

    public void recordFailedLogin(int maxAttempts, Duration lockoutDuration) {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= maxAttempts) {
            this.lockedUntil = Instant.now().plus(lockoutDuration);
        }
    }

    /** Stores a newly generated secret without turning 2FA on yet - the caller must confirm a code first. */
    public void startTwoFactorSetup(String secret) {
        requireActive();
        this.twoFactorSecret = Objects.requireNonNull(secret, "Secret is required");
        this.twoFactorEnabled = false;
    }

    public void enableTwoFactor() {
        requireActive();
        if (twoFactorSecret == null) {
            throw new IllegalStateException("Call startTwoFactorSetup before enabling two-factor authentication");
        }
        this.twoFactorEnabled = true;
    }

    public void disableTwoFactor() {
        requireActive();
        this.twoFactorEnabled = false;
        this.twoFactorSecret = null;
    }

    public static String lastFourDigits(String value) {
        return value.substring(value.length() - 4);
    }

    public static String normalizePhoneNumber(String phone) {
        String digitsOnly = phone.replaceAll("\\D", "");

        // Accept a "234" country-code prefix and normalize it back to the local 0-leading form.
        if (digitsOnly.length() == 13 && digitsOnly.startsWith("234")) {
            digitsOnly = "0" + digitsOnly.substring(3);
        }

        boolean hasValidPrefix = digitsOnly.length() == 11
                && Arrays.stream(VALID_PHONE_PREFIXES).anyMatch(digitsOnly::startsWith);
        if (!hasValidPrefix) {
            throw new IllegalArgumentException("Phone number is not a recognised Nigerian mobile number");
        }
        return "+234 " + digitsOnly.substring(1);
    }

    private void requireActive() {
        if (status != UserStatus.ACTIVE) {
            throw new IllegalStateException("User must be active to perform this action");
        }
    }
}
