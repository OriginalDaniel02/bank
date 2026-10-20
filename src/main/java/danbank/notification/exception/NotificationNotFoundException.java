package danbank.notification.exception;

import danbank.common.exception.NotFoundException;

public class NotificationNotFoundException extends NotFoundException {
    public NotificationNotFoundException() {
        super("Notification not found");
    }
}
