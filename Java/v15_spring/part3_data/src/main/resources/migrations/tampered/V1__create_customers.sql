-- V1 EDITADA después de aplicarse (lo que NO se debe hacer): alguien le agregó una columna.
create sequence customers_seq start with 1 increment by 50;

create table customers (
    id    bigint       primary key,
    name  varchar(100) not null,
    email varchar(200) not null unique,
    phone varchar(20)
);
