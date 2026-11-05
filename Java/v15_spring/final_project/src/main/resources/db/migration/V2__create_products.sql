-- El catálogo. El CHECK es la última defensa: aunque un error de código lo intentara, la base
-- no acepta existencias negativas (part3_data/.../jdbc/JdbcAndTransactions).
CREATE TABLE products (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    price_cents BIGINT       NOT NULL CHECK (price_cents > 0),
    stock       INTEGER      NOT NULL CHECK (stock >= 0)
);
