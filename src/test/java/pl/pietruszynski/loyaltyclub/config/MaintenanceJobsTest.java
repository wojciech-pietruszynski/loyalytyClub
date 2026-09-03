package pl.pietruszynski.loyaltyclub.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponStatus;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerCoupon;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CustomerCouponRepository;
import pl.pietruszynski.loyaltyclub.idempotency.IdempotencyService;
import pl.pietruszynski.loyaltyclub.notification.PointExpiryNotificationService;
import pl.pietruszynski.loyaltyclub.security.TokenRevocationService;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Zadania cykliczne utrzymania.
 *
 * <p>Zadne z nich nie jest warunkiem poprawnosci odczytow -- stan kuponu i stan
 * punktu wyliczane sa z dat przy kazdym odczycie. Test pilnuje wiec dwoch rzeczy:
 * ze zadanie nic nie robi, gdy nie ma czego porzadkowac (pusty przebieg nie moze
 * generowac zapisow), i ze nie usuwa niczego, czego nie powinno.
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceJobsTest {

    @Mock private CustomerCouponRepository customerCouponRepository;
    @Mock private PointExpiryNotificationService pointExpiryNotificationService;
    @Mock private TokenRevocationService tokenRevocationService;
    @Mock private IdempotencyService idempotencyService;

    @InjectMocks
    private MaintenanceJobs maintenanceJobs;

    @Test
    void markLapsedCouponsAsExpired_shouldPersistExpiredStatusForEveryLapsedCoupon() {
        CustomerCoupon first = coupon(1L);
        CustomerCoupon second = coupon(2L);
        when(customerCouponRepository.findLapsedActiveCoupons(eq(CouponStatus.ACTIVE), any()))
                .thenReturn(List.of(first, second));

        maintenanceJobs.markLapsedCouponsAsExpired();

        assertThat(first.getStatus()).isEqualTo(CouponStatus.EXPIRED);
        assertThat(second.getStatus()).isEqualTo(CouponStatus.EXPIRED);
        verify(customerCouponRepository).saveAll(List.of(first, second));
    }

    /** Zadanie szuka kuponow z zapisanym stanem {@code ACTIVE} i data waznosci w przeszlosci. */
    @Test
    void markLapsedCouponsAsExpired_shouldQueryWithCurrentMoment() {
        when(customerCouponRepository.findLapsedActiveCoupons(eq(CouponStatus.ACTIVE), any()))
                .thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now();
        maintenanceJobs.markLapsedCouponsAsExpired();

        ArgumentCaptor<LocalDateTime> moment = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(customerCouponRepository).findLapsedActiveCoupons(eq(CouponStatus.ACTIVE), moment.capture());
        assertThat(moment.getValue()).isAfterOrEqualTo(before);
    }

    @Test
    void markLapsedCouponsAsExpired_withNothingToDo_shouldNotWriteAnything() {
        when(customerCouponRepository.findLapsedActiveCoupons(eq(CouponStatus.ACTIVE), any()))
                .thenReturn(List.of());

        maintenanceJobs.markLapsedCouponsAsExpired();

        verify(customerCouponRepository, never()).saveAll(any());
    }

    @Test
    void sendPointExpiryNotifications_shouldDelegateToNotificationService() {
        when(pointExpiryNotificationService.notifyUpcomingExpirations()).thenReturn(3);

        maintenanceJobs.sendPointExpiryNotifications();

        verify(pointExpiryNotificationService).notifyUpcomingExpirations();
    }

    @Test
    void purgeRevokedTokens_shouldDelegateToRevocationService() {
        when(tokenRevocationService.purgeExpired()).thenReturn(2);

        maintenanceJobs.purgeRevokedTokens();

        verify(tokenRevocationService).purgeExpired();
    }

    /**
     * Rezerwacje kluczy idempotencji trzymamy 30 dni. Skrocenie tego okna otwiera
     * mozliwosc ponownego wykonania starego zadania jako nowego.
     */
    @Test
    void purgeIdempotencyKeys_shouldKeepThirtyDaysOfHistory() {
        when(idempotencyService.purgeOlderThan(any())).thenReturn(5);

        LocalDateTime before = LocalDateTime.now();
        maintenanceJobs.purgeIdempotencyKeys();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(idempotencyService).purgeOlderThan(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(
                before.minusDays(30).minusMinutes(1),
                before.minusDays(30).plusMinutes(1));
    }

    private CustomerCoupon coupon(Long id) {
        return CustomerCoupon.builder()
                .id(id)
                .couponCode("KUPPL" + id)
                .country("PL")
                .status(CouponStatus.ACTIVE)
                .issuedAt(LocalDateTime.now().minusDays(30))
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();
    }
}
