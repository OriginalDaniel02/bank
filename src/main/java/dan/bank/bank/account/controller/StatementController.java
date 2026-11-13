package dan.bank.bank.account.controller;

import dan.bank.bank.account.model.TransactionType;
import dan.bank.bank.account.service.AccountService;
import dan.bank.bank.account.service.StatementService;
import dan.bank.bank.auth.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/statement")
public class StatementController {

    private final StatementService statements;
    private final AccountService accounts;
    private final CurrentUser currentUser;

    public StatementController(StatementService statements, AccountService accounts, CurrentUser currentUser) {
        this.statements = statements;
        this.accounts = accounts;
        this.currentUser = currentUser;
    }

    /** Dates are yyyy-MM-dd and inclusive; default is the last 30 days. format=json (default) or csv. */
    @GetMapping
    public ResponseEntity<?> statement(
            @PathVariable long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(defaultValue = "json") String format) {
        accounts.requireOwnedBy(currentUser.id(), accountId);
        LocalDate end = to != null ? to : statements.today();
        LocalDate start = from != null ? from : end.minusDays(29);
        StatementService.Statement statement = statements.statement(accountId, start, end, type);

        if ("csv".equalsIgnoreCase(format)) {
            String filename = "statement-" + statement.accountNumber() + "-" + start + "-" + end + ".csv";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .body(StatementService.toCsv(statement));
        }
        return ResponseEntity.ok(statement);
    }
}
