package pl.pietruszynski.loyaltyclub.api.store.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.pietruszynski.loyaltyclub.api.admin.dto.LoginRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.LoginResponse;
import pl.pietruszynski.loyaltyclub.security.AuthenticationTokenService;
import pl.pietruszynski.loyaltyclub.security.AuthenticationTokenService.IssuedToken;

@RestController
@RequestMapping("/api/store/auth")
@RequiredArgsConstructor
public class StoreAuthController {

    private static final String ROLE = "STORE";
    private static final String AUTHORITY = "ROLE_STORE";

    private final AuthenticationTokenService authenticationTokenService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(toResponse(
                authenticationTokenService.login(request.username(), request.password(), AUTHORITY, ROLE)));
    }

    /** Odpowiednik {@code /api/admin/auth/refresh} -- kasa nie musi przechowywac hasla. */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(toResponse(authenticationTokenService.refresh(authentication, ROLE)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        authenticationTokenService.logout(authorizationHeader);
        return ResponseEntity.noContent().build();
    }

    private LoginResponse toResponse(IssuedToken issued) {
        return LoginResponse.builder()
                .token(issued.token())
                .expiresAt(issued.expiresAt())
                .role(issued.role())
                .country(null)
                .build();
    }
}
