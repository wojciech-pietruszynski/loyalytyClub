package pl.pietruszynski.loyaltyclub.api.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import pl.pietruszynski.loyaltyclub.api.admin.dto.LoginRequest;
import pl.pietruszynski.loyaltyclub.api.admin.model.TechnicalUser;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TechnicalUserRepository;
import pl.pietruszynski.loyaltyclub.api.admin.security.AdminUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.admin.security.JwtService;
import pl.pietruszynski.loyaltyclub.api.ecom.security.EcomUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.store.security.StoreUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.admin.service.AccountService;
import pl.pietruszynski.loyaltyclub.security.AuthenticationTokenService;
import pl.pietruszynski.loyaltyclub.security.TokenRevocationService;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AuthController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class})
// Wydawanie tokenu jest testowane razem z kontrolerem -- to ono decyduje,
// czy poswiadczenia pasuja do przestrzeni, do ktorej przyszlo zadanie.
@Import(AuthenticationTokenService.class)
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean AuthenticationManager authenticationManager;
    @MockitoBean TechnicalUserRepository technicalUserRepository;
    @MockitoBean JwtService jwtService;
    @MockitoBean AdminUserDetailsService adminUserDetailsService;
    @MockitoBean StoreUserDetailsService storeUserDetailsService;
    @MockitoBean EcomUserDetailsService ecomUserDetailsService;
    @MockitoBean TokenRevocationService tokenRevocationService;
    @MockitoBean AccountService accountService;

    @Test
    void login_adminUser_shouldReturnTokenAndAdminRole() throws Exception {
        User principal = new User("admin", "enc",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtService.generateToken("admin", "ADMIN")).thenReturn("jwt-token");
        when(jwtService.extractExpirationEpochMillis("jwt-token")).thenReturn(9999999L);

        LoginRequest request = new LoginRequest("admin", "password");

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void login_technicalUser_shouldReturnTokenWithCountry() throws Exception {
        User principal = new User("techpl", "enc",
                List.of(new SimpleGrantedAuthority("ROLE_TECHNICAL")));
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        TechnicalUser techUser = TechnicalUser.builder()
                .username("techpl")
                .password("enc")
                .country("PL")
                .enabled(true)
                .build();

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtService.generateToken("techpl", "TECHNICAL")).thenReturn("jwt-tech-token");
        when(jwtService.extractExpirationEpochMillis("jwt-tech-token")).thenReturn(9999999L);
        when(technicalUserRepository.findByUsername("techpl")).thenReturn(Optional.of(techUser));

        LoginRequest request = new LoginRequest("techpl", "secret");

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TECHNICAL"))
                .andExpect(jsonPath("$.country").value("PL"));
    }

    @Test
    void refresh_withoutAuthentication_shouldReturn401() throws Exception {
        // Without a valid JWT, Authentication is null → controller returns 401
        mockMvc.perform(post("/api/admin/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_authenticated_shouldReturnNewToken() throws Exception {
        User principal = new User("admin", "enc",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        when(jwtService.generateToken("admin", "ADMIN")).thenReturn("new-token");
        when(jwtService.extractExpirationEpochMillis("new-token")).thenReturn(9999999L);

        mockMvc.perform(post("/api/admin/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-token"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void login_badCredentials_shouldReturn401() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        LoginRequest request = new LoginRequest("admin", "wrong");

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Menedzer uwierzytelniania obsluguje trzy zbiory kont naraz. Konto kasowe
     * ma poprawne haslo, ale nie ma prawa dostac tokenu panelu.
     */
    @Test
    void login_storeAccountOnAdminEndpoint_shouldReturn401() throws Exception {
        User principal = new User("store01", "enc",
                List.of(new SimpleGrantedAuthority("ROLE_STORE")));
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(auth);

        LoginRequest request = new LoginRequest("store01", "password");

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    /** Wylogowanie jest dostepne bez uwierzytelnienia i idempotentne. */
    @Test
    void logout_shouldReturn204() throws Exception {
        mockMvc.perform(post("/api/admin/auth/logout")
                        .header("Authorization", "Bearer some-token"))
                .andExpect(status().isNoContent());
    }
}
