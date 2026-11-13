package dan.bank.bank.device;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface DeviceChallengeRepository extends JpaRepository<DeviceChallenge, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeviceChallenge> findWithLockByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeviceChallenge> findWithLockById(Long id);
}
