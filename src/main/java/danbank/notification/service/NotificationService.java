package danbank.notification.service;

import danbank.notification.dto.NotificationDto;
import danbank.notification.event.NotificationEvent;
import danbank.notification.exception.NotificationNotFoundException;
import danbank.notification.model.Notification;
import danbank.notification.repository.NotificationRepository;
import danbank.account.dtos.PageResponse;
import danbank.user.model.User;
import danbank.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Fire-and-forget: publishes an event instead of writing directly. A
     * {@link NotificationEventListener} persists it only after the caller's
     * transaction commits, so a rolled-back transfer never leaves a stray alert.
     */
    public void notify(long userId, String type, String message) {
        eventPublisher.publishEvent(new NotificationEvent(userId, type, message));
    }

    /**
     * REQUIRES_NEW, not the default REQUIRED: this runs from an AFTER_COMMIT
     * transaction-synchronization callback, where leftover state from the
     * just-finished transaction can otherwise get silently (mis)joined instead
     * of starting a fresh one.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(NotificationEvent event) {
        User user = userRepository.findById(event.userId()).orElse(null);
        if (user == null) {
            return; // best-effort: nothing sensible to notify if the user is gone
        }
        notificationRepository.save(new Notification(user, event.type(), event.message()));
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> getForUser(long userId, Pageable pageable) {
        Page<Notification> page = notificationRepository.findByUser_IdOrderByCreatedAtDesc(userId, pageable);
        return new PageResponse<>(
                page.map(NotificationDto::from).getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    @Transactional
    public void markRead(long userId, long notificationId) {
        Notification notification = notificationRepository.findByIdAndUser_Id(notificationId, userId)
                .orElseThrow(NotificationNotFoundException::new);
        notification.markRead();
    }
}
