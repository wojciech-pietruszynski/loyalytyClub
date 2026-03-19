package pl.pietruszynski.loyaltyclub.api.store.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pl.pietruszynski.loyaltyclub.api.store.model.StorePointsPromotion;
import pl.pietruszynski.loyaltyclub.api.store.repository.StorePointsPromotionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorePromotionServiceTest {

    @Mock
    private StorePointsPromotionRepository storePointsPromotionRepository;

    @InjectMocks
    private StorePromotionService storePromotionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storePromotionService, "defaultPointsPerCurrency", new BigDecimal("1.00"));
    }

    @Test
    void resolvePointsPerCurrency_noActivePromotion_shouldReturnDefault() {
        when(storePointsPromotionRepository.findActivePromotions(eq("PL"), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        BigDecimal result = storePromotionService.resolvePointsPerCurrency("PL", LocalDateTime.now());

        assertThat(result).isEqualByComparingTo("1.00");
    }

    @Test
    void resolvePointsPerCurrency_activePromotion_shouldReturnPromotionRate() {
        StorePointsPromotion promo = StorePointsPromotion.builder()
                .name("Double Points")
                .country("PL")
                .pointsPerCurrency(new BigDecimal("2.50"))
                .startsAt(LocalDateTime.now().minusDays(1))
                .enabled(true)
                .build();
        when(storePointsPromotionRepository.findActivePromotions(eq("PL"), any(LocalDateTime.class)))
                .thenReturn(List.of(promo));

        BigDecimal result = storePromotionService.resolvePointsPerCurrency("PL", LocalDateTime.now());

        assertThat(result).isEqualByComparingTo("2.50");
    }

    @Test
    void resolvePointsPerCurrency_countryNormalized_shouldUppercase() {
        when(storePointsPromotionRepository.findActivePromotions(eq("PL"), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        storePromotionService.resolvePointsPerCurrency("pl", LocalDateTime.now());

        verify(storePointsPromotionRepository).findActivePromotions(eq("PL"), any(LocalDateTime.class));
    }

    @Test
    void resolvePointsPerCurrency_multiplePromotions_shouldReturnFirst() {
        StorePointsPromotion promo1 = StorePointsPromotion.builder()
                .name("First")
                .country("PL")
                .pointsPerCurrency(new BigDecimal("3.00"))
                .startsAt(LocalDateTime.now().minusDays(1))
                .enabled(true)
                .build();
        StorePointsPromotion promo2 = StorePointsPromotion.builder()
                .name("Second")
                .country("PL")
                .pointsPerCurrency(new BigDecimal("5.00"))
                .startsAt(LocalDateTime.now().minusDays(2))
                .enabled(true)
                .build();
        when(storePointsPromotionRepository.findActivePromotions(eq("PL"), any(LocalDateTime.class)))
                .thenReturn(List.of(promo1, promo2));

        BigDecimal result = storePromotionService.resolvePointsPerCurrency("PL", LocalDateTime.now());

        assertThat(result).isEqualByComparingTo("3.00");
    }

    @Test
    void resolvePointsPerCurrency_nullCountry_shouldNormalizeToEmpty() {
        when(storePointsPromotionRepository.findActivePromotions(eq(""), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        BigDecimal result = storePromotionService.resolvePointsPerCurrency(null, LocalDateTime.now());

        assertThat(result).isEqualByComparingTo("1.00");
    }
}
