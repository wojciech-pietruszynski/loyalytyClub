package pl.pietruszynski.loyaltyclub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Parametry reguly premiowania poleceń. Cztery decyzje, ktore ta regula wymagala,
 * sa rozstrzygniete nastepujaco i wystawione jako konfiguracja, bo w programie
 * dzialajacym w kilku krajach beda przedmiotem decyzji marketingowych:
 *
 * <ul>
 *   <li><b>moment naliczenia</b> -- pierwszy zakup poleconego spelniajacy prog
 *       kwotowy, a nie sama rejestracja. Rejestracja nic nie kosztuje, wiec
 *       premiowanie jej samej otwiera program na zakladanie kont dla premii;</li>
 *   <li><b>wysokosc premii</b> -- stala liczba punktow, osobno dla polecajacego
 *       i dla poleconego. Wartosc procentowa wiazalaby premie z kwota zakupu
 *       i wymagalaby dodatkowego limitu gornego;</li>
 *   <li><b>prog minimalnego zakupu</b> -- kwota, ponizej ktorej zakup nie uruchamia
 *       naliczenia;</li>
 *   <li><b>limit polecen</b> -- maksymalna liczba rozliczonych polecen na jednego
 *       polecajacego.</li>
 * </ul>
 *
 * <p>Dodatkowo obowiazuje okno czasowe liczone od rejestracji poleconego --
 * bez niego relacja polecenia pozostawalaby zobowiazaniem bez terminu.
 */
@ConfigurationProperties(prefix = "app.referral")
public record ReferralProperties(
        Boolean enabled,
        BigDecimal minimumPurchaseAmount,
        Integer referrerPoints,
        Integer referredPoints,
        Integer maxRewardsPerReferrer,
        Integer qualifyingWindowDays,
        Integer validityDays
) {

    public ReferralProperties {
        enabled = enabled == null || enabled;
        minimumPurchaseAmount = minimumPurchaseAmount == null ? new BigDecimal("100.00") : minimumPurchaseAmount;
        referrerPoints = referrerPoints == null ? 500 : referrerPoints;
        referredPoints = referredPoints == null ? 250 : referredPoints;
        maxRewardsPerReferrer = maxRewardsPerReferrer == null ? 10 : maxRewardsPerReferrer;
        qualifyingWindowDays = qualifyingWindowDays == null ? 365 : qualifyingWindowDays;
        validityDays = validityDays == null ? 365 : validityDays;
    }
}
