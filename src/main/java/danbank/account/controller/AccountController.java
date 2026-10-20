package danbank.account.controller;

import danbank.account.dtos.AccountDto;
import danbank.account.dtos.AccountUpdate;
import danbank.account.dtos.MoneyRequest;
import danbank.account.dtos.PageResponse;
import danbank.account.dtos.TransactionDto;
import danbank.account.dtos.TransferRequest;
import danbank.account.model.Currency;
import danbank.account.model.TransactionType;
import danbank.account.service.AccountServ;
import danbank.common.dto.ApiResponse;
import danbank.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AccountServ accountService;

    public AccountController(AccountServ accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountDto>>> getMyAccounts(@AuthenticationPrincipal AuthUser caller) {
        return ResponseEntity.ok(accountService.getAccountsForUser(caller.getId()));
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AccountDto>>> getAllAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return ResponseEntity.ok(accountService.getAllUserAccount(pageable));
    }

    @GetMapping("/{accountId}")
    @PreAuthorize("@accessPolicy.ownsAccount(authentication, #accountId)")
    public ResponseEntity<ApiResponse<AccountDto>> getAccount(@PathVariable long accountId) {
        return ResponseEntity.ok(accountService.getAccount(accountId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AccountDto>> createAccount(
            @AuthenticationPrincipal AuthUser caller,
            @RequestParam(defaultValue = "NGN") Currency currency
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.createAccount(caller.getId(), currency));
    }

    @PostMapping("/{accountId}/credit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TransactionDto>> creditAccount(
            @PathVariable long accountId,
            @Valid @RequestBody MoneyRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.credit(accountId, request));
    }

    @PostMapping("/{accountId}/debit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TransactionDto>> debitAccount(
            @PathVariable long accountId,
            @Valid @RequestBody MoneyRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.debit(accountId, request));
    }

    @GetMapping("/{accountId}/transactions")
    @PreAuthorize("@accessPolicy.ownsAccount(authentication, #accountId)")
    public ResponseEntity<ApiResponse<PageResponse<TransactionDto>>> getTransactionHistory(
            @PathVariable long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return ResponseEntity.ok(accountService.getTransactionHistory(accountId, page, size, type, from, to));
    }

    @GetMapping("/{accountId}/transactions/statement.csv")
    @PreAuthorize("@accessPolicy.ownsAccount(authentication, #accountId)")
    public ResponseEntity<String> downloadStatement(
            @PathVariable long accountId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        List<TransactionDto> transactions = accountService.getTransactionsForStatement(accountId, type, from, to);

        StringBuilder csv = new StringBuilder("date,type,reference,transfer_group,amount,description\n");
        for (TransactionDto t : transactions) {
            csv.append(t.createdAt()).append(',')
                    .append(t.type()).append(',')
                    .append(t.reference()).append(',')
                    .append(t.transferGroup() != null ? t.transferGroup() : "").append(',')
                    .append(t.amount()).append(',')
                    .append('"').append(t.description() != null ? t.description().replace("\"", "\"\"") : "").append('"')
                    .append('\n');
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header("Content-Disposition", "attachment; filename=\"statement-" + accountId + ".csv\"")
                .body(csv.toString());
    }

    @PostMapping("/transfer")
    @PreAuthorize("@accessPolicy.ownsAccount(authentication, #request.fromAccountId())")
    public ResponseEntity<ApiResponse<List<TransactionDto>>> transfer(
            @Valid @RequestBody TransferRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.transfer(request));
    }

    @PutMapping("/{accountId}")
    public ResponseEntity<ApiResponse<AccountDto>> updateAccount(
            @PathVariable long accountId,
            @AuthenticationPrincipal AuthUser caller,
            @Valid @RequestBody AccountUpdate request
    ) {
        return ResponseEntity.ok(accountService.updateAccount(accountId, caller.getId(), request));
    }

    @PostMapping("/{accountId}/deactivate")
    public ResponseEntity<ApiResponse<AccountDto>> deactivateAccount(
            @PathVariable long accountId,
            @AuthenticationPrincipal AuthUser caller
    ) {
        return ResponseEntity.ok(accountService.deActivateAccount(caller.getId(), accountId));
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<ApiResponse<AccountDto>> closeAccount(
            @PathVariable long accountId,
            @AuthenticationPrincipal AuthUser caller
    ) {
        return ResponseEntity.ok(accountService.closeAccount(caller.getId(), accountId));
    }
}
