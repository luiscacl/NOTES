-- V2: una tabla de referencia, con sus datos fijos (los datos de referencia sí van en migraciones).
create table countries (
    code varchar(2)  primary key,
    name varchar(60) not null
);

insert into countries (code, name) values ('MX', 'México'), ('US', 'Estados Unidos'), ('CA', 'Canadá');
