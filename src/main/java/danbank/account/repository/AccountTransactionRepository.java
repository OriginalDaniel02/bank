package danbank.account.repository;

import danbank.account.model.AccountTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AccountTransactionRepository
        extends JpaRepository<AccountTransaction, Long>, JpaSpecificationExecutor<AccountTransaction> {

    Optional<AccountTransaction> findByReference(String reference);

    boolean existsByReference(String reference);

    List<AccountTransaction> findByTransferGroupOrderByIdAsc(String transferGroup);

    @Query("""
            select coalesce(sum(t.amount), 0)
            from AccountTransaction t
            where t.account.id = :accountId
              and t.type in (danbank.account.model.TransactionType.DEBIT, danbank.account.model.TransactionType.TRANSFER_OUT)
              and t.createdAt >= :since
            """)
    BigDecimal sumOutgoingSince(@Param("accountId") Long accountId, @Param("since") Instant since);
}
