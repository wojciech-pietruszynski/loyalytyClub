-- Powiadomienia o wygasajacych punktach.
--
-- Punkty przepadaja po 365 dniach, a system nie mial kanalu, ktory by o tym
-- uprzedzil. Tabela pelni role rejestru wyslanych powiadomien: jednoznaczna para
-- (transaction_id, notice_days) gwarantuje, ze dla danej transakcji i danego progu
-- ostrzegawczego powiadomienie powstanie dokladnie raz, niezaleznie od tego ile
-- razy zadanie cykliczne sie uruchomi.

CREATE TABLE point_expiry_notifications (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    transaction_id BIGINT NOT NULL,
    notice_days INTEGER NOT NULL,
    points INTEGER NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP,
    channel VARCHAR(30) NOT NULL,
    CONSTRAINT uq_point_expiry_notifications UNIQUE (transaction_id, notice_days),
    CONSTRAINT fk_point_expiry_notifications_customer
        FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_point_expiry_notifications_transaction
        FOREIGN KEY (transaction_id) REFERENCES transactions(id)
);

CREATE INDEX idx_point_expiry_notifications_customer ON point_expiry_notifications(customer_id);

-- Wyszukiwanie transakcji zblizajacych sie do wygasniecia oraz raport
-- "punkty w obiegu" filtruja po tej samej parze kolumn.
CREATE INDEX IF NOT EXISTS idx_transactions_available_from_expires_at
    ON transactions(available_from, expires_at);
