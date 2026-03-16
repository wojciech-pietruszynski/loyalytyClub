CREATE INDEX idx_customers_email ON customers(email);
CREATE INDEX idx_customers_customer_number ON customers(customer_number);
CREATE INDEX idx_customers_country ON customers(country);
CREATE INDEX idx_coupon_templates_country ON coupon_templates(country);
CREATE INDEX idx_customer_coupons_customer_id ON customer_coupons(customer_id);
CREATE INDEX idx_customer_coupons_issued_at ON customer_coupons(issued_at);
CREATE INDEX idx_transactions_customer_id_timestamp ON transactions(customer_id, timestamp);

ALTER TABLE customer_coupons
    ADD CONSTRAINT fk_customer_coupons_customer
    FOREIGN KEY (customer_id) REFERENCES customers(id);

ALTER TABLE customer_coupons
    ADD CONSTRAINT fk_customer_coupons_template
    FOREIGN KEY (coupon_template_id) REFERENCES coupon_templates(id);

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_customer
    FOREIGN KEY (customer_id) REFERENCES customers(id);
