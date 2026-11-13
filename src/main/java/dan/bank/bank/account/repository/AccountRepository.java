package dan.bank.bank.account.repository;
import dan.bank.bank.account.model.Account;
import dan.bank.bank.account.model.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByIdAndUser_Id(Long accountId, Long userId);

    List<Account> findByUser_Id(Long userId);

    List<Account> findByUser_IdAndStatusNot(Long userId, AccountStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findWithLockById(Long accountId);

    Optional<Account> findByAccountNumber(String accountNumber);

    @Query("select a.id as id, a.user.id as userId, a.accountName as accountName, a.accountNumber as accountNumber "
            + "from Account a where a.id = :id")
    Optional<AccountParty> findPartyById(@Param("id") Long id);

    boolean existsByAccountNumber(String accountNumber);
}
