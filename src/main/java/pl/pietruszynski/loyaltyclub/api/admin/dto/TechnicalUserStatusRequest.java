package pl.pietruszynski.loyaltyclub.api.admin.dto;

import jakarta.validation.constraints.NotNull;

public record TechnicalUserStatusRequest(
        @NotNull(message = "Status is required")
        Boolean enabled
) {}
