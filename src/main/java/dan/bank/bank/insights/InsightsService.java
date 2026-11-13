package dan.bank.bank.insights;

import dan.bank.bank.account.model.Account;
import dan.bank.bank.account.model.AccountTransaction;
import dan.bank.bank.account.model.TransactionType;
import dan.bank.bank.account.repository.AccountRepository;
import dan.bank.bank.account.repository.AccountTransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cash-flow insights from the real ledger. There is no merchant data, so there are no "food" or "transport"
 * categories: money is grouped by what actually happened to it (transfer, fee, withdrawal, ...).
 * Moving money to and from your own savings goals is reported separately, never as income or spending.
 */
@Service
public class InsightsService {

    static final int MONTHS = 6;
    private static final int MAX_ROWS_FOR_LARGEST = 5000;

    private final AccountRepository accounts;
    private final AccountTransactionRepository transactions;
    private final ZoneId zone;

    public InsightsService(AccountRepository accounts, AccountTransactionRepository transactions,
                           @Value("${bank.limits.zone:Africa/Lagos}") String zone) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.zone = ZoneId.of(zone);
    }

    public record Month(String month, BigDecimal moneyIn, BigDecimal moneyOut) { }
    public record Slice(String label, BigDecimal amount) { }
    public record Largest(String description, BigDecimal amount, Instant date) { }
    public record View(Long accountId, List<Month> months, Month thisMonth, Month lastMonth,
                       List<Slice> outflow, List<Slice> inflow, BigDecimal savedThisMonth, Largest largestExpense) { }

    @Transactional(readOnly = true)
    public View insights(long userId, Long accountId) {
        Account account = pick(userId, accountId);
        if (account == null) {
            return new View(null, List.of(), new Month(YearMonth.now(zone).toString(), BigDecimal.ZERO, BigDecimal.ZERO),
                    new Month(YearMonth.now(zone).minusMonths(1).toString(), BigDecimal.ZERO, BigDecimal.ZERO),
                    List.of(), List.of(), BigDecimal.ZERO, null);
        }
        YearMonth current = YearMonth.now(zone);
        List<Month> months = new ArrayList<>();
        Map<TransactionType, BigDecimal> currentByType = Map.of();
        for (int i = MONTHS - 1; i >= 0; i--) {
            YearMonth month = current.minusMonths(i);
            Map<TransactionType, BigDecimal> byType = totals(account.getId(), month);
            months.add(new Month(month.toString(), sum(byType, true), sum(byType, false)));
            if (i == 0) currentByType = byType;
        }
        BigDecimal savedOut = currentByType.getOrDefault(TransactionType.SAVINGS_OUT, BigDecimal.ZERO);
        BigDecimal savedIn = currentByType.getOrDefault(TransactionType.SAVINGS_IN, BigDecimal.ZERO);
        return new View(account.getId(), months, months.get(MONTHS - 1), months.get(MONTHS - 2),
                slices(currentByType, false), slices(currentByType, true), savedOut.subtract(savedIn), largestExpense(account.getId(), current));
    }

    private Account pick(long userId, Long accountId) {
        if (accountId != null) {
            return accounts.findByIdAndUser_Id(accountId, userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        }
        return accounts.findByUser_Id(userId).stream().min(Comparator.comparingLong(Account::getId)).orElse(null);
    }

    private Map<TransactionType, BigDecimal> totals(Long accountId, YearMonth month) {
        Instant from = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        Map<TransactionType, BigDecimal> result = new LinkedHashMap<>();
        for (Object[] row : transactions.sumByTypeBetween(accountId, from, to)) {
            result.put((TransactionType) row[0], (BigDecimal) row[1]);
        }
        return result;
    }

    private static boolean isIncome(TransactionType type) {
        return type == TransactionType.CREDIT || type == TransactionType.TRANSFER_IN || type == TransactionType.FEE_INCOME;
    }

    private static boolean isSpending(TransactionType type) {
        return type == TransactionType.DEBIT || type == TransactionType.TRANSFER_OUT || type == TransactionType.FEE;
    }

    private static BigDecimal sum(Map<TransactionType, BigDecimal> byType, boolean income) {
        return byType.entrySet().stream()
                .filter(e -> income ? isIncome(e.getKey()) : isSpending(e.getKey()))
                .map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<Slice> slices(Map<TransactionType, BigDecimal> byType, boolean income) {
        return byType.entrySet().stream()
                .filter(e -> income ? isIncome(e.getKey()) : isSpending(e.getKey()))
                .map(e -> new Slice(label(e.getKey()), e.getValue()))
                .sorted(Comparator.comparing(Slice::amount).reversed())
                .toList();
    }

    private static String label(TransactionType type) {
        return switch (type) {
            case CREDIT -> "Deposits and credits";
            case TRANSFER_IN -> "Transfers received";
            case FEE_INCOME -> "Fee income";
            case DEBIT -> "Withdrawals";
            case TRANSFER_OUT -> "Transfers sent";
            case FEE -> "Fees";
            case SAVINGS_OUT, SAVINGS_IN -> "Savings";
        };
    }

    private Largest largestExpense(Long accountId, YearMonth month) {
        Instant from = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        List<AccountTransaction> rows = transactions.findInRange(accountId, from, to);
        if (rows.size() > MAX_ROWS_FOR_LARGEST) return null;
        return rows.stream().filter(t -> isSpending(t.getType()) && t.getType() != TransactionType.FEE)
                .max(Comparator.comparing(AccountTransaction::getAmount))
                .map(t -> new Largest(t.getDescription() == null || t.getDescription().isBlank() ? label(t.getType()) : t.getDescription(),
                        t.getAmount(), t.getCreatedAt()))
                .orElse(null);
    }

}
