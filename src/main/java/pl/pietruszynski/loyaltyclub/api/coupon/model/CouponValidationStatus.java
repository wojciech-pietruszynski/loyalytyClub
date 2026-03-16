package pl.pietruszynski.loyaltyclub.api.coupon.model;

public enum CouponValidationStatus {
    VALID,
    COUPON_NOT_FOUND,
    CUSTOMER_NOT_FOUND,
    COUPON_BELONGS_TO_ANOTHER_ACCOUNT,
    COUPON_ALREADY_USED,
    COUPON_EXPIRED
}
