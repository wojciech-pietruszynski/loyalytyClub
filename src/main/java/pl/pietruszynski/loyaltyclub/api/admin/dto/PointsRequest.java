package pl.pietruszynski.loyaltyclub.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PointsRequest(
        @NotNull(message = "Points are required")
        @Positive(message = "Points must be greater than zero")
        Integer points,
        @NotBlank(message = "Description is required")
        String description
) {}


