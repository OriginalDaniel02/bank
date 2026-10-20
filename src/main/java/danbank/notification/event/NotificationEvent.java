package danbank.notification.event;

/**
 * Published whenever something notification-worthy happens (money moved, login
 * locked, etc). This is the same shape a real message broker topic would carry -
 * swapping {@link danbank.notification.service.NotificationEventListener} for a
 * RabbitMQ/Kafka consumer (and the publish side for a real producer) would not
 * change anything else in the codebase.
 */
public record NotificationEvent(Long userId, String type, String message) {
}
