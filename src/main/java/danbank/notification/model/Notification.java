package danbank.notification.model;

import danbank.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * An in-app notification. This is the delivery seam for alerts (money moved,
 * account locked, etc.) - it does not send real email or SMS. Plugging in a
 * real channel (Spring Mail, Twilio) means adding a listener that reads these
 * rows, or publishes alongside them; it needs real provider credentials this
 * project doesn't have.
 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public Notification(User user, String type, String message) {
        this.user = Objects.requireNonNull(user, "User is required");
        this.type = Objects.requireNonNull(type, "Type is required");
        this.message = Objects.requireNonNull(message, "Message is required");
        this.read = false;
    }

    public void markRead() {
        this.read = true;
    }
}
