-- Pedidos y sus renglones. El renglón COPIA el nombre y el precio del producto: si mañana cambia
-- el precio, el pedido de hoy debe seguir diciendo lo que el cliente pagó.
CREATE TABLE orders (
    id          BIGSERIAL   PRIMARY KEY,
    customer    VARCHAR(50) NOT NULL REFERENCES users (username),
    status      VARCHAR(20) NOT NULL CHECK (status IN ('PLACED', 'SHIPPED', 'CANCELLED')),
    created_at  TIMESTAMPTZ NOT NULL,
    total_cents BIGINT      NOT NULL CHECK (total_cents > 0)
);

CREATE INDEX idx_orders_customer ON orders (customer, created_at DESC);   -- "mis pedidos", lo más nuevo primero

CREATE TABLE order_lines (
    id               BIGSERIAL    PRIMARY KEY,
    order_id         BIGINT       NOT NULL REFERENCES orders (id),
    product_id       BIGINT       NOT NULL REFERENCES products (id),
    product_name     VARCHAR(120) NOT NULL,
    unit_price_cents BIGINT       NOT NULL CHECK (unit_price_cents > 0),
    quantity         INTEGER      NOT NULL CHECK (quantity > 0)
);

CREATE INDEX idx_order_lines_order ON order_lines (order_id);
