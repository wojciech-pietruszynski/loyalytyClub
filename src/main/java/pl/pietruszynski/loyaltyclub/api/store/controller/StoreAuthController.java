package pl.pietruszynski.loyaltyclub.api.store.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.pietruszynski.loyaltyclub.api.admin.dto.LoginRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.LoginResponse;
import pl.pietruszynski.loyaltyclub.api.admin.security.JwtService;
import pl.pietruszynski.loyaltyclub.api.store.security.StoreUserDetailsService;

@RestController
@RequestMapping("/api/store/auth")
@RequiredArgsConstructor
public class StoreAuthController {

    private final AuthenticationManager authenticationManager;
    private final StoreUserDetailsService storeUserDetailsService;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        storeUserDetailsService.loadUserByUsername(request.username());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        UserDetails principal = (UserDetails) authentication.getPrincipal();
        String token = jwtService.generateToken(principal.getUsername(), "STORE");
        long expiresAt = jwtService.extractExpirationEpochMillis(token);

        return ResponseEntity.ok(LoginResponse.builder()
                .token(token)
                .expiresAt(expiresAt)
                .role("STORE")
                .country(null)
                .build());
    }
}
