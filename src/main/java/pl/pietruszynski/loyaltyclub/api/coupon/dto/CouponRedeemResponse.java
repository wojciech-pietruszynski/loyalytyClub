package pl.pietruszynski.loyaltyclub.api.coupon.dto;

import java.time.LocalDateTime;

public record CouponRedeemResponse(
        String couponCode,
        String customerNumber,
        String status,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt,
        CouponDefinitionResponse definition
) {}
