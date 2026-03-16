package pl.pietruszynski.loyaltyclub.api.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class StorePromotionDto {
    private Long id;
    private String name;
    private String country;
    private BigDecimal pointsPerCurrency;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private boolean enabled;
}
