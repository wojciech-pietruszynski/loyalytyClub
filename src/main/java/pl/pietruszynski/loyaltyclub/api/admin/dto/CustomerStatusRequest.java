package pl.pietruszynski.loyaltyclub.api.admin.dto;

import jakarta.validation.constraints.NotNull;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerStatus;

public record CustomerStatusRequest(
        @NotNull(message = "Status is required")
        CustomerStatus status
) {}
