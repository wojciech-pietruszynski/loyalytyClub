package pl.pietruszynski.loyaltyclub.api.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import pl.pietruszynski.loyaltyclub.api.ecom.security.EcomUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.store.security.StoreUserDetailsService;
import pl.pietruszynski.loyaltyclub.security.TokenRevocationService;

import java.time.LocalDateTime;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtService jwtService;
    @Mock private AdminUserDetailsService adminUserDetailsService;
    @Mock private StoreUserDetailsService storeUserDetailsService;
    @Mock private EcomUserDetailsService ecomUserDetailsService;
    @Mock private TokenRevocationService tokenRevocationService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    @Test
    void doFilterInternal_noAuthHeader_shouldPassThrough() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(jwtService, never()).extractUsername(any());
    }

    @Test
    void doFilterInternal_nonBearerHeader_shouldPassThrough() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(jwtService, never()).extractUsername(any());
    }

    @Test
    void doFilterInternal_validAdminToken_shouldSetAuthentication() throws Exception {
        SecurityContextHolder.clearContext();
        UserDetails adminDetails = new User("admin", "enc",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        when(request.getHeader("Authorization")).thenReturn("Bearer valid-jwt");
        when(jwtService.extractUsername("valid-jwt")).thenReturn("admin");
        when(jwtService.extractRole("valid-jwt")).thenReturn("ADMIN");
        when(adminUserDetailsService.loadUserByUsername("admin")).thenReturn(adminDetails);
        when(jwtService.isTokenValid("valid-jwt", adminDetails)).thenReturn(true);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("admin");
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_validStoreToken_shouldSetAuthentication() throws Exception {
        SecurityContextHolder.clearContext();
        UserDetails storeDetails = new User("store01", "enc",
                List.of(new SimpleGrantedAuthority("ROLE_STORE")));

        when(request.getHeader("Authorization")).thenReturn("Bearer store-jwt");
        when(jwtService.extractUsername("store-jwt")).thenReturn("store01");
        when(jwtService.extractRole("store-jwt")).thenReturn("STORE");
        when(storeUserDetailsService.loadUserByUsername("store01")).thenReturn(storeDetails);
        when(jwtService.isTokenValid("store-jwt", storeDetails)).thenReturn(true);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_validEcomToken_shouldSetAuthentication() throws Exception {
        SecurityContextHolder.clearContext();
        UserDetails ecomDetails = new User("ecom01", "enc",
                List.of(new SimpleGrantedAuthority("ROLE_ECOM")));

        when(request.getHeader("Authorization")).thenReturn("Bearer ecom-jwt");
        when(jwtService.extractUsername("ecom-jwt")).thenReturn("ecom01");
        when(jwtService.extractRole("ecom-jwt")).thenReturn("ECOM");
        when(ecomUserDetailsService.loadUserByUsername("ecom01")).thenReturn(ecomDetails);
        when(jwtService.isTokenValid("ecom-jwt", ecomDetails)).thenReturn(true);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_invalidToken_shouldPassThroughWithoutAuth() throws Exception {
        SecurityContextHolder.clearContext();

        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-jwt");
        when(jwtService.extractUsername("invalid-jwt")).thenThrow(new RuntimeException("bad token"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_tokenNotValid_shouldNotSetAuth() throws Exception {
        SecurityContextHolder.clearContext();
        UserDetails adminDetails = new User("admin", "enc",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        when(request.getHeader("Authorization")).thenReturn("Bearer expired-jwt");
        when(jwtService.extractUsername("expired-jwt")).thenReturn("admin");
        when(jwtService.extractRole("expired-jwt")).thenReturn("ADMIN");
        when(adminUserDetailsService.loadUserByUsername("admin")).thenReturn(adminDetails);
        when(jwtService.isTokenValid("expired-jwt", adminDetails)).thenReturn(false);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_unknownRole_shouldFallbackToAllSources() throws Exception {
        SecurityContextHolder.clearContext();
        UserDetails adminDetails = new User("admin", "enc",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        when(request.getHeader("Authorization")).thenReturn("Bearer mystery-jwt");
        when(jwtService.extractUsername("mystery-jwt")).thenReturn("admin");
        when(jwtService.extractRole("mystery-jwt")).thenReturn("MYSTERY");
        when(adminUserDetailsService.loadUserByUsername("admin")).thenReturn(adminDetails);
        when(jwtService.isTokenValid("mystery-jwt", adminDetails)).thenReturn(true);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_unknownRole_adminFails_storeFallback() throws Exception {
        SecurityContextHolder.clearContext();
        UserDetails storeDetails = new User("store01", "enc",
                List.of(new SimpleGrantedAuthority("ROLE_STORE")));

        when(request.getHeader("Authorization")).thenReturn("Bearer mystery-jwt2");
        when(jwtService.extractUsername("mystery-jwt2")).thenReturn("store01");
        when(jwtService.extractRole("mystery-jwt2")).thenReturn("UNKNOWN");
        when(adminUserDetailsService.loadUserByUsername("store01"))
                .thenThrow(new UsernameNotFoundException("not found"));
        when(storeUserDetailsService.loadUserByUsername("store01")).thenReturn(storeDetails);
        when(jwtService.isTokenValid("mystery-jwt2", storeDetails)).thenReturn(true);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_unknownRole_adminAndStoreFail_ecomFallback() throws Exception {
        SecurityContextHolder.clearContext();
        UserDetails ecomDetails = new User("ecom01", "enc",
                List.of(new SimpleGrantedAuthority("ROLE_ECOM")));

        when(request.getHeader("Authorization")).thenReturn("Bearer mystery-jwt3");
        when(jwtService.extractUsername("mystery-jwt3")).thenReturn("ecom01");
        when(jwtService.extractRole("mystery-jwt3")).thenReturn("UNKNOWN");
        when(adminUserDetailsService.loadUserByUsername("ecom01"))
                .thenThrow(new UsernameNotFoundException("not admin"));
        when(storeUserDetailsService.loadUserByUsername("ecom01"))
                .thenThrow(new UsernameNotFoundException("not store"));
        when(ecomUserDetailsService.loadUserByUsername("ecom01")).thenReturn(ecomDetails);
        when(jwtService.isTokenValid("mystery-jwt3", ecomDetails)).thenReturn(true);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_technicalRole_shouldUseAdminDetailsService() throws Exception {
        SecurityContextHolder.clearContext();
        UserDetails techDetails = new User("techpl", "enc",
                List.of(new SimpleGrantedAuthority("ROLE_TECHNICAL")));

        when(request.getHeader("Authorization")).thenReturn("Bearer tech-jwt");
        when(jwtService.extractUsername("tech-jwt")).thenReturn("techpl");
        when(jwtService.extractRole("tech-jwt")).thenReturn("TECHNICAL");
        when(adminUserDetailsService.loadUserByUsername("techpl")).thenReturn(techDetails);
        when(jwtService.isTokenValid("tech-jwt", techDetails)).thenReturn(true);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        SecurityContextHolder.clearContext();
    }

    /**
     * Token wycofany wylogowaniem albo zmiana hasla nie moze juz uwierzytelniac,
     * mimo ze jego podpis i data waznosci sa nadal poprawne.
     */
    @Test
    void doFilterInternal_revokedToken_shouldNotSetAuthentication() throws Exception {
        SecurityContextHolder.clearContext();

        when(request.getHeader("Authorization")).thenReturn("Bearer revoked-jwt");
        when(jwtService.extractUsername("revoked-jwt")).thenReturn("admin");
        when(jwtService.extractTokenId("revoked-jwt")).thenReturn("token-id");
        when(jwtService.extractIssuedAt("revoked-jwt")).thenReturn(LocalDateTime.now());
        when(tokenRevocationService.isRevoked(eq("token-id"), eq("admin"), any())).thenReturn(true);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(adminUserDetailsService, never()).loadUserByUsername(any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
