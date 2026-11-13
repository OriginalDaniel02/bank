package dan.bank.bank.device;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrustedDeviceRepository extends JpaRepository<TrustedDevice, Long> {
    Optional<TrustedDevice> findByTokenHashAndUserIdAndRevokedFalse(String tokenHash, Long userId);
    List<TrustedDevice> findByUserIdAndRevokedFalseOrderByLastUsedAtDesc(Long userId);
    long countByUserIdAndRevokedFalse(Long userId);
    Optional<TrustedDevice> findByIdAndUserId(Long id, Long userId);
}
