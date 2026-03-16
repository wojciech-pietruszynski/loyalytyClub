package pl.pietruszynski.loyaltyclub.api.coupon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.pietruszynski.loyaltyclub.api.coupon.model.CouponRedemptionRequest;

import java.util.Optional;

public interface CouponRedemptionRequestRepository extends JpaRepository<CouponRedemptionRequest, Long> {
    Optional<CouponRedemptionRequest> findByIdempotencyKey(String idempotencyKey);
}
