package pl.pietruszynski.loyaltyclub.api.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CouponTemplateCreateRequest(
        @NotNull(message = "Coupon value is required")
        @DecimalMin(value = "0.01", message = "Coupon value must be greater than zero")
        BigDecimal couponValue,
        @NotNull(message = "Minimum purchase value is required")
        @DecimalMin(value = "0.00", message = "Minimum purchase value cannot be negative")
        BigDecimal minimumPurchaseValue,
        @NotNull(message = "Required points are required")
        @Positive(message = "Required points must be greater than zero")
        Integer requiredPoints,
        @NotBlank(message = "Country is required")
        @Size(min = 2, max = 3, message = "Country code must have 2 or 3 characters")
        String country,
        @NotNull(message = "Validity days are required")
        @Positive(message = "Validity days must be greater than zero")
        Integer validityDays,
        @NotBlank(message = "Coupon prefix is required")
        @Size(max = 20, message = "Coupon prefix cannot exceed 20 characters")
        String couponPrefix
) {}
