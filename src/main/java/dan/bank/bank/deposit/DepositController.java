package dan.bank.bank.deposit;

import dan.bank.bank.auth.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/deposits")
public class DepositController {

    private final DepositService service;
    private final CurrentUser currentUser;

    public DepositController(DepositService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    public record StartRequest(@NotNull Long accountId, @NotNull @DecimalMin(value = "0.01", message = "Enter an amount") BigDecimal amount) { }

    /** Starts a card payment. Send the customer to the returned authorizationUrl to pay. */
    @PostMapping
    public ResponseEntity<DepositService.Started> start(@Valid @RequestBody StartRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.start(currentUser.id(), request.accountId(), request.amount()));
    }

    /** Where the customer lands after paying: asks the provider if the payment is still pending. */
    @GetMapping("/{reference}")
    public ResponseEntity<DepositService.View> status(@PathVariable String reference) {
        return ResponseEntity.ok(service.status(currentUser.id(), reference));
    }
}
