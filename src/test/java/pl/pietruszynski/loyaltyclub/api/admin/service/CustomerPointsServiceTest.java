package pl.pietruszynski.loyaltyclub.api.admin.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.pietruszynski.loyaltyclub.api.admin.model.Customer;
import pl.pietruszynski.loyaltyclub.api.admin.model.Transaction;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionState;
import pl.pietruszynski.loyaltyclub.api.admin.model.TransactionType;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CustomerRepository;
import pl.pietruszynski.loyaltyclub.api.admin.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Rozdzielenie biezacego salda od dorobku punktowego. Poziom lojalnosciowy byl
 * dotad liczony z biezacego salda, przez co wykorzystanie punktow obnizalo status
 * klienta -- to blad dziedzinowy, nie uproszczenie.
 */
@ExtendWith(MockitoExtension.class)
class CustomerPointsServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private TransactionRepository transactionRepository;

    @InjectMocks
    private CustomerPointsService customerPointsService;

    @Test
    void refresh_pointsExchange_shouldLowerBalanceButNotLifetimePoints() {
        Customer customer = customer();
        Transaction sale = dated(TransactionType.SALE, 1000, LocalDateTime.now().minusDays(60));
        Transaction redemption = immediate(TransactionType.POINTS_REDEMPTION, -400);

        stubHistory(customer, List.of(sale, redemption));

        customerPointsService.refresh(customer);

        assertThat(customer.getLoyaltyPoints()).isEqualTo(600);
        assertThat(customer.getLifetimePoints()).isEqualTo(1000);
    }

    @Test
    void refresh_expiredPoints_shouldLeaveLifetimePointsIntact() {
        Customer customer = customer();
        Transaction expired = dated(TransactionType.SALE, 500, LocalDateTime.now().minusDays(400));

        stubHistory(customer, List.of(expired));

        customerPointsService.refresh(customer);

        assertThat(expired.getState()).isEqualTo(TransactionState.EXPIRED);
        assertThat(customer.getLoyaltyPoints()).isZero();
        assertThat(customer.getLifetimePoints()).isEqualTo(500);
    }

    @Test
    void refresh_pendingPoints_shouldCountTowardsLifetimeButNotBalance() {
        Customer customer = customer();
        Transaction pending = dated(TransactionType.SALE, 300, LocalDateTime.now().minusDays(2));

        stubHistory(customer, List.of(pending));

        customerPointsService.refresh(customer);

        assertThat(pending.getState()).isEqualTo(TransactionState.PENDING);
        assertThat(customer.getLoyaltyPoints()).isZero();
        assertThat(customer.getLifetimePoints()).isEqualTo(300);
    }

    @Test
    void refresh_returnedGoods_shouldReduceLifetimePoints() {
        Customer customer = customer();
        Transaction sale = dated(TransactionType.SALE, 1000, LocalDateTime.now().minusDays(60));
        Transaction goodsReturn = dated(TransactionType.RETURN, -400, LocalDateTime.now().minusDays(60));

        stubHistory(customer, List.of(sale, goodsReturn));

        customerPointsService.refresh(customer);

        // Zwrot towaru cofa faktycznie zdobyte punkty -- inaczej niz wymiana na kupon.
        assertThat(customer.getLifetimePoints()).isEqualTo(600);
        assertThat(customer.getLoyaltyPoints()).isEqualTo(600);
    }

    @Test
    void refresh_cancelledCouponRefund_shouldNotInflateLifetimePoints() {
        Customer customer = customer();
        Transaction sale = dated(TransactionType.SALE, 1000, LocalDateTime.now().minusDays(60));
        Transaction redemption = immediate(TransactionType.POINTS_REDEMPTION, -400);
        Transaction refund = immediate(TransactionType.POINTS_REFUND, 400);

        stubHistory(customer, List.of(sale, redemption, refund));

        customerPointsService.refresh(customer);

        assertThat(customer.getLoyaltyPoints()).isEqualTo(1000);
        assertThat(customer.getLifetimePoints()).isEqualTo(1000);
    }

    @Test
    void refresh_referralBonus_shouldCountTowardsLifetimePoints() {
        Customer customer = customer();
        Transaction bonus = dated(TransactionType.REFERRAL, 500, LocalDateTime.now().minusDays(1));
        bonus.setAvailableFrom(LocalDateTime.now().minusDays(1));

        stubHistory(customer, List.of(bonus));

        customerPointsService.refresh(customer);

        assertThat(customer.getLoyaltyPoints()).isEqualTo(500);
        assertThat(customer.getLifetimePoints()).isEqualTo(500);
    }

    @Test
    void refresh_lifetimePointsNeverGoNegative() {
        Customer customer = customer();
        Transaction correction = immediate(TransactionType.MANUAL_ADJUSTMENT, -50);

        stubHistory(customer, List.of(correction));

        customerPointsService.refresh(customer);

        assertThat(customer.getLifetimePoints()).isZero();
    }

    @Test
    void resolveState_immediateTypes_areAlwaysAvailable() {
        Transaction adjustment = immediate(TransactionType.MANUAL_ADJUSTMENT, 10);
        adjustment.setAvailableFrom(LocalDateTime.now().plusDays(30));
        adjustment.setExpiresAt(LocalDateTime.now().minusDays(1));

        assertThat(customerPointsService.resolveState(adjustment, LocalDateTime.now()))
                .isEqualTo(TransactionState.AVAILABLE);
    }

    private void stubHistory(Customer customer, List<Transaction> transactions) {
        when(transactionRepository.findAllByCustomerIdOrderByPurchaseTimestampAsc(customer.getId()))
                .thenReturn(transactions);
        lenient().when(transactionRepository.saveAll(any())).thenReturn(transactions);
        lenient().when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Customer customer() {
        Customer customer = Customer.builder()
                .firstName("Jan")
                .lastName("Kowalski")
                .email("jan@pl.com")
                .customerNumber("C001")
                .phoneNumber("123456789")
                .country("PL")
                .build();
        customer.setId(1L);
        return customer;
    }

    /** Transakcja podlegajaca karencji i wygasnieciu (sprzedaz, zwrot, polecenie). */
    private Transaction dated(TransactionType type, int points, LocalDateTime purchasedAt) {
        return Transaction.builder()
                .points(points)
                .amount(BigDecimal.ZERO)
                .pointsPerCurrency(BigDecimal.ONE)
                .description(type.name())
                .country("PL")
                .type(type)
                .state(TransactionState.PENDING)
                .purchaseTimestamp(purchasedAt)
                .availableFrom(purchasedAt.plusDays(30))
                .expiresAt(purchasedAt.plusDays(365))
                .build();
    }

    /** Transakcja bez karencji i bez daty wygasniecia (korekta, operacje kuponowe). */
    private Transaction immediate(TransactionType type, int points) {
        LocalDateTime now = LocalDateTime.now();
        return Transaction.builder()
                .points(points)
                .amount(BigDecimal.ZERO)
                .pointsPerCurrency(BigDecimal.ONE)
                .description(type.name())
                .country("PL")
                .type(type)
                .state(TransactionState.AVAILABLE)
                .purchaseTimestamp(now)
                .availableFrom(now)
                .expiresAt(now.plusDays(365))
                .build();
    }
}
