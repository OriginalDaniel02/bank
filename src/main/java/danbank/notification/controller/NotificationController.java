package danbank.notification.controller;

import danbank.account.dtos.PageResponse;
import danbank.common.dto.ApiResponse;
import danbank.notification.dto.NotificationDto;
import danbank.notification.service.NotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/{userId}/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("@accessPolicy.isSelfOrAdmin(authentication, #userId)")
    public ResponseEntity<ApiResponse<PageResponse<NotificationDto>>> getNotifications(
            @PathVariable long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return ResponseEntity.ok(ApiResponse.ok("Notifications found", notificationService.getForUser(userId, pageable)));
    }

    @PatchMapping("/{notificationId}/read")
    @PreAuthorize("@accessPolicy.isSelfOrAdmin(authentication, #userId)")
    public ResponseEntity<Void> markRead(@PathVariable long userId, @PathVariable long notificationId) {
        notificationService.markRead(userId, notificationId);
        return ResponseEntity.noContent().build();
    }
}
