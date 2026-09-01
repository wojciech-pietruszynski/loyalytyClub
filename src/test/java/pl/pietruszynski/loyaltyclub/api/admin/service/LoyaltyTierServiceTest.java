package pl.pietruszynski.loyaltyclub.api.admin.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.LoyaltyTier;
import pl.pietruszynski.loyaltyclub.api.admin.repository.LoyaltyTierRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Wyznaczanie poziomu lojalnosciowego.
 *
 * <p>Poziom liczony jest z dorobku punktowego, a nie z biezacego salda -- inaczej
 * wymiana punktow na kupon albo ich wygasniecie obnizalyby status uczestnika,
 * czyli program karalby za korzystanie z niego.
 */
@ExtendWith(MockitoExtension.class)
class LoyaltyTierServiceTest {

    @Mock private LoyaltyTierRepository loyaltyTierRepository;

    @InjectMocks
    private LoyaltyTierService loyaltyTierService;

    @ParameterizedTest
    @CsvSource({
            "0,      BRONZE",
            "999,    BRONZE",
            "1000,   SILVER",
            "4999,   SILVER",
            "5000,   GOLD",
            "999999, GOLD"
    })
    void resolveTierCode_shouldReturnHighestThresholdReached(int points, String expectedTier) {
        when(loyaltyTierRepository.findAllByOrderByMinPointsAsc()).thenReturn(tiers());

        assertThat(loyaltyTierService.resolveTierCode(points)).isEqualTo(expectedTier);
    }

    /** Poziom bierze sie z dorobku, nie z salda. */
    @Test
    void resolveTierCode_shouldUseLifetimePointsNotCurrentBalance() {
        when(loyaltyTierRepository.findAllByOrderByMinPointsAsc()).thenReturn(tiers());
        Customer customer = Customer.builder()
                .loyaltyPoints(0)
                .lifetimePoints(6000)
                .build();

        assertThat(loyaltyTierService.resolveTierCode(customer)).isEqualTo("GOLD");
    }

    @Test
    void resolveTierCode_withoutCustomer_shouldReturnNullWithoutQueryingTiers() {
        assertThat(loyaltyTierService.resolveTierCode((Customer) null)).isNull();

        verifyNoInteractions(loyaltyTierRepository);
    }

    @Test
    void resolveTierCode_withUnknownPoints_shouldReturnNullWithoutQueryingTiers() {
        assertThat(loyaltyTierService.resolveTierCode((Integer) null)).isNull();

        verifyNoInteractions(loyaltyTierRepository);
    }

    /** Program bez zdefiniowanych progow nie przyznaje zadnego poziomu. */
    @Test
    void resolveTierCode_withoutConfiguredTiers_shouldReturnNull() {
        when(loyaltyTierRepository.findAllByOrderByMinPointsAsc()).thenReturn(List.of());

        assertThat(loyaltyTierService.resolveTierCode(10_000)).isNull();
    }

    /** Punkty ponizej najnizszego progu nie daja poziomu. */
    @Test
    void resolveTierCode_belowLowestThreshold_shouldReturnNull() {
        when(loyaltyTierRepository.findAllByOrderByMinPointsAsc())
                .thenReturn(List.of(tier("SILVER", 1000)));

        assertThat(loyaltyTierService.resolveTierCode(999)).isNull();
    }

    @Test
    void getTiers_shouldReturnTiersOrderedByThreshold() {
        when(loyaltyTierRepository.findAllByOrderByMinPointsAsc()).thenReturn(tiers());

        assertThat(loyaltyTierService.getTiers())
                .extracting(LoyaltyTier::getCode)
                .containsExactly("BRONZE", "SILVER", "GOLD");
    }

    private List<LoyaltyTier> tiers() {
        return List.of(tier("BRONZE", 0), tier("SILVER", 1000), tier("GOLD", 5000));
    }

    private LoyaltyTier tier(String code, int minPoints) {
        LoyaltyTier tier = new LoyaltyTier();
        tier.setCode(code);
        tier.setMinPoints(minPoints);
        return tier;
    }
}
