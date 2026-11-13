package dan.bank.bank.Account.repository;

import dan.bank.bank.Account.model.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {
    Optional<IdempotencyKey> findByUserIdAndKeyValue(Long userId, String keyValue);
}
