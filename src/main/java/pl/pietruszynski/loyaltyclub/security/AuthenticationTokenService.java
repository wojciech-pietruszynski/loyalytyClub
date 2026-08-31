package pl.pietruszynski.loyaltyclub.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import pl.pietruszynski.loyaltyclub.api.admin.security.JwtService;

import java.util.Collection;
import java.util.Map;

/**
 * Wspolna obsluga logowania, odswiezania i wylogowania dla wszystkich przestrzeni
 * API. Wczesniej token wydawal wylacznie panel administracyjny i kasa, kazdy
 * wlasnym kodem; sklep internetowy nie mial punktu logowania w ogole i musial
 * poslugiwac sie metoda HTTP Basic przy kazdym zadaniu.
 *
 * <p>Klasa pilnuje takze, by poswiadczenia byly sprawdzane wobec wlasciwego zbioru
 * kont: menedzer uwierzytelniania obsluguje trzy dostawcy naraz, wiec bez jawnego
 * sprawdzenia roli konto kasowe uwierzytelnialoby sie w punkcie logowania panelu.
 */
@Service
@RequiredArgsConstructor
public class AuthenticationTokenService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenRevocationService tokenRevocationService;

    /**
     * @param requiredAuthority uprawnienie, ktore musi posiadac konto, np. {@code ROLE_ECOM}
     * @param role              wartosc deklaracji {@code role} zapisywanej w tokenie
     */
    public IssuedToken login(String username, String password, String requiredAuthority, String role) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );
        UserDetails principal = (UserDetails) authentication.getPrincipal();

        if (!hasAuthority(principal.getAuthorities(), requiredAuthority)) {
            throw new BadCredentialsException("Bad credentials");
        }

        return issue(principal.getUsername(), role);
    }

    /**
     * Logowanie do przestrzeni obslugujacej wiecej niz jedna role -- panel przyjmuje
     * konta administracyjne i techniczne. Konto musi posiadac ktores z podanych
     * uprawnien; w przeciwnym razie zadanie jest odrzucane, mimo ze poswiadczenia
     * moglyby byc poprawne dla innej przestrzeni.
     *
     * @param rolesByAuthority uprawnienie -> wartosc deklaracji {@code role}
     */
    public IssuedToken loginAsAny(String username, String password, Map<String, String> rolesByAuthority) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );
        UserDetails principal = (UserDetails) authentication.getPrincipal();

        String role = rolesByAuthority.entrySet().stream()
                .filter(entry -> hasAuthority(principal.getAuthorities(), entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new BadCredentialsException("Bad credentials"));

        return issue(principal.getUsername(), role);
    }

    public IssuedToken refresh(Authentication authentication, String role) {
        UserDetails principal = (UserDetails) authentication.getPrincipal();
        return issue(principal.getUsername(), role);
    }

    /**
     * Wycofuje okazany token. Wylogowanie jest idempotentne -- powtorzone zadanie
     * z tym samym tokenem konczy sie powodzeniem i nie zmienia stanu.
     */
    public void logout(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        if (token == null) {
            return;
        }
        try {
            tokenRevocationService.revokeToken(
                    jwtService.extractTokenId(token),
                    jwtService.extractUsername(token),
                    jwtService.extractExpiration(token)
            );
        } catch (RuntimeException ex) {
            // Token nieczytelny lub wygasly -- i tak nie daje juz dostepu.
        }
    }

    public IssuedToken issue(String username, String role) {
        String token = jwtService.generateToken(username, role);
        return new IssuedToken(token, jwtService.extractExpirationEpochMillis(token), username, role);
    }

    public boolean hasAuthority(Collection<? extends GrantedAuthority> authorities, String authority) {
        return authorities != null && authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    public record IssuedToken(String token, long expiresAt, String username, String role) {
    }
}
