package danbank;

import danbank.account.dtos.AccountResponse;
import danbank.account.dtos.PageResponse;
import danbank.account.dtos.TransactionDto;
import danbank.account.model.Account;
import danbank.account.model.AccountTransaction;
import danbank.account.model.TransactionType;
import danbank.account.repository.AccountRepo;
import danbank.account.repository.AccountTransactionRepository;
import danbank.account.service.AccountServ;
import danbank.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountTransactionHistoryTest {

    @Mock
    private AccountRepo accountRepository;

    @Mock
    private AccountTransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    private AccountServ accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountServ(accountRepository, transactionRepository, userRepository);
    }

    @Test
    void returnsPagedTransactionsForExistingAccount() {
        Account account = new Account();
        account.setId(1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        AccountTransaction transaction = new AccountTransaction();
        transaction.setAccount(account);
        transaction.setType(TransactionType.CREDIT);
        transaction.setAmount(new BigDecimal("50.00"));
        transaction.setReference("ref-1");
        transaction.setDescription("Initial deposit");
        transaction.setCreatedAt(Instant.now());

        Page<AccountTransaction> page = new PageImpl<>(List.of(transaction));
        when(transactionRepository.findByAccount_IdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(page);

        AccountResponse<PageResponse<TransactionDto>> response = accountService.getTransactionHistory(1L, 0, 20);

        assertEquals("Transactions found", response.message());
        assertEquals(1, response.data().content().size());
        assertEquals("ref-1", response.data().content().get(0).reference());
        assertEquals(1, response.data().totalElements());
        assertEquals(1, response.data().totalPages());
        assertEquals(0, response.data().page());
    }

    @Test
    void throwsNotFoundWhenAccountDoesNotExist() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> accountService.getTransactionHistory(99, 0, 20));
        verify(transactionRepository, never()).findByAccount_IdOrderByCreatedAtDesc(anyLong(), any());
    }

    @Test
    void clampsPageAndSizeToSafeBounds() {
        Account account = new Account();
        account.setId(1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.findByAccount_IdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        accountService.getTransactionHistory(1L, -5, 5000);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findByAccount_IdOrderByCreatedAtDesc(eq(1L), pageableCaptor.capture());

        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(100, pageableCaptor.getValue().getPageSize());
    }
}
