ALTER TABLE transactions ADD COLUMN amount DECIMAL(12,2) NOT NULL DEFAULT 0;
ALTER TABLE transactions ADD COLUMN points_per_currency DECIMAL(8,2) NOT NULL DEFAULT 1;
ALTER TABLE transactions ADD COLUMN type VARCHAR(30) NOT NULL DEFAULT 'MANUAL_ADJUSTMENT';
ALTER TABLE transactions ADD COLUMN state VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE';
ALTER TABLE transactions ADD COLUMN purchase_timestamp TIMESTAMP;
ALTER TABLE transactions ADD COLUMN available_from TIMESTAMP;
ALTER TABLE transactions ADD COLUMN expires_at TIMESTAMP;
ALTER TABLE transactions ADD COLUMN source_transaction_id BIGINT;

UPDATE transactions
SET purchase_timestamp = COALESCE(purchase_timestamp, timestamp),
    available_from = COALESCE(available_from, timestamp),
    expires_at = COALESCE(expires_at, timestamp + INTERVAL '365 days');

ALTER TABLE transactions ALTER COLUMN purchase_timestamp SET NOT NULL;
ALTER TABLE transactions ALTER COLUMN available_from SET NOT NULL;
ALTER TABLE transactions ALTER COLUMN expires_at SET NOT NULL;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_source_transaction
    FOREIGN KEY (source_transaction_id) REFERENCES transactions(id);

CREATE INDEX idx_transactions_source_transaction_id ON transactions(source_transaction_id);

CREATE TABLE store_points_promotions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    country VARCHAR(3) NOT NULL,
    points_per_currency DECIMAL(8,2) NOT NULL,
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_store_points_promotions_country_starts_at
    ON store_points_promotions(country, starts_at);
