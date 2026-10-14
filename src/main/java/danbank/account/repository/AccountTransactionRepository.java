package danbank.account.repository;
import danbank.account.model.AccountTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {
    Page<AccountTransaction> findByAccount_IdOrderByCreatedAtDesc(Long accountId, Pageable pageable);
}
