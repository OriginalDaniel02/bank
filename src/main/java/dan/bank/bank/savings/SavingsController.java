package dan.bank.bank.savings;

import dan.bank.bank.auth.CurrentUser;
import dan.bank.bank.user.service.PinService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/savings")
public class SavingsController {

    private final SavingsService service;
    private final PinService pinService;
    private final CurrentUser currentUser;

    public SavingsController(SavingsService service, PinService pinService, CurrentUser currentUser) {
        this.service = service;
        this.pinService = pinService;
        this.currentUser = currentUser;
    }

    public record CreateRequest(@NotBlank(message = "Give your goal a name") @Size(max = 50, message = "The name is too long") String name,
                                @NotNull(message = "Choose a type") SavingsType type,
                                @NotNull(message = "Enter a target") @DecimalMin(value = "0.01", message = "Enter a target") BigDecimal targetAmount,
                                LocalDate deadline) { }

    public record MoveRequest(@NotNull Long accountId, @NotNull @DecimalMin(value = "0.01", message = "Enter an amount") BigDecimal amount) { }

    public record CloseRequest(@NotNull Long accountId) { }

    public record TargetRequest(@NotNull @DecimalMin(value = "0.01", message = "Enter a target") BigDecimal targetAmount) { }

    @GetMapping
    public ResponseEntity<SavingsService.Summary> list() {
        return ResponseEntity.ok(service.list(currentUser.id()));
    }

    @PostMapping
    public ResponseEntity<SavingsService.GoalView> create(@Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.create(currentUser.id(), request.name(), request.type(), request.targetAmount(), request.deadline()));
    }

    @PostMapping("/{id}/deposit")
    public ResponseEntity<SavingsService.GoalView> deposit(@PathVariable long id, @Valid @RequestBody MoveRequest request) {
        return ResponseEntity.ok(service.deposit(currentUser.id(), id, request.accountId(), request.amount()));
    }

    /** Taking money out of savings needs the transaction PIN. */
    @PostMapping("/{id}/withdraw")
    public ResponseEntity<SavingsService.GoalView> withdraw(
            @PathVariable long id,
            @RequestHeader(value = "X-Transaction-Pin", required = false) String pin,
            @Valid @RequestBody MoveRequest request) {
        pinService.verify(currentUser.id(), pin);
        return ResponseEntity.ok(service.withdraw(currentUser.id(), id, request.accountId(), request.amount()));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<SavingsService.GoalView> close(
            @PathVariable long id,
            @RequestHeader(value = "X-Transaction-Pin", required = false) String pin,
            @Valid @RequestBody CloseRequest request) {
        pinService.verify(currentUser.id(), pin);
        return ResponseEntity.ok(service.close(currentUser.id(), id, request.accountId()));
    }

    @PutMapping("/{id}/target")
    public ResponseEntity<SavingsService.GoalView> changeTarget(@PathVariable long id, @Valid @RequestBody TargetRequest request) {
        return ResponseEntity.ok(service.changeTarget(currentUser.id(), id, request.targetAmount()));
    }
}
