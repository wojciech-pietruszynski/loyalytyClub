package pl.pietruszynski.loyaltyclub.api.store.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "store_points_promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorePointsPromotion {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "store_points_promotions_seq")
    @SequenceGenerator(name = "store_points_promotions_seq", sequenceName = "store_points_promotions_id_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 3)
    private String country;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal pointsPerCurrency;

    @Column(nullable = false)
    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
