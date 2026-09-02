package dan.bank.bank.Account.service;

import dan.bank.bank.Account.dtos.AccountDto;
import dan.bank.bank.Account.dtos.AccountResponse;
import dan.bank.bank.Account.dtos.AccountUpdate;
import dan.bank.bank.Account.dtos.MoneyRequest;
import dan.bank.bank.Account.dtos.PageResponse;
import dan.bank.bank.Account.dtos.TransactionDto;
import dan.bank.bank.Account.dtos.TransferRequest;
import dan.bank.bank.Account.model.Account;
import dan.bank.bank.Account.model.AccountStatus;
import dan.bank.bank.Account.model.AccountTransaction;
import dan.bank.bank.Account.model.AccountType;
import dan.bank.bank.Account.model.TransactionType;
import dan.bank.bank.Account.repository.AccountRepo;
import dan.bank.bank.Account.repository.AccountTransactionRepository;
import dan.bank.bank.User.model.User;
import dan.bank.bank.User.repository.UserRepository;
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
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AccountServ {

    private final AccountRepo accountRepository;
    private final AccountTransactionRepository transactionRepository;
    private final UserRepository userRepository;

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
    public AccountResponse<AccountDto> createAccount(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> notFound("User not found"));

        Account account = new Account();
        account.setUser(user);
        account.setAccountName((user.getFirstName() + " " + user.getLastName()).strip());
        account.setAccountNumber(generateAccountNumber());
        account.setBalance(BigDecimal.ZERO.setScale(2,RoundingMode.UNNECESSARY));
        account.setType(AccountType.SAVINGS);
        account.setStatus(AccountStatus.ACTIVE);

        Account savedAccount = accountRepository.save(account);
        return new AccountResponse<>("Account created", toDto(savedAccount));
    }

    @Transactional(readOnly = true)
    public AccountResponse<List<AccountDto>> getAllUserAccount() {
        List<AccountDto> accounts = accountRepository.findAll().stream()
                .map(this::toDto)
                .toList();
        return new AccountResponse<>("Accounts found", accounts);
    }

    @Transactional(readOnly = true)
    public AccountResponse<AccountDto> getAccount(Long accountId) {
        return new AccountResponse<>("Account found", toDto(findAccount(accountId)));
    }

    private static final int MAX_PAGE_SIZE = 100;

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
    public AccountResponse<AccountDto> updateAccount(Long accountId, Long userId, AccountUpdate update) {
        Account account = findOwnedAccount(userId, accountId);
        if (update.type() != null) account.setType(update.type());
        if (update.status() != null) account.setStatus(update.status());
        return new AccountResponse<>("Account updated", toDto(account));
    }

    private Account findActiveAccountWithLock(long accountId) {
        Account account = accountRepository.findWithLockById(accountId)
                .orElseThrow(() -> notFound("Account not found"));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account is not active");
        }
        return account;
    }

    private Account findOwnedAccount(long userId, long accountId) {
        return accountRepository.findByIdAndUser_Id(accountId, userId)
                .orElseThrow(() -> notFound("Account not found"));
    }

    private Account findAccount(long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> notFound("Account not found"));
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

    private String generateAccountNumber() {
        String accountNumber;
        do {
            accountNumber = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L));
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    private AccountDto toDto(Account account) {
        return new AccountDto(account.getId(), account.getUser().getId(), account.getAccountName(), account.getAccountNumber(), account.getBalance(), account.getType(), account.getStatus());
    }

    private TransactionDto toDto(AccountTransaction transaction) {
        return new TransactionDto(transaction.getReference(), transaction.getType().name(), transaction.getAccount().getId(), transaction.getAmount(), transaction.getDescription(), transaction.getCreatedAt());
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
