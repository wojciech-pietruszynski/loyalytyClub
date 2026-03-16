package pl.pietruszynski.loyaltyclub.api.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class CustomerCouponDto {
    private Long id;
    private String couponCode;
    private Long customerId;
    private String customerName;
    private String country;
    private BigDecimal couponValue;
    private BigDecimal minimumPurchaseValue;
    private Integer requiredPoints;
    private Integer validityDays;
    private String couponPrefix;
    private String reason;
    private String status;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
}
