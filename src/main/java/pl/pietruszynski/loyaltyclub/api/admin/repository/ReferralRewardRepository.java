package pl.pietruszynski.loyaltyclub.api.admin.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.pietruszynski.loyaltyclub.api.admin.model.ReferralReward;

import java.util.List;

public interface ReferralRewardRepository extends JpaRepository<ReferralReward, Long> {

    boolean existsByReferredId(Long referredCustomerId);

    long countByReferrerId(Long referrerCustomerId);

    @EntityGraph(attributePaths = {"referrer", "referred"})
    List<ReferralReward> findAllByReferrerIdOrderByAwardedAtDesc(Long referrerCustomerId);
}
