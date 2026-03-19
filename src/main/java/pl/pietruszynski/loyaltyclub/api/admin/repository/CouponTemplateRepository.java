package pl.pietruszynski.loyaltyclub.api.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponTemplate;

import java.math.BigDecimal;
import java.util.List;

public interface CouponTemplateRepository extends JpaRepository<CouponTemplate, Long> {
    List<CouponTemplate> findAllByCountry(String country);
    boolean existsByCouponValueAndMinimumPurchaseValueAndRequiredPointsAndCountryAndValidityDaysAndCouponPrefix(
            BigDecimal couponValue,
            BigDecimal minimumPurchaseValue,
            Integer requiredPoints,
            String country,
            Integer validityDays,
            String couponPrefix
    );
}
