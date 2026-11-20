CREATE TABLE movements (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    amount      BIGINT       NOT NULL CHECK (amount BETWEEN 1 AND 1000000000000),
    type        VARCHAR(10)  NOT NULL CHECK (type IN ('INGRESO', 'GASTO')),
    occurred_at TIMESTAMPTZ  NOT NULL,
    description VARCHAR(200),
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX idx_movements_user_occurred ON movements (user_id, occurred_at DESC, id DESC);
