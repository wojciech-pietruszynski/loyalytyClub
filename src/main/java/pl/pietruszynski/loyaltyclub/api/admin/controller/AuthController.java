package pl.pietruszynski.loyaltyclub.api.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import pl.pietruszynski.loyaltyclub.api.admin.dto.LoginRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.LoginResponse;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TechnicalUserRepository;
import pl.pietruszynski.loyaltyclub.api.admin.security.AdminUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.admin.security.JwtService;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AdminUserDetailsService adminUserDetailsService;
    private final TechnicalUserRepository technicalUserRepository;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        adminUserDetailsService.loadUserByUsername(request.username());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        UserDetails principal = (UserDetails) authentication.getPrincipal();
        String role = resolveRole(principal.getAuthorities());
        String token = jwtService.generateToken(principal.getUsername(), role);
        long expiresAt = jwtService.extractExpirationEpochMillis(token);

        return ResponseEntity.ok(buildLoginResponse(token, expiresAt, principal.getUsername(), principal.getAuthorities()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }
        String username = authentication.getName();
        UserDetails principal = (UserDetails) authentication.getPrincipal();
        String role = resolveRole(principal.getAuthorities());
        String token = jwtService.generateToken(username, role);
        long expiresAt = jwtService.extractExpirationEpochMillis(token);
        return ResponseEntity.ok(buildLoginResponse(token, expiresAt, username, principal.getAuthorities()));
    }

    private LoginResponse buildLoginResponse(String token, long expiresAt, String username, java.util.Collection<? extends GrantedAuthority> authorities) {
        boolean technical = authorities.stream().anyMatch(a -> "ROLE_TECHNICAL".equals(a.getAuthority()));
        String country = technical
                ? technicalUserRepository.findByUsername(username).map(u -> u.getCountry()).orElse(null)
                : null;

        return LoginResponse.builder()
                .token(token)
                .expiresAt(expiresAt)
                .role(technical ? "TECHNICAL" : "ADMIN")
                .country(country)
                .build();
    }

    private String resolveRole(java.util.Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().anyMatch(a -> "ROLE_TECHNICAL".equals(a.getAuthority()))
                ? "TECHNICAL"
                : "ADMIN";
    }
}


