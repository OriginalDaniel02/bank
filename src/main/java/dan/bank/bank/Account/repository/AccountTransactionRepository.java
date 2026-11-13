package dan.bank.bank.Account.repository;
import dan.bank.bank.Account.model.AccountTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {
    java.util.List<AccountTransaction> findByReferenceStartingWith(String prefix);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(t.amount), 0) from AccountTransaction t where t.account.id = :accountId and t.type = :type and t.createdAt >= :since")
    java.math.BigDecimal sumSince(@org.springframework.data.repository.query.Param("accountId") Long accountId, @org.springframework.data.repository.query.Param("type") dan.bank.bank.Account.model.TransactionType type, @org.springframework.data.repository.query.Param("since") java.time.Instant since);

    Page<AccountTransaction> findByAccount_IdOrderByCreatedAtDesc(Long accountId, Pageable pageable);
}
