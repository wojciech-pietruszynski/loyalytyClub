package pl.pietruszynski.loyaltyclub.api.ecom.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import pl.pietruszynski.loyaltyclub.api.admin.dto.CustomerCouponDto;
import pl.pietruszynski.loyaltyclub.api.admin.dto.TransactionDto;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponReason;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponStatus;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponTemplate;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerCoupon;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerStatus;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionState;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionType;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CustomerCouponRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CustomerRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TransactionRepository;
import pl.pietruszynski.loyaltyclub.api.admin.service.LoyaltyTierService;
import pl.pietruszynski.loyaltyclub.api.ecom.dto.EcomCustomerProfileDto;
import pl.pietruszynski.loyaltyclub.api.store.dto.StorePointsBalanceResponse;
import pl.pietruszynski.loyaltyclub.api.store.service.StoreTransactionService;
import pl.pietruszynski.loyaltyclub.exception.ResourceNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Odczytowe API dla integracji sklepu internetowego.
 *
 * <p>Klient adresowany jest tu numerem uczestnika, a nie identyfikatorem bazy --
 * sklep internetowy nie zna kluczy glownych. Kazde wywolanie musi wiec najpierw
 * rozwiazac numer na klienta i odrzucic numer nieznany, zamiast zwracac pusty wynik.
 */
