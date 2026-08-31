package pl.pietruszynski.loyaltyclub.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponStatus;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerCoupon;
import pl.pietruszynski.loyaltyclub.api.admin.repository.CustomerCouponRepository;
import pl.pietruszynski.loyaltyclub.idempotency.IdempotencyService;
import pl.pietruszynski.loyaltyclub.notification.PointExpiryNotificationService;
import pl.pietruszynski.loyaltyclub.security.TokenRevocationService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Zadania cykliczne utrzymania.
 *
 * <p>Zadne z nich nie jest warunkiem poprawnosci odczytow: stan kuponu i stan
 * transakcji punktowej sa wyliczane z dat przy kazdym odczycie. Zadania jedynie
 * porzadkuja baze i wysylaja powiadomienia, wiec pominiecie przebiegu nie zmienia
 * tego, co widzi uzytkownik.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MaintenanceJobs {

    private static final int IDEMPOTENCY_RETENTION_DAYS = 30;

    private final CustomerCouponRepository customerCouponRepository;
    private final PointExpiryNotificationService pointExpiryNotificationService;
    private final TokenRevocationService tokenRevocationService;
    private final IdempotencyService idempotencyService;

    /** Utrwala stan {@code EXPIRED} kuponow, ktorych nikt nie probowal uzyc. */
    @Scheduled(cron = "${app.maintenance.coupon-expiry-cron:0 5 1 * * *}")
    @Transactional
    public void markLapsedCouponsAsExpired() {
        List<CustomerCoupon> lapsed = customerCouponRepository
                .findLapsedActiveCoupons(CouponStatus.ACTIVE, LocalDateTime.now());
        if (lapsed.isEmpty()) {
            return;
        }
        lapsed.forEach(coupon -> coupon.setStatus(CouponStatus.EXPIRED));
        customerCouponRepository.saveAll(lapsed);
        log.info("Marked {} lapsed coupons as expired", lapsed.size());
    }

    @Scheduled(cron = "${app.maintenance.point-expiry-notice-cron:0 15 6 * * *}")
    public void sendPointExpiryNotifications() {
        int created = pointExpiryNotificationService.notifyUpcomingExpirations();
        if (created > 0) {
            log.info("Created {} point expiry notifications", created);
        }
    }

    @Scheduled(cron = "${app.maintenance.token-cleanup-cron:0 30 2 * * *}")
    public void purgeRevokedTokens() {
        int removed = tokenRevocationService.purgeExpired();
        if (removed > 0) {
            log.info("Purged {} expired token revocations", removed);
        }
    }

    @Scheduled(cron = "${app.maintenance.idempotency-cleanup-cron:0 45 2 * * *}")
    public void purgeIdempotencyKeys() {
        int removed = idempotencyService.purgeOlderThan(LocalDateTime.now().minusDays(IDEMPOTENCY_RETENTION_DAYS));
        if (removed > 0) {
            log.info("Purged {} idempotency reservations", removed);
        }
    }
}
