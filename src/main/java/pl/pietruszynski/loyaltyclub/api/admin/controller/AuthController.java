package pl.pietruszynski.loyaltyclub.api.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import pl.pietruszynski.loyaltyclub.api.admin.dto.ChangePasswordRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.LoginRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.LoginResponse;
import pl.pietruszynski.loyaltyclub.api.admin.audit.Auditable;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TechnicalUserRepository;
import pl.pietruszynski.loyaltyclub.api.admin.service.AccountService;
import pl.pietruszynski.loyaltyclub.security.AuthenticationTokenService;
import pl.pietruszynski.loyaltyclub.security.AuthenticationTokenService.IssuedToken;

import java.util.Collection;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String ROLE_TECHNICAL = "ROLE_TECHNICAL";

    private final AuthenticationTokenService authenticationTokenService;
    private final AccountService accountService;
    private final TechnicalUserRepository technicalUserRepository;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // Panel obsluguje dwa rodzaje kont; rola wynika z uprawnien konta, ktore
        // przeszlo uwierzytelnienie, a nie z tego, do ktorego punktu przyszlo zadanie.
        IssuedToken issued = authenticationTokenService.loginAsAny(
                request.username(),
                request.password(),
                java.util.Map.of("ROLE_ADMIN", "ADMIN", ROLE_TECHNICAL, "TECHNICAL")
        );
        return ResponseEntity.ok(buildLoginResponse(issued));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        UserDetails principal = (UserDetails) authentication.getPrincipal();
        String role = resolveRole(principal.getAuthorities());
        return ResponseEntity.ok(buildLoginResponse(authenticationTokenService.refresh(authentication, role)));
    }

    /** Wycofuje okazany token; kolejne zadanie z nim otrzyma 401. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        authenticationTokenService.logout(authorizationHeader);
        return ResponseEntity.noContent().build();
    }

    /** Zmiana wlasnego hasla; uniewaznia wszystkie sesje konta, wlacznie z biezaca. */
    @PostMapping("/change-password")
    @Auditable(value = "CHANGE_OWN_PASSWORD", resourceType = "ACCOUNT")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        accountService.changeOwnPassword(authentication.getName(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private LoginResponse buildLoginResponse(IssuedToken issued) {
        String country = "TECHNICAL".equals(issued.role())
                ? technicalUserRepository.findByUsername(issued.username()).map(user -> user.getCountry()).orElse(null)
                : null;

        return LoginResponse.builder()
                .token(issued.token())
                .expiresAt(issued.expiresAt())
                .role(issued.role())
                .country(country)
                .build();
    }

    private String resolveRole(Collection<? extends GrantedAuthority> authorities) {
        return authenticationTokenService.hasAuthority(authorities, ROLE_TECHNICAL) ? "TECHNICAL" : "ADMIN";
    }
}
