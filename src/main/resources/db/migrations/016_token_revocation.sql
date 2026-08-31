-- Uniewaznianie tokenow JWT.
--
-- Token jest bezstanowy, wiec sam z siebie pozostaje wazny do konca okresu
-- waznosci; wylogowanie nie mialo dotad zadnego skutku po stronie serwera.
-- Wprowadzamy dwa uzupelniajace sie mechanizmy:
--
--   revoked_tokens     -- pojedynczy token uniewazniony przez wylogowanie,
--                         identyfikowany deklaracja jti,
--   user_token_cutoffs -- granica czasowa dla konta: kazdy token wydany przed
--                         not_before jest odrzucany. Ustawiana przy zmianie
--                         i resecie hasla oraz przy dezaktywacji konta, dzieki
--                         czemu jedna operacja unieważnia wszystkie sesje.
--
-- Oba zbiory sa male: wpisy z revoked_tokens kasuje zadanie porzadkowe po
-- uplywie waznosci tokenu, a user_token_cutoffs ma jeden wiersz na konto.

CREATE TABLE revoked_tokens (
    id BIGSERIAL PRIMARY KEY,
    token_id VARCHAR(64) NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_revoked_tokens_expires_at ON revoked_tokens(expires_at);

CREATE TABLE user_token_cutoffs (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    not_before TIMESTAMP NOT NULL,
    reason VARCHAR(100) NOT NULL
);
