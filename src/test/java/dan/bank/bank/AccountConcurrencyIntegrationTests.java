package dan.bank.bank;

import dan.bank.bank.Account.dtos.AccountDto;
import dan.bank.bank.Account.dtos.MoneyRequest;
import dan.bank.bank.Account.dtos.TransferRequest;
import dan.bank.bank.Account.repository.AccountRepo;
import dan.bank.bank.Account.repository.AccountTransactionRepository;
import dan.bank.bank.Account.service.AccountServ;
import dan.bank.bank.Auth.repository.RefreshTokenRepository;
import dan.bank.bank.User.model.User;
import dan.bank.bank.User.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AccountConcurrencyIntegrationTests {

    private static final int NUMBER_OF_REQUESTS = 20;
    private static final BigDecimal REQUEST_AMOUNT = new BigDecimal("5.00");

    @Autowired
    private AccountServ accountService;

    @Autowired
    private AccountRepo accountRepository;

    @Autowired
    private AccountTransactionRepository transactionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void clearDatabase() {
        // Delete child records first so foreign-key constraints are respected.
        transactionRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void concurrentDebitsDoNotLoseBalanceUpdates() throws Exception {
        long accountId = createAccountForNewUser();
        accountService.credit(accountId, new MoneyRequest(new BigDecimal("100.00"), "Initial balance"));

        runConcurrently(NUMBER_OF_REQUESTS, () ->
                accountService.debit(accountId, new MoneyRequest(REQUEST_AMOUNT, "Concurrent debit"))
        );

        BigDecimal finalBalance = accountRepository.findById(accountId).orElseThrow().getBalance();

        assertEquals(new BigDecimal("0.00"), finalBalance);
        // One initial credit plus one ledger entry for every successful debit.
        assertEquals(NUMBER_OF_REQUESTS + 1, transactionRepository.count());
    }

    @Test
    void concurrentTransfersPreserveTotalMoneyAndCreateBothLedgerEntries() throws Exception {
        long sourceAccountId = createAccountForNewUser();
        long destinationAccountId = createAccountForNewUser();
        accountService.credit(sourceAccountId, new MoneyRequest(new BigDecimal("100.00"), "Initial balance"));

        runConcurrently(NUMBER_OF_REQUESTS, () ->
                accountService.transfer(new TransferRequest(
                        sourceAccountId,
                        destinationAccountId,
                        REQUEST_AMOUNT,
                        "Concurrent transfer"
                ))
        );

        BigDecimal sourceBalance = accountRepository.findById(sourceAccountId).orElseThrow().getBalance();
        BigDecimal destinationBalance = accountRepository.findById(destinationAccountId).orElseThrow().getBalance();

        assertEquals(new BigDecimal("0.00"), sourceBalance);
        assertEquals(new BigDecimal("100.00"), destinationBalance);
        assertEquals(new BigDecimal("100.00"), sourceBalance.add(destinationBalance));
        // One initial credit plus two entries (out and in) for every transfer.
        assertEquals((NUMBER_OF_REQUESTS * 2L) + 1, transactionRepository.count());
    }

    private long createAccountForNewUser() {
        String uniqueValue = UUID.randomUUID().toString();
        User user = User.builder()
                .firstName("Test")
                .lastName("User")
                .email(uniqueValue + "@example.com")
                .phoneNumber("080" + Math.abs(uniqueValue.hashCode()))
                .address("Test address")
                .dateOfBirth(LocalDate.of(2000, 1, 1))
                .password("not-used-by-this-test")
                .build();

        User savedUser = userRepository.save(user);
        AccountDto account = accountService.createAccount(savedUser.getId()).data();
        return account.id();
    }

    private void runConcurrently(int numberOfRequests, ThrowingRunnable work) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(numberOfRequests);
        CountDownLatch ready = new CountDownLatch(numberOfRequests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();

        try {
            for (int request = 0; request < numberOfRequests; request++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS), "Timed out waiting for concurrent start");
                    work.run();
                    return null;
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Workers were not ready in time");
            start.countDown();

            for (Future<?> result : results) {
                result.get(20, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Executor did not stop in time");
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
