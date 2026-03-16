package pl.pietruszynski.loyaltyclub.api.coupon.dto;

import pl.pietruszynski.loyaltyclub.api.coupon.model.CouponValidationStatus;

import java.time.LocalDateTime;

public record CouponValidationResponse(
        CouponValidationStatus status,
        String couponCode,
        String customerNumber,
        String couponStatus,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt,
        CouponDefinitionResponse definition
) {}
