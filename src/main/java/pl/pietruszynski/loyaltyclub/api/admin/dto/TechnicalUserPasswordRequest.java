package pl.pietruszynski.loyaltyclub.api.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record TechnicalUserPasswordRequest(
        @NotBlank(message = "Password is required")
        String password
) {}
