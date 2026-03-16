package pl.pietruszynski.loyaltyclub.api.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.pietruszynski.loyaltyclub.api.ecom.security.EcomUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.store.security.StoreUserDetailsService;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AdminUserDetailsService adminUserDetailsService;
    private final StoreUserDetailsService storeUserDetailsService;
    private final EcomUserDetailsService ecomUserDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String jwt = authHeader.substring(7);
            String username = jwtService.extractUsername(jwt);
            String role = jwtService.extractRole(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = loadUserDetails(username, role);
                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception ignored) {
            // Invalid token: leave context unauthenticated.
        }

        filterChain.doFilter(request, response);
    }

    private UserDetails loadUserDetails(String username, String role) {
        if (role != null && !role.isBlank()) {
            return switch (role.trim().toUpperCase()) {
                case "ADMIN", "TECHNICAL" -> adminUserDetailsService.loadUserByUsername(username);
                case "STORE" -> storeUserDetailsService.loadUserByUsername(username);
                case "ECOM" -> ecomUserDetailsService.loadUserByUsername(username);
                default -> loadUserDetailsFromAnySource(username);
            };
        }
        return loadUserDetailsFromAnySource(username);
    }

    private UserDetails loadUserDetailsFromAnySource(String username) {
        try {
            return adminUserDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException ignored) {
            // Continue with store and ecom sources.
        }

        try {
            return storeUserDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException ignored) {
            // Continue with ecom source.
        }

        return ecomUserDetailsService.loadUserByUsername(username);
    }
}


