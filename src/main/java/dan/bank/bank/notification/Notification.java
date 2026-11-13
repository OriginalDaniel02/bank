package dan.bank.bank.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private NotificationType type;

    @Column(nullable = false, updatable = false, length = 100)
    private String title;

    @Column(nullable = false, updatable = false, length = 500)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Notification(Long userId, NotificationType type, String title, String message) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message.length() > 500 ? message.substring(0, 500) : message;
    }
}
