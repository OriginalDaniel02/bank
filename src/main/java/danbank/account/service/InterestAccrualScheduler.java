package danbank.account.service;

import danbank.account.model.AccountStatus;
import danbank.account.model.AccountType;
import danbank.account.repository.AccountRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Drives the daily interest-accrual batch job. Kept separate from
 * {@link InterestAccrualService} so each account is processed through the
 * Spring-managed proxy and gets its own transaction (a {@code @Transactional}
 * method called from elsewhere in the same class would silently run with no
 * transaction at all).
 */
@Component
public class InterestAccrualScheduler {

    private static final Logger log = LoggerFactory.getLogger(InterestAccrualScheduler.class);

    private final AccountRepo accountRepository;
    private final InterestAccrualService interestAccrualService;

    public InterestAccrualScheduler(AccountRepo accountRepository, InterestAccrualService interestAccrualService) {
        this.accountRepository = accountRepository;
        this.interestAccrualService = interestAccrualService;
    }

    @Scheduled(cron = "${bank.interest.accrual-cron}")
    public void accrueDailyInterest() {
        List<Long> savingsAccountIds = accountRepository.findIdsByTypeAndStatus(AccountType.SAVINGS, AccountStatus.ACTIVE);
        int creditedCount = 0;

        for (Long accountId : savingsAccountIds) {
            if (interestAccrualService.accrueInterestForOneAccount(accountId)) {
                creditedCount++;
            }
        }

        log.info("Interest accrual run complete: {} of {} savings accounts credited", creditedCount, savingsAccountIds.size());
    }
}
