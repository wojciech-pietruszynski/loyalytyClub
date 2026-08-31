package pl.pietruszynski.loyaltyclub.api.store.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import pl.pietruszynski.loyaltyclub.api.admin.dto.LoginRequest;
import pl.pietruszynski.loyaltyclub.api.admin.security.AdminUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.admin.security.JwtService;
import pl.pietruszynski.loyaltyclub.api.ecom.security.EcomUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.store.security.StoreUserDetailsService;
import pl.pietruszynski.loyaltyclub.security.AuthenticationTokenService;
import pl.pietruszynski.loyaltyclub.security.TokenRevocationService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = StoreAuthController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class})
@Import(AuthenticationTokenService.class)
class StoreAuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AuthenticationManager authenticationManager;
    @MockBean JwtService jwtService;
    @MockBean AdminUserDetailsService adminUserDetailsService;
    @MockBean StoreUserDetailsService storeUserDetailsService;
    @MockBean EcomUserDetailsService ecomUserDetailsService;
    @MockBean TokenRevocationService tokenRevocationService;

    @Test
    void login_validCredentials_shouldReturnStoreToken() throws Exception {
        User principal = new User("store01", "enc",
                List.of(new SimpleGrantedAuthority("ROLE_STORE")));
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtService.generateToken("store01", "STORE")).thenReturn("store-jwt-token");
        when(jwtService.extractExpirationEpochMillis("store-jwt-token")).thenReturn(9999999L);

        LoginRequest request = new LoginRequest("store01", "storepass");

        mockMvc.perform(post("/api/store/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("store-jwt-token"))
                .andExpect(jsonPath("$.role").value("STORE"));
    }

    /** Odswiezenie tokenu -- kasa nie musi przechowywac hasla miedzy zmianami. */
    @Test
    void refresh_authenticated_shouldReturnNewToken() throws Exception {
        User principal = new User("store01", "enc",
                List.of(new SimpleGrantedAuthority("ROLE_STORE")));
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        when(jwtService.generateToken("store01", "STORE")).thenReturn("refreshed-token");
        when(jwtService.extractExpirationEpochMillis("refreshed-token")).thenReturn(9999999L);

        mockMvc.perform(post("/api/store/auth/refresh").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("refreshed-token"))
                .andExpect(jsonPath("$.role").value("STORE"));
    }

    @Test
    void refresh_withoutAuthentication_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/store/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    /** Konto sklepu internetowego nie moze dostac tokenu kasy. */
    @Test
    void login_ecomAccountOnStoreEndpoint_shouldReturn401() throws Exception {
        User principal = new User("ecom", "enc",
                List.of(new SimpleGrantedAuthority("ROLE_ECOM")));
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(auth);

        LoginRequest request = new LoginRequest("ecom", "ecompass");

        mockMvc.perform(post("/api/store/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
