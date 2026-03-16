package pl.pietruszynski.loyaltyclub.api.admin.dto;

import jakarta.validation.constraints.NotNull;

public record StorePromotionStatusRequest(
        @NotNull(message = "Status is required")
        Boolean enabled
) {}
