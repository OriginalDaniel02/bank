package dan.bank.bank.deposit;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.Optional;

public interface DepositRepository extends JpaRepository<Deposit, Long> {

    Optional<Deposit> findByReference(String reference);

    Optional<Deposit> findByReferenceAndUserId(String reference, Long userId);

    /** Row lock, so a webhook and a user-triggered check can never both credit the same deposit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Deposit> findWithLockByReference(String reference);

    long countByUserIdAndStatusAndCreatedAtAfter(Long userId, DepositStatus status, Instant after);
}
