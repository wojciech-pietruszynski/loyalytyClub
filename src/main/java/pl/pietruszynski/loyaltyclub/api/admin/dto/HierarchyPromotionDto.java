package pl.pietruszynski.loyaltyclub.api.admin.dto;

import lombok.Builder;
import lombok.Getter;
import pl.pietruszynski.loyaltyclub.api.store.model.HierarchyPromotionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class HierarchyPromotionDto {
    private Long id;
    private String name;
    private String country;
    private String hierarchy;
    private String productClass;
    private String subclass;
    private HierarchyPromotionType type;
    private BigDecimal multiplier;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private boolean enabled;
}
