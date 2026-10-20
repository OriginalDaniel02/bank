package danbank.account.service;

import danbank.account.model.Account;
import danbank.account.model.AccountStatus;
import danbank.account.model.AccountTransaction;
import danbank.account.model.AccountType;
import danbank.account.model.TransactionType;
import danbank.account.repository.AccountRepo;
import danbank.account.repository.AccountTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Accrues interest on active savings accounts on a fixed schedule, the same way a
 * retail bank runs an end-of-day batch job. The configured rate is an annual
 * percentage; each run credits balance * (annualRate / 365).
 */
@Service
public class InterestAccrualService {

    private static final Logger log = LoggerFactory.getLogger(InterestAccrualService.class);
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

    @Scheduled(cron = "${bank.interest.accrual-cron}")
    @Transactional
    public void accrueDailyInterest() {
        List<Account> savingsAccounts = accountRepository.findByTypeAndStatus(AccountType.SAVINGS, AccountStatus.ACTIVE);
        int creditedCount = 0;

        for (Account account : savingsAccounts) {
            BigDecimal interest = account.getBalance()
                    .multiply(savingsAnnualRate)
                    .divide(DAYS_IN_YEAR, 2, RoundingMode.HALF_UP);

            if (interest.signum() <= 0) {
                continue;
            }

            account.credit(interest);
            transactionRepository.save(new AccountTransaction(
                    account, TransactionType.CREDIT, interest, UUID.randomUUID().toString(), "Daily interest accrual"
            ));
            creditedCount++;
        }

        log.info("Interest accrual run complete: {} of {} savings accounts credited", creditedCount, savingsAccounts.size());
    }
}
