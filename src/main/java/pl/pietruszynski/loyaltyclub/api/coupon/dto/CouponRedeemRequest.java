package pl.pietruszynski.loyaltyclub.api.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CouponRedeemRequest(
        @NotBlank(message = "Customer number is required")
        String customerNumber,
        @NotNull(message = "Coupon template id is required")
        Long couponTemplateId
) {}
