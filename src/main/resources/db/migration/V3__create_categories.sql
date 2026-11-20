CREATE TABLE categories (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT REFERENCES users (id),
    name       VARCHAR(50)  NOT NULL,
    type       VARCHAR(10)  NOT NULL CHECK (type IN ('INGRESO', 'GASTO')),
    deleted_at TIMESTAMPTZ
);

-- Una categoría sin user_id es predefinida; el nombre es único por dueño y tipo sin distinguir mayúsculas.
CREATE UNIQUE INDEX uq_categories_owner_type_name
    ON categories (COALESCE(user_id, 0), type, lower(name))
    WHERE deleted_at IS NULL;

INSERT INTO categories (user_id, name, type) VALUES
    (NULL, 'Alimentación', 'GASTO'),
    (NULL, 'Transporte', 'GASTO'),
    (NULL, 'Vivienda', 'GASTO'),
    (NULL, 'Cuentas y servicios', 'GASTO'),
    (NULL, 'Salud', 'GASTO'),
    (NULL, 'Educación', 'GASTO'),
    (NULL, 'Entretenimiento', 'GASTO'),
    (NULL, 'Otros', 'GASTO'),
    (NULL, 'Sueldo', 'INGRESO'),
    (NULL, 'Honorarios', 'INGRESO'),
    (NULL, 'Otros', 'INGRESO');

ALTER TABLE movements ADD COLUMN category_id BIGINT;

-- Los movimientos existentes (también los eliminados) pasan a "Otros" de su tipo.
UPDATE movements m
   SET category_id = c.id
  FROM categories c
 WHERE c.user_id IS NULL AND c.name = 'Otros' AND c.type = m.type;

ALTER TABLE movements ALTER COLUMN category_id SET NOT NULL;
ALTER TABLE movements ADD CONSTRAINT fk_movements_category FOREIGN KEY (category_id) REFERENCES categories (id);
