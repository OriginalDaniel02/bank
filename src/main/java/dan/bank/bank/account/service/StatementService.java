package dan.bank.bank.account.service;

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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Account statements. The ledger does not store a balance after each row, so balances are derived:
 * the closing balance is the current balance minus everything after the period, and the opening
 * balance is the closing balance minus the period's net movement.
 */
@Service
public class StatementService {

    static final int MAX_DAYS = 366;
    static final int MAX_ROWS = 5000;

    private final AccountRepository accountRepository;
    private final AccountTransactionRepository transactionRepository;
    private final ZoneId zone;

    public StatementService(AccountRepository accountRepository, AccountTransactionRepository transactionRepository,
                            @Value("${bank.limits.zone:Africa/Lagos}") String zone) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.zone = ZoneId.of(zone);
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    @Transactional(readOnly = true)
    public Statement statement(long accountId, LocalDate from, LocalDate to, TransactionType typeFilter) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statement range cannot exceed " + MAX_DAYS + " days");
        }
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        Instant start = from.atStartOfDay(zone).toInstant();
        Instant endExclusive = to.plusDays(1).atStartOfDay(zone).toInstant();

        BigDecimal netAfterPeriod = BigDecimal.ZERO;
        for (Object[] row : transactionRepository.sumByTypeSince(accountId, endExclusive)) {
            netAfterPeriod = netAfterPeriod.add(signed((TransactionType) row[0], (BigDecimal) row[1]));
        }
        List<AccountTransaction> rows = transactionRepository.findInRange(accountId, start, endExclusive);
        if (rows.size() > MAX_ROWS) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Too many transactions; choose a shorter range");
        }

        BigDecimal netInPeriod = BigDecimal.ZERO;
        for (AccountTransaction t : rows) netInPeriod = netInPeriod.add(signed(t.getType(), t.getAmount()));
        BigDecimal closing = account.getBalance().subtract(netAfterPeriod);
        BigDecimal opening = closing.subtract(netInPeriod);

        BigDecimal running = opening;
        BigDecimal credits = BigDecimal.ZERO;
        BigDecimal debits = BigDecimal.ZERO;
        List<Line> lines = new ArrayList<>();
        for (AccountTransaction t : rows) {
            BigDecimal delta = signed(t.getType(), t.getAmount());
            running = running.add(delta);
            if (delta.signum() > 0) credits = credits.add(t.getAmount()); else debits = debits.add(t.getAmount());
            if (typeFilter == null || t.getType() == typeFilter) {
                lines.add(new Line(t.getCreatedAt(), t.getReference(), t.getType().name(),
                        delta.signum() > 0 ? "CREDIT" : "DEBIT", t.getAmount(), running, t.getDescription()));
            }
        }
        return new Statement(account.getAccountNumber(), account.getAccountName(), from, to,
                opening, closing, credits, debits, lines);
    }

    static BigDecimal signed(TransactionType type, BigDecimal amount) {
        return switch (type) {
            case CREDIT, TRANSFER_IN, FEE_INCOME -> amount;
            case DEBIT, TRANSFER_OUT, FEE -> amount.negate();
        };
    }

    /** CSV with spreadsheet-formula injection guarded: text cells starting with = + - @ are prefixed. */
    public static String toCsv(Statement s) {
        StringBuilder out = new StringBuilder();
        out.append("Account,").append(cell(s.accountName())).append('\n');
        out.append("Account number,").append(s.accountNumber()).append('\n');
        out.append("Period,").append(s.from()).append(" to ").append(s.to()).append('\n');
        out.append("Opening balance,").append(s.openingBalance().toPlainString()).append('\n');
        out.append("Closing balance,").append(s.closingBalance().toPlainString()).append('\n');
        out.append("Total credits,").append(s.totalCredits().toPlainString()).append('\n');
        out.append("Total debits,").append(s.totalDebits().toPlainString()).append("\n\n");
        out.append("Date,Reference,Type,Direction,Amount,Balance,Description\n");
        for (Line l : s.lines()) {
            out.append(l.date()).append(',').append(cell(l.reference())).append(',').append(l.type()).append(',')
                    .append(l.direction()).append(',').append(l.amount().toPlainString()).append(',')
                    .append(l.balance().toPlainString()).append(',').append(cell(l.description())).append('\n');
        }
        return out.toString();
    }

    private static String cell(String value) {
        if (value == null) return "";
        String text = value.replace("\r", " ").replace("\n", " ");
        if (!text.isEmpty() && "=+-@\t".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        if (text.contains(",") || text.contains("\"")) text = "\"" + text.replace("\"", "\"\"") + "\"";
        return text;
    }

    public record Line(Instant date, String reference, String type, String direction,
                       BigDecimal amount, BigDecimal balance, String description) { }

    public record Statement(String accountNumber, String accountName, LocalDate from, LocalDate to,
                            BigDecimal openingBalance, BigDecimal closingBalance,
                            BigDecimal totalCredits, BigDecimal totalDebits, List<Line> lines) { }
}
