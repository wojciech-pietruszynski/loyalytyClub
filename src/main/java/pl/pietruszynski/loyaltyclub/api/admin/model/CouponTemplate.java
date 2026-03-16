package pl.pietruszynski.loyaltyclub.api.admin.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "coupon_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "coupon_templates_seq")
    @SequenceGenerator(name = "coupon_templates_seq", sequenceName = "coupon_templates_id_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal couponValue;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal minimumPurchaseValue;

    @Column(nullable = false)
    private Integer requiredPoints;

    @Column(nullable = false, length = 3)
    private String country;

    @Column(nullable = false)
    private Integer validityDays;

    @Column(nullable = false, length = 20)
    private String couponPrefix;
}
