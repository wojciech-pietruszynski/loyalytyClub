ALTER TABLE technical_users
ADD COLUMN IF NOT EXISTS password_preview VARCHAR(255) NOT NULL DEFAULT '';

ALTER TABLE transactions
ADD COLUMN IF NOT EXISTS country VARCHAR(3);

UPDATE transactions t
SET country = c.country
FROM customers c
WHERE t.customer_id = c.id
  AND (t.country IS NULL OR btrim(t.country) = '');

UPDATE transactions
SET country = 'PL'
WHERE country IS NULL OR btrim(country) = '';

ALTER TABLE transactions
ALTER COLUMN country SET NOT NULL;

ALTER TABLE customer_coupons
ADD COLUMN IF NOT EXISTS country VARCHAR(3);

UPDATE customer_coupons cc
SET country = ct.country
FROM coupon_templates ct
WHERE cc.coupon_template_id = ct.id
  AND (cc.country IS NULL OR btrim(cc.country) = '');

UPDATE customer_coupons
SET country = 'PL'
WHERE country IS NULL OR btrim(country) = '';

ALTER TABLE customer_coupons
ALTER COLUMN country SET NOT NULL;
