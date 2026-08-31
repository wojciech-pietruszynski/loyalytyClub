package pl.pietruszynski.loyaltyclub.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Long> {

    boolean existsByTokenId(String tokenId);

    @Modifying
    @Query("DELETE FROM RevokedToken t WHERE t.expiresAt < :now")
    int deleteExpired(LocalDateTime now);
}
