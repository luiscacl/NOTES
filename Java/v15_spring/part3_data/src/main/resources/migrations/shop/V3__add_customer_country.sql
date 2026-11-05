-- V3: una columna OBLIGATORIA nueva en una tabla que YA TIENE DATOS. En tres pasos:
alter table customers add column country_code varchar(2) references countries (code);   -- 1) agregarla opcional
update customers set country_code = 'MX' where country_code is null;                     -- 2) llenar las filas viejas
alter table customers alter column country_code set not null;                            -- 3) ahora sí, obligatoria
