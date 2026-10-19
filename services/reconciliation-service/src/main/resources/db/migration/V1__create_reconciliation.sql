CREATE TABLE invoice (
    id           UUID PRIMARY KEY,
    reference    VARCHAR(27)    NOT NULL UNIQUE,
    reference_type VARCHAR(4)   NOT NULL,
    amount       NUMERIC(12, 2) NOT NULL,
    currency     VARCHAR(3)     NOT NULL,
    paid_amount  NUMERIC(12, 2) NOT NULL DEFAULT 0,
    status       VARCHAR(20)    NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Every incoming credit is booked exactly once, whatever its source (primary key = source-scoped entry id).
CREATE TABLE credit_entry (
    entry_id     VARCHAR(100) PRIMARY KEY,
    source       VARCHAR(20)    NOT NULL,
    reference    VARCHAR(27),
    amount       NUMERIC(12, 2) NOT NULL,
    currency     VARCHAR(3)     NOT NULL,
    booked_on    DATE,
    invoice_id   UUID,
    result       VARCHAR(20)    NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_credit_result ON credit_entry (result);
