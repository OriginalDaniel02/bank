package danbank.account.repository;

import danbank.account.model.Account;
import danbank.account.model.AccountStatus;
import danbank.account.model.AccountType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface AccountRepo extends JpaRepository<Account, Long> {

    Optional<Account> findByIdAndUser_Id(Long accountId, Long userId);

    List<Account> findByUser_Id(Long userId);

    boolean existsByAccountNumber(String accountNumber);

    List<Account> findByTypeAndStatus(AccountType type, AccountStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findWithLockById(Long accountId);
}
