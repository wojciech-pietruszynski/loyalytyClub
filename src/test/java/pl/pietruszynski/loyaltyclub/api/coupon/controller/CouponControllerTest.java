package pl.pietruszynski.loyaltyclub.api.coupon.controller;

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
import pl.pietruszynski.loyaltyclub.api.admin.security.AdminUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.admin.security.JwtService;
import pl.pietruszynski.loyaltyclub.api.coupon.dto.CouponRedeemRequest;
import pl.pietruszynski.loyaltyclub.api.coupon.dto.CouponRedeemResponse;
import pl.pietruszynski.loyaltyclub.api.coupon.dto.CouponValidationResponse;
import pl.pietruszynski.loyaltyclub.api.coupon.model.CouponValidationStatus;
import pl.pietruszynski.loyaltyclub.api.coupon.service.CouponService;
import pl.pietruszynski.loyaltyclub.api.ecom.security.EcomUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.store.security.StoreUserDetailsService;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = CouponController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class})
class CouponControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean CouponService couponService;
    @MockBean JwtService jwtService;
    @MockBean AdminUserDetailsService adminUserDetailsService;
    @MockBean StoreUserDetailsService storeUserDetailsService;
    @MockBean EcomUserDetailsService ecomUserDetailsService;

    @Test
    void redeemPoints_valid_shouldReturn200() throws Exception {
        CouponRedeemResponse response = new CouponRedeemResponse(
                "BONUS00000000001", "C001", "ACTIVE",
                LocalDateTime.now(), LocalDateTime.now().plusDays(30), null
        );
        when(couponService.redeemPointsForCoupon(any(), eq("IDEM-001"))).thenReturn(response);

        CouponRedeemRequest request = new CouponRedeemRequest("C001", 1L);

        mockMvc.perform(post("/api/coupon/redeem-points")
                        .header("Idempotency-Key", "IDEM-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couponCode").value("BONUS00000000001"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void validateCoupon_valid_shouldReturn200() throws Exception {
        CouponValidationResponse response = new CouponValidationResponse(
                CouponValidationStatus.VALID, "BONUS00000000001", "C001",
                "ACTIVE", LocalDateTime.now(), LocalDateTime.now().plusDays(30), null
        );
        when(couponService.validateCoupon("BONUS00000000001", "C001")).thenReturn(response);

        mockMvc.perform(get("/api/coupon/validate")
                        .param("couponCode", "BONUS00000000001")
                        .param("customerNumber", "C001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALID"));
    }

    @Test
    void validateCoupon_customerNotFound_shouldReturn200WithStatus() throws Exception {
        CouponValidationResponse response = new CouponValidationResponse(
                CouponValidationStatus.CUSTOMER_NOT_FOUND, "CODE1", "GHOST",
                null, null, null, null
        );
        when(couponService.validateCoupon("CODE1", "GHOST")).thenReturn(response);

        mockMvc.perform(get("/api/coupon/validate")
                        .param("couponCode", "CODE1")
                        .param("customerNumber", "GHOST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CUSTOMER_NOT_FOUND"));
    }
}
