package dan.bank.bank.Account.controller;

import dan.bank.bank.Account.dtos.AccountDto;
import dan.bank.bank.Account.dtos.AccountResponse;
import dan.bank.bank.Account.dtos.AccountUpdate;
import dan.bank.bank.Account.dtos.MoneyRequest;
import dan.bank.bank.Account.dtos.PageResponse;
import dan.bank.bank.Account.dtos.TransactionDto;
import dan.bank.bank.Account.dtos.TransferRequest;
import dan.bank.bank.Account.service.AccountServ;
import dan.bank.bank.Auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final CurrentUser currentUser;

    public AccountController(AccountServ accountService, CurrentUser currentUser) {
        this.accountService = accountService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ResponseEntity<AccountResponse<List<AccountDto>>> getAllAccounts() {
        return ResponseEntity.ok(accountService.getAccountsForUser(currentUser.id()));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse<AccountDto>> getAccount(@PathVariable long accountId) {
        accountService.requireOwnedBy(currentUser.id(), accountId);
        return ResponseEntity.ok(accountService.getAccount(accountId));
    }

    @PostMapping("/user/{userId}")
    public ResponseEntity<AccountResponse<AccountDto>> createAccount(@PathVariable long userId) {
        currentUser.requireSelf(userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.createAccount(userId));
    }

    @PostMapping("/{accountId}/credit")
    public ResponseEntity<AccountResponse<TransactionDto>> creditAccount(
            @PathVariable long accountId,
            @Valid @RequestBody MoneyRequest request
    ) {
        accountService.requireOwnedBy(currentUser.id(), accountId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.credit(accountId, request));
    }

    @PostMapping("/{accountId}/debit")
    public ResponseEntity<AccountResponse<TransactionDto>> debitAccount(
            @PathVariable long accountId,
            @Valid @RequestBody MoneyRequest request
    ) {
        accountService.requireOwnedBy(currentUser.id(), accountId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.debit(accountId, request));
    }

    @GetMapping("/{accountId}/transactions")
    public ResponseEntity<AccountResponse<PageResponse<TransactionDto>>> getTransactionHistory(
            @PathVariable long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        accountService.requireOwnedBy(currentUser.id(), accountId);
        return ResponseEntity.ok(accountService.getTransactionHistory(accountId, page, size));
    }

    @PostMapping("/transfer")
    public ResponseEntity<AccountResponse<List<TransactionDto>>> transfer(
            @Valid @RequestBody TransferRequest request
    ) {
        // You may only send money out of your own account; any active account can receive.
        accountService.requireOwnedBy(currentUser.id(), request.fromAccountId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.transfer(request));
    }

    @PutMapping("/{accountId}/user/{userId}")
    public ResponseEntity<AccountResponse<AccountDto>> updateAccount(
            @PathVariable long accountId,
            @PathVariable long userId,
            @Valid @RequestBody AccountUpdate request
    ) {
        currentUser.requireSelf(userId);
        return ResponseEntity.ok(accountService.updateAccount(accountId, userId, request));
    }

    @PostMapping("/{accountId}/user/{userId}/deactivate")
    public ResponseEntity<AccountResponse<AccountDto>> deactivateAccount(
            @PathVariable long accountId,
            @PathVariable long userId
    ) {
        currentUser.requireSelf(userId);
        return ResponseEntity.ok(accountService.deActivateAccount(userId, accountId));
    }

    @DeleteMapping("/{accountId}/user/{userId}")
    public ResponseEntity<AccountResponse<AccountDto>> deleteAccount(
            @PathVariable long accountId,
            @PathVariable long userId
    ) {
        currentUser.requireSelf(userId);
        return ResponseEntity.ok(accountService.deleteAccount(userId, accountId));
    }
}
