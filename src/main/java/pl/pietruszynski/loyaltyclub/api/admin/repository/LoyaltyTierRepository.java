package pl.pietruszynski.loyaltyclub.api.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.pietruszynski.loyaltyclub.api.admin.model.LoyaltyTier;

import java.util.List;

public interface LoyaltyTierRepository extends JpaRepository<LoyaltyTier, Long> {
    List<LoyaltyTier> findAllByOrderByMinPointsAsc();
}
