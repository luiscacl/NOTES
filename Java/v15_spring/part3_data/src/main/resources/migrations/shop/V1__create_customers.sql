-- V1: la primera versión del esquema.
create sequence customers_seq start with 1 increment by 50;   -- el de @SequenceGenerator (allocationSize = 50)

create table customers (
    id    bigint       primary key,
    name  varchar(100) not null,
    email varchar(200) not null unique
);
