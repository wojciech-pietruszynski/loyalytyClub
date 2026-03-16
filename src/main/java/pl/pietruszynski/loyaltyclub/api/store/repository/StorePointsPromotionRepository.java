package pl.pietruszynski.loyaltyclub.api.store.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.pietruszynski.loyaltyclub.api.store.model.StorePointsPromotion;

import java.time.LocalDateTime;
import java.util.List;

public interface StorePointsPromotionRepository extends JpaRepository<StorePointsPromotion, Long> {
    List<StorePointsPromotion> findAllByOrderByStartsAtDesc();
    List<StorePointsPromotion> findAllByCountryOrderByStartsAtDesc(String country);

    @Query("""
            SELECT p
            FROM StorePointsPromotion p
            WHERE p.enabled = true
              AND p.country = :country
              AND p.startsAt <= :moment
              AND (p.endsAt IS NULL OR p.endsAt >= :moment)
            ORDER BY p.pointsPerCurrency DESC, p.startsAt DESC
            """)
    List<StorePointsPromotion> findActivePromotions(String country, LocalDateTime moment);
}
