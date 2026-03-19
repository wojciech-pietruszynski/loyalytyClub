package pl.pietruszynski.loyaltyclub.api.store.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "hierarchy_promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HierarchyPromotion {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hierarchy_promotions_seq")
    @SequenceGenerator(name = "hierarchy_promotions_seq", sequenceName = "hierarchy_promotions_id_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 3)
    private String country;

    @Column(name = "hierarchy_code")
    private String hierarchy;

    @Column(name = "product_class")
    private String productClass;

    @Column(name = "subclass")
    private String subclass;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HierarchyPromotionType type;

    @Column(precision = 8, scale = 4)
    private BigDecimal multiplier;

    @Column(nullable = false)
    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
