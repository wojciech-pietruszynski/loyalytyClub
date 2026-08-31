package pl.pietruszynski.loyaltyclub.api.ecom.controller;

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
import pl.pietruszynski.loyaltyclub.api.admin.security.AdminUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.admin.security.JwtService;
import pl.pietruszynski.loyaltyclub.api.ecom.security.EcomUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.store.security.StoreUserDetailsService;
import pl.pietruszynski.loyaltyclub.security.AuthenticationTokenService;
import pl.pietruszynski.loyaltyclub.security.TokenRevocationService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kanal e-commerce uwierzytelnial sie dotad wylacznie metoda HTTP Basic --
 * SDK nie potrafilo samo pozyskac tokenu, bo backend nie udostepnial punktu
 * logowania. Testy pilnuja, ze punkt istnieje i nie przyjmuje kont z innych
 * przestrzeni.
 */
@WebMvcTest(value = EcomAuthController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class})
@Import(AuthenticationTokenService.class)
class EcomAuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean AuthenticationManager authenticationManager;
    @MockitoBean JwtService jwtService;
    @MockitoBean AdminUserDetailsService adminUserDetailsService;
    @MockitoBean StoreUserDetailsService storeUserDetailsService;
    @MockitoBean EcomUserDetailsService ecomUserDetailsService;
    @MockitoBean TokenRevocationService tokenRevocationService;

    @Test
    void login_validCredentials_shouldReturnEcomToken() throws Exception {
        User principal = new User("ecom", "enc",
                List.of(new SimpleGrantedAuthority("ROLE_ECOM")));
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtService.generateToken("ecom", "ECOM")).thenReturn("ecom-jwt-token");
        when(jwtService.extractExpirationEpochMillis("ecom-jwt-token")).thenReturn(9999999L);

        LoginRequest request = new LoginRequest("ecom", "ecompass");

        mockMvc.perform(post("/api/ecom/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("ecom-jwt-token"))
                .andExpect(jsonPath("$.role").value("ECOM"));
    }

    @Test
    void login_badCredentials_shouldReturn401() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        LoginRequest request = new LoginRequest("ecom", "wrong");

        mockMvc.perform(post("/api/ecom/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_adminAccountOnEcomEndpoint_shouldReturn401() throws Exception {
        User principal = new User("admin", "enc",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        when(authenticationManager.authenticate(any())).thenReturn(auth);

        LoginRequest request = new LoginRequest("admin", "password");

        mockMvc.perform(post("/api/ecom/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_authenticated_shouldReturnNewToken() throws Exception {
        User principal = new User("ecom", "enc",
                List.of(new SimpleGrantedAuthority("ROLE_ECOM")));
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        when(jwtService.generateToken("ecom", "ECOM")).thenReturn("refreshed");
        when(jwtService.extractExpirationEpochMillis("refreshed")).thenReturn(9999999L);

        mockMvc.perform(post("/api/ecom/auth/refresh").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("refreshed"));
    }

    @Test
    void refresh_withoutAuthentication_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/ecom/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_shouldReturn204() throws Exception {
        mockMvc.perform(post("/api/ecom/auth/logout")
                        .header("Authorization", "Bearer some-token"))
                .andExpect(status().isNoContent());
    }
}
