CREATE TABLE budgets (
    id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT      NOT NULL REFERENCES users (id),
    month   VARCHAR(7)  NOT NULL CHECK (month ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    amount  BIGINT      NOT NULL CHECK (amount BETWEEN 1 AND 1000000000000),
    CONSTRAINT uq_budgets_user_month UNIQUE (user_id, month)
);

CREATE TABLE budget_lines (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    budget_id   BIGINT NOT NULL REFERENCES budgets (id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories (id),
    amount      BIGINT NOT NULL CHECK (amount BETWEEN 1 AND 1000000000000),
    CONSTRAINT uq_budget_lines_budget_category UNIQUE (budget_id, category_id)
);
