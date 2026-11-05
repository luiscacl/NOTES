-- Una migración que falla A LA MITAD: la primera sentencia funciona, la segunda no.
create table tag_aliases (alias varchar(30) primary key);
insert into tabla_que_no_existe (x) values (1);