@ExtendWith(MockitoExtension.class)
class EcomServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private CustomerCouponRepository customerCouponRepository;
    @Mock private StoreTransactionService storeTransactionService;
    @Mock private LoyaltyTierService loyaltyTierService;

    @InjectMocks
    private EcomService ecomService;

    @Test
    void getPointsBalance_shouldDelegateToTheSingleSourceOfTruth() {
        StorePointsBalanceResponse balance = new StorePointsBalanceResponse(1L, "C001", 20, 100, 5);
        when(storeTransactionService.getPointsBalance("C001")).thenReturn(balance);

        assertThat(ecomService.getPointsBalance("C001")).isSameAs(balance);
        // Saldo liczy jedna klasa; powielenie regul dalo by dwie rozne odpowiedzi.
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void getCustomerProfile_shouldExposeBalanceLifetimePointsAndTier() {
        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer()));
        when(loyaltyTierService.resolveTierCode(any(Customer.class))).thenReturn("SILVER");

        EcomCustomerProfileDto profile = ecomService.getCustomerProfile("C001");

        assertThat(profile.customerNumber()).isEqualTo("C001");
        assertThat(profile.loyaltyPoints()).isEqualTo(100);
        assertThat(profile.lifetimePoints()).isEqualTo(1500);
        assertThat(profile.loyaltyTierCode()).isEqualTo("SILVER");
        assertThat(profile.referralCode()).isEqualTo("REF123");
        assertThat(profile.status()).isEqualTo("ACTIVE");
    }

    /** Rekordy sprzed wprowadzenia cyklu zycia konta nie maja stanu; domyslnie sa czynne. */
    @Test
    void getCustomerProfile_withoutStatus_shouldReportActive() {
        Customer customer = customer();
        customer.setStatus(null);
        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(loyaltyTierService.resolveTierCode(any(Customer.class))).thenReturn(null);

        assertThat(ecomService.getCustomerProfile("C001").status()).isEqualTo("ACTIVE");
    }

    @Test
    void getCustomerProfile_shouldPassThroughSuspendedStatus() {
        Customer customer = customer();
        customer.setStatus(CustomerStatus.INACTIVE);
        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(loyaltyTierService.resolveTierCode(any(Customer.class))).thenReturn(null);

        assertThat(ecomService.getCustomerProfile("C001").status()).isEqualTo("INACTIVE");
    }

    @Test
    void getTransactions_shouldMapLifecycleDatesAndStateNames() {
        Customer customer = customer();
        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(transactionRepository.findAllByCustomerIdOrderByTimestampAsc(1L))
                .thenReturn(List.of(transaction(customer)));

        List<TransactionDto> transactions = ecomService.getTransactions("C001");

        assertThat(transactions).singleElement().satisfies(dto -> {
            assertThat(dto.getPoints()).isEqualTo(100);
            assertThat(dto.getType()).isEqualTo("SALE");
            assertThat(dto.getState()).isEqualTo("AVAILABLE");
            assertThat(dto.getAvailableFrom()).isNotNull();
            assertThat(dto.getExpiresAt()).isNotNull();
        });
    }

    /** Rekordy bez typu i stanu (sprzed reklasyfikacji) nie moga wywracac odczytu. */
    @Test
    void getTransactions_withoutTypeAndState_shouldMapThemToNull() {
        Customer customer = customer();
        Transaction transaction = transaction(customer);
        transaction.setType(null);
        transaction.setState(null);
        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(transactionRepository.findAllByCustomerIdOrderByTimestampAsc(1L)).thenReturn(List.of(transaction));

        TransactionDto dto = ecomService.getTransactions("C001").getFirst();

        assertThat(dto.getType()).isNull();
        assertThat(dto.getState()).isNull();
    }

    @Test
    void getTransactionsPage_shouldKeepPagingMetadata() {
        Customer customer = customer();
        Pageable pageable = PageRequest.of(0, 10);
        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(transactionRepository.findAllByCustomerId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(transaction(customer)), pageable, 25));

        var page = ecomService.getTransactions("C001", pageable);

        assertThat(page.getTotalElements()).isEqualTo(25);
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void getCoupons_shouldFlattenTemplateTermsIntoTheResponse() {
        Customer customer = customer();
        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(customerCouponRepository.findAllByCustomerIdOrderByIssuedAtDesc(1L))
                .thenReturn(List.of(coupon(customer, CouponStatus.ACTIVE, LocalDateTime.now().plusDays(7))));

        List<CustomerCouponDto> coupons = ecomService.getCoupons("C001");

        assertThat(coupons).singleElement().satisfies(dto -> {
            assertThat(dto.getCouponCode()).isEqualTo("KUPPL123");
            assertThat(dto.getCustomerName()).isEqualTo("Jan Kowalski");
            assertThat(dto.getCouponValue()).isEqualByComparingTo("10.00");
            assertThat(dto.getMinimumPurchaseValue()).isEqualByComparingTo("50.00");
            assertThat(dto.getRequiredPoints()).isEqualTo(300);
            assertThat(dto.getStatus()).isEqualTo("ACTIVE");
        });
    }

    /**
     * Stan kuponu wyliczany jest z dat przy kazdym odczycie, wiec kupon po terminie
     * ma byc pokazany jako {@code EXPIRED} takze wtedy, gdy zadanie cykliczne
     * nie zdazylo jeszcze utrwalic tego stanu.
     */
    @Test
    void getCoupons_shouldReportLapsedCouponAsExpiredEvenWhenStillStoredAsActive() {
        Customer customer = customer();
        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(customerCouponRepository.findAllByCustomerIdOrderByIssuedAtDesc(1L))
                .thenReturn(List.of(coupon(customer, CouponStatus.ACTIVE, LocalDateTime.now().minusDays(1))));

        assertThat(ecomService.getCoupons("C001").getFirst().getStatus()).isEqualTo("EXPIRED");
    }

    /** Kupon zrealizowany zostaje zrealizowany, nawet po uplynieciu terminu. */
    @Test
    void getCoupons_shouldKeepFinalStatusOfUsedCoupon() {
        Customer customer = customer();
        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(customerCouponRepository.findAllByCustomerIdOrderByIssuedAtDesc(1L))
                .thenReturn(List.of(coupon(customer, CouponStatus.USED, LocalDateTime.now().minusDays(1))));

        assertThat(ecomService.getCoupons("C001").getFirst().getStatus()).isEqualTo("USED");
    }

    /** Kupony sprzed wprowadzenia powodu wydania traktujemy jak wymiane punktow. */
    @Test
    void getCoupons_withoutReason_shouldDefaultToPointsExchange() {
        Customer customer = customer();
        CustomerCoupon coupon = coupon(customer, CouponStatus.ACTIVE, LocalDateTime.now().plusDays(7));
        coupon.setReason(null);
        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(customerCouponRepository.findAllByCustomerIdOrderByIssuedAtDesc(1L)).thenReturn(List.of(coupon));

        assertThat(ecomService.getCoupons("C001").getFirst().getReason()).isEqualTo("POINTS_EXCHANGE");
    }

    @Test
    void getCouponsPage_shouldKeepPagingMetadata() {
        Customer customer = customer();
        Pageable pageable = PageRequest.of(1, 5);
        when(customerRepository.findByCustomerNumber("C001")).thenReturn(Optional.of(customer));
        when(customerCouponRepository.findAllByCustomerId(1L, pageable)).thenReturn(
                new PageImpl<>(List.of(coupon(customer, CouponStatus.ACTIVE, LocalDateTime.now().plusDays(7))),
                        pageable, 11));

        var page = ecomService.getCoupons("C001", pageable);

        assertThat(page.getTotalElements()).isEqualTo(11);
        assertThat(page.getNumber()).isEqualTo(1);
    }

    /**
     * Nieznany numer uczestnika musi konczyc sie bledem, a nie pusta lista --
     * inaczej literowka po stronie sklepu wygladalaby jak konto bez historii.
     */
    @Test
    void unknownCustomerNumber_shouldBeReportedOnEveryReadOperation() {
        when(customerRepository.findByCustomerNumber("BRAK")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ecomService.getCustomerProfile("BRAK"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("BRAK");
        assertThatThrownBy(() -> ecomService.getTransactions("BRAK"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> ecomService.getCoupons("BRAK"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> ecomService.getTransactions("BRAK", PageRequest.of(0, 10)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> ecomService.getCoupons("BRAK", PageRequest.of(0, 10)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(transactionRepository, org.mockito.Mockito.never())
                .findAllByCustomerIdOrderByTimestampAsc(any());
    }

    private Customer customer() {
        return Customer.builder()
                .id(1L)
                .firstName("Jan")
                .lastName("Kowalski")
                .email("jan@example.com")
                .customerNumber("C001")
                .phoneNumber("123456789")
                .country("PL")
                .loyaltyPoints(100)
                .lifetimePoints(1500)
                .status(CustomerStatus.ACTIVE)
                .referralCode("REF123")
                .build();
    }

    private Transaction transaction(Customer customer) {
        LocalDateTime now = LocalDateTime.now();
        return Transaction.builder()
                .id(10L)
                .customer(customer)
                .points(100)
                .amount(new BigDecimal("100.00"))
                .pointsPerCurrency(BigDecimal.ONE)
                .description("Zakup")
                .country("PL")
                .type(TransactionType.SALE)
                .state(TransactionState.AVAILABLE)
                .timestamp(now)
                .purchaseTimestamp(now)
                .availableFrom(now)
                .expiresAt(now.plusDays(365))
                .build();
    }

    private CustomerCoupon coupon(Customer customer, CouponStatus status, LocalDateTime expiresAt) {
        return CustomerCoupon.builder()
                .id(5L)
                .couponCode("KUPPL123")
                .country("PL")
                .customer(customer)
                .couponTemplate(CouponTemplate.builder()
                        .id(1L)
                        .couponValue(new BigDecimal("10.00"))
                        .minimumPurchaseValue(new BigDecimal("50.00"))
                        .requiredPoints(300)
                        .country("PL")
                        .validityDays(7)
                        .couponPrefix("KUPPL")
                        .build())
                .reason(CouponReason.POINTS_EXCHANGE)
                .status(status)
                .issuedAt(LocalDateTime.now().minusDays(1))
                .expiresAt(expiresAt)
                .build();
    }
}
