package dan.bank.bank.account.controller;

import dan.bank.bank.account.service.TransferPolicy;
import dan.bank.bank.auth.CurrentUser;
import dan.bank.bank.user.repository.UserRepository;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Lets a client show the fee, the total debit and the caller's limits before they confirm a transfer. */
@Validated
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferQuoteController {

    private final TransferPolicy policy;
    private final CurrentUser currentUser;
    private final UserRepository userRepository;

    public TransferQuoteController(TransferPolicy policy, CurrentUser currentUser, UserRepository userRepository) {
        this.policy = policy;
        this.currentUser = currentUser;
        this.userRepository = userRepository;
    }

    public record Quote(BigDecimal amount, BigDecimal fee, BigDecimal total,
                        BigDecimal perTransferLimit, BigDecimal dailyLimit, int kycTier) { }

    @GetMapping("/quote")
    public ResponseEntity<Quote> quote(@RequestParam @NotNull @DecimalMin("0.01") BigDecimal amount) {
        int tier = userRepository.findById(currentUser.id()).orElseThrow().getKycTier();
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = policy.feeFor(rounded);
        return ResponseEntity.ok(new Quote(rounded, fee, rounded.add(fee),
                policy.perTransferMaxFor(tier), policy.dailyMaxFor(tier), tier));
    }
}
