package danbank.account.service;

import danbank.account.dtos.AccountDto;
import danbank.account.dtos.AccountUpdate;
import danbank.account.dtos.MoneyRequest;
import danbank.account.dtos.PageResponse;
import danbank.account.dtos.TransactionDto;
import danbank.account.dtos.TransferRequest;
import danbank.account.exception.AccountNotActiveException;
import danbank.account.exception.AccountNotFoundException;
import danbank.account.exception.AccountOwnerNotFoundException;
import danbank.account.exception.InsufficientFundsException;
import danbank.account.model.Account;
import danbank.account.model.AccountStatus;
import danbank.account.model.AccountTransaction;
import danbank.account.model.Currency;
import danbank.account.model.TransactionType;
import danbank.account.repository.AccountRepo;
import danbank.account.repository.AccountTransactionRepository;
import danbank.common.audit.Audit;
import danbank.common.dto.ApiResponse;
import danbank.common.exception.BadRequestException;
import danbank.common.exception.ConflictException;
import danbank.notification.service.NotificationService;
import danbank.user.exception.UserNotFoundException;
import danbank.user.model.User;
import danbank.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountServ {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int ACCOUNT_NUMBER_LENGTH = 10;
    private static final int MAX_ACCOUNT_NUMBER_ATTEMPTS = 10;

    private final AccountRepo accountRepository;
    private final AccountTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SecureRandom random = new SecureRandom();
    private final BigDecimal dailyDebitLimit;

    public AccountServ(
            AccountRepo accountRepository,
            AccountTransactionRepository transactionRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            @Value("${bank.limits.daily-debit-limit}") BigDecimal dailyDebitLimit
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.dailyDebitLimit = dailyDebitLimit;
    }

    @Transactional
    public ApiResponse<AccountDto> createAccount(long userId, Currency currency) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        Account account = Account.builder()
                .user(user)
                .accountName((user.getFirstName() + " " + user.getLastName()).strip())
                .accountNumber(generateUniqueAccountNumber())
                .currency(currency)
                .build();

        Account savedAccount = accountRepository.save(account);
        Audit.log("ACCOUNT_CREATED", "accountId={} userId={}", savedAccount.getId(), userId);
        return ApiResponse.ok("Account created", toDto(savedAccount));
    }

    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<AccountDto>> getAllUserAccount(Pageable pageable) {
        Page<AccountDto> page = accountRepository.findAll(pageable).map(this::toDto);
        return ApiResponse.ok("Accounts found", new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()
        ));
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
    public ApiResponse<PageResponse<TransactionDto>> getTransactionHistory(
            long accountId, int page, int size, TransactionType type, Instant from, Instant to
    ) {
        findAccount(accountId);

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<AccountTransaction> transactions = transactionRepository.findAll(
                TransactionSpecs.matching(accountId, type, from, to), pageable
        );

        PageResponse<TransactionDto> pageResponse = new PageResponse<>(
                transactions.map(this::toDto).getContent(),
                transactions.getNumber(),
                transactions.getSize(),
                transactions.getTotalElements(),
                transactions.getTotalPages()
        );
        return ApiResponse.ok("Transactions found", pageResponse);
    }

    /** Every matching transaction, unpaged and oldest-first, for a downloadable statement. */
    @Transactional(readOnly = true)
    public List<TransactionDto> getTransactionsForStatement(long accountId, TransactionType type, Instant from, Instant to) {
        findAccount(accountId);
        Sort oldestFirst = Sort.by(Sort.Direction.ASC, "createdAt");
        return transactionRepository.findAll(TransactionSpecs.matching(accountId, type, from, to), oldestFirst)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ApiResponse<TransactionDto> credit(long accountId, MoneyRequest request) {
        Optional<TransactionDto> existing = findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return ApiResponse.ok("Account credited", existing.get());
        }

        Account account = findActiveAccountWithLock(accountId);
        BigDecimal amount = normalizeMoney(request.amount());
        account.credit(amount);

        TransactionDto transaction = recordTransaction(
                account, TransactionType.CREDIT, amount, request.description(), request.idempotencyKey(), null
        );
        Audit.log("ACCOUNT_CREDIT", "accountId={} amount={}", accountId, amount);
        notifyOwner(account, "Your account %s was credited %s %s".formatted(account.getAccountNumber(), account.getCurrency(), amount));
        return ApiResponse.ok("Account credited", transaction);
    }

    @Transactional
    public ApiResponse<TransactionDto> debit(long accountId, MoneyRequest request) {
        Optional<TransactionDto> existing = findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return ApiResponse.ok("Account debited", existing.get());
        }

        Account account = findActiveAccountWithLock(accountId);
        BigDecimal amount = normalizeMoney(request.amount());
        ensureSufficientFunds(account, amount);
        ensureWithinDailyLimit(account, amount);
        account.debit(amount);

        TransactionDto transaction = recordTransaction(
                account, TransactionType.DEBIT, amount, request.description(), request.idempotencyKey(), null
        );
        Audit.log("ACCOUNT_DEBIT", "accountId={} amount={}", accountId, amount);
        notifyOwner(account, "Your account %s was debited %s %s".formatted(account.getAccountNumber(), account.getCurrency(), amount));
        return ApiResponse.ok("Account debited", transaction);
    }

    @Transactional
    public ApiResponse<List<TransactionDto>> transfer(TransferRequest request) {
        Optional<List<TransactionDto>> existing = findByTransferGroup(request.idempotencyKey());
        if (existing.isPresent()) {
            return ApiResponse.ok("Transfer completed", existing.get());
        }

        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BadRequestException("Source and destination accounts must differ");
        }

        // Always take locks in account-id order. This prevents A->B and B->A deadlocks.
        long lowerId = Math.min(request.fromAccountId(), request.toAccountId());
        long higherId = Math.max(request.fromAccountId(), request.toAccountId());
        Account lowerAccount = findActiveAccountWithLock(lowerId);
        Account higherAccount = findActiveAccountWithLock(higherId);

        Account sourceAccount = request.fromAccountId() == lowerId ? lowerAccount : higherAccount;
        Account destinationAccount = request.toAccountId() == lowerId ? lowerAccount : higherAccount;

        if (sourceAccount.getCurrency() != destinationAccount.getCurrency()) {
            throw new BadRequestException(
                    "Cannot transfer between accounts in different currencies (this bank does not convert currencies)"
            );
        }

        BigDecimal amount = normalizeMoney(request.amount());

        ensureSufficientFunds(sourceAccount, amount);
        ensureWithinDailyLimit(sourceAccount, amount);
        sourceAccount.debit(amount);
        destinationAccount.credit(amount);

        String transferGroup = request.idempotencyKey() != null ? request.idempotencyKey() : UUID.randomUUID().toString();
        List<TransactionDto> transactionEntries = List.of(
                recordTransaction(sourceAccount, TransactionType.TRANSFER_OUT, amount, request.description(), null, transferGroup),
                recordTransaction(destinationAccount, TransactionType.TRANSFER_IN, amount, request.description(), null, transferGroup)
        );

        Audit.log("TRANSFER", "from={} to={} amount={}", sourceAccount.getId(), destinationAccount.getId(), amount);
        notifyOwner(sourceAccount, "%s %s was transferred out of your account %s".formatted(amount, sourceAccount.getCurrency(), sourceAccount.getAccountNumber()));
        notifyOwner(destinationAccount, "%s %s was transferred into your account %s".formatted(amount, destinationAccount.getCurrency(), destinationAccount.getAccountNumber()));
        return ApiResponse.ok("Transfer completed", transactionEntries);
    }

    @Transactional
    public ApiResponse<AccountDto> deActivateAccount(long userId, long accountId) {
        Account account = findOwnedAccount(userId, accountId);
        account.deactivate();
        Audit.log("ACCOUNT_DEACTIVATED", "accountId={} userId={}", accountId, userId);
        return ApiResponse.ok("Account deactivated", toDto(account));
    }

    @Transactional
    public ApiResponse<AccountDto> closeAccount(long userId, long accountId) {
        Account account = findOwnedAccount(userId, accountId);
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new ConflictException("Account has already been closed");
        }
        if (account.getBalance().signum() != 0) {
            throw new ConflictException("Only zero-balance accounts can be closed");
        }
        account.close();
        Audit.log("ACCOUNT_CLOSED", "accountId={} userId={}", accountId, userId);
        return ApiResponse.ok("Account closed", toDto(account));
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
            throw new AccountNotActiveException();
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
            throw new InsufficientFundsException();
        }
    }

    private void ensureWithinDailyLimit(Account account, BigDecimal amount) {
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        BigDecimal alreadySpentToday = transactionRepository.sumOutgoingSince(account.getId(), startOfDay);
        if (alreadySpentToday.add(amount).compareTo(dailyDebitLimit) > 0) {
            throw new ConflictException("This transaction would exceed the daily debit limit of " + dailyDebitLimit);
        }
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private void notifyOwner(Account account, String message) {
        notificationService.notify(account.getUser().getId(), "TRANSACTION", message);
    }

    private Optional<TransactionDto> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return transactionRepository.findByReference(idempotencyKey).map(this::toDto);
    }

    private Optional<List<TransactionDto>> findByTransferGroup(String transferGroup) {
        if (transferGroup == null) {
            return Optional.empty();
        }
        List<AccountTransaction> existing = transactionRepository.findByTransferGroupOrderByIdAsc(transferGroup);
        return existing.isEmpty() ? Optional.empty() : Optional.of(existing.stream().map(this::toDto).toList());
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

    private TransactionDto recordTransaction(
            Account account, TransactionType type, BigDecimal amount, String description,
            String clientReference, String transferGroup
    ) {
        String reference = clientReference != null ? clientReference : UUID.randomUUID().toString();
        AccountTransaction savedTransaction = transactionRepository.save(
                new AccountTransaction(account, type, amount, reference, transferGroup, description)
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
