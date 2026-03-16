package pl.pietruszynski.loyaltyclub.api.admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "customer_coupons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_coupons_seq")
    @SequenceGenerator(name = "customer_coupons_seq", sequenceName = "customer_coupons_id_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String couponCode;

    @Column(nullable = false, length = 3)
    private String country;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_template_id", nullable = false)
    private CouponTemplate couponTemplate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, columnDefinition = "varchar(30) default 'POINTS_EXCHANGE'")
    private CouponReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'ACTIVE'")
    private CouponStatus status;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;
}
