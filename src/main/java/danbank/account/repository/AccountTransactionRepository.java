package danbank.account.repository;

import danbank.account.model.AccountTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {

    Page<AccountTransaction> findByAccount_IdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    @Query("""
            select coalesce(sum(t.amount), 0)
            from AccountTransaction t
            where t.account.id = :accountId
              and t.type in (danbank.account.model.TransactionType.DEBIT, danbank.account.model.TransactionType.TRANSFER_OUT)
              and t.createdAt >= :since
            """)
    BigDecimal sumOutgoingSince(@Param("accountId") Long accountId, @Param("since") Instant since);
}
