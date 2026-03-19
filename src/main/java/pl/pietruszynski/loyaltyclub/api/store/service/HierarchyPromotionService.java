package pl.pietruszynski.loyaltyclub.api.store.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.pietruszynski.loyaltyclub.api.store.dto.HierarchyRequest;
import pl.pietruszynski.loyaltyclub.api.store.model.HierarchyPromotion;
import pl.pietruszynski.loyaltyclub.api.store.model.HierarchyPromotionType;
import pl.pietruszynski.loyaltyclub.api.store.repository.HierarchyPromotionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HierarchyPromotionService {

    private final HierarchyPromotionRepository hierarchyPromotionRepository;

    public List<HierarchyPromotion> getActivePromotions(String country, LocalDateTime moment) {
        String normalizedCountry = normalizeCountry(country);
        return hierarchyPromotionRepository.findActivePromotions(normalizedCountry, moment);
    }

    public boolean isItemExcluded(HierarchyRequest item, List<HierarchyPromotion> promotions) {
        return promotions.stream()
                .filter(p -> p.getType() == HierarchyPromotionType.EXCLUSION)
                .anyMatch(p -> matchesHierarchy(p, item));
    }

    public BigDecimal resolveItemMultiplier(HierarchyRequest item, List<HierarchyPromotion> promotions) {
        return promotions.stream()
                .filter(p -> p.getType() == HierarchyPromotionType.MULTIPLIER)
                .filter(p -> matchesHierarchy(p, item))
                .map(HierarchyPromotion::getMultiplier)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ONE);
    }

    boolean matchesHierarchy(HierarchyPromotion promotion, HierarchyRequest item) {
        if (!isBlank(promotion.getHierarchy()) && !promotion.getHierarchy().equalsIgnoreCase(item.hierarchy())) {
            return false;
        }
        if (!isBlank(promotion.getProductClass()) && !promotion.getProductClass().equalsIgnoreCase(item.productClass())) {
            return false;
        }
        if (!isBlank(promotion.getSubclass()) && !promotion.getSubclass().equalsIgnoreCase(item.subclass())) {
            return false;
        }
        return true;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeCountry(String country) {
        if (country == null || country.isBlank()) {
            return "";
        }
        return country.trim().toUpperCase(Locale.ROOT);
    }
}
