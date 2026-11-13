package dan.bank.bank.deposit;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paystack calls this when a payment succeeds. It is public (Paystack has no login) and protected by the signature
 * instead. The body is read as raw bytes because the signature is computed over exactly those bytes.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class PaystackWebhookController {

    private final DepositService service;

    public PaystackWebhookController(DepositService service) {
        this.service = service;
    }

    @PostMapping("/paystack")
    public ResponseEntity<Void> receive(@RequestBody byte[] body,
                                        @RequestHeader(value = "x-paystack-signature", required = false) String signature) {
        return service.handleWebhook(body, signature)
                ? ResponseEntity.ok().build()
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
