CREATE TABLE coupon_redemption_requests (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    customer_number VARCHAR(255) NOT NULL,
    coupon_template_id BIGINT NOT NULL,
    customer_coupon_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_coupon_redemption_requests_customer_number
    ON coupon_redemption_requests(customer_number);

ALTER TABLE coupon_redemption_requests
    ADD CONSTRAINT fk_coupon_redemption_requests_customer_coupon
    FOREIGN KEY (customer_coupon_id) REFERENCES customer_coupons(id);
