package dan.bank.bank.notification;

import dan.bank.bank.account.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * In-app notifications. This is also where email/SMS sending would hook in:
 * call a sender from {@link #notify} once a provider is chosen.
 */
@Service
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void notify(long userId, NotificationType type, String title, String message) {
        repository.save(new Notification(userId, type, title, message));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> list(long userId, Boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<Notification> result = Boolean.TRUE.equals(unreadOnly)
                ? repository.findByUserIdAndReadOrderByCreatedAtDescIdDesc(userId, false, pageable)
                : repository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
        return new PageResponse<>(result.map(this::toDto).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public long unreadCount(long userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(long userId, long notificationId) {
        Notification notification = repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.setRead(true);
    }

    @Transactional
    public int markAllRead(long userId) {
        return repository.markAllRead(userId);
    }

    private NotificationDto toDto(Notification n) {
        return new NotificationDto(n.getId(), n.getType().name(), n.getTitle(), n.getMessage(), n.isRead(), n.getCreatedAt());
    }

    public record NotificationDto(Long id, String type, String title, String message, boolean read, java.time.Instant createdAt) { }
}
