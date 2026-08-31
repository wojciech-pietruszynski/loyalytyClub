package pl.pietruszynski.loyaltyclub.api.admin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import pl.pietruszynski.loyaltyclub.api.admin.dto.CouponIssueRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.CouponTemplateCreateRequest;
import pl.pietruszynski.loyaltyclub.api.admin.dto.StorePromotionCreateRequest;
import pl.pietruszynski.loyaltyclub.api.admin.model.*;
import pl.pietruszynski.loyaltyclub.api.admin.repository.*;
import pl.pietruszynski.loyaltyclub.api.store.model.StorePointsPromotion;
import pl.pietruszynski.loyaltyclub.api.store.repository.StorePointsPromotionRepository;
import pl.pietruszynski.loyaltyclub.exception.BusinessException;
import pl.pietruszynski.loyaltyclub.exception.ResourceNotFoundException;
import pl.pietruszynski.loyaltyclub.api.store.repository.HierarchyPromotionRepository;
import pl.pietruszynski.loyaltyclub.idempotency.IdempotencyService;
import pl.pietruszynski.loyaltyclub.idempotency.IdempotencyService.IdempotentResult;
import pl.pietruszynski.loyaltyclub.util.CouponCodeGenerator;
import pl.pietruszynski.loyaltyclub.util.ReferralCodeGenerator;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoyaltyServiceTest {

    @Mock private CouponCodeGenerator couponCodeGenerator;
    @Mock private ReferralCodeGenerator referralCodeGenerator;
    @Mock private CustomerRepository customerRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private CouponTemplateRepository couponTemplateRepository;
    @Mock private CouponPrefixRepository couponPrefixRepository;
    @Mock private CustomerCouponRepository customerCouponRepository;
    @Mock private StorePointsPromotionRepository storePointsPromotionRepository;
    @Mock private HierarchyPromotionRepository hierarchyPromotionRepository;
    @Mock private CustomerPointsService customerPointsService;
    @Mock private ReferralRewardService referralRewardService;
    @Mock private IdempotencyService idempotencyService;

    @InjectMocks
    private LoyaltyService loyaltyService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(loyaltyService, "availableCountryCodesConfig", "PL,DE");
        // Kod polecenia nadawany jest tylko przy tworzeniu klienta — pozostale testy tych stubow nie uzywaja.
        lenient().when(referralCodeGenerator.generate(10)).thenReturn("REFCODE12345");
        lenient().when(customerRepository.existsByReferralCode(anyString())).thenReturn(false);
        // Swiezy klucz idempotencji: warstwa przepuszcza operacje do wykonania.
        lenient().when(idempotencyService.execute(anyString(), anyString(), anyString(), any(), any()))
                .thenAnswer(inv -> {
                    java.util.function.Supplier<IdempotentResult<?>> action = inv.getArgument(3);
                    return action.get().value();
                });
    }

    // -----------------------------------------------------------------------
    // getAllCustomers
    // -----------------------------------------------------------------------

    @Test
    void getAllCustomers_noScope_shouldReturnAll() {
        List<Customer> all = List.of(customer("jan@pl.com", "PL"), customer("hans@de.com", "DE"));
        when(customerRepository.findAll()).thenReturn(all);

        List<Customer> result = loyaltyService.getAllCustomers();

        assertThat(result).hasSize(2);
    }

    @Test
    void getAllCustomers_withScope_shouldDelegateToScopedQuery() {
        List<Customer> pl = List.of(customer("jan@pl.com", "PL"));
        when(customerRepository.findAllByCountry("PL")).thenReturn(pl);

        List<Customer> result = loyaltyService.getAllCustomers("pl");

        assertThat(result).hasSize(1);
        verify(customerRepository).findAllByCountry("PL");
    }

    // -----------------------------------------------------------------------
    // getCustomerById
    // -----------------------------------------------------------------------

    @Test
    void getCustomerById_existingId_shouldReturnCustomer() {
        Customer c = customer("a@b.com", "PL");
        c.setId(1L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(c));

        Customer result = loyaltyService.getCustomerById(1L);

        assertThat(result.getEmail()).isEqualTo("a@b.com");
    }

    @Test
    void getCustomerById_missingId_shouldThrowResourceNotFoundException() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loyaltyService.getCustomerById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getCustomerById_wrongCountryScope_shouldThrowRuntimeException() {
        Customer c = customer("a@b.com", "DE");
        c.setId(2L);
        when(customerRepository.findById(2L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> loyaltyService.getCustomerById(2L, "PL"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access denied");
    }

    // -----------------------------------------------------------------------
    // createCustomer
    // -----------------------------------------------------------------------

    @Test
    void createCustomer_valid_shouldSaveAndReturn() {
        Customer c = customer("new@pl.com", "PL");
        when(customerRepository.existsByEmail(anyString())).thenReturn(false);
        when(customerRepository.existsByCustomerNumber(anyString())).thenReturn(false);
        when(customerRepository.save(any())).thenReturn(c);

        Customer result = loyaltyService.createCustomer(c);

        assertThat(result.getEmail()).isEqualTo("new@pl.com");
        verify(customerRepository).save(c);
    }

    @Test
    void createCustomer_duplicateEmail_shouldThrow() {
        Customer c = customer("dup@pl.com", "PL");
        when(customerRepository.existsByEmail("dup@pl.com")).thenReturn(true);

        assertThatThrownBy(() -> loyaltyService.createCustomer(c))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("email already exists");
    }

    @Test
    void createCustomer_disallowedCountry_shouldThrow() {
        Customer c = customer("a@fr.com", "FR");
        when(customerRepository.existsByEmail(anyString())).thenReturn(false);
        when(customerRepository.existsByCustomerNumber(anyString())).thenReturn(false);

        assertThatThrownBy(() -> loyaltyService.createCustomer(c))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Country code is not allowed");
    }

    @Test
    void createCustomer_missingFirstName_shouldThrow() {
        Customer c = Customer.builder()
                .firstName("")
                .lastName("Kowalski")
                .email("a@pl.com")
                .customerNumber("C1")
                .phoneNumber("111")
                .country("PL")
                .build();

        assertThatThrownBy(() -> loyaltyService.createCustomer(c))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("required");
    }

    @Test
    void createCustomer_countryNormalized_shouldSaveUppercased() {
        Customer c = customer("a@pl.com", "pl");
        when(customerRepository.existsByEmail(anyString())).thenReturn(false);
        when(customerRepository.existsByCustomerNumber(anyString())).thenReturn(false);
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Customer saved = loyaltyService.createCustomer(c);

        assertThat(saved.getCountry()).isEqualTo("PL");
    }

    // -----------------------------------------------------------------------
    // updateCustomer
    // -----------------------------------------------------------------------

    @Test
    void updateCustomer_valid_shouldUpdateFields() {
        Customer existing = customer("old@pl.com", "PL");
        existing.setId(1L);
        Customer updates = customer("new@pl.com", "PL");
        updates.setFirstName("NewName");

        when(customerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(customerRepository.existsByEmailAndIdNot("new@pl.com", 1L)).thenReturn(false);
        when(customerRepository.existsByCustomerNumberAndIdNot(anyString(), eq(1L))).thenReturn(false);
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Customer result = loyaltyService.updateCustomer(1L, updates);

        assertThat(result.getEmail()).isEqualTo("new@pl.com");
        assertThat(result.getFirstName()).isEqualTo("NewName");
    }

    @Test
    void updateCustomer_duplicateEmail_shouldThrow() {
        Customer existing = customer("old@pl.com", "PL");
        existing.setId(1L);
        Customer updates = customer("dup@pl.com", "PL");

        when(customerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(customerRepository.existsByEmailAndIdNot("dup@pl.com", 1L)).thenReturn(true);

        assertThatThrownBy(() -> loyaltyService.updateCustomer(1L, updates))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("email already exists");
    }

    // -----------------------------------------------------------------------
    // getAvailableCountryCodes
    // -----------------------------------------------------------------------

    @Test
    void getAvailableCountryCodes_noScope_shouldReturnAll() {
        List<String> codes = loyaltyService.getAvailableCountryCodes();
        assertThat(codes).containsExactlyInAnyOrder("PL", "DE");
    }

    @Test
    void getAvailableCountryCodes_withScope_shouldReturnOnlyThatCountry() {
        List<String> codes = loyaltyService.getAvailableCountryCodes("pl");
        assertThat(codes).containsExactly("PL");
    }

    // -----------------------------------------------------------------------
    // issueCoupon - POINTS_EXCHANGE
    // -----------------------------------------------------------------------

    @Test
    void issueCoupon_pointsExchange_enoughPoints_shouldDeductAndSave() {
        Customer customer = customer("a@pl.com", "PL");
        customer.setId(1L);
        customer.setLoyaltyPoints(200);

        CouponTemplate template = template("PL", 100);
        template.setId(1L);

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(customerCouponRepository.existsByCouponCode(anyString())).thenReturn(false);
        when(customerCouponRepository.save(any())).thenAnswer(inv -> {
            CustomerCoupon cc = inv.getArgument(0);
            cc.setId(99L);
            return cc;
        });

        CouponIssueRequest request = new CouponIssueRequest(1L, 1L, CouponReason.POINTS_EXCHANGE);
        CustomerCoupon result = loyaltyService.issueCoupon(request);

        verify(transactionRepository).save(argThat(t -> t.getPoints() == -100
                && t.getType() == TransactionType.POINTS_REDEMPTION));
        verify(customerPointsService).refresh(customer);
        assertThat(result.getStatus()).isEqualTo(CouponStatus.ACTIVE);
    }

    @Test
    void issueCoupon_pointsExchange_notEnoughPoints_shouldThrow() {
        Customer customer = customer("a@pl.com", "PL");
        customer.setId(1L);
        customer.setLoyaltyPoints(50);

        CouponTemplate template = template("PL", 100);
        template.setId(1L);

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

        CouponIssueRequest request = new CouponIssueRequest(1L, 1L, CouponReason.POINTS_EXCHANGE);

        assertThatThrownBy(() -> loyaltyService.issueCoupon(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Not enough points");
    }

    @Test
    void issueCoupon_countryMismatch_shouldThrow() {
        Customer customer = customer("a@pl.com", "PL");
        customer.setId(1L);
        customer.setLoyaltyPoints(200);

        CouponTemplate template = template("DE", 100);
        template.setId(1L);

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

        CouponIssueRequest request = new CouponIssueRequest(1L, 1L, CouponReason.POINTS_EXCHANGE);

        assertThatThrownBy(() -> loyaltyService.issueCoupon(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("country");
    }

    // -----------------------------------------------------------------------
    // addPoints
    // -----------------------------------------------------------------------

    @Test
    void addPoints_shouldRecordAdjustmentAndRecalculateBalances() {
        Customer customer = customer("a@pl.com", "PL");
        customer.setId(1L);
        customer.setLoyaltyPoints(100);

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            t.setId(7L);
            return t;
        });

        Transaction result = loyaltyService.addPoints(1L, 50, "bonus", "key-1");

        assertThat(result.getPoints()).isEqualTo(50);
        assertThat(result.getType()).isEqualTo(TransactionType.MANUAL_ADJUSTMENT);
        // Saldo powstaje z przeliczenia historii, nie z modyfikacji przyrostowej.
        verify(customerPointsService).refresh(customer);
    }

    /**
     * Korekta reczna nie ma numeru dokumentu kasowego, wiec przed podwojnym
     * wykonaniem chroni ja wylacznie klucz idempotencji.
     */
    @Test
    void addPoints_shouldRouteThroughIdempotencyLayer() {
        Customer customer = customer("a@pl.com", "PL");
        customer.setId(1L);

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            t.setId(7L);
            return t;
        });

        loyaltyService.addPoints(1L, 50, "bonus", "key-1");

        verify(idempotencyService).execute(eq("ADD_POINTS"), eq("key-1"), eq("1|50|bonus"), any(), any());
    }

    @Test
    void addPoints_inactiveCustomer_shouldThrow() {
        Customer customer = customer("a@pl.com", "PL");
        customer.setId(1L);
        customer.setStatus(CustomerStatus.INACTIVE);

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> loyaltyService.addPoints(1L, 50, "bonus", "key-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("cannot take part in point operations");
    }

    // -----------------------------------------------------------------------
    // createCouponTemplate
    // -----------------------------------------------------------------------

    @Test
    void createCouponTemplate_valid_shouldSave() {
        CouponTemplateCreateRequest req = new CouponTemplateCreateRequest(
                new BigDecimal("10.00"),
                new BigDecimal("50.00"),
                100,
                "PL",
                30,
                "BONUS"
        );

        when(couponPrefixRepository.existsByValue("BONUS")).thenReturn(true);
        when(couponTemplateRepository.existsByCouponValueAndMinimumPurchaseValueAndRequiredPointsAndCountryAndValidityDaysAndCouponPrefix(
                any(), any(), any(), any(), any(), any())).thenReturn(false);
        when(couponTemplateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CouponTemplate result = loyaltyService.createCouponTemplate(req);

        assertThat(result.getCountry()).isEqualTo("PL");
        assertThat(result.getCouponPrefix()).isEqualTo("BONUS");
    }

    @Test
    void createCouponTemplate_invalidPrefix_shouldThrow() {
        CouponTemplateCreateRequest req = new CouponTemplateCreateRequest(
                new BigDecimal("10.00"),
                new BigDecimal("50.00"),
                100,
                "PL",
                30,
                "UNKNOWN"
        );
        when(couponPrefixRepository.existsByValue("UNKNOWN")).thenReturn(false);

        assertThatThrownBy(() -> loyaltyService.createCouponTemplate(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("prefix is not allowed");
    }

    @Test
    void createCouponTemplate_duplicate_shouldThrow() {
        CouponTemplateCreateRequest req = new CouponTemplateCreateRequest(
                new BigDecimal("10.00"),
                new BigDecimal("50.00"),
                100,
                "PL",
                30,
                "BONUS"
        );
        when(couponPrefixRepository.existsByValue("BONUS")).thenReturn(true);
        when(couponTemplateRepository.existsByCouponValueAndMinimumPurchaseValueAndRequiredPointsAndCountryAndValidityDaysAndCouponPrefix(
                any(), any(), any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> loyaltyService.createCouponTemplate(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");
    }

    // -----------------------------------------------------------------------
    // createStorePromotion
    // -----------------------------------------------------------------------

    @Test
    void createStorePromotion_valid_shouldSave() {
        LocalDateTime starts = LocalDateTime.now().plusDays(1);
        StorePromotionCreateRequest req = new StorePromotionCreateRequest(
                "Summer Sale", "PL", new BigDecimal("2.00"), starts, null, true
        );
        when(storePointsPromotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StorePointsPromotion result = loyaltyService.createStorePromotion(req, null);

        assertThat(result.getName()).isEqualTo("Summer Sale");
        assertThat(result.getCountry()).isEqualTo("PL");
    }

    @Test
    void createStorePromotion_endBeforeStart_shouldThrow() {
        LocalDateTime starts = LocalDateTime.now().plusDays(5);
        LocalDateTime ends = LocalDateTime.now().plusDays(1);
        StorePromotionCreateRequest req = new StorePromotionCreateRequest(
                "Bad Dates", "PL", new BigDecimal("2.00"), starts, ends, true
        );

        assertThatThrownBy(() -> loyaltyService.createStorePromotion(req, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("end date must be after start date");
    }

    @Test
    void createStorePromotion_zeroPointsPerCurrency_shouldThrow() {
        LocalDateTime starts = LocalDateTime.now().plusDays(1);
        StorePromotionCreateRequest req = new StorePromotionCreateRequest(
                "Zero Promo", "PL", BigDecimal.ZERO, starts, null, true
        );

        assertThatThrownBy(() -> loyaltyService.createStorePromotion(req, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Points per currency must be greater than zero");
    }

    // -----------------------------------------------------------------------
    // getCouponPrefixes
    // -----------------------------------------------------------------------

    @Test
    void getCouponPrefixes_shouldReturnSortedValues() {
        CouponPrefix p1 = new CouponPrefix();
        p1.setValue("BONUS");
        CouponPrefix p2 = new CouponPrefix();
        p2.setValue("SALE");
        when(couponPrefixRepository.findAllByOrderByValueAsc()).thenReturn(List.of(p1, p2));

        List<String> prefixes = loyaltyService.getCouponPrefixes();

        assertThat(prefixes).containsExactly("BONUS", "SALE");
    }

    // -----------------------------------------------------------------------
    // ensureInScope
    // -----------------------------------------------------------------------

    @Test
    void getIssuedCoupons_withScope_shouldCallScopedRepo() {
        when(customerCouponRepository.findAllByCountryOrderByIssuedAtDesc("PL")).thenReturn(List.of());

        loyaltyService.getIssuedCoupons("pl");

        verify(customerCouponRepository).findAllByCountryOrderByIssuedAtDesc("PL");
    }

    @Test
    void getIssuedCoupons_noScope_shouldCallFindAll() {
        when(customerCouponRepository.findAllByOrderByIssuedAtDesc()).thenReturn(List.of());

        loyaltyService.getIssuedCoupons();

        verify(customerCouponRepository).findAllByOrderByIssuedAtDesc();
    }

    // -----------------------------------------------------------------------
    // getCouponsForCustomer
    // -----------------------------------------------------------------------

    @Test
    void getCouponsForCustomer_noScope_shouldReturnCoupons() {
        Customer c = customer("a@pl.com", "PL");
        c.setId(1L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(c));
        when(customerCouponRepository.findAllByCustomerIdOrderByIssuedAtDesc(1L)).thenReturn(List.of());

        List<CustomerCoupon> result = loyaltyService.getCouponsForCustomer(1L);

        verify(customerCouponRepository).findAllByCustomerIdOrderByIssuedAtDesc(1L);
        assertThat(result).isEmpty();
    }

    @Test
    void getCouponsForCustomer_withScope_customerOutOfScope_shouldThrow() {
        Customer c = customer("a@de.com", "DE");
        c.setId(2L);
        when(customerRepository.findById(2L)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> loyaltyService.getCouponsForCustomer(2L, "PL"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access denied");
    }

    // -----------------------------------------------------------------------
    // getTransactionsForCustomer
    // -----------------------------------------------------------------------

    @Test
    void getTransactionsForCustomer_noScope_shouldReturnTransactions() {
        Customer c = customer("a@pl.com", "PL");
        c.setId(1L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(c));
        when(transactionRepository.findAllByCustomerIdOrderByTimestampAsc(1L)).thenReturn(List.of());

        List<Transaction> result = loyaltyService.getTransactionsForCustomer(1L);

        verify(transactionRepository).findAllByCustomerIdOrderByTimestampAsc(1L);
        assertThat(result).isEmpty();
    }

    @Test
    void getTransactionsForCustomer_withScope_shouldEnforceScope() {
        Customer c = customer("a@pl.com", "PL");
        c.setId(1L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(c));
        when(transactionRepository.findAllByCustomerIdOrderByTimestampAsc(1L)).thenReturn(List.of());

        loyaltyService.getTransactionsForCustomer(1L, "PL");

        verify(transactionRepository).findAllByCustomerIdOrderByTimestampAsc(1L);
    }

    // -----------------------------------------------------------------------
    // getCouponTemplates
    // -----------------------------------------------------------------------

    @Test
    void getCouponTemplates_noScope_shouldReturnAll() {
        when(couponTemplateRepository.findAll()).thenReturn(List.of());

        loyaltyService.getCouponTemplates();

        verify(couponTemplateRepository).findAll();
    }

    @Test
    void getCouponTemplates_withScope_shouldCallScopedRepo() {
        when(couponTemplateRepository.findAllByCountry("PL")).thenReturn(List.of());

        loyaltyService.getCouponTemplates("pl");

        verify(couponTemplateRepository).findAllByCountry("PL");
    }

    // -----------------------------------------------------------------------
    // getStorePromotions
    // -----------------------------------------------------------------------

    @Test
    void getStorePromotions_noScope_shouldReturnAll() {
        when(storePointsPromotionRepository.findAllByOrderByStartsAtDesc()).thenReturn(List.of());

        loyaltyService.getStorePromotions(null);

        verify(storePointsPromotionRepository).findAllByOrderByStartsAtDesc();
    }

    @Test
    void getStorePromotions_withScope_shouldReturnScopedList() {
        when(storePointsPromotionRepository.findAllByCountryOrderByStartsAtDesc("PL")).thenReturn(List.of());

        loyaltyService.getStorePromotions("pl");

        verify(storePointsPromotionRepository).findAllByCountryOrderByStartsAtDesc("PL");
    }

    // -----------------------------------------------------------------------
    // updateStorePromotion
    // -----------------------------------------------------------------------

    @Test
    void updateStorePromotion_valid_shouldUpdateFields() {
        StorePointsPromotion existing = StorePointsPromotion.builder()
                .name("Old")
                .country("PL")
                .pointsPerCurrency(new BigDecimal("1.00"))
                .startsAt(LocalDateTime.now())
                .enabled(true)
                .build();
        existing.setId(1L);

        LocalDateTime newStart = LocalDateTime.now().plusDays(1);
        StorePromotionCreateRequest req = new StorePromotionCreateRequest(
                "New Name", "PL", new BigDecimal("3.00"), newStart, null, true
        );

        when(storePointsPromotionRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(storePointsPromotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StorePointsPromotion result = loyaltyService.updateStorePromotion(1L, req, null);

        assertThat(result.getName()).isEqualTo("New Name");
        assertThat(result.getPointsPerCurrency()).isEqualByComparingTo("3.00");
    }

    @Test
    void updateStorePromotion_notFound_shouldThrow() {
        LocalDateTime newStart = LocalDateTime.now().plusDays(1);
        StorePromotionCreateRequest req = new StorePromotionCreateRequest(
                "Promo", "PL", new BigDecimal("1.00"), newStart, null, true
        );
        when(storePointsPromotionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loyaltyService.updateStorePromotion(99L, req, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStorePromotion_outOfScope_shouldThrow() {
        StorePointsPromotion existing = StorePointsPromotion.builder()
                .name("Old")
                .country("DE")
                .pointsPerCurrency(new BigDecimal("1.00"))
                .startsAt(LocalDateTime.now())
                .enabled(true)
                .build();
        existing.setId(2L);

        LocalDateTime start = LocalDateTime.now().plusDays(1);
        StorePromotionCreateRequest req = new StorePromotionCreateRequest(
                "New", "DE", new BigDecimal("1.00"), start, null, true
        );
        when(storePointsPromotionRepository.findById(2L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> loyaltyService.updateStorePromotion(2L, req, "PL"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access denied");
    }

    // -----------------------------------------------------------------------
    // setStorePromotionEnabled
    // -----------------------------------------------------------------------

    @Test
    void setStorePromotionEnabled_shouldToggleEnabled() {
        StorePointsPromotion promo = StorePointsPromotion.builder()
                .name("Promo")
                .country("PL")
                .pointsPerCurrency(new BigDecimal("1.00"))
                .startsAt(LocalDateTime.now())
                .enabled(true)
                .build();
        promo.setId(1L);

        when(storePointsPromotionRepository.findById(1L)).thenReturn(Optional.of(promo));
        when(storePointsPromotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StorePointsPromotion result = loyaltyService.setStorePromotionEnabled(1L, false, null);

        assertThat(result.isEnabled()).isFalse();
    }

    @Test
    void setStorePromotionEnabled_notFound_shouldThrow() {
        when(storePointsPromotionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loyaltyService.setStorePromotionEnabled(99L, true, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void setStorePromotionEnabled_outOfScope_shouldThrow() {
        StorePointsPromotion promo = StorePointsPromotion.builder()
                .name("Promo")
                .country("DE")
                .pointsPerCurrency(new BigDecimal("1.00"))
                .startsAt(LocalDateTime.now())
                .enabled(true)
                .build();
        promo.setId(3L);

        when(storePointsPromotionRepository.findById(3L)).thenReturn(Optional.of(promo));

        assertThatThrownBy(() -> loyaltyService.setStorePromotionEnabled(3L, false, "PL"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Access denied");
    }

    // -----------------------------------------------------------------------
    // createCustomer — duplicate customer number
    // -----------------------------------------------------------------------

    @Test
    void createCustomer_duplicateCustomerNumber_shouldThrow() {
        Customer c = customer("a@pl.com", "PL");
        when(customerRepository.existsByEmail(anyString())).thenReturn(false);
        when(customerRepository.existsByCustomerNumber(anyString())).thenReturn(true);

        assertThatThrownBy(() -> loyaltyService.createCustomer(c))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("customer number already exists");
    }

    // -----------------------------------------------------------------------
    // updateCustomer — duplicate customer number
    // -----------------------------------------------------------------------

    @Test
    void updateCustomer_duplicateCustomerNumber_shouldThrow() {
        Customer existing = customer("old@pl.com", "PL");
        existing.setId(1L);
        Customer updates = customer("new@pl.com", "PL");
        updates.setCustomerNumber("TAKEN");

        when(customerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(customerRepository.existsByEmailAndIdNot(anyString(), eq(1L))).thenReturn(false);
        when(customerRepository.existsByCustomerNumberAndIdNot("TAKEN", 1L)).thenReturn(true);

        assertThatThrownBy(() -> loyaltyService.updateCustomer(1L, updates))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("customer number already exists");
    }

    // -----------------------------------------------------------------------
    // validateCouponIssueRequest — null fields
    // -----------------------------------------------------------------------

    @Test
    void issueCoupon_nullCustomerId_shouldThrow() {
        CouponIssueRequest request = new CouponIssueRequest(null, 1L, CouponReason.POINTS_EXCHANGE);

        assertThatThrownBy(() -> loyaltyService.issueCoupon(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("required");
    }

    @Test
    void issueCoupon_nullReason_shouldThrow() {
        CouponIssueRequest request = new CouponIssueRequest(1L, 1L, null);

        assertThatThrownBy(() -> loyaltyService.issueCoupon(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("required");
    }

    // -----------------------------------------------------------------------
    // issueCoupon MANUAL reason (no points deducted)
    // -----------------------------------------------------------------------

    @Test
    void issueCoupon_complaintReason_shouldNotDeductPoints() {
        Customer customer = customer("a@pl.com", "PL");
        customer.setId(1L);
        customer.setLoyaltyPoints(50);

        CouponTemplate template = template("PL", 100);
        template.setId(1L);

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(customerCouponRepository.existsByCouponCode(anyString())).thenReturn(false);
        when(customerCouponRepository.save(any())).thenAnswer(inv -> {
            CustomerCoupon cc = inv.getArgument(0);
            cc.setId(1L);
            return cc;
        });

        CouponIssueRequest request = new CouponIssueRequest(1L, 1L, CouponReason.COMPLAINT);
        loyaltyService.issueCoupon(request);

        // points should NOT be deducted for COMPLAINT reason
        assertThat(customer.getLoyaltyPoints()).isEqualTo(50);
        verify(transactionRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // generateUniqueCouponCode — exhaustion after 100 attempts
    // -----------------------------------------------------------------------

    @Test
    void issueCoupon_couponCodeExhausted_shouldThrow() {
        Customer customer = customer("a@pl.com", "PL");
        customer.setId(1L);
        customer.setLoyaltyPoints(200);

        CouponTemplate template = template("PL", 100);
        template.setId(1L);

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
        // every generated code is already taken
        when(customerCouponRepository.existsByCouponCode(anyString())).thenReturn(true);

        CouponIssueRequest request = new CouponIssueRequest(1L, 1L, CouponReason.POINTS_EXCHANGE);

        assertThatThrownBy(() -> loyaltyService.issueCoupon(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cannot generate unique coupon code");
    }

    // -----------------------------------------------------------------------
    // importCustomersFromCsv
    // -----------------------------------------------------------------------

    @Test
    void importCustomersFromCsv_commaSeparated_shouldImportRows() {
        String csv = "Jan,Kowalski,jan@pl.com,C001,123456789,PL\n"
                   + "Anna,Nowak,anna@pl.com,C002,987654321,PL\n";
        MockMultipartFile file = csvFile(csv);

        when(customerRepository.existsByEmail(anyString())).thenReturn(false);
        when(customerRepository.existsByCustomerNumber(anyString())).thenReturn(false);
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int count = loyaltyService.importCustomersFromCsv(file);

        assertThat(count).isEqualTo(2);
        verify(customerRepository, times(2)).save(any());
    }

    @Test
    void importCustomersFromCsv_semicolonSeparated_shouldImportRows() {
        String csv = "Jan;Kowalski;jan2@pl.com;C003;111222333;PL\n";
        MockMultipartFile file = csvFile(csv);

        when(customerRepository.existsByEmail(anyString())).thenReturn(false);
        when(customerRepository.existsByCustomerNumber(anyString())).thenReturn(false);
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int count = loyaltyService.importCustomersFromCsv(file);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void importCustomersFromCsv_withPolishHeader_shouldSkipHeader() {
        String csv = "Imie;Nazwisko;Email;NumerKlienta;NumerTelefonu;Kraj\n"
                   + "Jan;Kowalski;jan3@pl.com;C004;111000111;PL\n";
        MockMultipartFile file = csvFile(csv);

        when(customerRepository.existsByEmail(anyString())).thenReturn(false);
        when(customerRepository.existsByCustomerNumber(anyString())).thenReturn(false);
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int count = loyaltyService.importCustomersFromCsv(file);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void importCustomersFromCsv_emptyFile_shouldThrow() {
        MockMultipartFile file = csvFile("");

        assertThatThrownBy(() -> loyaltyService.importCustomersFromCsv(file))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void importCustomersFromCsv_wrongColumnCount_shouldThrow() {
        String csv = "Jan,Kowalski,jan@pl.com\n";
        MockMultipartFile file = csvFile(csv);

        assertThatThrownBy(() -> loyaltyService.importCustomersFromCsv(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid CSV format");
    }

    @Test
    void importCustomersFromCsv_rowWithError_shouldThrowWithLineInfo() {
        // First row has disallowed country FR
        String csv = "Jan,Kowalski,jan@pl.com,C001,123456789,FR\n";
        MockMultipartFile file = csvFile(csv);

        when(customerRepository.existsByEmail(anyString())).thenReturn(false);
        when(customerRepository.existsByCustomerNumber(anyString())).thenReturn(false);

        assertThatThrownBy(() -> loyaltyService.importCustomersFromCsv(file))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("CSV import error at line");
    }

    @Test
    void importCustomersFromCsv_nullFile_shouldThrow() {
        assertThatThrownBy(() -> loyaltyService.importCustomersFromCsv(null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("CSV file is empty");
    }

    @Test
    void importCustomersFromCsv_ioException_shouldThrow() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getInputStream()).thenThrow(new IOException("disk error"));

        assertThatThrownBy(() -> loyaltyService.importCustomersFromCsv(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot read CSV file");
    }

    // -----------------------------------------------------------------------
    // validateCouponTemplateCreateRequest — edge cases
    // -----------------------------------------------------------------------

    @Test
    void createCouponTemplate_zeroRequiredPoints_shouldThrow() {
        CouponTemplateCreateRequest req = new CouponTemplateCreateRequest(
                new BigDecimal("10.00"),
                new BigDecimal("50.00"),
                0,
                "PL",
                30,
                "BONUS"
        );

        assertThatThrownBy(() -> loyaltyService.createCouponTemplate(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("greater than zero");
    }

    // -----------------------------------------------------------------------
    // createStorePromotion — disabled by null enabled field
    // -----------------------------------------------------------------------

    @Test
    void createStorePromotion_nullEnabledDefaultsToTrue() {
        LocalDateTime starts = LocalDateTime.now().plusDays(1);
        StorePromotionCreateRequest req = new StorePromotionCreateRequest(
                "Promo", "PL", new BigDecimal("1.00"), starts, null, null
        );
        when(storePointsPromotionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StorePointsPromotion result = loyaltyService.createStorePromotion(req, null);

        assertThat(result.isEnabled()).isTrue();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "customers.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private Customer customer(String email, String country) {
        return Customer.builder()
                .firstName("Jan")
                .lastName("Kowalski")
                .email(email)
                .customerNumber("C" + email.hashCode())
                .phoneNumber("123456789")
                .country(country)
                .loyaltyPoints(0)
                .build();
    }

    private CouponTemplate template(String country, int requiredPoints) {
        return CouponTemplate.builder()
                .couponValue(new BigDecimal("10.00"))
                .minimumPurchaseValue(new BigDecimal("50.00"))
                .requiredPoints(requiredPoints)
                .country(country)
                .validityDays(30)
                .couponPrefix("BONUS")
                .build();
    }
}
