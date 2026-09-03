package pl.pietruszynski.loyaltyclub.api.store.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import pl.pietruszynski.loyaltyclub.PersistenceTest;
import pl.pietruszynski.loyaltyclub.api.store.model.HierarchyPromotion;
import pl.pietruszynski.loyaltyclub.api.store.model.HierarchyPromotionType;
import pl.pietruszynski.loyaltyclub.api.store.model.StorePointsPromotion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Okno obowiazywania promocji. Promocja wybrana blednie zmienia liczbe punktow
 * naliczonych przy sprzedazy, wiec warunek "aktywna teraz" musi byc sprawdzony
 * na bazie, a nie tylko przez mock w tescie serwisu.
 */
@PersistenceTest
class PromotionRepositoryTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private StorePointsPromotionRepository storePointsPromotionRepository;
    @Autowired private HierarchyPromotionRepository hierarchyPromotionRepository;

    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        // Kolumna TIMESTAMP ma mniejsza rozdzielczosc niz LocalDateTime.now(); bez
        // obciecia porownania na granicy okna sprawdzalyby blad zaokraglenia,
        // a nie regule.
        now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    }

    @Test
    void findActivePromotions_shouldSkipDisabledFuturePastAndForeignPromotions() {
        persistPointsPromotion("aktywna", "PL", "2.00", true, now.minusDays(1), now.plusDays(1));
        persistPointsPromotion("wylaczona", "PL", "9.00", false, now.minusDays(1), now.plusDays(1));
        persistPointsPromotion("przyszla", "PL", "9.00", true, now.plusDays(1), now.plusDays(2));
        persistPointsPromotion("zakonczona", "PL", "9.00", true, now.minusDays(5), now.minusDays(1));
        persistPointsPromotion("inny kraj", "DE", "9.00", true, now.minusDays(1), now.plusDays(1));

        assertThat(storePointsPromotionRepository.findActivePromotions("PL", now))
                .extracting(StorePointsPromotion::getName)
                .containsExactly("aktywna");
    }

    /** Brak daty konca oznacza promocje bezterminowa, a nie zakonczona. */
    @Test
    void findActivePromotions_shouldTreatMissingEndDateAsOpenEnded() {
        persistPointsPromotion("bezterminowa", "PL", "2.00", true, now.minusDays(10), null);

        assertThat(storePointsPromotionRepository.findActivePromotions("PL", now)).hasSize(1);
    }

    /** Granice okna sa domkniete z obu stron. */
    @Test
    void findActivePromotions_shouldIncludePromotionStartingOrEndingExactlyNow() {
        persistPointsPromotion("start teraz", "PL", "2.00", true, now, now.plusDays(1));
        persistPointsPromotion("koniec teraz", "PL", "3.00", true, now.minusDays(1), now);

        assertThat(storePointsPromotionRepository.findActivePromotions("PL", now)).hasSize(2);
    }

    /** Przy nakladajacych sie promocjach pierwsza ma byc najkorzystniejsza dla klienta. */
    @Test
    void findActivePromotions_shouldReturnHighestRateFirst() {
        persistPointsPromotion("nizsza", "PL", "1.50", true, now.minusDays(1), now.plusDays(1));
        persistPointsPromotion("wyzsza", "PL", "3.00", true, now.minusDays(1), now.plusDays(1));

        assertThat(storePointsPromotionRepository.findActivePromotions("PL", now))
                .extracting(StorePointsPromotion::getName)
                .containsExactly("wyzsza", "nizsza");
    }

    @Test
    void findActiveHierarchyPromotions_shouldApplyTheSameWindowRules() {
        persistHierarchyPromotion("aktywna", "PL", true, now.minusDays(1), now.plusDays(1));
        persistHierarchyPromotion("wylaczona", "PL", false, now.minusDays(1), now.plusDays(1));
        persistHierarchyPromotion("bezterminowa", "PL", true, now.minusDays(3), null);
        persistHierarchyPromotion("inny kraj", "DE", true, now.minusDays(1), now.plusDays(1));

        assertThat(hierarchyPromotionRepository.findActivePromotions("PL", now))
                .extracting(HierarchyPromotion::getName)
                // ORDER BY startsAt DESC
                .containsExactly("aktywna", "bezterminowa");
    }

    @Test
    void findAllByCountry_shouldListNewestFirstForTheAdminPanel() {
        persistHierarchyPromotion("starsza", "PL", true, now.minusDays(10), null);
        persistHierarchyPromotion("nowsza", "PL", true, now.minusDays(1), null);
        persistHierarchyPromotion("niemiecka", "DE", true, now.minusDays(5), null);

        assertThat(hierarchyPromotionRepository.findAllByCountryOrderByStartsAtDesc("PL"))
                .extracting(HierarchyPromotion::getName)
                .containsExactly("nowsza", "starsza");
        assertThat(hierarchyPromotionRepository.findAllByOrderByStartsAtDesc()).hasSize(3);
    }

    private void persistPointsPromotion(String name,
                                        String country,
                                        String pointsPerCurrency,
                                        boolean enabled,
                                        LocalDateTime startsAt,
                                        LocalDateTime endsAt) {
        entityManager.persistAndFlush(StorePointsPromotion.builder()
                .name(name)
                .country(country)
                .pointsPerCurrency(new BigDecimal(pointsPerCurrency))
                .enabled(enabled)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .build());
    }

    private void persistHierarchyPromotion(String name,
                                           String country,
                                           boolean enabled,
                                           LocalDateTime startsAt,
                                           LocalDateTime endsAt) {
        entityManager.persistAndFlush(HierarchyPromotion.builder()
                .name(name)
                .country(country)
                .hierarchy("42")
                .type(HierarchyPromotionType.MULTIPLIER)
                .multiplier(new BigDecimal("2.0000"))
                .enabled(enabled)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .build());
    }
}
