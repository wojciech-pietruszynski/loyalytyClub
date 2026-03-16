CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    customer_number VARCHAR(255) NOT NULL,
    phone_number VARCHAR(255) NOT NULL,
    country VARCHAR(3) NOT NULL,
    loyalty_points INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE coupon_templates (
    id BIGSERIAL PRIMARY KEY,
    coupon_value DECIMAL(12,2) NOT NULL,
    minimum_purchase_value DECIMAL(12,2) NOT NULL,
    required_points INTEGER NOT NULL,
    country VARCHAR(3) NOT NULL,
    validity_days INTEGER NOT NULL,
    coupon_prefix VARCHAR(20) NOT NULL
);

CREATE TABLE customer_coupons (
    id BIGSERIAL PRIMARY KEY,
    coupon_code VARCHAR(40) NOT NULL,
    country VARCHAR(3) NOT NULL,
    customer_id BIGINT NOT NULL,
    coupon_template_id BIGINT NOT NULL,
    reason VARCHAR(30) NOT NULL DEFAULT 'POINTS_EXCHANGE',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL
);

CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    points INTEGER NOT NULL,
    description VARCHAR(255) NOT NULL,
    country VARCHAR(3) NOT NULL,
    timestamp TIMESTAMP NOT NULL
);

CREATE TABLE technical_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    password_preview VARCHAR(255) NOT NULL DEFAULT '',
    country VARCHAR(3) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
