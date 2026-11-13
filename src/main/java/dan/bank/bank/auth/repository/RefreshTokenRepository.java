package dan.bank.bank.auth.repository;

import dan.bank.bank.auth.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update RefreshToken t set t.revoked = true where t.user.id = :userId and t.revoked = false")
    int revokeAllForUser(@org.springframework.data.repository.query.Param("userId") Long userId);

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
