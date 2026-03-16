package pl.pietruszynski.loyaltyclub.api.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class CouponTemplateDto {
    private Long id;
    private BigDecimal couponValue;
    private BigDecimal minimumPurchaseValue;
    private Integer requiredPoints;
    private String country;
    private Integer validityDays;
    private String couponPrefix;
}
