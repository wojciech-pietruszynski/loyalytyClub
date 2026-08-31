package pl.pietruszynski.loyaltyclub.api.admin.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "loyalty_tiers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "min_points", nullable = false)
    private Integer minPoints;
}
