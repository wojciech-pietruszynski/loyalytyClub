-- Cykl zycia uczestnika oraz dorobek punktowy niezalezny od biezacego salda.
--
-- lifetime_points -- suma punktow faktycznie zdobytych (sprzedaz, zwroty towaru,
-- korekty reczne, premie za polecenia). Wymiana punktow na kupon i wygasniecie
-- punktow tej wartosci nie pomniejszaja, dzieki czemu korzystanie z programu
-- nie obniza poziomu lojalnosciowego klienta.
--
-- status -- ACTIVE / INACTIVE / ANONYMIZED. Zamiast usuwania rekordu (kolidujacego
-- z historia transakcji i logiem audytowym) uczestnika dezaktywujemy albo
-- anonimizujemy, zachowujac spojnosc ksiegowa danych.

ALTER TABLE customers ADD COLUMN IF NOT EXISTS lifetime_points INTEGER NOT NULL DEFAULT 0;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE customers ADD COLUMN IF NOT EXISTS status_changed_at TIMESTAMP;

-- Przeliczenie wsteczne z historii transakcji. Do dorobku nie wliczaja sie
-- operacje kuponowe (POINTS_REDEMPTION / POINTS_REFUND) -- patrz migracja 011.
UPDATE customers c
SET lifetime_points = GREATEST(0, COALESCE((
        SELECT SUM(t.points)
        FROM transactions t
        WHERE t.customer_id = c.id
          AND t.type IN ('SALE', 'RETURN', 'MANUAL_ADJUSTMENT', 'REFERRAL')
    ), 0));

-- Data rejestracji nie byla utrwalana; najlepszym dostepnym przyblizeniem jest
-- pierwsza transakcja klienta, a dla klientow bez transakcji -- chwila migracji.
UPDATE customers c
SET created_at = COALESCE(
        (SELECT MIN(t.timestamp) FROM transactions t WHERE t.customer_id = c.id),
        CURRENT_TIMESTAMP)
WHERE created_at IS NULL;

ALTER TABLE customers ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE customers ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_customers_status ON customers(status);
