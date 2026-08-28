package pl.pietruszynski.loyaltyclub.api.admin.dto;

/**
 * Podsumowanie raportowe panelu. Pole {@code scope} zawiera kod kraju, do ktorego
 * ograniczony jest wynik, albo pusty ciag, gdy obejmuje wszystkie kraje.
 */
public record ReportsSummaryDto(
        String scope,
        long customerCount,
        long totalLoyaltyPoints,
        long transactionsLast30Days
) {}
