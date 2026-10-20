package danbank.notification.dto;

import danbank.notification.model.Notification;

import java.time.Instant;

public record NotificationDto(
        Long id,
        String type,
        String message,
        boolean read,
        Instant createdAt
) {
    public static NotificationDto from(Notification notification) {
        return new NotificationDto(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
