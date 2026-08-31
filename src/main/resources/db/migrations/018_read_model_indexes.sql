-- Indeksy pod stronicowane i wyszukiwane odczyty kartoteki.
--
-- Kartoteka klientow i lista kuponow zwracaly dotad pelne kolekcje; po wprowadzeniu
-- stronicowania zapytania sortuja po kluczu i filtruja po kraju albo po fragmencie
-- nazwiska/numeru klienta. Wyszukiwanie jest bezwzgledne na wielkosc liter,
-- wiec indeksy zakladamy na wyrazeniu lower(...).

CREATE INDEX IF NOT EXISTS idx_customers_country_id ON customers(country, id);
CREATE INDEX IF NOT EXISTS idx_customers_lower_last_name ON customers(lower(last_name));
CREATE INDEX IF NOT EXISTS idx_customers_lower_email ON customers(lower(email));
CREATE INDEX IF NOT EXISTS idx_customers_lower_customer_number ON customers(lower(customer_number));

CREATE INDEX IF NOT EXISTS idx_customer_coupons_country_issued_at
    ON customer_coupons(country, issued_at DESC);
CREATE INDEX IF NOT EXISTS idx_customer_coupons_customer_issued_at
    ON customer_coupons(customer_id, issued_at DESC);
CREATE INDEX IF NOT EXISTS idx_customer_coupons_status_expires_at
    ON customer_coupons(status, expires_at);
