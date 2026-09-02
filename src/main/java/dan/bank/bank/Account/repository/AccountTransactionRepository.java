package dan.bank.bank.Account.repository;
import dan.bank.bank.Account.model.AccountTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {
    Page<AccountTransaction> findByAccount_IdOrderByCreatedAtDesc(Long accountId, Pageable pageable);
}
