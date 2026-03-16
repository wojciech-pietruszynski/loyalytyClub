package pl.pietruszynski.loyaltyclub.api.coupon.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.pietruszynski.loyaltyclub.api.admin.model.CustomerCoupon;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_redemption_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponRedemptionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "customer_number", nullable = false, length = 255)
    private String customerNumber;

    @Column(name = "coupon_template_id", nullable = false)
    private Long couponTemplateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_coupon_id")
    private CustomerCoupon customerCoupon;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
