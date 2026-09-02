package dan.bank.bank.Account.repository;
import dan.bank.bank.Account.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;
import java.util.Optional;
public interface AccountRepo extends JpaRepository<Account, Long> {

    Optional<Account> findByIdAndUser_Id(Long accountId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findWithLockById(Long accountId);

    boolean existsByAccountNumber(String accountNumber);
}
