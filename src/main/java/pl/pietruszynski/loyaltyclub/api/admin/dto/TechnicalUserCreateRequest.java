package pl.pietruszynski.loyaltyclub.api.admin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Haslo jest opcjonalne. Gdy go nie podano, serwer generuje haslo jednorazowe
 * i zwraca je raz w odpowiedzi -- to jest sciezka zalecana.
 */
public record TechnicalUserCreateRequest(
        @NotBlank(message = "Username is required")
        String username,
        String password,
        @NotBlank(message = "Country is required")
        String country,
        Boolean enabled
) {}
