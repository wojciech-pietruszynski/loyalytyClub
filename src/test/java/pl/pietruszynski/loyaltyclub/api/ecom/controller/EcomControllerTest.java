package pl.pietruszynski.loyaltyclub.api.ecom.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.pietruszynski.loyaltyclub.api.admin.dto.CustomerCouponDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.TransactionDto;
import pl.pietruszynski.loyaltyclub.api.admin.security.AdminUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.admin.security.JwtService;
import pl.pietruszynski.loyaltyclub.api.ecom.dto.EcomCustomerProfileDto;
import pl.pietruszynski.loyaltyclub.api.ecom.security.EcomUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.ecom.service.EcomService;
import pl.pietruszynski.loyaltyclub.api.store.dto.StorePointsBalanceResponse;
import pl.pietruszynski.loyaltyclub.api.store.security.StoreUserDetailsService;
import pl.pietruszynski.loyaltyclub.exception.ResourceNotFoundException;
import pl.pietruszynski.loyaltyclub.security.TokenRevocationService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = EcomController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class})
class EcomControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean EcomService ecomService;
    @MockitoBean JwtService jwtService;
    @MockitoBean AdminUserDetailsService adminUserDetailsService;
    @MockitoBean StoreUserDetailsService storeUserDetailsService;
    @MockitoBean EcomUserDetailsService ecomUserDetailsService;
    @MockitoBean TokenRevocationService tokenRevocationService;

    @Test
    void info_shouldReturnStatusReady() throws Exception {
        mockMvc.perform(get("/api/ecom"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.name").value("ecom"));
    }

    @Test
    void getPoints_shouldReturnBalanceSplitIntoStates() throws Exception {
        when(ecomService.getPointsBalance("C001"))
                .thenReturn(new StorePointsBalanceResponse(1L, "C001", 20, 100, 5));

        mockMvc.perform(get("/api/ecom/customers/C001/points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerNumber").value("C001"))
                .andExpect(jsonPath("$.availablePoints").value(100))
                .andExpect(jsonPath("$.pendingPoints").value(20))
                .andExpect(jsonPath("$.expiredPoints").value(5));
    }

    @Test
    void getProfile_shouldReturnLoyaltySummary() throws Exception {
        when(ecomService.getCustomerProfile("C001")).thenReturn(new EcomCustomerProfileDto(
                1L, "C001", "Jan", "Kowalski", "jan@example.com", "123456789",
                "PL", 100, 1500, "SILVER", "REF123", "ACTIVE"));

        mockMvc.perform(get("/api/ecom/customers/C001/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerNumber").value("C001"))
                .andExpect(jsonPath("$.lifetimePoints").value(1500))
                .andExpect(jsonPath("$.loyaltyTierCode").value("SILVER"))
                .andExpect(jsonPath("$.referralCode").value("REF123"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    /** Nieznany numer uczestnika to 404, a nie pusta odpowiedz. */
    @Test
    void getProfile_forUnknownCustomer_shouldReturn404() throws Exception {
        when(ecomService.getCustomerProfile("BRAK"))
                .thenThrow(new ResourceNotFoundException("Customer not found with number: BRAK"));

        mockMvc.perform(get("/api/ecom/customers/BRAK/profile"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTransactions_shouldReturnHistoryWithLifecycleFields() throws Exception {
        when(ecomService.getTransactions("C001")).thenReturn(List.of(TransactionDto.builder()
                .id(10L)
                .points(100)
                .description("Zakup")
                .amount(new BigDecimal("100.00"))
                .type("SALE")
                .state("AVAILABLE")
                .timestamp(LocalDateTime.now())
                .availableFrom(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(365))
                .build()));

        mockMvc.perform(get("/api/ecom/customers/C001/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].points").value(100))
                .andExpect(jsonPath("$[0].type").value("SALE"))
                .andExpect(jsonPath("$[0].state").value("AVAILABLE"))
                .andExpect(jsonPath("$[0].expiresAt").exists());
    }

    /**
     * Stronicowany odpowiednik zwraca wlasna koperte, a nie serializacje
     * {@code org.springframework.data.domain.Page} -- ksztalt odpowiedzi jest
     * czescia kontraktu, z ktorego generowane sa biblioteki klienckie.
     */
    @Test
    void getTransactionsPage_shouldReturnStablePageEnvelope() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        when(ecomService.getTransactions(eq("C001"), any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(TransactionDto.builder().id(10L).points(100).build()), pageable, 25));

        mockMvc.perform(get("/api/ecom/customers/C001/transactions/paged"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].points").value(100))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void getTransactionsPage_shouldPassRequestedPageAndSize() throws Exception {
        when(ecomService.getTransactions(eq("C001"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 0));

        mockMvc.perform(get("/api/ecom/customers/C001/transactions/paged")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(ecomService).getTransactions(eq("C001"),
                argThat(pageable -> pageable.getPageNumber() == 2 && pageable.getPageSize() == 5));
    }

    @Test
    void getCoupons_shouldReturnCouponTermsAlongWithStatus() throws Exception {
        when(ecomService.getCoupons("C001")).thenReturn(List.of(CustomerCouponDto.builder()
                .id(5L)
                .couponCode("KUPPL123")
                .customerId(1L)
                .customerName("Jan Kowalski")
                .country("PL")
                .couponValue(new BigDecimal("10.00"))
                .minimumPurchaseValue(new BigDecimal("50.00"))
                .requiredPoints(300)
                .validityDays(7)
                .couponPrefix("KUPPL")
                .reason("POINTS_EXCHANGE")
                .status("ACTIVE")
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build()));

        mockMvc.perform(get("/api/ecom/customers/C001/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].couponCode").value("KUPPL123"))
                .andExpect(jsonPath("$[0].couponValue").value(10.00))
                .andExpect(jsonPath("$[0].minimumPurchaseValue").value(50.00))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void getCouponsPage_shouldReturnStablePageEnvelope() throws Exception {
        when(ecomService.getCoupons(eq("C001"), any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(CustomerCouponDto.builder().id(5L).couponCode("KUPPL123").build()),
                        PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/ecom/customers/C001/coupons/paged"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].couponCode").value("KUPPL123"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }
}
