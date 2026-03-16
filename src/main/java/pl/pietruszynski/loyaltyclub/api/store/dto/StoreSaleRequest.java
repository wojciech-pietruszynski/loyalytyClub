package pl.pietruszynski.loyaltyclub.api.store.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record StoreSaleRequest(
        @NotBlank(message = "Customer number is required")
        String customerNumber,
        @NotEmpty(message = "Items are required")
        List<@NotNull(message = "Item cannot be null") @Valid StoreTransactionItemRequest> items,
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal totalAmount,
        @NotBlank(message = "Source transaction number is required")
        String sourceTransactionNumber,
        LocalDateTime purchaseTimestamp
) {}
