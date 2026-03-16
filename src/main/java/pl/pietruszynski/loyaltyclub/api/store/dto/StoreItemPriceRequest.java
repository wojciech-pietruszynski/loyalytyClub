package pl.pietruszynski.loyaltyclub.api.store.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record StoreItemPriceRequest(
        @NotNull(message = "Item price amount is required")
        @DecimalMin(value = "0.00", message = "Item price amount cannot be negative")
        BigDecimal amount,
        @NotBlank(message = "Item price currency is required")
        String currency
) {}
