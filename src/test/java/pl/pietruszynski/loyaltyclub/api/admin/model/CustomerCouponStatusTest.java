package pl.pietruszynski.loyaltyclub.api.admin.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stan kuponu wyliczany z dat -- tak samo jak stan transakcji punktowej.
 * Wczesniej przeterminowanie utrwalalo sie dopiero przy probie walidacji, wiec
 * kupon, ktorego nikt nie probowal uzyc, byl prezentowany jako aktywny na listach
 * i w raportach.
 */
class CustomerCouponStatusTest {

    private final LocalDateTime now = LocalDateTime.now();

    @Test
    void activeCouponBeforeExpiry_staysActive() {
        assertThat(coupon(CouponStatus.ACTIVE, now.plusDays(1)).effectiveStatus(now))
                .isEqualTo(CouponStatus.ACTIVE);
    }

    @Test
    void activeCouponAfterExpiry_isReportedAsExpired() {
        assertThat(coupon(CouponStatus.ACTIVE, now.minusSeconds(1)).effectiveStatus(now))
                .isEqualTo(CouponStatus.EXPIRED);
    }

    @Test
    void couponExpiringExactlyNow_isReportedAsExpired() {
        assertThat(coupon(CouponStatus.ACTIVE, now).effectiveStatus(now))
                .isEqualTo(CouponStatus.EXPIRED);
    }

    /** Stany koncowe sa nietykalne -- uplyw czasu ich nie zmienia. */
    @Test
    void usedCouponAfterExpiry_staysUsed() {
        assertThat(coupon(CouponStatus.USED, now.minusDays(10)).effectiveStatus(now))
                .isEqualTo(CouponStatus.USED);
    }

    @Test
    void cancelledCouponAfterExpiry_staysCancelled() {
        assertThat(coupon(CouponStatus.CANCELLED, now.minusDays(10)).effectiveStatus(now))
                .isEqualTo(CouponStatus.CANCELLED);
    }

    @Test
    void missingPersistedStatus_defaultsToActive() {
        assertThat(coupon(null, now.plusDays(1)).effectiveStatus(now))
                .isEqualTo(CouponStatus.ACTIVE);
    }

    private CustomerCoupon coupon(CouponStatus status, LocalDateTime expiresAt) {
        return CustomerCoupon.builder()
                .couponCode("KUPPL00000000001")
                .country("PL")
                .reason(CouponReason.POINTS_EXCHANGE)
                .status(status)
                .issuedAt(now.minusDays(30))
                .expiresAt(expiresAt)
                .build();
    }
}
