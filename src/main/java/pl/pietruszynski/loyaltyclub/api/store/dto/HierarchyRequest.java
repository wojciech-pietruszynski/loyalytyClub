package pl.pietruszynski.loyaltyclub.api.store.dto;

import jakarta.validation.constraints.NotBlank;

public record HierarchyRequest(
        @NotBlank(message = "Hierarchy code is required")
        String hierarchy,
        String productClass,
        String subclass
) {}
