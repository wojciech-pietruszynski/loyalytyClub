-- Rejestr rozliczonych polecen. Do tej pory relacja "kto kogo polecil" byla
-- utrwalana (migracja 010), ale nic za nia nie przyznawalo punktow.
--
-- Jednoznaczny indeks na referred_customer_id realizuje regule "jedna premia na
-- poleconego uczestnika": powtorne wywolanie naliczenia (np. przy drugim zakupie
-- albo przy rownoleglym zapisie z dwoch kas) konczy sie naruszeniem ograniczenia,
-- a nie podwojna premia.

CREATE TABLE referral_rewards (
    id BIGSERIAL PRIMARY KEY,
    referrer_customer_id BIGINT NOT NULL,
    referred_customer_id BIGINT NOT NULL,
    qualifying_transaction_id BIGINT,
    referrer_transaction_id BIGINT,
    referred_transaction_id BIGINT,
    referrer_points INTEGER NOT NULL,
    referred_points INTEGER NOT NULL,
    country VARCHAR(3) NOT NULL,
    awarded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_referral_rewards_referred UNIQUE (referred_customer_id),
    CONSTRAINT fk_referral_rewards_referrer
        FOREIGN KEY (referrer_customer_id) REFERENCES customers(id),
    CONSTRAINT fk_referral_rewards_referred
        FOREIGN KEY (referred_customer_id) REFERENCES customers(id),
    CONSTRAINT fk_referral_rewards_qualifying_transaction
        FOREIGN KEY (qualifying_transaction_id) REFERENCES transactions(id),
    CONSTRAINT fk_referral_rewards_referrer_transaction
        FOREIGN KEY (referrer_transaction_id) REFERENCES transactions(id),
    CONSTRAINT fk_referral_rewards_referred_transaction
        FOREIGN KEY (referred_transaction_id) REFERENCES transactions(id)
);

CREATE INDEX idx_referral_rewards_referrer ON referral_rewards(referrer_customer_id);
