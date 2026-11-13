package dan.bank.bank;

import dan.bank.bank.account.dto.MoneyRequest;
import dan.bank.bank.account.dto.TransferRequest;
import dan.bank.bank.account.repository.AccountRepository;
import dan.bank.bank.account.repository.AccountTransactionRepository;
import dan.bank.bank.account.repository.IdempotencyKeyRepository;
import dan.bank.bank.account.service.AccountService;
import dan.bank.bank.account.service.IdempotentTransferService;
import dan.bank.bank.auth.repository.RefreshTokenRepository;
import dan.bank.bank.notification.NotificationService;
import dan.bank.bank.user.model.User;
import dan.bank.bank.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

/**
 * A broken notification system must never fail a transfer that already happened, and must never
 * make an idempotent retry move the money a second time.
 */
@SpringBootTest
class NotificationFailureTests {

    @MockitoBean NotificationService notifications;

    @Autowired AccountRepository accountRepository;
    @Autowired AccountTransactionRepository transactionRepository;
    @Autowired IdempotencyKeyRepository idempotencyKeyRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired UserRepository userRepository;
    @Autowired AccountService accountService;
    @Autowired IdempotentTransferService transferService;

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

    @Test
    void retryAfterNotificationFailureDoesNotMoveMoneyTwice() {
        doThrow(new IllegalStateException("notification store down"))
                .when(notifications).notify(anyLong(), any(), any(), any());
        User alice = newUser();
        long from = accountService.createAccount(alice.getId()).data().id();
        long to = accountService.createAccount(newUser().getId()).data().id();
        accountService.credit(from, new MoneyRequest(new BigDecimal("100.00"), "seed"));
        TransferRequest request = new TransferRequest(from, to, new BigDecimal("30.00"), "rent");

        var first = transferService.transfer(alice.getId(), "key-nf", request);   // must not throw
        var second = transferService.transfer(alice.getId(), "key-nf", request);  // replay

        assertEquals(first.data().get(0).reference(), second.data().get(0).reference());
        assertEquals(new BigDecimal("70.00"), accountRepository.findById(from).orElseThrow().getBalance());
        assertEquals(new BigDecimal("30.00"), accountRepository.findById(to).orElseThrow().getBalance());
    }
}
