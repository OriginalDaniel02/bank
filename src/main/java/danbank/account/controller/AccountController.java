package danbank.account.controller;

import danbank.account.dtos.AccountDto;
import danbank.account.dtos.AccountUpdate;
import danbank.account.dtos.MoneyRequest;
import danbank.account.dtos.PageResponse;
import danbank.account.dtos.TransactionDto;
import danbank.account.dtos.TransferRequest;
import danbank.account.service.AccountServ;
import danbank.common.dto.ApiResponse;
import danbank.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

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
    public ResponseEntity<ApiResponse<List<AccountDto>>> getAllAccounts() {
        return ResponseEntity.ok(accountService.getAllUserAccount());
    }

    @GetMapping("/{accountId}")
    @PreAuthorize("@accessPolicy.ownsAccount(authentication, #accountId)")
    public ResponseEntity<ApiResponse<AccountDto>> getAccount(@PathVariable long accountId) {
        return ResponseEntity.ok(accountService.getAccount(accountId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AccountDto>> createAccount(@AuthenticationPrincipal AuthUser caller) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.createAccount(caller.getId()));
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
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(accountService.getTransactionHistory(accountId, page, size));
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
    public ResponseEntity<ApiResponse<AccountDto>> deleteAccount(
            @PathVariable long accountId,
            @AuthenticationPrincipal AuthUser caller
    ) {
        return ResponseEntity.ok(accountService.deleteAccount(caller.getId(), accountId));
    }
}
