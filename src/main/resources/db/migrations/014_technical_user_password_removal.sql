-- Usuniecie jawnego hasla konta technicznego.
--
-- Kolumna password_preview przechowywala haslo obok skrotu BCrypt, aby administrator
-- mogl je pozniej odczytac. Przejecie kopii bazy oznaczalo natychmiastowe przejecie
-- wszystkich kont technicznych. Haslo jest teraz prezentowane wylacznie raz --
-- w odpowiedzi na utworzenie konta albo na reset inicjowany przez role ADMIN.

ALTER TABLE technical_users DROP COLUMN IF EXISTS password_preview;

-- Data ostatniej zmiany hasla; wykorzystywana przy wygaszaniu wydanych tokenow
-- (patrz migracja 016) oraz w raportowaniu bezpieczenstwa.
ALTER TABLE technical_users ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP;
UPDATE technical_users SET password_changed_at = CURRENT_TIMESTAMP WHERE password_changed_at IS NULL;

-- BaseUser jest wspolna nadklasa kont admin/store/ecom, wiec kolumna musi istniec
-- w kazdej z trzech tabel.
ALTER TABLE admin_users ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP;
UPDATE admin_users SET password_changed_at = CURRENT_TIMESTAMP WHERE password_changed_at IS NULL;

ALTER TABLE store_users ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP;
UPDATE store_users SET password_changed_at = CURRENT_TIMESTAMP WHERE password_changed_at IS NULL;

ALTER TABLE ecom_users ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP;
UPDATE ecom_users SET password_changed_at = CURRENT_TIMESTAMP WHERE password_changed_at IS NULL;
