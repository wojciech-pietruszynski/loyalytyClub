-- Log audytowy panelu administracyjnego, progi lojalnosciowe i kolumny polecen przy klientach.

CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL,
    username VARCHAR(255) NOT NULL,
    role VARCHAR(50),
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_occurred_at
    ON admin_audit_logs(occurred_at DESC);

CREATE TABLE IF NOT EXISTS loyalty_tiers (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    min_points INTEGER NOT NULL
);

INSERT INTO loyalty_tiers (code, min_points) VALUES
    ('BRONZE', 0),
    ('SILVER', 1000),
    ('GOLD', 5000)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE customers ADD COLUMN IF NOT EXISTS referral_code VARCHAR(64);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS referred_by_customer_id BIGINT;

-- Kody polecen sa unikalne, ale opcjonalne — w Postgresie NULL nie koliduje z unikalnoscia.
CREATE UNIQUE INDEX IF NOT EXISTS idx_customers_referral_code
    ON customers(referral_code);

CREATE INDEX IF NOT EXISTS idx_customers_referred_by
    ON customers(referred_by_customer_id);

DO $$
BEGIN
    ALTER TABLE customers
        ADD CONSTRAINT fk_customers_referred_by
        FOREIGN KEY (referred_by_customer_id) REFERENCES customers(id);
EXCEPTION
    WHEN duplicate_object THEN NULL;
END
$$;
