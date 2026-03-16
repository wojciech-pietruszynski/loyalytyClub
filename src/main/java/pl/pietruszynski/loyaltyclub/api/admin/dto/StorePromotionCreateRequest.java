package pl.pietruszynski.loyaltyclub.api.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StorePromotionCreateRequest(
        @NotBlank(message = "Name is required")
        String name,
        @NotBlank(message = "Country is required")
        String country,
        @NotNull(message = "Points per currency is required")
        @DecimalMin(value = "0.01", message = "Points per currency must be greater than zero")
        BigDecimal pointsPerCurrency,
        @NotNull(message = "Starts at is required")
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        Boolean enabled
) {}
