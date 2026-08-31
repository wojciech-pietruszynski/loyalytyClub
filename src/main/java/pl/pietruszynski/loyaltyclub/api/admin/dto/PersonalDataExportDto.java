package pl.pietruszynski.loyaltyclub.api.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Komplet danych uczestnika w postaci przenoszalnej (RODO, art. 20).
 * Struktura odwzorowuje to, co system o uczestniku przechowuje: profil,
 * pelna historie punktowa, wydane kupony i rozliczone polecenia.
 */
public record PersonalDataExportDto(
        LocalDateTime generatedAt,
        Profile profile,
        List<TransactionEntry> transactions,
        List<CouponEntry> coupons,
        List<ReferralEntry> referrals
) {

    public record Profile(
            Long id,
            String customerNumber,
            String firstName,
            String lastName,
            String email,
            String phoneNumber,
            String country,
            String status,
            Integer loyaltyPoints,
            Integer lifetimePoints,
            String loyaltyTierCode,
            String referralCode,
            String referredByCustomerNumber,
            LocalDateTime createdAt
    ) {}

    public record TransactionEntry(
            Long id,
            LocalDateTime timestamp,
            LocalDateTime purchaseTimestamp,
            String type,
            String state,
            Integer points,
            BigDecimal amount,
            String country,
            String description,
            String sourceTransactionNumber,
            LocalDateTime availableFrom,
            LocalDateTime expiresAt
    ) {}

    public record CouponEntry(
            Long id,
            String couponCode,
            String status,
            String reason,
            BigDecimal couponValue,
            Integer requiredPoints,
            String country,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {}

    public record ReferralEntry(
            Long id,
            String referredCustomerNumber,
            Integer referrerPoints,
            Integer referredPoints,
            LocalDateTime awardedAt
    ) {}
}
