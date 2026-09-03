package pl.pietruszynski.loyaltyclub.api.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.pietruszynski.loyaltyclub.api.admin.audit.AuditService;
import pl.pietruszynski.loyaltyclub.api.admin.dto.AdminAuditLogDto;
import pl.pietruszynski.loyaltyclub.api.admin.security.AdminUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.admin.security.JwtService;
import pl.pietruszynski.loyaltyclub.api.ecom.security.EcomUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.store.security.StoreUserDetailsService;
import pl.pietruszynski.loyaltyclub.security.TokenRevocationService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kontrakt HTTP logu audytowego. Ograniczenie dostepu do roli ADMIN sprawdza
 * {@code SecurityAuthorizationIntegrationTest} na pelnym lancuchu filtrow.
 */
@WebMvcTest(value = AuditController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class})
class AuditControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean AuditService auditService;
    @MockitoBean JwtService jwtService;
    @MockitoBean AdminUserDetailsService adminUserDetailsService;
    @MockitoBean StoreUserDetailsService storeUserDetailsService;
    @MockitoBean EcomUserDetailsService ecomUserDetailsService;
    @MockitoBean TokenRevocationService tokenRevocationService;

    @Test
    void getLogs_shouldReturnLatestEntries() throws Exception {
        when(auditService.getLatestLogs()).thenReturn(List.of(new AdminAuditLogDto(
                1L, LocalDateTime.now(), "admin", "ROLE_ADMIN", "ADD_POINTS", "CUSTOMER", "42")));

        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[0].role").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$[0].action").value("ADD_POINTS"))
                .andExpect(jsonPath("$[0].resourceType").value("CUSTOMER"))
                .andExpect(jsonPath("$[0].resourceId").value("42"))
                .andExpect(jsonPath("$[0].timestamp").exists());
    }

    @Test
    void getLogs_withEmptyLog_shouldReturnEmptyArray() throws Exception {
        when(auditService.getLatestLogs()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
