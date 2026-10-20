package danbank.account.service;

import danbank.account.dtos.AccountDto;
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
import danbank.account.model.TransactionType;
import danbank.account.repository.AccountRepo;
import danbank.account.repository.AccountTransactionRepository;
import danbank.common.dto.ApiResponse;
import danbank.user.exception.UserNotFoundException;
import danbank.user.model.User;
import danbank.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class AccountServ {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int ACCOUNT_NUMBER_LENGTH = 10;
    private static final int MAX_ACCOUNT_NUMBER_ATTEMPTS = 10;

    private final AccountRepo accountRepository;
    private final AccountTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();
    private final BigDecimal dailyDebitLimit;

    public AccountServ(
            AccountRepo accountRepository,
            AccountTransactionRepository transactionRepository,
            UserRepository userRepository,
            @Value("${bank.limits.daily-debit-limit}") BigDecimal dailyDebitLimit
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.dailyDebitLimit = dailyDebitLimit;
    }

    @Transactional
    public ApiResponse<AccountDto> createAccount(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        Account account = Account.builder()
                .user(user)
                .accountName((user.getFirstName() + " " + user.getLastName()).strip())
                .accountNumber(generateUniqueAccountNumber())
                .build();

        Account savedAccount = accountRepository.save(account);
        return ApiResponse.ok("Account created", toDto(savedAccount));
    }

    @Transactional(readOnly = true)
    public ApiResponse<List<AccountDto>> getAllUserAccount() {
        List<AccountDto> accounts = accountRepository.findAll().stream()
                .map(this::toDto)
                .toList();
        return ApiResponse.ok("Accounts found", accounts);
    }

    @Transactional(readOnly = true)
    public ApiResponse<List<AccountDto>> getAccountsForUser(long userId) {
        List<AccountDto> accounts = accountRepository.findByUser_Id(userId).stream()
                .map(this::toDto)
                .toList();
        return ApiResponse.ok("Accounts found", accounts);
    }

    @Transactional(readOnly = true)
    public ApiResponse<AccountDto> getAccount(long accountId) {
        return ApiResponse.ok("Account found", toDto(findAccount(accountId)));
    }

    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<TransactionDto>> getTransactionHistory(long accountId, int page, int size) {
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
        return ApiResponse.ok("Transactions found", pageResponse);
    }

    @Transactional
    public ApiResponse<TransactionDto> credit(long accountId, MoneyRequest request) {
        Account account = findActiveAccountWithLock(accountId);
        BigDecimal amount = normalizeMoney(request.amount());
        account.credit(amount);

        TransactionDto transaction = recordTransaction(
                account, TransactionType.CREDIT, amount, request.description()
        );
        return ApiResponse.ok("Account credited", transaction);
    }

    @Transactional
    public ApiResponse<TransactionDto> debit(long accountId, MoneyRequest request) {
        Account account = findActiveAccountWithLock(accountId);
        BigDecimal amount = normalizeMoney(request.amount());
        ensureSufficientFunds(account, amount);
        ensureWithinDailyLimit(account, amount);
        account.debit(amount);

        TransactionDto transaction = recordTransaction(
                account, TransactionType.DEBIT, amount, request.description()
        );
        return ApiResponse.ok("Account debited", transaction);
    }

    @Transactional
    public ApiResponse<List<TransactionDto>> transfer(TransferRequest request) {
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
        ensureWithinDailyLimit(sourceAccount, amount);
        sourceAccount.debit(amount);
        destinationAccount.credit(amount);

        String transferGroup = UUID.randomUUID().toString();
        List<TransactionDto> transactionEntries = List.of(
                recordTransaction(sourceAccount, TransactionType.TRANSFER_OUT, amount, request.description(), transferGroup),
                recordTransaction(destinationAccount, TransactionType.TRANSFER_IN, amount, request.description(), transferGroup)
        );
        return ApiResponse.ok("Transfer completed", transactionEntries);
    }

    @Transactional
    public ApiResponse<AccountDto> deActivateAccount(long userId, long accountId) {
        Account account = findOwnedAccount(userId, accountId);
        account.deactivate();
        return ApiResponse.ok("Account deactivated", toDto(account));
    }

    @Transactional
    public ApiResponse<AccountDto> deleteAccount(long userId, long accountId) {
        Account account = findOwnedAccount(userId, accountId);
        if (account.getBalance().signum() != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only zero-balance accounts can be deleted");
        }
        accountRepository.delete(account);
        return ApiResponse.ok("Account deleted", toDto(account));
    }

    @Transactional
    public ApiResponse<AccountDto> updateAccount(long accountId, long userId, AccountUpdate update) {
        Account account = findOwnedAccount(userId, accountId);
        if (update.type() != null) {
            account.changeType(update.type());
        }
        if (update.status() == AccountStatus.ACTIVE) {
            account.activate();
        } else if (update.status() == AccountStatus.NOT_ACTIVE) {
            account.deactivate();
        }
        return ApiResponse.ok("Account updated", toDto(account));
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

    private void ensureWithinDailyLimit(Account account, BigDecimal amount) {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        BigDecimal alreadySpentToday = transactionRepository.sumOutgoingSince(account.getId(), startOfDay);
        if (alreadySpentToday.add(amount).compareTo(dailyDebitLimit) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This transaction would exceed the daily debit limit of " + dailyDebitLimit
            );
        }
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private String generateUniqueAccountNumber() {
        for (int attempt = 0; attempt < MAX_ACCOUNT_NUMBER_ATTEMPTS; attempt++) {
            String candidate = randomDigits(ACCOUNT_NUMBER_LENGTH);
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique account number, please retry");
    }

    private String randomDigits(int length) {
        StringBuilder digits = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            digits.append(random.nextInt(10));
        }
        return digits.toString();
    }

    private TransactionDto recordTransaction(Account account, TransactionType type, BigDecimal amount, String description) {
        AccountTransaction savedTransaction = transactionRepository.save(
                new AccountTransaction(account, type, amount, UUID.randomUUID().toString(), description)
        );
        return toDto(savedTransaction);
    }

    private TransactionDto recordTransaction(Account account, TransactionType type, BigDecimal amount, String description, String transferGroup) {
        AccountTransaction savedTransaction = transactionRepository.save(
                new AccountTransaction(account, type, amount, UUID.randomUUID().toString(), transferGroup, description)
        );
        return toDto(savedTransaction);
    }

    private AccountDto toDto(Account account) {
        return AccountDto.from(account);
    }

    private TransactionDto toDto(AccountTransaction transaction) {
        return new TransactionDto(
                transaction.getReference(),
                transaction.getTransferGroup(),
                transaction.getType().name(),
                transaction.getAccount().getId(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getCreatedAt()
        );
    }
}
