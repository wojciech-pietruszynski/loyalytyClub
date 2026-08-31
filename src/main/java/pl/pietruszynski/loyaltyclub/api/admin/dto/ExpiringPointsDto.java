package pl.pietruszynski.loyaltyclub.api.admin.dto;

import java.time.LocalDateTime;

/** Pozycja zestawienia punktow zblizajacych sie do wygasniecia. */
public record ExpiringPointsDto(
        Long transactionId,
        Long customerId,
        String customerNumber,
        String country,
        Integer points,
        LocalDateTime expiresAt
) {}
