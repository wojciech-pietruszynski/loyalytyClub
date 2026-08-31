-- Rozdzielenie operacji punktowych, ktore do tej pory dzielily typ MANUAL_ADJUSTMENT.
--
-- Wymiana punktow na kupon nie jest korekta reczna: nie jest zdarzeniem handlowym
-- i nie moze pomniejszac dorobku klienta liczonego do poziomu lojalnosciowego
-- (kolumna lifetime_points dodawana w migracji 012). Analogicznie zwrot punktow
-- przy anulowaniu kuponu nie moze tego dorobku podwyzszac.
--
-- Nowe wartosci kolumny transactions.type:
--   POINTS_REDEMPTION -- pobranie punktow przy wydaniu kuponu (ujemne),
--   POINTS_REFUND     -- zwrot punktow przy anulowaniu kuponu (dodatnie),
--   REFERRAL          -- premia za polecenie (dodatnie).

UPDATE transactions
SET type = 'POINTS_REDEMPTION'
WHERE type = 'MANUAL_ADJUSTMENT'
  AND points < 0
  AND description LIKE 'Issued coupon (points exchange):%';

CREATE INDEX IF NOT EXISTS idx_transactions_customer_id_type
    ON transactions(customer_id, type);
