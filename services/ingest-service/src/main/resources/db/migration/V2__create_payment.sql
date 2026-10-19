CREATE TABLE payment (
    id                     UUID PRIMARY KEY,
    idempotency_key        VARCHAR(100) NOT NULL UNIQUE,
    fingerprint            VARCHAR(64)  NOT NULL,
    debtor_iban            VARCHAR(34)  NOT NULL,
    creditor_iban          VARCHAR(34)  NOT NULL,
    amount                 NUMERIC(12, 2) NOT NULL,
    currency               VARCHAR(3)   NOT NULL,
    reference_type         VARCHAR(4)   NOT NULL,
    reference              VARCHAR(27)  NOT NULL,
    remittance_information VARCHAR(140) NOT NULL,
    status                 VARCHAR(16)  NOT NULL,
    rejection_code         VARCHAR(40),
    rejection_reason       VARCHAR(200),
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL
);
