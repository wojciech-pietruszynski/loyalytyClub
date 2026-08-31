package pl.pietruszynski.loyaltyclub.api.coupon.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.pietruszynski.loyaltyclub.api.admin.model.*;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CouponTemplateRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CustomerCouponRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CustomerRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TransactionRepository;
import pl.pietruszynski.loyaltyclub.api.admin.service.CustomerPointsService;
import pl.pietruszynski.loyaltyclub.api.coupon.dto.CouponRedeemRequest;
import pl.pietruszynski.loyaltyclub.api.coupon.dto.CouponRedeemResponse;
import pl.pietruszynski.loyaltyclub.api.coupon.dto.CouponValidationResponse;
import pl.pietruszynski.loyaltyclub.api.coupon.model.CouponRedemptionRequest;
import pl.pietruszynski.loyaltyclub.api.coupon.model.CouponValidationStatus;
import pl.pietruszynski.loyaltyclub.api.coupon.repository.CouponRedemptionRequestRepository;
import pl.pietruszynski.loyaltyclub.exception.ResourceNotFoundException;
import pl.pietruszynski.loyaltyclub.util.CouponCodeGenerator;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock private CouponCodeGenerator couponCodeGenerator;
    @Mock private CustomerRepository customerRepository;
    @Mock private CouponTemplateRepository couponTemplateRepository;
    @Mock private CustomerCouponRepository customerCouponRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private CouponRedemptionRequestRepository couponRedemptionRequestRepository;
    @Mock private CustomerPointsService customerPointsService;

    @InjectMocks
    private CouponService couponService;

    // -----------------------------------------------------------------------
    // validateCoupon
    // -----------------------------------------------------------------------

    @Test
    void validateCoupon_valid_shouldReturnValidStatus() {
        Customer customer = customer("C001", "PL", 200);
        CustomerCoupon coupon = activeCoupon("BONUS00000000001", customer, LocalDateTime.now().plusDays(10));

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(customerCouponRepository.findByCouponCode("BONUS00000000001")).thenReturn(Optional.of(coupon));

        CouponValidationResponse response = couponService.validateCoupon("BONUS00000000001", "C001");

        assertThat(response.status()).isEqualTo(CouponValidationStatus.VALID);
        assertThat(response.couponCode()).isEqualTo("BONUS00000000001");
    }

    @Test
    void validateCoupon_customerNotFound_shouldReturnCustomerNotFoundStatus() {
        when(customerRepository.findByCustomerNumber("GHOST")).thenReturn(Optional.empty());

        CouponValidationResponse response = couponService.validateCoupon("CODE123", "GHOST");

        assertThat(response.status()).isEqualTo(CouponValidationStatus.CUSTOMER_NOT_FOUND);
    }

    @Test
    void validateCoupon_couponNotFound_shouldReturnCouponNotFoundStatus() {
        Customer customer = customer("C001", "PL", 0);
        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(customerCouponRepository.findByCouponCode("UNKNOWN")).thenReturn(Optional.empty());

        CouponValidationResponse response = couponService.validateCoupon("UNKNOWN", "C001");

        assertThat(response.status()).isEqualTo(CouponValidationStatus.COUPON_NOT_FOUND);
    }

    @Test
    void validateCoupon_belongsToAnotherCustomer_shouldReturnWrongOwnerStatus() {
        Customer customer = customer("C001", "PL", 0);
        customer.setId(1L);
        Customer otherCustomer = customer("C002", "PL", 0);
        otherCustomer.setId(2L);
        CustomerCoupon coupon = activeCoupon("BONUS00000000002", otherCustomer, LocalDateTime.now().plusDays(10));

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(customerCouponRepository.findByCouponCode("BONUS00000000002")).thenReturn(Optional.of(coupon));

        CouponValidationResponse response = couponService.validateCoupon("BONUS00000000002", "C001");

        assertThat(response.status()).isEqualTo(CouponValidationStatus.COUPON_BELONGS_TO_ANOTHER_ACCOUNT);
    }

    @Test
    void validateCoupon_alreadyUsed_shouldReturnUsedStatus() {
        Customer customer = customer("C001", "PL", 0);
        CustomerCoupon coupon = usedCoupon("BONUS00000000003", customer, LocalDateTime.now().plusDays(10));

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(customerCouponRepository.findByCouponCode("BONUS00000000003")).thenReturn(Optional.of(coupon));

        CouponValidationResponse response = couponService.validateCoupon("BONUS00000000003", "C001");

        assertThat(response.status()).isEqualTo(CouponValidationStatus.COUPON_ALREADY_USED);
    }

    @Test
    void validateCoupon_expired_shouldReturnExpiredStatusAndUpdateDb() {
        Customer customer = customer("C001", "PL", 0);
        CustomerCoupon coupon = activeCoupon("BONUS00000000004", customer, LocalDateTime.now().minusDays(1));

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(customerCouponRepository.findByCouponCode("BONUS00000000004")).thenReturn(Optional.of(coupon));
        when(customerCouponRepository.save(any())).thenReturn(coupon);

        CouponValidationResponse response = couponService.validateCoupon("BONUS00000000004", "C001");

        assertThat(response.status()).isEqualTo(CouponValidationStatus.COUPON_EXPIRED);
        verify(customerCouponRepository).save(coupon);
    }

    @Test
    void validateCoupon_blankCouponCode_shouldThrow() {
        assertThatThrownBy(() -> couponService.validateCoupon("", "C001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("couponCode is required");
    }

    @Test
    void validateCoupon_blankCustomerNumber_shouldThrow() {
        assertThatThrownBy(() -> couponService.validateCoupon("CODE", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("customerNumber is required");
    }

    @Test
    void validateCoupon_couponCodeNormalized_shouldBeUppercased() {
        Customer customer = customer("C001", "PL", 0);
        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(customerCouponRepository.findByCouponCode("BONUS00000000005")).thenReturn(Optional.empty());

        CouponValidationResponse response = couponService.validateCoupon("bonus00000000005", "C001");

        verify(customerCouponRepository).findByCouponCode("BONUS00000000005");
        assertThat(response.status()).isEqualTo(CouponValidationStatus.COUPON_NOT_FOUND);
    }

    // -----------------------------------------------------------------------
    // redeemPointsForCoupon
    // -----------------------------------------------------------------------

    @Test
    void redeemPointsForCoupon_valid_shouldDeductPointsAndReturnCoupon() {
        Customer customer = customer("C001", "PL", 200);
        CouponTemplate template = couponTemplate("PL", 100);
        CustomerCoupon coupon = activeCoupon("BONUS00000000010", customer, LocalDateTime.now().plusDays(30));

        CouponRedeemRequest request = new CouponRedeemRequest("C001", 1L);

        when(couponRedemptionRequestRepository.findByIdempotencyKey("IDEM-001")).thenReturn(Optional.empty());
        when(couponRedemptionRequestRepository.saveAndFlush(any())).thenAnswer(inv -> {
            CouponRedemptionRequest r = inv.getArgument(0);
            r.setId(1L);
            return r;
        });
        when(customerRepository.findByCustomerNumberForUpdate("C001")).thenReturn(Optional.of(customer));
        when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(transactionRepository.save(any())).thenReturn(null);
        when(customerCouponRepository.save(any())).thenAnswer(inv -> {
            CustomerCoupon cc = inv.getArgument(0);
            cc.setId(10L);
            return cc;
        });
        when(couponRedemptionRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CouponRedeemResponse response = couponService.redeemPointsForCoupon(request, "IDEM-001");

        assertThat(response.status()).isEqualTo(CouponStatus.ACTIVE.name());
        // Wymiana punktow nie jest korekta reczna i nie moze obnizac dorobku uczestnika.
        verify(transactionRepository).save(argThat(t -> t.getPoints() == -100
                && t.getType() == TransactionType.POINTS_REDEMPTION));
        verify(customerPointsService).refresh(customer);
    }

    @Test
    void redeemPointsForCoupon_notEnoughPoints_shouldThrow() {
        Customer customer = customer("C001", "PL", 50);
        CouponTemplate template = couponTemplate("PL", 100);

        CouponRedeemRequest request = new CouponRedeemRequest("C001", 1L);

        when(couponRedemptionRequestRepository.findByIdempotencyKey("IDEM-002")).thenReturn(Optional.empty());
        when(couponRedemptionRequestRepository.saveAndFlush(any())).thenAnswer(inv -> {
            CouponRedemptionRequest r = inv.getArgument(0);
            r.setId(2L);
            return r;
        });
        when(customerRepository.findByCustomerNumberForUpdate("C001")).thenReturn(Optional.of(customer));
        when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> couponService.redeemPointsForCoupon(request, "IDEM-002"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not enough points");
    }

    @Test
    void redeemPointsForCoupon_customerNotFound_shouldThrow() {
        CouponRedeemRequest request = new CouponRedeemRequest("GHOST", 1L);

        when(couponRedemptionRequestRepository.findByIdempotencyKey("IDEM-003")).thenReturn(Optional.empty());
        when(couponRedemptionRequestRepository.saveAndFlush(any())).thenAnswer(inv -> {
            CouponRedemptionRequest r = inv.getArgument(0);
            r.setId(3L);
            return r;
        });
        when(customerRepository.findByCustomerNumberForUpdate("GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.redeemPointsForCoupon(request, "IDEM-003"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void redeemPointsForCoupon_existingIdempotencyKey_shouldReturnCachedResult() {
        Customer customer = customer("C001", "PL", 200);
        CustomerCoupon coupon = activeCoupon("BONUS00000000020", customer, LocalDateTime.now().plusDays(30));

        CouponRedemptionRequest existing = CouponRedemptionRequest.builder()
                .idempotencyKey("IDEM-004")
                .customerNumber("C001")
                .couponTemplateId(1L)
                .customerCoupon(coupon)
                .build();

        CouponRedeemRequest request = new CouponRedeemRequest("C001", 1L);

        when(couponRedemptionRequestRepository.findByIdempotencyKey("IDEM-004")).thenReturn(Optional.of(existing));

        CouponRedeemResponse response = couponService.redeemPointsForCoupon(request, "IDEM-004");

        assertThat(response.couponCode()).isEqualTo("BONUS00000000020");
        // No actual redemption should have taken place
        verify(customerRepository, never()).save(any());
    }

    @Test
    void redeemPointsForCoupon_countryMismatch_shouldThrow() {
        Customer customer = customer("C001", "PL", 200);
        CouponTemplate template = couponTemplate("DE", 100);

        CouponRedeemRequest request = new CouponRedeemRequest("C001", 1L);

        when(couponRedemptionRequestRepository.findByIdempotencyKey("IDEM-005")).thenReturn(Optional.empty());
        when(couponRedemptionRequestRepository.saveAndFlush(any())).thenAnswer(inv -> {
            CouponRedemptionRequest r = inv.getArgument(0);
            r.setId(5L);
            return r;
        });
        when(customerRepository.findByCustomerNumberForUpdate("C001")).thenReturn(Optional.of(customer));
        when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> couponService.redeemPointsForCoupon(request, "IDEM-005"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("country");
    }

    @Test
    void redeemPointsForCoupon_blankIdempotencyKey_shouldThrow() {
        CouponRedeemRequest request = new CouponRedeemRequest("C001", 1L);

        assertThatThrownBy(() -> couponService.redeemPointsForCoupon(request, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency-Key is required");
    }

    @Test
    void redeemPointsForCoupon_tooLongIdempotencyKey_shouldThrow() {
        CouponRedeemRequest request = new CouponRedeemRequest("C001", 1L);
        String longKey = "X".repeat(101);

        assertThatThrownBy(() -> couponService.redeemPointsForCoupon(request, longKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 100 characters");
    }

    // -----------------------------------------------------------------------
    // handleExistingIdempotencyRequest — in-progress case
    // -----------------------------------------------------------------------

    @Test
    void redeemPointsForCoupon_idempotencyKeyInProgress_shouldThrowIllegalState() {
        // existing request found but coupon not yet assigned (in-progress)
        CouponRedemptionRequest inProgress = CouponRedemptionRequest.builder()
                .idempotencyKey("IDEM-IN-PROG")
                .customerNumber("C001")
                .couponTemplateId(1L)
                .customerCoupon(null) // still in progress
                .build();

        CouponRedeemRequest request = new CouponRedeemRequest("C001", 1L);

        when(couponRedemptionRequestRepository.findByIdempotencyKey("IDEM-IN-PROG"))
                .thenReturn(Optional.of(inProgress));

        assertThatThrownBy(() -> couponService.redeemPointsForCoupon(request, "IDEM-IN-PROG"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("in progress");
    }

    @Test
    void redeemPointsForCoupon_existingKeyWithDifferentPayload_shouldThrow() {
        Customer customer = customer("C001", "PL", 200);
        CustomerCoupon coupon = activeCoupon("BONUS00000000099", customer, LocalDateTime.now().plusDays(30));

        CouponRedemptionRequest existing = CouponRedemptionRequest.builder()
                .idempotencyKey("IDEM-DIFF")
                .customerNumber("C001")
                .couponTemplateId(2L) // different template id
                .customerCoupon(coupon)
                .build();

        // request uses couponTemplateId=1 but existing was for template 2
        CouponRedeemRequest request = new CouponRedeemRequest("C001", 1L);

        when(couponRedemptionRequestRepository.findByIdempotencyKey("IDEM-DIFF"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> couponService.redeemPointsForCoupon(request, "IDEM-DIFF"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different payload");
    }

    // -----------------------------------------------------------------------
    // redeemPointsForCoupon — couponTemplate not found
    // -----------------------------------------------------------------------

    @Test
    void redeemPointsForCoupon_templateNotFound_shouldThrow() {
        Customer customer = customer("C001", "PL", 200);
        CouponRedeemRequest request = new CouponRedeemRequest("C001", 99L);

        when(couponRedemptionRequestRepository.findByIdempotencyKey("IDEM-T-MISS")).thenReturn(Optional.empty());
        when(couponRedemptionRequestRepository.saveAndFlush(any())).thenAnswer(inv -> {
            CouponRedemptionRequest r = inv.getArgument(0);
            r.setId(10L);
            return r;
        });
        when(customerRepository.findByCustomerNumberForUpdate("C001")).thenReturn(Optional.of(customer));
        when(couponTemplateRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.redeemPointsForCoupon(request, "IDEM-T-MISS"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Coupon template not found");
    }

    // -----------------------------------------------------------------------
    // validateCoupon — already expired status (CouponStatus.EXPIRED)
    // -----------------------------------------------------------------------

    @Test
    void validateCoupon_statusAlreadyExpired_shouldReturnExpiredStatus() {
        Customer customer = customer("C001", "PL", 0);
        CustomerCoupon coupon = activeCoupon("BONUS00000000006", customer, LocalDateTime.now().plusDays(10));
        // status already EXPIRED in db
        coupon.setStatus(CouponStatus.EXPIRED);

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(customerCouponRepository.findByCouponCode("BONUS00000000006")).thenReturn(Optional.of(coupon));

        CouponValidationResponse response = couponService.validateCoupon("BONUS00000000006", "C001");

        assertThat(response.status()).isEqualTo(CouponValidationStatus.COUPON_EXPIRED);
    }

    // -----------------------------------------------------------------------
    // redeemPointsForCoupon — race condition (idempotency key collision)
    // -----------------------------------------------------------------------

    @Test
    void redeemPointsForCoupon_idempotencyKeyRaceCondition_shouldReturnCachedResult() {
        // saveAndFlush throws DataIntegrityViolationException with "idempotency_key" in message
        Customer customer = customer("C001", "PL", 200);
        CustomerCoupon coupon = activeCoupon("BONUS00000000030", customer, LocalDateTime.now().plusDays(30));

        CouponRedemptionRequest racedRequest = CouponRedemptionRequest.builder()
                .idempotencyKey("IDEM-RACE")
                .customerNumber("C001")
                .couponTemplateId(1L)
                .customerCoupon(coupon)
                .build();

        CouponRedeemRequest request = new CouponRedeemRequest("C001", 1L);

        DataIntegrityViolationException collision = new DataIntegrityViolationException("idempotency_key constraint");

        when(couponRedemptionRequestRepository.findByIdempotencyKey("IDEM-RACE"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(racedRequest));
        when(couponRedemptionRequestRepository.saveAndFlush(any())).thenThrow(collision);

        CouponRedeemResponse response = couponService.redeemPointsForCoupon(request, "IDEM-RACE");

        assertThat(response.couponCode()).isEqualTo("BONUS00000000030");
        verify(customerRepository, never()).save(any());
    }

    @Test
    void redeemPointsForCoupon_couponCodeCollisionRetrySucceeds_shouldReturnCoupon() {
        // First save of CustomerCoupon throws DataIntegrityViolationException with "coupon_code",
        // second save succeeds.
        Customer customer = customer("C001", "PL", 200);
        CouponTemplate template = couponTemplate("PL", 100);
        CustomerCoupon coupon = activeCoupon("BONUS00000000040", customer, LocalDateTime.now().plusDays(30));

        CouponRedeemRequest request = new CouponRedeemRequest("C001", 1L);

        when(couponRedemptionRequestRepository.findByIdempotencyKey("IDEM-RETRY")).thenReturn(Optional.empty());
        when(couponRedemptionRequestRepository.saveAndFlush(any())).thenAnswer(inv -> {
            CouponRedemptionRequest r = inv.getArgument(0);
            r.setId(20L);
            return r;
        });
        when(customerRepository.findByCustomerNumberForUpdate("C001")).thenReturn(Optional.of(customer));
        when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(transactionRepository.save(any())).thenReturn(null);

        DataIntegrityViolationException couponCodeCollision = new DataIntegrityViolationException("coupon_code constraint");
        when(customerCouponRepository.save(any()))
                .thenThrow(couponCodeCollision)  // first attempt fails
                .thenAnswer(inv -> {             // second attempt succeeds
                    CustomerCoupon cc = inv.getArgument(0);
                    cc.setId(40L);
                    return cc;
                });
        when(couponRedemptionRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CouponRedeemResponse response = couponService.redeemPointsForCoupon(request, "IDEM-RETRY");

        assertThat(response.status()).isEqualTo(CouponStatus.ACTIVE.name());
        verify(customerCouponRepository, times(2)).save(any());
    }

    @Test
    void redeemPointsForCoupon_nonCouponCodeConstraintViolation_shouldRethrow() {
        // DataIntegrityViolationException NOT about coupon_code → rethrow
        Customer customer = customer("C001", "PL", 200);
        CouponTemplate template = couponTemplate("PL", 100);

        CouponRedeemRequest request = new CouponRedeemRequest("C001", 1L);

        when(couponRedemptionRequestRepository.findByIdempotencyKey("IDEM-RETHROW")).thenReturn(Optional.empty());
        when(couponRedemptionRequestRepository.saveAndFlush(any())).thenAnswer(inv -> {
            CouponRedemptionRequest r = inv.getArgument(0);
            r.setId(30L);
            return r;
        });
        when(customerRepository.findByCustomerNumberForUpdate("C001")).thenReturn(Optional.of(customer));
        when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(template));
        when(transactionRepository.save(any())).thenReturn(null);

        DataIntegrityViolationException otherConstraint = new DataIntegrityViolationException("some_other_constraint violation");
        when(customerCouponRepository.save(any())).thenThrow(otherConstraint);

        assertThatThrownBy(() -> couponService.redeemPointsForCoupon(request, "IDEM-RETHROW"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Customer customer(String number, String country, int points) {
        Customer c = Customer.builder()
                .firstName("Jan")
                .lastName("Kowalski")
                .email(number + "@test.com")
                .customerNumber(number)
                .phoneNumber("111222333")
                .country(country)
                .loyaltyPoints(points)
                .build();
        c.setId((long) number.hashCode());
        return c;
    }

    private CouponTemplate couponTemplate(String country, int requiredPoints) {
        CouponTemplate t = CouponTemplate.builder()
                .couponValue(new BigDecimal("10.00"))
                .minimumPurchaseValue(new BigDecimal("50.00"))
                .requiredPoints(requiredPoints)
                .country(country)
                .validityDays(30)
                .couponPrefix("BONUS")
                .build();
        t.setId(1L);
        return t;
    }

    private CustomerCoupon activeCoupon(String code, Customer customer, LocalDateTime expiresAt) {
        CustomerCoupon cc = CustomerCoupon.builder()
                .couponCode(code)
                .country(customer.getCountry())
                .customer(customer)
                .couponTemplate(couponTemplate(customer.getCountry(), 100))
                .reason(CouponReason.POINTS_EXCHANGE)
                .status(CouponStatus.ACTIVE)
                .issuedAt(LocalDateTime.now().minusDays(1))
                .expiresAt(expiresAt)
                .build();
        cc.setId((long) code.hashCode());
        return cc;
    }

    private CustomerCoupon usedCoupon(String code, Customer customer, LocalDateTime expiresAt) {
        CustomerCoupon cc = activeCoupon(code, customer, expiresAt);
        cc.setStatus(CouponStatus.USED);
        return cc;
    }

    /**
     * Stan kuponu wyliczamy z dat przy kazdym odczycie. Kupon, ktorego nikt nie
     * probowal uzyc, jest raportowany jako wygasly mimo utrwalonego stanu ACTIVE.
     */
    @Test
    void validateCoupon_lapsedButPersistedActive_shouldReportExpired() {
        Customer customer = customer("C001", "PL", 200);
        CustomerCoupon coupon = activeCoupon("BONUS00000000099", customer, LocalDateTime.now().minusDays(1));
        coupon.setStatus(CouponStatus.ACTIVE);

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(customerCouponRepository.findByCouponCode("BONUS00000000099")).thenReturn(Optional.of(coupon));
        when(customerCouponRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CouponValidationResponse response = couponService.validateCoupon("BONUS00000000099", "C001");

        assertThat(response.status()).isEqualTo(CouponValidationStatus.COUPON_EXPIRED);
        assertThat(coupon.getStatus()).isEqualTo(CouponStatus.EXPIRED);
    }

    /** Kupon wycofany przez operatora nie jest ani wazny, ani zrealizowany. */
    @Test
    void validateCoupon_cancelledCoupon_shouldReportCancelled() {
        Customer customer = customer("C001", "PL", 200);
        CustomerCoupon coupon = activeCoupon("BONUS00000000098", customer, LocalDateTime.now().plusDays(10));
        coupon.setStatus(CouponStatus.CANCELLED);

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(customerCouponRepository.findByCouponCode("BONUS00000000098")).thenReturn(Optional.of(coupon));

        CouponValidationResponse response = couponService.validateCoupon("BONUS00000000098", "C001");

        assertThat(response.status()).isEqualTo(CouponValidationStatus.COUPON_CANCELLED);
    }

    @Test
    void validateCoupon_inactiveCustomer_shouldReportCustomerNotActive() {
        Customer customer = customer("C001", "PL", 200);
        customer.setStatus(CustomerStatus.INACTIVE);
        CustomerCoupon coupon = activeCoupon("BONUS00000000097", customer, LocalDateTime.now().plusDays(10));

        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(customerCouponRepository.findByCouponCode("BONUS00000000097")).thenReturn(Optional.of(coupon));

        CouponValidationResponse response = couponService.validateCoupon("BONUS00000000097", "C001");

        assertThat(response.status()).isEqualTo(CouponValidationStatus.CUSTOMER_NOT_ACTIVE);
    }
}
