package pl.pietruszynski.loyaltyclub.api.admin.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "coupon_prefixes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponPrefix {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String value;
}
