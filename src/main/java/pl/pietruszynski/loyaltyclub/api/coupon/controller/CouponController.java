package pl.pietruszynski.loyaltyclub.api.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.pietruszynski.loyaltyclub.api.coupon.dto.CouponRedeemRequest;
import pl.pietruszynski.loyaltyclub.api.coupon.dto.CouponRedeemResponse;
import pl.pietruszynski.loyaltyclub.api.coupon.dto.CouponValidationResponse;
import pl.pietruszynski.loyaltyclub.api.coupon.service.CouponService;

@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/redeem-points")
    public CouponRedeemResponse redeemPoints(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CouponRedeemRequest request
    ) {
        return couponService.redeemPointsForCoupon(request, idempotencyKey);
    }

    @GetMapping("/validate")
    public CouponValidationResponse validateCoupon(
            @RequestParam String couponCode,
            @RequestParam String customerNumber
    ) {
        return couponService.validateCoupon(couponCode, customerNumber);
    }
}
