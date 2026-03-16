package pl.pietruszynski.loyaltyclub.api.admin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponTemplate;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CouponTemplateRepository;

import java.math.BigDecimal;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class CouponTemplateSeeder implements CommandLineRunner {

    private final CouponTemplateRepository couponTemplateRepository;

    @Override
    public void run(String... args) {
        seedTemplate(new BigDecimal("10"), new BigDecimal("11"), 300, "PL", 7, "KUPPL");
        seedTemplate(new BigDecimal("50"), new BigDecimal("100"), 1500, "PL", 7, "KUPPL");
    }

    private void seedTemplate(
            BigDecimal couponValue,
            BigDecimal minimumPurchaseValue,
            Integer requiredPoints,
            String country,
            Integer validityDays,
            String couponPrefix
    ) {
        String normalizedCountry = country.trim().toUpperCase(Locale.ROOT);
        String normalizedPrefix = couponPrefix.trim().toUpperCase(Locale.ROOT);

        boolean exists = couponTemplateRepository
                .existsByCouponValueAndMinimumPurchaseValueAndRequiredPointsAndCountryAndValidityDaysAndCouponPrefix(
                        couponValue,
                        minimumPurchaseValue,
                        requiredPoints,
                        normalizedCountry,
                        validityDays,
                        normalizedPrefix
                );
        if (!exists) {
            couponTemplateRepository.save(CouponTemplate.builder()
                    .couponValue(couponValue)
                    .minimumPurchaseValue(minimumPurchaseValue)
                    .requiredPoints(requiredPoints)
                    .country(normalizedCountry)
                    .validityDays(validityDays)
                    .couponPrefix(normalizedPrefix)
                    .build());
        }
    }
}
