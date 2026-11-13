package dan.bank.bank.account.repository;
import dan.bank.bank.account.model.AccountTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import dan.bank.bank.account.model.TransactionType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {
    java.util.List<AccountTransaction> findByReferenceStartingWith(String prefix);

    @Query("select coalesce(sum(t.amount), 0) from AccountTransaction t where t.account.id = :accountId and t.type = :type and t.createdAt >= :since")
    java.math.BigDecimal sumSince(@Param("accountId") Long accountId, @Param("type") TransactionType type, @Param("since") java.time.Instant since);

    @Query("select t from AccountTransaction t where t.account.id = :accountId and t.createdAt >= :from and t.createdAt < :to order by t.createdAt asc, t.id asc")
    java.util.List<AccountTransaction> findInRange(@Param("accountId") Long accountId, @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query("select t.type, sum(t.amount) from AccountTransaction t where t.account.id = :accountId and t.createdAt >= :from and t.createdAt < :to group by t.type")
    java.util.List<Object[]> sumByTypeBetween(@Param("accountId") Long accountId, @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query("select t.type, sum(t.amount) from AccountTransaction t where t.account.id = :accountId and t.createdAt >= :since group by t.type")
    java.util.List<Object[]> sumByTypeSince(@Param("accountId") Long accountId, @Param("since") java.time.Instant since);

    Page<AccountTransaction> findByAccount_IdOrderByCreatedAtDesc(Long accountId, Pageable pageable);
}
