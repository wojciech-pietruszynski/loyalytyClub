package pl.pietruszynski.loyaltyclub.api.admin.dto;

/**
 * Podsumowanie raportowe panelu. Pole {@code scope} zawiera kod kraju, do ktorego
 * ograniczony jest wynik, albo pusty ciag, gdy obejmuje wszystkie kraje.
 *
 * <p>Punkty sa rozbite na trzy stany, bo suma wszystkich naliczen nie jest
 * "punktami w obiegu": punkty oczekujace nie sa jeszcze dostepne, a wygasle
 * nie sa juz zobowiazaniem programu. {@code totalLoyaltyPoints} zachowuje
 * dotychczasowa nazwe dla zgodnosci wstecz i zawiera punkty dostepne.
 */
public record ReportsSummaryDto(
        String scope,
        long customerCount,
        long totalLoyaltyPoints,
        long transactionsLast30Days,
        long availablePoints,
        long pendingPoints,
        long expiredPoints
) {

    public static ReportsSummaryDto of(String scope,
                                       long customerCount,
                                       long transactionsLast30Days,
                                       long availablePoints,
                                       long pendingPoints,
                                       long expiredPoints) {
        return new ReportsSummaryDto(
                scope,
                customerCount,
                availablePoints,
                transactionsLast30Days,
                availablePoints,
                pendingPoints,
                expiredPoints
        );
    }
}
