package pl.pietruszynski.loyaltyclub.api.store.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.pietruszynski.loyaltyclub.api.store.dto.HierarchyRequest;
import pl.pietruszynski.loyaltyclub.api.store.model.HierarchyPromotion;
import pl.pietruszynski.loyaltyclub.api.store.model.HierarchyPromotionType;
import pl.pietruszynski.loyaltyclub.api.store.repository.HierarchyPromotionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HierarchyPromotionServiceTest {

    @Mock
    private HierarchyPromotionRepository hierarchyPromotionRepository;

    @InjectMocks
    private HierarchyPromotionService hierarchyPromotionService;

    // -----------------------------------------------------------------------
    // getActivePromotions
    // -----------------------------------------------------------------------

    @Test
    void getActivePromotions_shouldDelegateToRepository() {
        HierarchyPromotion promo = multiplierPromotion("42", null, null, new BigDecimal("2.00"));
        when(hierarchyPromotionRepository.findActivePromotions(eq("PL"), any(LocalDateTime.class)))
                .thenReturn(List.of(promo));

        List<HierarchyPromotion> result = hierarchyPromotionService.getActivePromotions("PL", LocalDateTime.now());

        assertThat(result).hasSize(1);
        verify(hierarchyPromotionRepository).findActivePromotions(eq("PL"), any(LocalDateTime.class));
    }

    @Test
    void getActivePromotions_countryNormalized_shouldUppercase() {
        when(hierarchyPromotionRepository.findActivePromotions(eq("PL"), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        hierarchyPromotionService.getActivePromotions("pl", LocalDateTime.now());

        verify(hierarchyPromotionRepository).findActivePromotions(eq("PL"), any(LocalDateTime.class));
    }

    // -----------------------------------------------------------------------
    // isItemExcluded
    // -----------------------------------------------------------------------

    @Test
    void isItemExcluded_matchingExclusion_shouldReturnTrue() {
        HierarchyPromotion exclusion = exclusionPromotion("42", null, null);
        HierarchyRequest item = new HierarchyRequest("42", "SHOES", "SNEAKERS");

        assertThat(hierarchyPromotionService.isItemExcluded(item, List.of(exclusion))).isTrue();
    }

    @Test
    void isItemExcluded_noMatchingExclusion_shouldReturnFalse() {
        HierarchyPromotion exclusion = exclusionPromotion("99", null, null);
        HierarchyRequest item = new HierarchyRequest("42", "SHOES", "SNEAKERS");

        assertThat(hierarchyPromotionService.isItemExcluded(item, List.of(exclusion))).isFalse();
    }

    @Test
    void isItemExcluded_multiplierPromotionNotAnExclusion_shouldReturnFalse() {
        HierarchyPromotion multiplier = multiplierPromotion("42", null, null, new BigDecimal("2.00"));
        HierarchyRequest item = new HierarchyRequest("42", "SHOES", null);

        assertThat(hierarchyPromotionService.isItemExcluded(item, List.of(multiplier))).isFalse();
    }

    @Test
    void isItemExcluded_emptyList_shouldReturnFalse() {
        HierarchyRequest item = new HierarchyRequest("42", null, null);

        assertThat(hierarchyPromotionService.isItemExcluded(item, Collections.emptyList())).isFalse();
    }

    // -----------------------------------------------------------------------
    // resolveItemMultiplier
    // -----------------------------------------------------------------------

    @Test
    void resolveItemMultiplier_matchingMultiplier_shouldReturnMultiplier() {
        HierarchyPromotion promo = multiplierPromotion("42", null, null, new BigDecimal("4.00"));
        HierarchyRequest item = new HierarchyRequest("42", "SHOES", "SNEAKERS");

        BigDecimal result = hierarchyPromotionService.resolveItemMultiplier(item, List.of(promo));

        assertThat(result).isEqualByComparingTo("4.00");
    }

    @Test
    void resolveItemMultiplier_noMatchingMultiplier_shouldReturnOne() {
        HierarchyPromotion promo = multiplierPromotion("99", null, null, new BigDecimal("4.00"));
        HierarchyRequest item = new HierarchyRequest("42", "SHOES", "SNEAKERS");

        BigDecimal result = hierarchyPromotionService.resolveItemMultiplier(item, List.of(promo));

        assertThat(result).isEqualByComparingTo("1");
    }

    @Test
    void resolveItemMultiplier_multipleMatches_shouldReturnHighest() {
        HierarchyPromotion promo1 = multiplierPromotion("42", null, null, new BigDecimal("2.00"));
        HierarchyPromotion promo2 = multiplierPromotion("42", "SHOES", null, new BigDecimal("4.00"));
        HierarchyRequest item = new HierarchyRequest("42", "SHOES", "SNEAKERS");

        BigDecimal result = hierarchyPromotionService.resolveItemMultiplier(item, List.of(promo1, promo2));

        assertThat(result).isEqualByComparingTo("4.00");
    }

    @Test
    void resolveItemMultiplier_emptyList_shouldReturnOne() {
        HierarchyRequest item = new HierarchyRequest("42", null, null);

        BigDecimal result = hierarchyPromotionService.resolveItemMultiplier(item, Collections.emptyList());

        assertThat(result).isEqualByComparingTo("1");
    }

    // -----------------------------------------------------------------------
    // matchesHierarchy
    // -----------------------------------------------------------------------

    @Test
    void matchesHierarchy_exactMatch_shouldReturnTrue() {
        HierarchyPromotion promo = multiplierPromotion("42", "SHOES", "SNEAKERS", BigDecimal.ONE);
        HierarchyRequest item = new HierarchyRequest("42", "SHOES", "SNEAKERS");

        assertThat(hierarchyPromotionService.matchesHierarchy(promo, item)).isTrue();
    }

    @Test
    void matchesHierarchy_hierarchyOnlyPromotion_matchesAnyClassAndSubclass() {
        HierarchyPromotion promo = multiplierPromotion("42", null, null, BigDecimal.ONE);
        HierarchyRequest item = new HierarchyRequest("42", "SHOES", "SNEAKERS");

        assertThat(hierarchyPromotionService.matchesHierarchy(promo, item)).isTrue();
    }

    @Test
    void matchesHierarchy_hierarchyMismatch_shouldReturnFalse() {
        HierarchyPromotion promo = multiplierPromotion("42", null, null, BigDecimal.ONE);
        HierarchyRequest item = new HierarchyRequest("99", "SHOES", "SNEAKERS");

        assertThat(hierarchyPromotionService.matchesHierarchy(promo, item)).isFalse();
    }

    @Test
    void matchesHierarchy_classMismatch_shouldReturnFalse() {
        HierarchyPromotion promo = multiplierPromotion("42", "SHOES", null, BigDecimal.ONE);
        HierarchyRequest item = new HierarchyRequest("42", "CLOTHES", "SHIRTS");

        assertThat(hierarchyPromotionService.matchesHierarchy(promo, item)).isFalse();
    }

    @Test
    void matchesHierarchy_subclassMismatch_shouldReturnFalse() {
        HierarchyPromotion promo = multiplierPromotion("42", "SHOES", "BOOTS", BigDecimal.ONE);
        HierarchyRequest item = new HierarchyRequest("42", "SHOES", "SNEAKERS");

        assertThat(hierarchyPromotionService.matchesHierarchy(promo, item)).isFalse();
    }

    @Test
    void matchesHierarchy_caseInsensitive_shouldReturnTrue() {
        HierarchyPromotion promo = multiplierPromotion("42", "shoes", null, BigDecimal.ONE);
        HierarchyRequest item = new HierarchyRequest("42", "SHOES", null);

        assertThat(hierarchyPromotionService.matchesHierarchy(promo, item)).isTrue();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private HierarchyPromotion multiplierPromotion(String hierarchy, String productClass, String subclass, BigDecimal multiplier) {
        return HierarchyPromotion.builder()
                .name("Multiplier")
                .country("PL")
                .hierarchy(hierarchy)
                .productClass(productClass)
                .subclass(subclass)
                .type(HierarchyPromotionType.MULTIPLIER)
                .multiplier(multiplier)
                .startsAt(LocalDateTime.now().minusDays(1))
                .enabled(true)
                .build();
    }

    private HierarchyPromotion exclusionPromotion(String hierarchy, String productClass, String subclass) {
        return HierarchyPromotion.builder()
                .name("Exclusion")
                .country("PL")
                .hierarchy(hierarchy)
                .productClass(productClass)
                .subclass(subclass)
                .type(HierarchyPromotionType.EXCLUSION)
                .startsAt(LocalDateTime.now().minusDays(1))
                .enabled(true)
                .build();
    }
}
