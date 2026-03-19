package pl.pietruszynski.loyaltyclub.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pl.pietruszynski.loyaltyclub.api.store.model.HierarchyPromotionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HierarchyPromotionCreateRequest(
        @NotBlank(message = "Name is required")
        String name,
        @NotBlank(message = "Country is required")
        String country,
        String hierarchy,
        String productClass,
        String subclass,
        @NotNull(message = "Type is required")
        HierarchyPromotionType type,
        BigDecimal multiplier,
        @NotNull(message = "Starts at is required")
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        Boolean enabled
) {}
