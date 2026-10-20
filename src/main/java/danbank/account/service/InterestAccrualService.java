package danbank.account.service;

import danbank.account.model.Account;
import danbank.account.model.AccountStatus;
import danbank.account.model.AccountTransaction;
import danbank.account.model.TransactionType;
import danbank.account.repository.AccountRepo;
import danbank.account.repository.AccountTransactionRepository;
import danbank.common.audit.Audit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Accrues interest on a single savings account. Each account is locked and
 * processed in its own short transaction rather than one long-running batch
 * transaction over every account, so a single run never holds a write lock
 * on one account while touching the rest.
 */
@Service
public class InterestAccrualService {

    private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("365");

    private final AccountRepo accountRepository;
    private final AccountTransactionRepository transactionRepository;
    private final BigDecimal savingsAnnualRate;

    public InterestAccrualService(
            AccountRepo accountRepository,
            AccountTransactionRepository transactionRepository,
            @Value("${bank.interest.savings-annual-rate}") BigDecimal savingsAnnualRate
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.savingsAnnualRate = savingsAnnualRate;
    }

    @Transactional
    public boolean accrueInterestForOneAccount(long accountId) {
        Account account = accountRepository.findWithLockById(accountId).orElse(null);
        if (account == null || account.getStatus() != AccountStatus.ACTIVE) {
            return false;
        }

        BigDecimal interest = account.getBalance()
                .multiply(savingsAnnualRate)
                .divide(DAYS_IN_YEAR, 2, RoundingMode.HALF_UP);

        if (interest.signum() <= 0) {
            return false;
        }

        account.credit(interest);
        transactionRepository.save(new AccountTransaction(
                account, TransactionType.CREDIT, interest, UUID.randomUUID().toString(), "Daily interest accrual"
        ));
        Audit.log("INTEREST_ACCRUED", "accountId={} amount={}", accountId, interest);
        return true;
    }
}
