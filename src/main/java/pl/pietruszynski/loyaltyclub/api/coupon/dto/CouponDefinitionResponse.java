package pl.pietruszynski.loyaltyclub.api.coupon.dto;

import java.math.BigDecimal;

public record CouponDefinitionResponse(
        Long couponTemplateId,
        BigDecimal couponValue,
        BigDecimal minimumPurchaseValue,
        Integer requiredPoints,
        Integer validityDays,
        String couponPrefix,
        String country
) {}
