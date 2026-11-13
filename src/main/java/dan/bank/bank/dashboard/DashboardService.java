package dan.bank.bank.dashboard;

import dan.bank.bank.account.model.Account;
import dan.bank.bank.account.model.AccountStatus;
import dan.bank.bank.account.model.AccountTransaction;
import dan.bank.bank.account.model.TransactionType;
import dan.bank.bank.account.repository.AccountRepository;
import dan.bank.bank.account.repository.AccountTransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Everything the home screen needs in one call: accounts, this month's totals, recent activity, 7-day spending. */
@Service
public class DashboardService {

    private static final int RECENT_COUNT = 10;
    private static final int SPENDING_DAYS = 7;

    private final AccountRepository accountRepository;
    private final AccountTransactionRepository transactionRepository;
    private final ZoneId zone;

    public DashboardService(AccountRepository accountRepository, AccountTransactionRepository transactionRepository,
                            @Value("${bank.limits.zone:Africa/Lagos}") String zone) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.zone = ZoneId.of(zone);
    }

    @Transactional(readOnly = true)
    public DashboardView dashboard(long userId, Long requestedAccountId) {
        List<Account> accounts = accountRepository.findByUser_IdAndStatusNot(userId, AccountStatus.CLOSED).stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId())).toList();
        List<AccountView> accountViews = accounts.stream().map(DashboardService::toView).toList();

        Account selected = accounts.stream()
                .filter(a -> requestedAccountId == null || a.getId() == requestedAccountId)
                .findFirst().orElse(null);
        if (requestedAccountId != null && selected == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        if (selected == null) {
            return new DashboardView(accountViews, null, new MonthlySummary(BigDecimal.ZERO, BigDecimal.ZERO),
                    List.of(), emptyWeek());
        }

        LocalDate today = LocalDate.now(zone);
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant();
        Instant tomorrow = today.plusDays(1).atStartOfDay(zone).toInstant();
        BigDecimal moneyIn = BigDecimal.ZERO;
        BigDecimal moneyOut = BigDecimal.ZERO;
        for (Object[] row : transactionRepository.sumByTypeBetween(selected.getId(), monthStart, tomorrow)) {
            if (isOwnSavingsMove((TransactionType) row[0])) continue;
            BigDecimal signed = signed((TransactionType) row[0], (BigDecimal) row[1]);
            if (signed.signum() > 0) moneyIn = moneyIn.add(signed); else moneyOut = moneyOut.add(signed.negate());
        }

        List<TransactionView> recent = transactionRepository
                .findByAccount_IdOrderByCreatedAtDesc(selected.getId(), PageRequest.of(0, RECENT_COUNT))
                .map(DashboardService::toView).getContent();

        return new DashboardView(accountViews, selected.getId(), new MonthlySummary(moneyIn, moneyOut), recent,
                dailySpending(selected.getId(), today, tomorrow));
    }

    private List<DailySpend> dailySpending(Long accountId, LocalDate today, Instant tomorrow) {
        LocalDate first = today.minusDays(SPENDING_DAYS - 1L);
        Map<LocalDate, BigDecimal> perDay = new TreeMap<>();
        for (int i = 0; i < SPENDING_DAYS; i++) perDay.put(first.plusDays(i), BigDecimal.ZERO);
        for (AccountTransaction t : transactionRepository.findInRange(accountId, first.atStartOfDay(zone).toInstant(), tomorrow)) {
            if (!isOwnSavingsMove(t.getType()) && signed(t.getType(), t.getAmount()).signum() < 0) {
                perDay.merge(t.getCreatedAt().atZone(zone).toLocalDate(), t.getAmount(), BigDecimal::add);
            }
        }
        List<DailySpend> result = new ArrayList<>();
        perDay.forEach((date, amount) -> result.add(new DailySpend(date, amount)));
        return result;
    }

    private List<DailySpend> emptyWeek() {
        LocalDate today = LocalDate.now(zone);
        List<DailySpend> week = new ArrayList<>();
        for (int i = SPENDING_DAYS - 1; i >= 0; i--) week.add(new DailySpend(today.minusDays(i), BigDecimal.ZERO));
        return week;
    }

    /** Money moved between your account and your own savings goals is neither income nor spending. */
    private static boolean isOwnSavingsMove(TransactionType type) {
        return type == TransactionType.SAVINGS_OUT || type == TransactionType.SAVINGS_IN;
    }

    private static BigDecimal signed(TransactionType type, BigDecimal amount) {
        return switch (type) {
            case CREDIT, TRANSFER_IN, FEE_INCOME, SAVINGS_IN -> amount;
            case DEBIT, TRANSFER_OUT, FEE, SAVINGS_OUT -> amount.negate();
        };
    }

    private static AccountView toView(Account a) {
        return new AccountView(a.getId(), a.getType().name(), a.getAccountName(), a.getAccountNumber(),
                a.getBalance(), a.getStatus().name(), a.getCreatedAt());
    }

    private static TransactionView toView(AccountTransaction t) {
        boolean credit = signed(t.getType(), t.getAmount()).signum() > 0;
        return new TransactionView(t.getReference(), t.getType().name(), credit ? "CREDIT" : "DEBIT", t.getAmount(),
                t.getDescription(), t.getCreatedAt(), "COMPLETED");
    }

    public record DashboardView(List<AccountView> accounts, Long selectedAccountId, MonthlySummary monthlySummary,
                                List<TransactionView> recentTransactions, List<DailySpend> dailySpending) { }

    public record AccountView(Long id, String type, String name, String accountNumber, BigDecimal availableBalance,
                              String status, Instant openedAt) { }

    public record MonthlySummary(BigDecimal moneyIn, BigDecimal moneyOut) { }

    public record TransactionView(String reference, String type, String direction, BigDecimal amount,
                                  String description, Instant createdAt, String status) { }

    public record DailySpend(LocalDate date, BigDecimal amount) { }
}
