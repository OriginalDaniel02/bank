package dan.bank.bank.deposit;

import dan.bank.bank.account.model.Account;
import dan.bank.bank.account.model.AccountStatus;
import dan.bank.bank.account.repository.AccountRepository;
import dan.bank.bank.notification.TransferNotifier;
import dan.bank.bank.user.model.User;
import dan.bank.bank.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Adding money by card. Deliberately NOT @Transactional: it makes network calls to the payment provider,
 * which must never happen while a database transaction (and its locks) is open.
 */
@Service
public class DepositService {

    private static final Logger log = LoggerFactory.getLogger(DepositService.class);
    private static final int MAX_UNFINISHED = 10;

    private final DepositRepository deposits;
    private final DepositSettler settler;
    private final PaymentGateway gateway;
    private final AccountRepository accounts;
    private final UserRepository users;
    private final TransferNotifier notifier;
    private final JsonMapper json;
    private final String secretKey;
    private final String callbackUrl;
    private final BigDecimal minAmount;
    private final BigDecimal maxAmount;
    private final BigDecimal unverifiedMaxAmount;

    public DepositService(DepositRepository deposits, DepositSettler settler, PaymentGateway gateway,
                          AccountRepository accounts, UserRepository users, TransferNotifier notifier, JsonMapper json,
                          @Value("${paystack.secret-key:}") String secretKey,
                          @Value("${paystack.callback-url:http://localhost:5173}") String callbackUrl,
                          @Value("${bank.deposits.min:100.00}") BigDecimal minAmount,
                          @Value("${bank.deposits.max:1000000.00}") BigDecimal maxAmount,
                          @Value("${bank.deposits.tier0-max:50000.00}") BigDecimal unverifiedMaxAmount) {
        this.deposits = deposits;
        this.settler = settler;
        this.gateway = gateway;
        this.accounts = accounts;
        this.users = users;
        this.notifier = notifier;
        this.json = json;
        this.secretKey = secretKey;
        this.callbackUrl = callbackUrl;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.unverifiedMaxAmount = unverifiedMaxAmount;
    }

    public record Started(String reference, String authorizationUrl, BigDecimal amount, String status) { }

    public record View(String reference, String status, BigDecimal amount, String currency, Long accountId,
                       Instant createdAt, Instant completedAt, String message) {
        static View of(Deposit d) {
            String message = switch (d.getStatus()) {
                case SUCCESS -> "Your deposit was successful.";
                case FAILED -> "The payment was not completed.";
                case PENDING -> "We are waiting for the payment to be confirmed.";
            };
            return new View(d.getReference(), d.getStatus().name(), d.getAmount(), d.getCurrency(),
                    d.getAccountId(), d.getCreatedAt(), d.getCompletedAt(), message);
        }
    }

    // ---------------------------------------------------------------- start

    public Started start(long userId, long accountId, BigDecimal requested) {
        requireConfigured();
        BigDecimal amount = normalize(requested);
        User user = users.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Account account = accounts.findByIdAndUser_Id(accountId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is not active");
        }
        BigDecimal ceiling = user.getKycTier() < 1 ? unverifiedMaxAmount.min(maxAmount) : maxAmount;
        if (amount.compareTo(ceiling) > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The most you can add at once is "
                    + ceiling.toPlainString() + (user.getKycTier() < 1 ? ". Verify your identity to raise it" : ""));
        }
        if (deposits.countByUserIdAndStatusAndCreatedAtAfter(userId, DepositStatus.PENDING, Instant.now().minus(1, ChronoUnit.DAYS)) >= MAX_UNFINISHED) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "You have too many unfinished payments. Please try again later");
        }

        String reference = "DBD" + UUID.randomUUID().toString().replace("-", "");
        deposits.save(new Deposit(userId, accountId, reference, amount));
        try {
            PaymentGateway.InitializedPayment payment = gateway.initialize(reference, user.getEmail(), toKobo(amount), callbackUrl);
            return new Started(reference, payment.authorizationUrl(), amount, DepositStatus.PENDING.name());
        } catch (PaymentGateway.GatewayException e) {
            settler.markFailed(reference, "Could not start the payment");
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "We could not start the payment. Please try again");
        }
    }

    // --------------------------------------------------------------- status

    /** Returns the deposit. If it is still pending, asks the provider, so it also works where webhooks cannot reach us. */
    public View status(long userId, String reference) {
        Deposit deposit = deposits.findByReferenceAndUserId(reference, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deposit not found"));
        if (deposit.getStatus() == DepositStatus.PENDING && gateway.isConfigured()) {
            reconcile(reference);
            deposit = deposits.findByReferenceAndUserId(reference, userId).orElse(deposit);
        }
        return View.of(deposit);
    }

    private void reconcile(String reference) {
        try {
            PaymentGateway.PaymentCheck check = gateway.verify(reference);
            switch (check.outcome()) {
                case SUCCESS -> settle(reference, check.amountKobo(), check.currency());
                case FAILED -> settler.markFailed(reference, "The payment was not completed");
                case PENDING -> { /* the customer may still be paying */ }
            }
        } catch (PaymentGateway.GatewayException e) {
            log.warn("Could not verify {} right now; it stays pending", reference);
        }
    }

    // -------------------------------------------------------------- webhook

    /** Returns false if the signature is wrong. Unknown or irrelevant events are accepted and ignored. */
    public boolean handleWebhook(byte[] rawBody, String signatureHeader) {
        requireConfigured();
        if (!PaystackSignature.isValid(secretKey, rawBody, signatureHeader)) {
            log.warn("Rejected a Paystack webhook with an invalid signature");
            return false;
        }
        try {
            JsonNode event = json.readTree(rawBody);
            if (!"charge.success".equals(event.path("event").asString(""))) return true;
            JsonNode data = event.path("data");
            if (!"success".equals(data.path("status").asString(""))) return true;
            settle(data.path("reference").asString(""), data.path("amount").asLong(-1), data.path("currency").asString(""));
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed webhook body");
        }
        return true;
    }

    // ------------------------------------------------------------- internals

    private void settle(String reference, long paidKobo, String currency) {
        if (reference.isBlank() || paidKobo < 0) return;
        DepositSettler.Result result = settler.settleSuccess(reference, paidKobo, currency);
        if (result == DepositSettler.Result.CREDITED) {
            deposits.findByReference(reference).ifPresent(d -> notifier.depositCompleted(d.getAccountId(), d.getAmount()));
        }
    }

    private void requireConfigured() {
        if (!gateway.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Card deposits are not available yet");
        }
    }

    private BigDecimal normalize(BigDecimal requested) {
        BigDecimal amount;
        try {
            amount = requested.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException tooManyDecimals) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount can have at most 2 decimal places");
        }
        if (amount.compareTo(minAmount) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The least you can add is " + minAmount.toPlainString());
        }
        return amount;
    }

    private static long toKobo(BigDecimal naira) {
        return naira.movePointRight(2).longValueExact();
    }

}
