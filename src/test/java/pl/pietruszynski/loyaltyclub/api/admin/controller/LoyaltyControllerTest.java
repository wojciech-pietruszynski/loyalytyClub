package pl.pietruszynski.loyaltyclub.api.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import pl.pietruszynski.loyaltyclub.api.admin.dto.*;
import pl.pietruszynski.loyaltyclub.api.admin.model.*;
import pl.pietruszynski.loyaltyclub.api.admin.security.AdminUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.admin.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import pl.pietruszynski.loyaltyclub.api.admin.service.CustomerPrivacyService;
import pl.pietruszynski.loyaltyclub.api.admin.service.LoyaltyService;
import pl.pietruszynski.loyaltyclub.api.admin.service.LoyaltyTierService;
import pl.pietruszynski.loyaltyclub.api.admin.service.TechnicalUserService;
import pl.pietruszynski.loyaltyclub.api.ecom.security.EcomUserDetailsService;
import pl.pietruszynski.loyaltyclub.api.store.model.HierarchyPromotion;
import pl.pietruszynski.loyaltyclub.api.store.model.HierarchyPromotionType;
import pl.pietruszynski.loyaltyclub.api.store.model.StorePointsPromotion;
import pl.pietruszynski.loyaltyclub.api.store.security.StoreUserDetailsService;
import pl.pietruszynski.loyaltyclub.security.TokenRevocationService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = LoyaltyController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class})
class LoyaltyControllerTest {

