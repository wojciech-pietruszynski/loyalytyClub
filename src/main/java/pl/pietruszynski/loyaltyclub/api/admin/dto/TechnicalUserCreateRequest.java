package pl.pietruszynski.loyaltyclub.api.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record TechnicalUserCreateRequest(
        @NotBlank(message = "Username is required")
        String username,
        @NotBlank(message = "Password is required")
        String password,
        @NotBlank(message = "Country is required")
        String country,
        Boolean enabled
) {}
