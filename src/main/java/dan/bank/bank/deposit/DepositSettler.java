package dan.bank.bank.deposit;

import dan.bank.bank.account.dto.MoneyRequest;
import dan.bank.bank.account.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The only place a deposit turns into money. Everything here runs in one database transaction with the
 * deposit row locked, so however many webhooks, retries and page checks arrive, the account is credited once.
 */
@Component
public class DepositSettler {

    private static final Logger log = LoggerFactory.getLogger(DepositSettler.class);

    public enum Result { CREDITED, ALREADY_CREDITED, UNKNOWN_REFERENCE, MISMATCH }

    private final DepositRepository deposits;
    private final AccountService accounts;

    public DepositSettler(DepositRepository deposits, AccountService accounts) {
        this.deposits = deposits;
        this.accounts = accounts;
    }

    @Transactional
    public Result settleSuccess(String reference, long paidKobo, String paidCurrency) {
        Deposit deposit = deposits.findWithLockByReference(reference).orElse(null);
        if (deposit == null) {
            log.warn("Payment confirmation for unknown reference {}", reference);
            return Result.UNKNOWN_REFERENCE;
        }
        if (deposit.getStatus() == DepositStatus.SUCCESS) {
            return Result.ALREADY_CREDITED;
        }
        BigDecimal paid = BigDecimal.valueOf(paidKobo, 2);
        if (!deposit.getCurrency().equalsIgnoreCase(paidCurrency) || paid.compareTo(deposit.getAmount()) != 0) {
            deposit.setStatus(DepositStatus.FAILED);
            deposit.setFailureReason("Paid amount or currency did not match the request");
            // Money was taken by the provider but is NOT credited. This needs a person to look at it.
            log.error("DEPOSIT MISMATCH reference={} expected={} {} paid={} {}",
                    reference, deposit.getAmount(), deposit.getCurrency(), paid, paidCurrency);
            return Result.MISMATCH;
        }
        // "DEP-<reference>" is unique in the ledger, which is a second guard against crediting twice.
        accounts.credit(deposit.getAccountId(), new MoneyRequest(deposit.getAmount(), "Card deposit"), "DEP-" + reference);
        deposit.setStatus(DepositStatus.SUCCESS);
        deposit.setFailureReason(null);
        deposit.setCompletedAt(Instant.now());
        return Result.CREDITED;
    }

    @Transactional
    public void markFailed(String reference, String reason) {
        deposits.findWithLockByReference(reference).ifPresent(deposit -> {
            if (deposit.getStatus() == DepositStatus.PENDING) {
                deposit.setStatus(DepositStatus.FAILED);
                deposit.setFailureReason(reason);
                deposit.setCompletedAt(Instant.now());
            }
        });
    }
}
