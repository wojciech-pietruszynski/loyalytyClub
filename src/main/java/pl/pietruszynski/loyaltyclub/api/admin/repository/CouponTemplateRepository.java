package pl.pietruszynski.loyaltyclub.api.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponTemplate;

import java.math.BigDecimal;

public interface CouponTemplateRepository extends JpaRepository<CouponTemplate, Long> {
    boolean existsByCouponValueAndMinimumPurchaseValueAndRequiredPointsAndCountryAndValidityDaysAndCouponPrefix(
            BigDecimal couponValue,
            BigDecimal minimumPurchaseValue,
            Integer requiredPoints,
            String country,
            Integer validityDays,
            String couponPrefix
    );
}
