package dan.bank.bank.insights;

import dan.bank.bank.auth.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/insights")
public class InsightsController {

    private final InsightsService service;
    private final CurrentUser currentUser;

    public InsightsController(InsightsService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ResponseEntity<InsightsService.View> insights(@RequestParam(required = false) Long accountId) {
        return ResponseEntity.ok(service.insights(currentUser.id(), accountId));
    }
}
