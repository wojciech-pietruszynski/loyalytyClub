package pl.pietruszynski.loyaltyclub.api.store.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StoreTransactionItemRequest(
        String cartPosition,
        @NotBlank(message = "Item ean is required")
        String ean,
        @NotBlank(message = "Item name is required")
        String name,
        @NotNull(message = "Item hierarchy is required")
        @Valid
        HierarchyRequest hierarchy,
        @NotNull(message = "Item price is required")
        @Valid
        StoreItemPriceRequest price
) {}
