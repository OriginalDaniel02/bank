package danbank.notification.service;

import danbank.notification.event.NotificationEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Persists a notification only after the transaction that raised it commits,
 * and does so on a dedicated worker thread (see {@link NotificationAsyncConfig}).
 *
 * <p>The {@code @Async} is not just a performance nicety here: without it this
 * callback runs synchronously on the same thread as the business transaction,
 * right after it commits but before its database connection is returned to
 * the pool. {@link NotificationService#persist} opens a second transaction
 * (its own connection) at that exact moment - under the kind of lock
 * contention transfers deliberately create (many callers serialized on one
 * account row), enough callbacks can land in that window simultaneously to
 * fully exhaust the connection pool, with nothing able to proceed. Moving the
 * callback onto a separate thread pool lets the original connection return to
 * the pool before the notification write ever asks for one.
 */
@Component
public class NotificationEventListener {

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationEvent(NotificationEvent event) {
        notificationService.persist(event);
    }
}
