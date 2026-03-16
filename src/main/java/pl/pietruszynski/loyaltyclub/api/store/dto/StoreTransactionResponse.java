package pl.pietruszynski.loyaltyclub.api.store.dto;

import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionState;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StoreTransactionResponse(
        Long transactionId,
        Long customerId,
        String customerNumber,
        TransactionType type,
        TransactionState state,
        Integer points,
        BigDecimal amount,
        BigDecimal pointsPerCurrency,
        LocalDateTime purchaseTimestamp,
        LocalDateTime availableFrom,
        LocalDateTime expiresAt
) {}
