package dan.bank.bank.kyc;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KycVerificationRepository extends JpaRepository<KycVerification, Long> {
    Optional<KycVerification> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
