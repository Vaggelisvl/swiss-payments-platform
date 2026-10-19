CREATE TABLE account (
    iban     VARCHAR(34) PRIMARY KEY,
    currency VARCHAR(3)  NOT NULL,
    balance  NUMERIC(14, 2) NOT NULL CHECK (balance >= 0),
    version  BIGINT      NOT NULL DEFAULT 0
);

-- Double-entry style journal: every booking writes a debit (negative) and, for internal creditors, a credit (positive).
CREATE TABLE ledger_entry (
    id         UUID PRIMARY KEY,
    payment_id UUID           NOT NULL,
    iban       VARCHAR(34)    NOT NULL,
    amount     NUMERIC(14, 2) NOT NULL,
    currency   VARCHAR(3)     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_ledger_payment ON ledger_entry (payment_id);

-- One row per processed payment: makes the consumer idempotent (at-least-once delivery).
CREATE TABLE processed_payment (
    payment_id    UUID PRIMARY KEY,
    outcome       VARCHAR(16) NOT NULL,
    reason_code   VARCHAR(40),
    processed_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