    @BeforeEach
    void stubTier() {
        // Poziom wynika teraz z dorobku punktowego uczestnika, nie z biezacego salda.
        lenient().when(loyaltyTierService.resolveTierCode(any(Customer.class))).thenReturn("BRONZE");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean LoyaltyService loyaltyService;
    @MockitoBean CustomerPrivacyService customerPrivacyService;
    @MockitoBean LoyaltyTierService loyaltyTierService;
    @MockitoBean TechnicalUserService technicalUserService;
    @MockitoBean JwtService jwtService;
    @MockitoBean AdminUserDetailsService adminUserDetailsService;
    @MockitoBean StoreUserDetailsService storeUserDetailsService;
    @MockitoBean EcomUserDetailsService ecomUserDetailsService;
    @MockitoBean TokenRevocationService tokenRevocationService;

    // -----------------------------------------------------------------------
    // GET /api/admin/customers
    // -----------------------------------------------------------------------

    @Test
    void getAllCustomers_shouldReturnList() throws Exception {
        Customer c = customer(1L, "jan@pl.com", "PL");
        when(loyaltyService.getAllCustomers(null)).thenReturn(List.of(c));

        mockMvc.perform(get("/api/admin/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("jan@pl.com"));
    }

    // -----------------------------------------------------------------------
    // GET /api/admin/config/countries
    // -----------------------------------------------------------------------

    @Test
    void getAvailableCountries_shouldReturnCodes() throws Exception {
        when(loyaltyService.getAvailableCountryCodes(null)).thenReturn(List.of("PL", "DE"));

        mockMvc.perform(get("/api/admin/config/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("PL"));
    }

    // -----------------------------------------------------------------------
    // GET /api/admin/config/coupon-prefixes
    // -----------------------------------------------------------------------

    @Test
    void getCouponPrefixes_shouldReturnPrefixes() throws Exception {
        when(loyaltyService.getCouponPrefixes()).thenReturn(List.of("BONUS", "SALE"));

        mockMvc.perform(get("/api/admin/config/coupon-prefixes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("BONUS"));
    }

    // -----------------------------------------------------------------------
    // POST /api/admin/customers
    // -----------------------------------------------------------------------

    @Test
    void createCustomer_valid_shouldReturn200() throws Exception {
        Customer saved = customer(1L, "new@pl.com", "PL");
        when(loyaltyService.createCustomer(any(), isNull(), isNull())).thenReturn(saved);

        CustomerDto dto = CustomerDto.builder()
                .firstName("Jan").lastName("Kowalski")
                .email("new@pl.com").customerNumber("C001")
                .phoneNumber("123456789").country("PL").build();

        mockMvc.perform(post("/api/admin/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@pl.com"));
    }

    // -----------------------------------------------------------------------
    // GET /api/admin/customers/{id}
    // -----------------------------------------------------------------------

    @Test
    void getCustomer_existing_shouldReturn200() throws Exception {
        when(loyaltyService.getCustomerById(1L, null)).thenReturn(customer(1L, "a@pl.com", "PL"));

        mockMvc.perform(get("/api/admin/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // -----------------------------------------------------------------------
    // PUT /api/admin/customers/{id}
    // -----------------------------------------------------------------------

    @Test
    void updateCustomer_valid_shouldReturn200() throws Exception {
        Customer updated = customer(1L, "upd@pl.com", "PL");
        when(loyaltyService.updateCustomer(eq(1L), any(), isNull())).thenReturn(updated);

        CustomerDto dto = CustomerDto.builder()
                .firstName("Jan").lastName("Kowalski")
                .email("upd@pl.com").customerNumber("C001")
                .phoneNumber("123456789").country("PL").build();

        mockMvc.perform(put("/api/admin/customers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("upd@pl.com"));
    }

    // -----------------------------------------------------------------------
    // GET /api/admin/customers/{id}/transactions
    // -----------------------------------------------------------------------

    @Test
    void getCustomerTransactions_shouldReturn200() throws Exception {
        when(loyaltyService.getTransactionsForCustomer(1L, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/customers/1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getCustomerTransactions_withData_shouldMapTransactionDto() throws Exception {
        Customer c = customer(1L, "a@pl.com", "PL");
        Transaction tx = Transaction.builder()
                .customer(c).points(50)
                .amount(BigDecimal.TEN).pointsPerCurrency(BigDecimal.ONE)
                .description("Store sale: TXN-001").country("PL")
                .type(TransactionType.SALE).state(TransactionState.AVAILABLE)
                .purchaseTimestamp(LocalDateTime.now()).availableFrom(LocalDateTime.now().plusDays(30))
                .expiresAt(LocalDateTime.now().plusDays(365))
                .build();
        tx.setId(1L);

        when(loyaltyService.getTransactionsForCustomer(1L, null)).thenReturn(List.of(tx));

        mockMvc.perform(get("/api/admin/customers/1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].points").value(50))
                .andExpect(jsonPath("$[0].description").value("Store sale: TXN-001"));
    }

    // -----------------------------------------------------------------------
    // GET /api/admin/customers/{id}/coupons
    // -----------------------------------------------------------------------

    @Test
    void getCustomerCoupons_shouldReturn200() throws Exception {
        when(loyaltyService.getCouponsForCustomer(1L, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/customers/1/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // -----------------------------------------------------------------------
    // POST /api/admin/customers/{id}/add-points
    // -----------------------------------------------------------------------

    @Test
    void addPoints_withIdempotencyKey_shouldReturn200() throws Exception {
        PointsRequest req = new PointsRequest(50, "bonus");
        when(loyaltyService.addPoints(eq(1L), eq(50), eq("bonus"), eq("key-1")))
                .thenReturn(Transaction.builder().points(50).description("bonus").build());

        mockMvc.perform(post("/api/admin/customers/1/add-points")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(50));
    }

    /**
     * Korekta reczna nie ma numeru dokumentu kasowego, wiec bez klucza idempotencji
     * nic nie chronilo jej przed podwojnym wykonaniem. Klucz jest wymagany.
     */
    @Test
    void addPoints_withoutIdempotencyKey_shouldReturn400() throws Exception {
        PointsRequest req = new PointsRequest(50, "bonus");

        mockMvc.perform(post("/api/admin/customers/1/add-points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // GET /api/admin/coupons
    // -----------------------------------------------------------------------

    @Test
    void getIssuedCoupons_shouldReturn200() throws Exception {
        when(loyaltyService.getIssuedCoupons(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // -----------------------------------------------------------------------
    // GET /api/admin/coupon-templates
    // -----------------------------------------------------------------------

    @Test
    void getCouponTemplates_shouldReturn200() throws Exception {
        when(loyaltyService.getCouponTemplates(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/coupon-templates"))
                .andExpect(status().isOk());
    }

    // -----------------------------------------------------------------------
    // POST /api/admin/coupon-templates
    // -----------------------------------------------------------------------

    @Test
    void createCouponTemplate_valid_shouldReturn200() throws Exception {
        CouponTemplate template = CouponTemplate.builder()
                .couponValue(new BigDecimal("10.00"))
                .minimumPurchaseValue(BigDecimal.ZERO)
                .requiredPoints(100)
                .country("PL")
                .validityDays(30)
                .couponPrefix("BONUS")
                .build();
        template.setId(1L);

        when(loyaltyService.createCouponTemplate(any(), isNull())).thenReturn(template);

        CouponTemplateCreateRequest req = new CouponTemplateCreateRequest(
                new BigDecimal("10.00"), BigDecimal.ZERO, 100, "PL", 30, "BONUS");

        mockMvc.perform(post("/api/admin/coupon-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.country").value("PL"));
    }

    // -----------------------------------------------------------------------
    // POST /api/admin/coupons/issue
    // -----------------------------------------------------------------------

    @Test
    void issueCoupon_shouldReturn200() throws Exception {
        Customer c = customer(1L, "a@pl.com", "PL");
        CouponTemplate t = CouponTemplate.builder()
                .couponValue(new BigDecimal("10.00")).minimumPurchaseValue(BigDecimal.ZERO)
                .requiredPoints(100).country("PL").validityDays(30).couponPrefix("BONUS").build();
        t.setId(1L);

        CustomerCoupon coupon = CustomerCoupon.builder()
                .couponCode("BONUS00000000001").country("PL").customer(c).couponTemplate(t)
                .reason(CouponReason.POINTS_EXCHANGE).status(CouponStatus.ACTIVE)
                .issuedAt(LocalDateTime.now()).expiresAt(LocalDateTime.now().plusDays(30)).build();
        coupon.setId(1L);

        when(loyaltyService.issueCoupon(any(), isNull())).thenReturn(coupon);

        CouponIssueRequest req = new CouponIssueRequest(1L, 1L, CouponReason.POINTS_EXCHANGE);

        mockMvc.perform(post("/api/admin/coupons/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couponCode").value("BONUS00000000001"));
    }

    // -----------------------------------------------------------------------
    // GET /api/admin/store-promotions
    // -----------------------------------------------------------------------

    @Test
    void getStorePromotions_shouldReturn200() throws Exception {
        when(loyaltyService.getStorePromotions(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/store-promotions"))
                .andExpect(status().isOk());
    }

    // -----------------------------------------------------------------------
    // POST /api/admin/store-promotions
    // -----------------------------------------------------------------------

    @Test
    void createStorePromotion_valid_shouldReturn200() throws Exception {
        StorePointsPromotion promo = StorePointsPromotion.builder()
                .name("Summer").country("PL")
                .pointsPerCurrency(new BigDecimal("2.00"))
                .startsAt(LocalDateTime.now().plusDays(1))
                .enabled(true).build();
        promo.setId(1L);

        when(loyaltyService.createStorePromotion(any(), isNull())).thenReturn(promo);

        StorePromotionCreateRequest req = new StorePromotionCreateRequest(
                "Summer", "PL", new BigDecimal("2.00"),
                LocalDateTime.now().plusDays(1), null, true);

        mockMvc.perform(post("/api/admin/store-promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Summer"));
    }

    // -----------------------------------------------------------------------
    // PUT /api/admin/store-promotions/{id}
    // -----------------------------------------------------------------------

    @Test
    void updateStorePromotion_valid_shouldReturn200() throws Exception {
        StorePointsPromotion promo = StorePointsPromotion.builder()
                .name("Updated").country("PL")
                .pointsPerCurrency(new BigDecimal("3.00"))
                .startsAt(LocalDateTime.now().plusDays(1))
                .enabled(true).build();
        promo.setId(1L);

        when(loyaltyService.updateStorePromotion(eq(1L), any(), isNull())).thenReturn(promo);

        StorePromotionCreateRequest req = new StorePromotionCreateRequest(
                "Updated", "PL", new BigDecimal("3.00"),
                LocalDateTime.now().plusDays(1), null, true);

        mockMvc.perform(put("/api/admin/store-promotions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    // -----------------------------------------------------------------------
    // PATCH /api/admin/store-promotions/{id}/status
    // -----------------------------------------------------------------------

    @Test
    void setStorePromotionStatus_shouldReturn200() throws Exception {
        StorePointsPromotion promo = StorePointsPromotion.builder()
                .name("Promo").country("PL")
                .pointsPerCurrency(BigDecimal.ONE)
                .startsAt(LocalDateTime.now())
                .enabled(false).build();
        promo.setId(1L);

        when(loyaltyService.setStorePromotionEnabled(eq(1L), eq(false), isNull())).thenReturn(promo);

        StorePromotionStatusRequest req = new StorePromotionStatusRequest(false);

        mockMvc.perform(patch("/api/admin/store-promotions/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    // -----------------------------------------------------------------------
    // POST /api/admin/tools/import-customers
    // -----------------------------------------------------------------------

    @Test
    void importCustomersCsv_shouldReturn200WithCount() throws Exception {
        when(loyaltyService.importCustomersFromCsv(any(), isNull())).thenReturn(3);

        MockMultipartFile file = new MockMultipartFile("file", "customers.csv", "text/csv",
                "Jan,Kowalski,jan@pl.com,C001,111,PL".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/admin/tools/import-customers").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount").value(3));
    }

    // -----------------------------------------------------------------------
    // GET /api/admin/hierarchy-promotions
    // -----------------------------------------------------------------------

    @Test
    void getHierarchyPromotions_shouldReturnList() throws Exception {
        HierarchyPromotion promo = hierarchyPromotion(1L, HierarchyPromotionType.MULTIPLIER, new BigDecimal("4.00"));
        when(loyaltyService.getHierarchyPromotions(null)).thenReturn(List.of(promo));

        mockMvc.perform(get("/api/admin/hierarchy-promotions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("MULTIPLIER"))
                .andExpect(jsonPath("$[0].multiplier").value(4.00));
    }

    // -----------------------------------------------------------------------
    // POST /api/admin/hierarchy-promotions
    // -----------------------------------------------------------------------

    @Test
    void createHierarchyPromotion_valid_shouldReturn200() throws Exception {
        HierarchyPromotion saved = hierarchyPromotion(1L, HierarchyPromotionType.MULTIPLIER, new BigDecimal("2.00"));
        when(loyaltyService.createHierarchyPromotion(any(), isNull())).thenReturn(saved);

        HierarchyPromotionCreateRequest req = new HierarchyPromotionCreateRequest(
                "Summer Bonus", "PL", "42", "SHOES", null,
                HierarchyPromotionType.MULTIPLIER, new BigDecimal("2.00"),
                LocalDateTime.now(), null, true
        );

        mockMvc.perform(post("/api/admin/hierarchy-promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("MULTIPLIER"));
    }

    // -----------------------------------------------------------------------
    // PUT /api/admin/hierarchy-promotions/{id}
    // -----------------------------------------------------------------------

    @Test
    void updateHierarchyPromotion_valid_shouldReturn200() throws Exception {
        HierarchyPromotion updated = hierarchyPromotion(1L, HierarchyPromotionType.EXCLUSION, null);
        when(loyaltyService.updateHierarchyPromotion(eq(1L), any(), isNull())).thenReturn(updated);

        HierarchyPromotionCreateRequest req = new HierarchyPromotionCreateRequest(
                "Exclusion", "PL", "99", null, null,
                HierarchyPromotionType.EXCLUSION, null,
                LocalDateTime.now(), null, true
        );

        mockMvc.perform(put("/api/admin/hierarchy-promotions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("EXCLUSION"));
    }

    // -----------------------------------------------------------------------
    // PATCH /api/admin/hierarchy-promotions/{id}/status
    // -----------------------------------------------------------------------

    @Test
    void setHierarchyPromotionStatus_shouldReturn200() throws Exception {
        HierarchyPromotion promo = hierarchyPromotion(1L, HierarchyPromotionType.MULTIPLIER, new BigDecimal("2.00"));
        promo.setEnabled(false);
        when(loyaltyService.setHierarchyPromotionEnabled(eq(1L), eq(false), isNull())).thenReturn(promo);

        StorePromotionStatusRequest req = new StorePromotionStatusRequest(false);

        mockMvc.perform(patch("/api/admin/hierarchy-promotions/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private HierarchyPromotion hierarchyPromotion(Long id, HierarchyPromotionType type, BigDecimal multiplier) {
        HierarchyPromotion p = HierarchyPromotion.builder()
                .name("Test Hierarchy Promo").country("PL")
                .hierarchy("42").productClass("SHOES").subclass(null)
                .type(type).multiplier(multiplier)
                .startsAt(LocalDateTime.now()).enabled(true).build();
        p.setId(id);
        return p;
    }

    private Customer customer(Long id, String email, String country) {
        Customer c = Customer.builder()
                .firstName("Jan").lastName("Kowalski")
                .email(email).customerNumber("C" + id)
                .phoneNumber("123456789").country(country)
                .loyaltyPoints(0).build();
        c.setId(id);
        return c;
    }
}
