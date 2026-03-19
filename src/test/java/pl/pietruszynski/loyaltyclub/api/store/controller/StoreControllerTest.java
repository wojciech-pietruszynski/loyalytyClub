package pl.pietruszynski.loyaltyclub.api.store.controller;

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
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionState;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionType;
import pl.pietruszynski.loyaltyclub.api.admin.security.AdminUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.admin.security.JwtService;
import pl.pietruszynski.loyaltyclub.api.ecom.security.EcomUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.store.dto.*;
import pl.pietruszynski.loyaltyclub.api.store.security.StoreUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.store.service.StoreTransactionService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = StoreController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class})
class StoreControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean StoreTransactionService storeTransactionService;
    @MockBean JwtService jwtService;
    @MockBean AdminUserDetailsService adminUserDetailsService;
    @MockBean StoreUserDetailsService storeUserDetailsService;
    @MockBean EcomUserDetailsService ecomUserDetailsService;

    @Test
    void info_shouldReturnStatusReady() throws Exception {
        mockMvc.perform(get("/api/store"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.name").value("store"));
    }

    @Test
    void registerSale_valid_shouldReturn200() throws Exception {
        StoreTransactionResponse resp = saleResponse();
        when(storeTransactionService.registerSale(eq("PL"), any())).thenReturn(resp);

        StoreSaleRequest request = new StoreSaleRequest(
                "C001",
                List.of(new StoreTransactionItemRequest("1", "EAN1", "Item1", "Cat",
                        new StoreItemPriceRequest(new BigDecimal("100.00"), "PLN"))),
                new BigDecimal("100.00"),
                "TXN-001",
                null
        );

        mockMvc.perform(post("/api/store/transactions/sale")
                        .header("X-CountryCode", "PL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(100));
    }

    @Test
    void registerReturn_valid_shouldReturn200() throws Exception {
        StoreTransactionResponse resp = returnResponse();
        when(storeTransactionService.registerReturn(eq("PL"), any())).thenReturn(resp);

        StoreReturnRequest request = new StoreReturnRequest(
                "C001",
                List.of(new StoreTransactionItemRequest("1", "EAN1", "Item1", "Cat",
                        new StoreItemPriceRequest(new BigDecimal("50.00"), "PLN"))),
                new BigDecimal("50.00"),
                "RET-001",
                "TXN-001",
                null
        );

        mockMvc.perform(post("/api/store/transactions/return")
                        .header("X-CountryCode", "PL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(-50));
    }

    @Test
    void getPointsBalance_shouldReturn200() throws Exception {
        StorePointsBalanceResponse balance = new StorePointsBalanceResponse(1L, "C001", 20, 100, 0);
        when(storeTransactionService.getPointsBalance("C001")).thenReturn(balance);

        mockMvc.perform(get("/api/store/customers/C001/points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availablePoints").value(100))
                .andExpect(jsonPath("$.pendingPoints").value(20));
    }

    private StoreTransactionResponse saleResponse() {
        return new StoreTransactionResponse(1L, 1L, "C001",
                TransactionType.SALE, TransactionState.PENDING,
                100, new BigDecimal("100.00"), BigDecimal.ONE,
                LocalDateTime.now(), LocalDateTime.now().plusDays(30), LocalDateTime.now().plusDays(365));
    }

    private StoreTransactionResponse returnResponse() {
        return new StoreTransactionResponse(2L, 1L, "C001",
                TransactionType.RETURN, TransactionState.AVAILABLE,
                -50, new BigDecimal("50.00"), BigDecimal.ONE,
                LocalDateTime.now(), LocalDateTime.now().plusDays(30), LocalDateTime.now().plusDays(365));
    }
}
