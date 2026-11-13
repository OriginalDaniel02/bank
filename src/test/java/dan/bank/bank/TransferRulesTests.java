package dan.bank.bank;

import dan.bank.bank.Account.dtos.MoneyRequest;
import dan.bank.bank.Account.dtos.TransferRequest;
import dan.bank.bank.Account.repository.AccountRepo;
import dan.bank.bank.Account.repository.AccountTransactionRepository;
import dan.bank.bank.Account.service.AccountServ;
import dan.bank.bank.Account.service.IdempotentTransferService;
import dan.bank.bank.Account.service.TransferPolicy;
import dan.bank.bank.Auth.repository.RefreshTokenRepository;
import dan.bank.bank.User.model.User;
import dan.bank.bank.User.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TransferRulesTests {

    @Autowired AccountRepo accountRepository;
    @Autowired AccountTransactionRepository transactionRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired UserRepository userRepository;
    @Autowired AccountServ defaultService;
    @Autowired IdempotentTransferService idempotentService;
    @Autowired dan.bank.bank.Account.repository.IdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    /** Services built with `new` are not Spring proxies, so give them a transaction like the real ones have. */
    private <T> T tx(java.util.function.Supplier<T> work) {
        return new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    @BeforeEach
    void clean() {
        idempotencyKeyRepository.deleteAll();
        transactionRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User newUser() {
        String id = UUID.randomUUID().toString();
        return userRepository.save(User.builder().firstName("T").lastName("U")
                .email(id + "@example.com").phoneNumber("0" + Math.abs(id.hashCode())).password("x").build());
    }

    private long account(User user) { return defaultService.createAccount(user.getId()).data().id(); }

    private AccountServ serviceWith(TransferPolicy policy) {
        return new AccountServ(accountRepository, transactionRepository, userRepository, policy);
    }

    private static TransferPolicy policy(String perTransfer, String daily, String incomeAccount) {
        return new TransferPolicy(new BigDecimal(perTransfer), new BigDecimal(daily), "UTC", incomeAccount,
                new BigDecimal("100.00"), new BigDecimal("10.00"), new BigDecimal("1000.00"),
                new BigDecimal("25.00"), new BigDecimal("50.00"));
    }

    @Test
    void sameIdempotencyKeyMovesMoneyOnlyOnce() {
        User alice = newUser();
        long from = account(alice), to = account(newUser());
        defaultService.credit(from, new MoneyRequest(new BigDecimal("100.00"), "seed"));
        TransferRequest request = new TransferRequest(from, to, new BigDecimal("30.00"), "rent");

        var first = idempotentService.transfer(alice.getId(), "key-1", request);
        var second = idempotentService.transfer(alice.getId(), "key-1", request);

        assertEquals(new BigDecimal("70.00"), accountRepository.findById(from).orElseThrow().getBalance());
        assertEquals(new BigDecimal("30.00"), accountRepository.findById(to).orElseThrow().getBalance());
        assertEquals(first.data().get(0).reference(), second.data().get(0).reference());
        assertEquals(2, second.data().size());
    }

    @Test
    void reusingKeyWithDifferentBodyIsRejected() {
        User alice = newUser();
        long from = account(alice), to = account(newUser());
        defaultService.credit(from, new MoneyRequest(new BigDecimal("100.00"), "seed"));
        idempotentService.transfer(alice.getId(), "key-2", new TransferRequest(from, to, new BigDecimal("10.00"), null));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () ->
                idempotentService.transfer(alice.getId(), "key-2", new TransferRequest(from, to, new BigDecimal("20.00"), null)));
        assertEquals(422, e.getStatusCode().value());
        assertEquals(new BigDecimal("90.00"), accountRepository.findById(from).orElseThrow().getBalance());
    }

    @Test
    void failedTransferReleasesKeySoItCanBeRetried() {
        User alice = newUser();
        long from = account(alice), to = account(newUser());
        TransferRequest request = new TransferRequest(from, to, new BigDecimal("10.00"), null);
        assertThrows(ResponseStatusException.class, () -> idempotentService.transfer(alice.getId(), "key-3", request)); // no funds
        defaultService.credit(from, new MoneyRequest(new BigDecimal("10.00"), "seed"));
        idempotentService.transfer(alice.getId(), "key-3", request);
        assertEquals(new BigDecimal("0.00"), accountRepository.findById(from).orElseThrow().getBalance());
    }

    @Test
    void perTransferAndDailyLimitsAreEnforced() {
        AccountServ limited = serviceWith(policy("50.00", "80.00", ""));
        long from = account(newUser()), to = account(newUser());
        tx(() -> limited.credit(from, new MoneyRequest(new BigDecimal("500.00"), "seed")));

        assertEquals(422, assertThrows(ResponseStatusException.class, () ->
                tx(() -> limited.transfer(new TransferRequest(from, to, new BigDecimal("60.00"), null)))).getStatusCode().value());
        tx(() -> limited.transfer(new TransferRequest(from, to, new BigDecimal("50.00"), null)));
        assertEquals(422, assertThrows(ResponseStatusException.class, () ->
                tx(() -> limited.transfer(new TransferRequest(from, to, new BigDecimal("40.00"), null)))).getStatusCode().value());
        tx(() -> limited.transfer(new TransferRequest(from, to, new BigDecimal("30.00"), null))); // exactly hits 80
    }

    @Test
    void feeIsChargedToSenderAndCreditedToIncomeAccountPreservingTotalMoney() {
        long income = account(newUser());
        String incomeNumber = accountRepository.findById(income).orElseThrow().getAccountNumber();
        AccountServ withFees = serviceWith(policy("100000.00", "100000.00", incomeNumber));
        long from = account(newUser()), to = account(newUser());
        tx(() -> withFees.credit(from, new MoneyRequest(new BigDecimal("500.00"), "seed")));

        tx(() -> withFees.transfer(new TransferRequest(from, to, new BigDecimal("200.00"), null))); // tier 2 fee 25.00

        assertEquals(new BigDecimal("275.00"), accountRepository.findById(from).orElseThrow().getBalance());
        assertEquals(new BigDecimal("200.00"), accountRepository.findById(to).orElseThrow().getBalance());
        assertEquals(new BigDecimal("25.00"), accountRepository.findById(income).orElseThrow().getBalance());
    }

    @Test
    void transferFailsWhenBalanceCannotCoverAmountPlusFee() {
        long income = account(newUser());
        String incomeNumber = accountRepository.findById(income).orElseThrow().getAccountNumber();
        AccountServ withFees = serviceWith(policy("100000.00", "100000.00", incomeNumber));
        long from = account(newUser()), to = account(newUser());
        tx(() -> withFees.credit(from, new MoneyRequest(new BigDecimal("50.00"), "seed")));

        assertEquals(409, assertThrows(ResponseStatusException.class, () ->
                tx(() -> withFees.transfer(new TransferRequest(from, to, new BigDecimal("45.00"), null)))).getStatusCode().value());
        assertEquals(new BigDecimal("50.00"), accountRepository.findById(from).orElseThrow().getBalance());
    }
}
