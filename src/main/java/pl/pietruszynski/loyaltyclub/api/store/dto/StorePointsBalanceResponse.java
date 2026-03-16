package pl.pietruszynski.loyaltyclub.api.store.dto;

public record StorePointsBalanceResponse(
        Long customerId,
        String customerNumber,
        Integer pendingPoints,
        Integer availablePoints,
        Integer expiredPoints
) {}
