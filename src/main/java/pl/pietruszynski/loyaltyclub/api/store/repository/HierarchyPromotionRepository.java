package pl.pietruszynski.loyaltyclub.api.store.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.pietruszynski.loyaltyclub.api.store.model.HierarchyPromotion;

import java.time.LocalDateTime;
import java.util.List;

public interface HierarchyPromotionRepository extends JpaRepository<HierarchyPromotion, Long> {

    List<HierarchyPromotion> findAllByOrderByStartsAtDesc();

    List<HierarchyPromotion> findAllByCountryOrderByStartsAtDesc(String country);

    @Query("""
            SELECT p
            FROM HierarchyPromotion p
            WHERE p.enabled = true
              AND p.country = :country
              AND p.startsAt <= :moment
              AND (p.endsAt IS NULL OR p.endsAt >= :moment)
            ORDER BY p.startsAt DESC
            """)
    List<HierarchyPromotion> findActivePromotions(String country, LocalDateTime moment);
}
