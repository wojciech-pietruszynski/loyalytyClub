-- Uogolniony klucz idempotencji dla operacji modyfikujacych saldo punktow.
--
-- Ochrone przed podwojnym naliczeniem dawal dotad wylacznie czesciowy indeks
-- unikalny na numerze dokumentu kasowego (migracja 007) -- transakcje recznej
-- korekty takiego numeru nie posiadaja, wiec korekta wykonana dwukrotnie
-- zapisywala sie dwa razy. Tabela dziala na tej samej zasadzie co
-- coupon_redemption_requests (migracja 008), ale nie jest zwiazana z jednym
-- przypadkiem uzycia: klucz jest unikalny w obrebie nazwy operacji.
--
-- request_fingerprint pozwala odroznic powtorzenie tego samego zadania
-- (odpowiadamy zapisanym wynikiem) od uzycia tego samego klucza do innej
-- tresci (odrzucamy).

CREATE TABLE idempotent_operations (
    id BIGSERIAL PRIMARY KEY,
    operation VARCHAR(60) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint VARCHAR(128) NOT NULL,
    result_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_idempotent_operations_operation_key UNIQUE (operation, idempotency_key)
);

CREATE INDEX idx_idempotent_operations_created_at ON idempotent_operations(created_at);
