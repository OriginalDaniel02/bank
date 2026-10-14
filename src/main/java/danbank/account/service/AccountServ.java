package danbank.account.service;

import danbank.account.dtos.AccountDto;
import danbank.account.dtos.AccountResponse;
import danbank.account.dtos.AccountUpdate;
import danbank.account.dtos.MoneyRequest;
import danbank.account.dtos.PageResponse;
import danbank.account.dtos.TransactionDto;
import danbank.account.dtos.TransferRequest;
import danbank.account.exception.AccountNotFoundException;
import danbank.account.exception.AccountOwnerNotFoundException;
import danbank.account.model.Account;
import danbank.account.model.AccountStatus;
import danbank.account.model.AccountTransaction;
import danbank.account.model.AccountType;
import danbank.account.model.TransactionType;
import danbank.account.repository.AccountRepo;
import danbank.account.repository.AccountTransactionRepository;
import danbank.user.exception.UserDuplicateException;
import danbank.user.exception.UserNotFoundException;
import danbank.user.model.User;
import danbank.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class AccountServ {

    private final AccountRepo accountRepository;
    private final AccountTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private static final int MAX_PAGE_SIZE = 100;

    public AccountServ(
            AccountRepo accountRepository,
            AccountTransactionRepository transactionRepository,
            UserRepository userRepository
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AccountResponse createAccount(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserDuplicateException::new);

        Account account = new Account();
        account.(user);
        account.setAccountName((user.getFirstName() + " " + user.getLastName()).strip());
        account.setAccountNumber(user.getPhoneNumber().strip().substring(2));
        account.setBalance(BigDecimal.ZERO.setScale(2,RoundingMode.UNNECESSARY));
        account.setType(AccountType.SAVINGS);
        account.setStatus(AccountStatus.ACTIVE);

        Account savedAccount = accountRepository.save(account);
        return AccountResponse.from(savedAccount);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAllUserAccount() {
        return accountRepository.findAll().stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long accountId) {
        return AccountResponse.from(findAccount(accountId));
    }


    @Transactional(readOnly = true)
    public AccountResponse<PageResponse<TransactionDto>> getTransactionHistory(long accountId, int page, int size) {
        findAccount(accountId);

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<AccountTransaction> transactions = transactionRepository.findByAccount_IdOrderByCreatedAtDesc(accountId, pageable);

        PageResponse<TransactionDto> pageResponse = new PageResponse<>(
                transactions.map(this::toDto).getContent(),
                transactions.getNumber(),
                transactions.getSize(),
                transactions.getTotalElements(),
                transactions.getTotalPages()
        );
        return new AccountResponse<>("Transactions found", pageResponse);
    }

    @Transactional
    public AccountResponse<TransactionDto> credit(long accountId, MoneyRequest request) {
        Account account = findActiveAccountWithLock(accountId);
        BigDecimal amount = normalizeMoney(request.amount());
        account.setBalance(account.getBalance().add(amount));

        TransactionDto transaction = recordTransaction(
                account, TransactionType.CREDIT, amount, request.description()
        );
        return new AccountResponse<>("Account credited", transaction);
    }

    @Transactional
    public AccountResponse<TransactionDto> debit(long accountId, MoneyRequest request) {
        Account account = findActiveAccountWithLock(accountId);
        BigDecimal amount = normalizeMoney(request.amount());
        ensureSufficientFunds(account, amount);
        account.setBalance(account.getBalance().subtract(amount));

        TransactionDto transaction = recordTransaction(
                account, TransactionType.DEBIT, amount, request.description()
        );
        return new AccountResponse<>("Account debited", transaction);
    }

    @Transactional
    public AccountResponse<List<TransactionDto>> transfer(TransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Source and destination accounts must differ"
            );
        }

        // Always take locks in account-id order. This prevents A->B and B->A deadlocks.
        long lowerId = Math.min(request.fromAccountId(), request.toAccountId());
        long higherId = Math.max(request.fromAccountId(), request.toAccountId());
        Account lowerAccount = findActiveAccountWithLock(lowerId);
        Account higherAccount = findActiveAccountWithLock(higherId);

        Account sourceAccount = request.fromAccountId() == lowerId ? lowerAccount : higherAccount;
        Account destinationAccount = request.toAccountId() == lowerId ? lowerAccount : higherAccount;
        BigDecimal amount = normalizeMoney(request.amount());

        ensureSufficientFunds(sourceAccount, amount);
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
        destinationAccount.setBalance(destinationAccount.getBalance().add(amount));

        String reference = UUID.randomUUID().toString();
        List<TransactionDto> transactionEntries = List.of(
                recordTransaction(sourceAccount, TransactionType.TRANSFER_OUT, amount, request.description(), reference),
                recordTransaction(destinationAccount, TransactionType.TRANSFER_IN, amount, request.description(), reference)
        );
        return new AccountResponse<>("Transfer completed", transactionEntries);
    }

    @Transactional
    public AccountResponse<AccountDto> deActivateAccount(Long userId, Long accountId) {
        Account account = findOwnedAccount(userId, accountId);
        account.setStatus(AccountStatus.NOT_ACTIVE);
        return new AccountResponse<>("Account deactivated", toDto(account));
    }

    @Transactional
    public AccountResponse<AccountDto> deleteAccount(Long userId, Long accountId) {
        Account account = findOwnedAccount(userId, accountId);
        if (account.getBalance().signum() != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only zero-balance accounts can be deleted");
        }
        accountRepository.delete(account);
        return new AccountResponse<>("Account deleted", null);
    }

    @Transactional
    public AccountResponse updateAccount(Long accountId, Long userId, AccountUpdate update) {
        Account account = findOwnedAccount(userId, accountId);
        if (update.type() != null) account.setType(update.type());
        if (update.status() != null) account.setStatus(update.status());
        return new AccountResponse<>("Account updated", toDto(account));
    }

    private Account findActiveAccountWithLock(long accountId) {
        Account account = accountRepository.findWithLockById(accountId)
                .orElseThrow(AccountOwnerNotFoundException::new);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is not active");
        }
        return account;
    }

    private Account findOwnedAccount(long userId, long accountId) {
        return accountRepository.findByIdAndUser_Id(accountId, userId)
                .orElseThrow(AccountOwnerNotFoundException::new);
    }

    private Account findAccount(long accountId) {
        return accountRepository.findById(accountId).orElseThrow(AccountNotFoundException::new);
    }

    private void ensureSufficientFunds(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient funds");
        }
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private TransactionDto recordTransaction(Account account, TransactionType type, BigDecimal amount, String description) {
        return recordTransaction(account, type, amount, description, UUID.randomUUID().toString());
    }

    private TransactionDto recordTransaction(Account account, TransactionType type, BigDecimal amount, String description, String reference) {
        AccountTransaction savedTransaction = transactionRepository.save(
                new AccountTransaction(account, type, amount, reference, description)
        );
        return new TransactionDto(savedTransaction.getReference(), savedTransaction.getType().name(), account.getId(), savedTransaction.getAmount(), savedTransaction.getDescription(), savedTransaction.getCreatedAt());
    }

    private AccountDto toDto(Account account) {
        return new AccountDto(account.getId(), account.getUser().getId(), account.getAccountName(), account.getAccountNumber(), account.getBalance(), account.getType(), account.getStatus());
    }

    private TransactionDto toDto(AccountTransaction transaction) {
        return new TransactionDto(transaction.getReference(), transaction.getType().name(), transaction.getAccount().getId(), transaction.getAmount(), transaction.getDescription(), transaction.getCreatedAt());
    }

    private AccountResponse saveNew(){

    }


}
