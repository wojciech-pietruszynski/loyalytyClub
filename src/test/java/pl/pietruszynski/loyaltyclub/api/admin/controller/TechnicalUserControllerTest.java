package pl.pietruszynski.loyaltyclub.api.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pl.pietruszynski.loyaltyclub.api.admin.dto.TechnicalUserCreateRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.TechnicalUserPasswordRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.TechnicalUserStatusRequest;
import pl.pietruszynski.loyaltyclub.api.admin.model.TechnicalUser;
import pl.pietruszynski.loyaltyclub.api.admin.security.AdminUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.admin.security.JwtService;
import pl.pietruszynski.loyaltyclub.api.admin.service.TechnicalUserService;
import pl.pietruszynski.loyaltyclub.api.ecom.security.EcomUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.store.security.StoreUserDetailsService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = TechnicalUserController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class})
class TechnicalUserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean TechnicalUserService technicalUserService;
    @MockBean JwtService jwtService;
    @MockBean AdminUserDetailsService adminUserDetailsService;
    @MockBean StoreUserDetailsService storeUserDetailsService;
    @MockBean EcomUserDetailsService ecomUserDetailsService;

    @Test
    void getTechnicalUsers_shouldReturnList() throws Exception {
        TechnicalUser user = techUser(1L, "techpl", "PL");
        when(technicalUserService.getAllTechnicalUsers()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/admin/technical-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("techpl"))
                .andExpect(jsonPath("$[0].country").value("PL"));
    }

    @Test
    void createTechnicalUser_valid_shouldReturn200() throws Exception {
        TechnicalUser user = techUser(1L, "techde", "DE");
        when(technicalUserService.createTechnicalUser(any())).thenReturn(user);

        TechnicalUserCreateRequest request = new TechnicalUserCreateRequest("techde", "secret", "DE", true);

        mockMvc.perform(post("/api/admin/technical-users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("techde"));
    }

    @Test
    void setTechnicalUserStatus_shouldReturn200() throws Exception {
        TechnicalUser user = techUser(1L, "tech", "PL");
        user.setEnabled(false);
        when(technicalUserService.setTechnicalUserEnabled(1L, false)).thenReturn(user);

        TechnicalUserStatusRequest req = new TechnicalUserStatusRequest(false);

        mockMvc.perform(patch("/api/admin/technical-users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void updateTechnicalUserPassword_shouldReturn200() throws Exception {
        TechnicalUser user = techUser(1L, "tech", "PL");
        when(technicalUserService.updateTechnicalUserPassword(1L, "newpass")).thenReturn(user);

        TechnicalUserPasswordRequest req = new TechnicalUserPasswordRequest("newpass");

        mockMvc.perform(patch("/api/admin/technical-users/1/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("tech"));
    }

    private TechnicalUser techUser(Long id, String username, String country) {
        TechnicalUser u = TechnicalUser.builder()
                .username(username)
                .password("enc")
                .passwordPreview("plain")
                .country(country)
                .enabled(true)
                .build();
        u.setId(id);
        return u;
    }
}
