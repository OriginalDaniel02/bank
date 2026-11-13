package dan.bank.bank.savings;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {

    List<SavingsGoal> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, SavingsStatus status);

    long countByUserIdAndStatus(Long userId, SavingsStatus status);

    /** Row lock, so two requests cannot both spend the same saved money. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SavingsGoal> findWithLockByIdAndUserId(Long id, Long userId);
}
