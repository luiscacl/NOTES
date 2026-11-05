-- Esquema de la guía jdbc/JdbcAndTransactions. Lo ejecuta Spring al arrancar
-- (spring.sql.init.*). Para versionar el esquema en serio: data/Migrations (Flyway).

create table if not exists account (
    id            bigint       primary key,
    owner         varchar(100) not null,
    balance_cents bigint       not null check (balance_cents >= 0)   -- la base de datos también protege
);

create table if not exists audit_log (
    id         bigserial    primary key,
    message    varchar(200) not null,
    created_at timestamp    not null default now()
);

create table if not exists product (
    sku   varchar(20)  primary key,
    name  varchar(100) not null,
    stock int          not null check (stock >= 0)
);
