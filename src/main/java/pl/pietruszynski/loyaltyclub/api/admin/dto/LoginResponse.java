package pl.pietruszynski.loyaltyclub.api.admin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {
    private String token;
    private long expiresAt;
    private String role;
    private String country;
}


