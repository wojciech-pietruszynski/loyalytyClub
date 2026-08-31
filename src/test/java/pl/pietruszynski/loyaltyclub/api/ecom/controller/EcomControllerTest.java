package pl.pietruszynski.loyaltyclub.api.ecom.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.pietruszynski.loyaltyclub.api.admin.security.AdminUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.admin.security.JwtService;
import pl.pietruszynski.loyaltyclub.api.ecom.security.EcomUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.ecom.service.EcomService;
import pl.pietruszynski.loyaltyclub.api.store.security.StoreUserDetailsService;
import pl.pietruszynski.loyaltyclub.security.TokenRevocationService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = EcomController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class})
class EcomControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean EcomService ecomService;
    @MockBean JwtService jwtService;
    @MockBean AdminUserDetailsService adminUserDetailsService;
    @MockBean StoreUserDetailsService storeUserDetailsService;
    @MockBean EcomUserDetailsService ecomUserDetailsService;
    @MockBean TokenRevocationService tokenRevocationService;

    @Test
    void info_shouldReturnStatusReady() throws Exception {
        mockMvc.perform(get("/api/ecom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.name").value("ecom"));
    }
}
