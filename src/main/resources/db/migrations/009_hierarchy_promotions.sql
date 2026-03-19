CREATE SEQUENCE IF NOT EXISTS hierarchy_promotions_id_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE hierarchy_promotions (
    id BIGINT NOT NULL DEFAULT nextval('hierarchy_promotions_id_seq') PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    country VARCHAR(3) NOT NULL,
    hierarchy_code VARCHAR(100),
    product_class VARCHAR(100),
    subclass VARCHAR(100),
    type VARCHAR(20) NOT NULL,
    multiplier DECIMAL(8,4),
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_hierarchy_promotions_country_starts_at
    ON hierarchy_promotions(country, starts_at);
