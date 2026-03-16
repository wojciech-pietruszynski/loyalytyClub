package pl.pietruszynski.loyaltyclub.api.store.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pietruszynski.loyaltyclub.api.store.repository.StorePointsPromotionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StorePromotionService {

    private final StorePointsPromotionRepository storePointsPromotionRepository;

    @Value("${app.store.default-points-per-currency:1.00}")
    private BigDecimal defaultPointsPerCurrency;

    public BigDecimal resolvePointsPerCurrency(String country, LocalDateTime purchaseTimestamp) {
        String normalizedCountry = normalizeCountry(country);
        return storePointsPromotionRepository.findActivePromotions(normalizedCountry, purchaseTimestamp).stream()
                .findFirst()
                .map(promotion -> promotion.getPointsPerCurrency())
                .orElse(defaultPointsPerCurrency);
    }

    private String normalizeCountry(String country) {
        if (country == null || country.isBlank()) {
            return "";
        }
        return country.trim().toUpperCase(Locale.ROOT);
    }
}
