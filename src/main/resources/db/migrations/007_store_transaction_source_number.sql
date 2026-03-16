ALTER TABLE transactions
    ADD COLUMN source_transaction_number VARCHAR(255);

CREATE UNIQUE INDEX uq_transactions_source_transaction_number
    ON transactions(source_transaction_number)
    WHERE source_transaction_number IS NOT NULL;
