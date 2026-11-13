package dan.bank.bank.dashboard;

import dan.bank.bank.auth.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentUser currentUser;

    public DashboardController(DashboardService dashboardService, CurrentUser currentUser) {
        this.dashboardService = dashboardService;
        this.currentUser = currentUser;
    }

    /** accountId is optional; the first account is shown when it is omitted. */
    @GetMapping
    public ResponseEntity<DashboardService.DashboardView> dashboard(@RequestParam(required = false) Long accountId) {
        return ResponseEntity.ok(dashboardService.dashboard(currentUser.id(), accountId));
    }
}
