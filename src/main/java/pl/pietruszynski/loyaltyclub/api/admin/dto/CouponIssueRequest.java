package pl.pietruszynski.loyaltyclub.api.admin.dto;

import jakarta.validation.constraints.NotNull;
import pl.pietruszynski.loyaltyclub.api.admin.model.CouponReason;

public record CouponIssueRequest(
        @NotNull(message = "Customer id is required")
        Long customerId,
        @NotNull(message = "Coupon template id is required")
        Long couponTemplateId,
        @NotNull(message = "Reason is required")
        CouponReason reason
) {}
