# Transacciones, concurrencia y aislamiento

**Bloque 1 — Qué es una transacción: autocommit y las cuatro garantías ACID**

- [Sección 1.1 — Qué es una transacción](#1-que-es)
- [Sección 1.2 — Un SELECT también abre una transacción](#1-select-abre)
- [Sección 1.3 — Toda operación ocurre siempre dentro de una transacción](#1-toda-operacion)
- [Sección 1.4 — Transacciones y autocommit en motores no relacionales](#1-nosql)
- [Sección 1.5 — Autocommit: quién decide cuándo hacer BEGIN y COMMIT](#1-autocommit)
- [Sección 1.6 — Por qué el estándar dice una cosa y la mayoría de los clientes hacen otra](#1-estandar-vs-clientes)
- [Sección 1.7 — DDL y autocommit: comportamiento diferente por motor](#1-ddl-autocommit)
- [Sección 1.8 — Errores comunes con autocommit](#1-errores-autocommit)
- [Sección 1.9 — Las cuatro garantías: ACID](#1-acid)

**Bloque 2 — MVCC, locks y niveles de aislamiento**

- [Sección 2.1 — Las versiones de fila: qué son y dónde viven físicamente](#2-versiones-fila)
- [Sección 2.2 — El snapshot: el criterio, y por qué es "de toda la base de datos"](#2-snapshot)
- [Sección 2.3 — Los locks: los dos tipos base y su relación con MVCC](#2-locks-base)
- [Sección 2.4 — Qué pasa con un UPDATE normal](#2-update-normal)
- [Sección 2.5 — Por qué el SELECT normal no bloquea](#2-select-no-bloquea)
- [Sección 2.6 — El problema: MVCC no protege el patrón read-modify-write](#2-read-modify-write)
- [Sección 2.7 — SELECT FOR UPDATE: adquirir el lock desde la lectura](#2-for-update)
- [Sección 2.8 — Por qué SELECT FOR UPDATE debe estar dentro de una transacción](#2-for-update-transaccion)
- [Sección 2.9 — SELECT FOR SHARE: permitir lectores concurrentes, bloquear escritores](#2-for-share)
- [Sección 2.10 — Qué locks toma la base de datos y cuándo se liberan](#2-locks-cuando-liberan)
- [Sección 2.11 — Timeouts de lock: qué pasa cuando la espera es demasiado larga](#2-timeouts)
- [Sección 2.12 — UPDATE atómico vs SELECT FOR UPDATE](#2-update-vs-for-update)
- [Sección 2.13 — El mecanismo: cómo el nivel afecta snapshots](#2-mecanismo-niveles)
- [Sección 2.14 — Cuándo usar cada nivel: guía práctica con advertencias](#2-guia-niveles)
- [Sección 2.15 — Los ORM no cambian nada de esto, solo lo empaquetan](#2-orm)

**Bloque 3 — Anomalías de concurrencia: referencia**

- [Sección 3.1 — Anomalías de lectura](#3-anomalias-lectura)
- [Sección 3.2 — Dirty read](#3-dirty-read)
- [Sección 3.3 — Non-repeatable read](#3-non-repeatable-read)
- [Sección 3.4 — Phantom read](#3-phantom-read)
- [Sección 3.5 — Anomalías de escritura](#3-anomalias-escritura)
- [Sección 3.6 — Lost update](#3-lost-update)
- [Sección 3.7 — Write skew](#3-write-skew)
- [Sección 3.8 — Resumen](#3-resumen)

**Bloque 4 — Savepoints y transacciones anidadas**

- [Sección 4.1 — El problema que resuelven los savepoints](#4-problema)
- [Sección 4.2 — Qué es un savepoint y cómo se usa](#4-que-es)
- [Sección 4.3 — Cómo los implementa el motor: WAL y sub-transacciones](#4-implementacion)
- [Sección 4.4 — Locks y savepoints: un comportamiento crítico](#4-locks)
- [Sección 4.5 — Savepoints ≠ transacciones anidadas reales](#4-no-son-anidadas)
- [Sección 4.6 — Cuándo usarlos](#4-cuando-usarlos)
- [Sección 4.7 — Por motor](#4-por-motor)

**Bloque 5 — El patrón universal: aislamiento y consistencia en cualquier motor**

- [Sección 5.1 — Pregunta 1: escrituras concurrentes sin lectura de por medio](#5-pregunta-1)
- [Sección 5.2 — Pregunta 2: qué le muestro a quien lee mientras otros escriben](#5-pregunta-2)
- [Sección 5.3 — Pregunta 3: cómo evito que una escritura se base en una decisión obsoleta](#5-pregunta-3)
- [Sección 5.4 — El costo de sostener la transacción abierta](#5-costo-sostener)
- [Sección 5.5 — Guía comparativa: las preguntas 2 y 3, por motor](#5-guia-comparativa)
- [Sección 5.6 — Sharding no es lo mismo que nacer distribuido](#5-sharding-vs-distribuido)
- [Sección 5.7 — Un puente de vocabulario con el resto de la industria](#5-vocabulario)
- [Sección 5.8 — Resumen rápido de alcance](#5-resumen-alcance)
- [Sección 5.9 — El patrón detrás de la tabla](#5-patron-tabla)
- [Sección 5.10 — Una precisión más sobre la Pregunta 2](#5-precision-pregunta-2)

**Bloque 6 — Granularidad de locks e intent locks**

- [Sección 6.1 — Jerarquía de granularidad](#6-jerarquia)
- [Sección 6.2 — Tipos de locks](#6-tipos-locks)
- [Sección 6.3 — Intent locks: el protocolo de jerarquía](#6-intent-locks)
- [Sección 6.4 — Lock escalation (SQL Server)](#6-lock-escalation)
- [Sección 6.5 — SELECT FOR UPDATE: variantes](#6-for-update-variantes)
- [Sección 6.6 — Deadlocks](#6-deadlocks)
- [Sección 6.7 — Ver locks activos en producción](#6-ver-locks)
- [Sección 6.8 — Advisory locks en PostgreSQL](#6-advisory-locks)

**Bloque 7 — Mecanismos avanzados: gap locks en MySQL y SSI en PostgreSQL**

- [Sección 7.1 — Gap locks en MySQL InnoDB (next-key locks)](#7-gap-locks)
- [Sección 7.2 — Snapshot Isolation (SI) vs Serializable Snapshot Isolation (SSI)](#7-si-vs-ssi)
- [Sección 7.3 — Retry: el contrato que exigen SERIALIZABLE, los deadlocks y el first-committer-wins](#7-retry)

**Bloque 8 — Transacciones distribuidas: motores nacidos distribuidos, sharding, y coordinación entre servicios**

- [Sección 8.1 — Mapa visual: ¿el motor nació distribuido, o le instalaron sharding encima?](#8-mapa-visual)
- [Sección 8.2 — Coordinación entre servicios distintos: el problema que ningún motor resuelve solo](#8-coordinacion-servicios)
- [Sección 8.3 — La alternativa moderna: Saga y Outbox](#8-saga-outbox)

---

**Alcance de este bloque**

Este bloque cubre transacciones, ACID, MVCC, locks, niveles de aislamiento, anomalías de concurrencia, savepoints, SSI y transacciones distribuidas (Bloque 1 a Bloque 8). Antes de entrar en el detalle, así de lejos se generaliza cada tipo de dato que vas a encontrar.

**Qué es 100% universal** (ocurre en cualquier sistema de datos con acceso concurrente):
toda transacción es, en el fondo, la misma promesa (atomicidad, consistencia,
aislamiento, durabilidad) sin importar el motor. Las anomalías del Bloque 3, dirty
read, non-repeatable read, phantom read, lost update y write skew, pueden ocurrir
en PostgreSQL, MySQL, MongoDB, Cassandra o Redis si no se toman las precauciones
correctas. El problema de concurrencia no es exclusivo de SQL.

**Qué aplica a motores SQL en general** (con variaciones de implementación):
autocommit y su default por motor (Bloque 1), MVCC y locks como mecanismo (Bloque 2),
savepoints como marcador de retorno dentro de una transacción (Bloque 4), isolation
levels (Bloque 2 y Bloque 5). Motores no relacionales resuelven la misma pregunta, qué
tamaño tiene la unidad atómica por defecto, con sus propios mecanismos, ver la
nota al final del Bloque 1.

**Qué es específico de un motor concreto:**
gap locks en MySQL InnoDB (Bloque 7), SSI (Serializable Snapshot Isolation) en
PostgreSQL (Bloque 7), xmin/xmax en la implementación de MVCC de PostgreSQL (el
detalle mecánico completo ya se explicó en Bloque 2, "El snapshot"), savepoints en
la forma descrita son SQL estándar (MongoDB los tiene parcialmente, Cassandra
no los tiene).

Cómo MongoDB, Cassandra, DynamoDB y Redis manejan (o no) estas mismas
anomalías con su propio vocabulario ya se explica al final del Bloque 3, en la
sección de Write skew.

## Bloque 1: Qué es una transacción: autocommit y las cuatro garantías ACID {#1-transacciones-acid}

Antes de entrar en cómo compiten las transacciones entre sí, hace falta establecer qué es una transacción y cuándo existe una. Esta sección responde eso en dos partes: primero qué es y cuándo empieza y termina (autocommit), después qué garantiza mientras está abierta (ACID). Todo lo que sigue en este bloque, anomalías, locks, niveles de aislamiento, ocurre dentro de los límites que esta sección define.

### Sección 1.1: Qué es una transacción {#1-que-es}

Una transacción es la unidad de trabajo que el motor trata como indivisible frente al resto del sistema. Lo que la define no es cuántos statements agrupa, es que el motor siempre la resuelve completa o no la resuelve, nunca deja un resultado a medias visible para nadie más. Puede contener una sola operación o muchas, y en ambos casos sigue siendo, por definición, una transacción.

**Con una sola operación, sin agrupar nada explícitamente:**

```sql
UPDATE cuentas SET saldo = saldo * 1.02 WHERE region = 'norte';
-- afecta 40,000 filas. El motor las actualiza todas o ninguna,
-- incluso si el proceso se cae a mitad de las 40,000.
-- Este statement, solo, ya es una transacción completa.
```

**Con varias operaciones agrupadas explícitamente:**

```sql
BEGIN;
UPDATE cuentas SET saldo = saldo - 100 WHERE id = 1;
UPDATE cuentas SET saldo = saldo + 100 WHERE id = 2;
COMMIT;
-- ambos UPDATE se tratan como un solo bloque. O quedan los dos, o ninguno.
```

Si algo falla antes del `COMMIT`, `ROLLBACK` revierte todo al estado anterior al `BEGIN`, sin importar cuántos statements hubo en medio.

**Incluso con una operación que no escribe nada:**

```sql
SELECT * FROM cuentas WHERE id = 1;
-- no modifica ningún dato, pero el motor la envuelve en una transacción
-- de todas formas, para que la lectura corresponda a un instante fijo
-- del tiempo, sin importar qué otras transacciones escriban mientras corre.
```

Aquí no hay nada que revertir si algo falla, así que la atomicidad frente a un error no es lo que está en juego. Pero sigue siendo una transacción porque necesita la otra garantía que ofrece cualquier transacción: ver un estado consistente de los datos en un momento dado, sin que una escritura ajena a medio terminar se filtre en la lectura. Es la misma unidad de trabajo de los dos ejemplos anteriores, aplicada a una lectura en vez de una escritura.

Los tres ejemplos son la misma idea en distintas escalas y con distintos propósitos. `BEGIN...COMMIT` no es lo que crea la transacción, es la herramienta para agrupar explícitamente varias operaciones que de otro modo el motor trataría por separado, una transacción por statement, sea ese statement una escritura o una lectura.

### Sección 1.2: Un SELECT también abre una transacción {#1-select-abre}

Falta ver qué gana un `SELECT` a cambio de abrir esa transacción, y por qué eso tiene consecuencias reales si se queda abierta demasiado tiempo. Un `SELECT` no crea ni guarda nada: las versiones de una fila las crean las escrituras (`INSERT`, `UPDATE`, `DELETE`), no las lecturas. Lo que un `SELECT` gana es el derecho a usar un snapshot, una regla que ya existe en el motor y que le garantiza una lectura consistente, anclada a un instante fijo, eligiendo entre las versiones que otras transacciones ya dejaron guardadas. Cómo crea cada tipo de escritura esas versiones, y cómo funciona la regla del snapshot a detalle, se explica en Bloque 2.

```sql
SELECT * FROM usuarios WHERE id = 1;
```

Internamente:

```
BEGIN;  (implícito)
  SELECT → el motor le asigna un snapshot MVCC: verás solo los datos confirmados
           hasta este instante exacto. Si alguien escribe filas nuevas mientras
           tu SELECT corre, no las verás. No hay estados parciales visibles.
COMMIT; (implícito con autocommit=TRUE)
```

La transacción en el `SELECT` no sirve para escribir nada, sirve para garantizar que la lectura es **consistente**: todos los datos que lees corresponden al mismo momento en el tiempo, sin importar qué hagan otras transacciones concurrentes. Qué es exactamente ese snapshot y cómo decide qué versión de cada fila mostrar es el tema central del Bloque 2.

Esto explica también el problema de **"idle in transaction"**: si tu sesión tiene autocommit desactivado y ejecutas un SELECT pero olvidas hacer commit, la transacción queda abierta indefinidamente con su snapshot activo. En PostgreSQL eso tiene consecuencias reales: vacuum no puede reclamar versiones antiguas de filas mientras haya un snapshot activo que todavía podría necesitarlas (el mecanismo completo, con línea de tiempo, está en Bloque 2; el detalle de por qué está en `postgresql_caso_estudio.md` §7). Una sola transacción olvidada puede causar table bloat en producción, porque el motor se ve obligado a seguir guardando cada vez más versiones viejas sin poder limpiarlas nunca.

```
En pg_stat_activity verás:
  state:      'idle in transaction'   ← conexión activa pero sin hacer nada
  xact_start: hace 45 minutos        ← sosteniendo un snapshot desde hace 45 min
  query:      'SELECT * FROM ...'    ← la última query que ejecutó
```

### Sección 1.3: Toda operación ocurre siempre dentro de una transacción {#1-toda-operacion}

Esto aplica sin excepción, incluso a los statements que no escriben ningún `BEGIN`, y sin importar si el statement lee o escribe, como ya se vio arriba con el `SELECT`. Un `UPDATE`, un `CREATE TABLE`, literalmente cualquier cosa, ocurre siempre dentro de una transacción. La pregunta nunca es "¿está en una transacción?", siempre es "¿quién decide dónde empieza y dónde termina esa transacción, tú o el motor?". Eso es exactamente lo que decide el autocommit, que se explica a continuación.

Cuando escribes un `UPDATE` sin `BEGIN` ni `COMMIT`, el motor no lo ejecuta suelto. Internamente siempre pasa esto:

```
1. El motor inicia una transacción
   (asigna un ID de transacción, registra el estado anterior en el log de durabilidad)
2. Ejecuta el statement
3. Verifica constraints (NOT NULL, UNIQUE, FK, CHECK...)
4. Si todo va bien  →  COMMIT: los cambios quedan permanentes
   Si algo falla   →  ROLLBACK: los cambios se deshacen completamente
```

### Sección 1.4: Transacciones y autocommit en motores no relacionales {#1-nosql}

La pregunta de fondo, qué tan grande es la unidad atómica por defecto y qué hace falta para agrupar algo más grande que eso, no es exclusiva de SQL. Cada motor no relacional la resuelve con su propio mecanismo:

| Motor     | Unidad atómica por defecto                                                  | Cómo agrupar más que eso                                                                                                                     |
| --------- | --------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| MongoDB   | Una escritura a un solo documento, siempre atómica sin abrir nada explícito | Transacciones multi-documento explícitas (`startTransaction()` / `commitTransaction()`)                                                      |
| Cassandra | Una escritura a una sola partition, siempre atómica                         | No existen transacciones multi-fila reales, solo Lightweight Transactions (`IF ...`) como check-and-set puntual                              |
| DynamoDB  | Una escritura a un solo item, siempre atómica                               | `TransactWriteItems`, agrupa hasta 100 items en una operación atómica real                                                                   |
| Redis     | Cada comando se ejecuta solo, de forma atómica gracias al single thread     | `MULTI`/`EXEC` agrupa comandos en un bloque sin interrupción de otros clientes, pero sin rollback si un comando falla en tiempo de ejecución |

El patrón se repite en los cuatro. La unidad atómica implícita es deliberadamente pequeña, un documento, una partition, un item, un comando, y agrupar algo más grande que eso siempre es una decisión explícita que alguien tiene que pedir, nunca el comportamiento por defecto. Es la misma idea detrás de "toda operación ocurre siempre dentro de una transacción" en SQL, con otro vocabulario. Con esa idea general establecida, así es como funciona el mecanismo concreto en SQL.

### Sección 1.5: Autocommit: quién decide cuándo hacer BEGIN y COMMIT {#1-autocommit}

**Autocommit es una propiedad de la sesión, no un comando SQL que cambie el resultado de una query.** Decide una sola cosa, si cada statement que envías se confirma solo, en su propia transacción de una sola operación, o si varios statements quedan agrupados en la misma transacción hasta que tú confirmes explícitamente.

```sql
-- Con autocommit activado, esto:
UPDATE usuarios SET nombre = 'Ana' WHERE id = 1;

-- es exactamente esto, ejecutado por el motor sin que lo veas:
BEGIN;
UPDATE usuarios SET nombre = 'Ana' WHERE id = 1;
COMMIT;   -- automático, invisible para ti

-- Con autocommit desactivado, el motor abre la transacción igual,
-- al recibir el primer statement, pero no la cierra sola:
BEGIN;    -- automático, al recibir el primer statement
UPDATE usuarios SET nombre = 'Ana' WHERE id = 1;
          -- el COMMIT queda pendiente hasta que lo pidas explícitamente.
          -- Si la sesión termina sin COMMIT, el motor hace ROLLBACK, sin avisos.
```

El mecanismo interno es idéntico en los dos casos. Lo único que cambia es quién decide cuándo cerrar la transacción, el motor de forma automática o tú de forma explícita.

Algunos motores exponen este modo directamente como un statement de SQL, aplicado a toda la sesión:

```sql
-- MySQL: cambia el modo de toda la sesión actual
SET autocommit = 0;   -- cada statement queda pendiente de COMMIT explícito
SET autocommit = 1;   -- vuelve a confirmar cada statement por separado

-- SQL Server: el default es lo opuesto, autocommit por statement
SET IMPLICIT_TRANSACTIONS ON;  -- deja de autoconfirmar cada statement
```

PostgreSQL y Oracle no tienen un statement de SQL equivalente. Ahí el modo lo decide la sesión o el cliente que abre la conexión, antes de que llegue ningún statement, no algo que se cambie desde dentro de la propia sesión SQL. En la práctica esto significa que el default que ves al conectarte depende también de qué cliente usas para conectarte, no solo del motor, y conviene confirmarlo la primera vez que trabajas con una combinación nueva en vez de asumirlo.

### Sección 1.6: Por qué el estándar dice una cosa y la mayoría de los clientes hacen otra {#1-estandar-vs-clientes}

El estándar SQL (SQL-92) define que las transacciones son implícitas por defecto, todo statement corre dentro de una transacción que alguien debe confirmar o abortar explícitamente. Eso es autocommit desactivado. En la práctica, la mayoría de los clientes interactivos y las librerías de conexión activan autocommit por defecto, por conveniencia, si escribes un `UPDATE` suelto en una sesión interactiva, probablemente quieres que se aplique de inmediato sin tener que escribir `COMMIT` después. El resultado es que el comportamiento que ves al conectarte no siempre coincide con el default que define el estándar, y además varía entre clientes del mismo motor. Es una razón más para confirmar el modo explícitamente al abrir cualquier sesión nueva, en vez de asumirlo por costumbre.

### Sección 1.7: DDL y autocommit: comportamiento diferente por motor {#1-ddl-autocommit}

**DDL (Data Definition Language)** es el subconjunto de SQL que define o modifica la estructura de la base de datos, no los datos que contiene: `CREATE TABLE`, `ALTER TABLE`, `DROP INDEX`, `CREATE INDEX`, entre otros. Se distingue del DML (Data Manipulation Language: `INSERT`, `UPDATE`, `DELETE`, `SELECT`), que opera sobre las filas dentro de una estructura que ya existe.

Hay algo que divide radicalmente a los motores: si el DDL participa en las transacciones igual que el DML, o las ignora.

**PostgreSQL, SQL Server, SQLite: DDL es transaccional.**

```sql
-- PostgreSQL:
BEGIN;
CREATE TABLE temporal (id INT);
INSERT INTO temporal VALUES (1);
-- algo sale mal:
ROLLBACK;
-- La tabla nunca existió. El CREATE TABLE se revirtió limpiamente.
```

Puedes hacer `ROLLBACK` de un `CREATE TABLE`, `ALTER TABLE` o `DROP TABLE`. Esto es crítico para migraciones: si el script falla a mitad, todo se revierte solo y la base de datos queda exactamente como estaba.

**MySQL, Oracle: DDL fuerza un COMMIT implícito.** En MySQL, cualquier statement DDL ejecuta automáticamente un `COMMIT` de la transacción activa antes de correr, y el DDL en sí es irreversible.

```sql
-- MySQL:
START TRANSACTION;
UPDATE pedidos SET estado = 'procesado' WHERE id = 10;  -- pendiente
ALTER TABLE pedidos ADD COLUMN descuento NUMERIC(10,2);
-- ↑ el ALTER hace COMMIT implícito: el UPDATE anterior quedó permanente
-- Si el ALTER falla a mitad: el UPDATE ya está commiteado. No hay vuelta atrás.
ROLLBACK;
-- Solo deshace lo posterior al ALTER. El UPDATE y el ALTER ya están fijos.
```

| Motor          | DDL en transacción                                             |
| -------------- | -------------------------------------------------------------- |
| **PostgreSQL** | Transaccional. `ROLLBACK` de `CREATE`/`ALTER`/`DROP` funciona. |
| **MySQL**      | `COMMIT` implícito antes de cada DDL. El DDL no es reversible. |
| **SQL Server** | Transaccional, igual que PostgreSQL.                           |
| **Oracle**     | `COMMIT` implícito, igual que MySQL.                           |
| **SQLite**     | Transaccional.                                                 |

### Sección 1.8: Errores comunes con autocommit {#1-errores-autocommit}

**Error 1 — Dar por hecho que un cambio queda guardado antes de que llegue el COMMIT.**

```sql
BEGIN;
UPDATE pedidos SET estado = 'enviado' WHERE id = 42;
-- la sesión termina aquí sin COMMIT (se cierra la conexión, se corta la red,
-- el proceso que la abrió se cae) antes de que se ejecute ningún COMMIT.

-- resultado: ROLLBACK automático. El pedido sigue como estaba.
-- Sin ningún error visible, sin ningún aviso.
```

Nada intermedio hace permanente un cambio. Solo un `COMMIT` que el motor llega a ejecutar. Por eso importa saber si la sesión que abre tu aplicación confirma cada statement sola o espera una orden explícita, exactamente lo que decide el autocommit visto arriba.

**Error 2 — Creer que el autocommit por statement protege de pérdidas parciales.** Con autocommit activado, cada statement es su propia transacción independiente. Si necesitas que dos statements sean atómicos entre sí, o ambos ocurren, o ninguno, el autocommit por statement no ayuda. Hace falta agruparlos explícitamente:

```sql
-- MAL, con autocommit activado son dos transacciones independientes:
UPDATE productos SET stock = stock - 1 WHERE id = 55;
INSERT INTO ventas (producto_id, cantidad) VALUES (55, 1);
-- si el INSERT falla, el stock ya quedó descontado. No hay vuelta atrás,
-- el producto se vendió en el registro de stock pero no en el de ventas.

-- BIEN, agrupados explícitamente en una sola transacción:
BEGIN;
UPDATE productos SET stock = stock - 1 WHERE id = 55;
INSERT INTO ventas (producto_id, cantidad) VALUES (55, 1);
COMMIT;
-- si algo falla antes del COMMIT, ROLLBACK deshace ambos cambios.
```

**Error 3 — Dejar transacciones abiertas ("idle in transaction").**

```sql
BEGIN;
SELECT * FROM pedidos WHERE cliente_id = 501;
-- ... la sesión queda abierta sin llegar a hacer COMMIT ni ROLLBACK ...
-- la transacción sigue viva indefinidamente, con su snapshot activo.
```

Esta transacción olvidada bloquea vacuum en PostgreSQL, puede causar table bloat y degrada el rendimiento con el tiempo (el detalle de por qué está en `postgresql_caso_estudio.md` §7).

Detectarlo en producción:

```sql
-- PostgreSQL: transacciones abiertas por más de 5 minutos sin actividad
SELECT pid, usename, state,
       now() - xact_start AS duracion_abierta,
       query
FROM pg_stat_activity
WHERE state = 'idle in transaction'
  AND xact_start < now() - interval '5 minutes';
```

Prevenirlo con un timeout automático:

```sql
-- Terminar la transacción automáticamente si lleva más de 30s sin actividad:
SET idle_in_transaction_session_timeout = '30s';
```

**Reglas prácticas:**

```
Regla 1: No asumas el modo de autocommit de una sesión nueva, confírmalo.
         El default varía por motor y, dentro del mismo motor, por cliente.
         PostgreSQL y Oracle en particular no tienen un statement de SQL
         para verlo o cambiarlo, depende de cómo se abrió la sesión.

Regla 2: Con autocommit desactivado, ninguna transacción es permanente hasta
         que el COMMIT se ejecuta. Terminar la sesión sin confirmar equivale
         a ROLLBACK. Siempre, sin excepción.

Regla 3: Con autocommit activado, un statement suelto no necesita BEGIN.
         Para operaciones que deben ser atómicas entre sí (mover dinero,
         crear un pedido con sus líneas de detalle), agrúpalas en
         BEGIN...COMMIT explícito.

Regla 4: En PostgreSQL, SQL Server y SQLite las migraciones DDL pueden ir
         dentro de una transacción y revertirse si algo falla. En MySQL
         y Oracle, asume que cada DDL es irreversible y planifica la
         migración en consecuencia.

Regla 5: Monitorea "idle in transaction" en producción.
         Configura idle_in_transaction_session_timeout en PostgreSQL.
         Una transacción olvidada abierta es un problema operacional real.
```

### Sección 1.9: Las cuatro garantías: ACID {#1-acid}

ACID no es un conjunto de cuatro características independientes, es la promesa completa que hace una base de datos relacional sobre qué pasa con los datos bajo cualquier condición. ACID es el nombre que acuñaron los motores relacionales para esa promesa, pero los cuatro problemas que describe, qué pasa si algo falla a mitad, qué reglas se cumplen siempre, qué ve una transacción del trabajo de las demás, qué sobrevive a un COMMIT, existen en cualquier sistema con escrituras concurrentes, tenga o no el nombre ACID. Los motores no relacionales responden las mismas cuatro preguntas con sus propios mecanismos, y a veces solo para una parte de los datos, como ya se vio en la tabla de motores no relacionales de la sección anterior. Cada letra describe un aspecto distinto de esa promesa, y cada una la implementa un mecanismo distinto del motor que vive en una capa concreta del sistema.

| Letra            | Garantiza                                                                    |
| ---------------- | ---------------------------------------------------------------------------- |
| **A**tomicidad   | Qué pasa si algo falla a mitad de una transacción                            |
| **C**onsistencia | Qué reglas deben cumplirse siempre en los datos                              |
| **I**solamiento  | Qué ve cada transacción del trabajo de las demás mientras corren en paralelo |
| **D**urabilidad  | Qué pasa con los datos después del COMMIT                                    |

#### A — Atomicidad

**El concepto:** una transacción, tenga una sola operación o cientos, se resuelve completa o no se resuelve. El motor nunca aplica una parte y pierde el resto, ni entre statements agrupados en un mismo `BEGIN...COMMIT` ni dentro de un solo statement que afecta muchas filas a la vez.

**El problema que resuelve:** es el mismo ejemplo de la transferencia entre cuentas que se usó para definir qué es una transacción, ahora con la pregunta de qué pasa si algo falla a mitad de camino. Sin atomicidad, el dinero podría salir de la cuenta origen y el proceso caerse antes de acreditarlo en el destino.

```sql
BEGIN;
UPDATE cuentas SET saldo = saldo - 100 WHERE id = 1;
-- ↑ el servidor se cae aquí. ¿Qué pasa con el dinero?
UPDATE cuentas SET saldo = saldo + 100 WHERE id = 2;
COMMIT;
```

Sin atomicidad, la cuenta 1 perdió 100 y la cuenta 2 nunca los recibió. Con atomicidad, al recuperarse el motor detecta que la transacción no fue commiteada y la revierte entera. Ambas cuentas quedan exactamente como estaban.

**Dónde vive:** en PostgreSQL, la atomicidad la implementan MVCC y el WAL juntos, versionando la fila y descartando las versiones de transacciones sin COMMIT al recuperarse de un crash; el mecanismo completo, con `xmin`/`xmax` y el algoritmo de recovery, está en `postgresql_caso_estudio.md` §6 y §4. MySQL InnoDB lo resuelve distinto, con un undo log que guarda la operación inversa de cada cambio y la reproduce en orden inverso si hay `ROLLBACK` o crash (ver `01_internals_del_motor.md` §5 para la comparación completa entre ambos mecanismos).

#### C — Consistencia

**El concepto:** la base de datos siempre pasa de un estado válido a otro estado válido. Ninguna transacción puede dejar los datos en un estado que viole las reglas definidas. Un estado a medias que rompa una regla nunca queda visible.

**Importante, C es diferente de A, I y D:** Atomicidad, Aislamiento y Durabilidad son garantías técnicas que el motor implementa completamente por sí solo. Consistencia es un objetivo que el motor ayuda a cumplir, pero que depende de quien diseña el schema: el motor fuerza los constraints que se definan; las reglas de negocio que no están en el schema son responsabilidad de la aplicación.

**Ojo con el nombre, esta C no es la C de CAP:** el teorema CAP también tiene una Consistency, pero ahí significa que todas las réplicas de un sistema distribuido ven el mismo dato al mismo tiempo, un problema de replicación entre nodos. La C de ACID es otra cosa, que los datos respeten las reglas de negocio y los constraints del schema dentro de una sola transacción. Comparten letra y nombre, pero describen problemas distintos, no son la misma garantía vista desde dos ángulos.

**Lo que el motor garantiza, constraints técnicos:**

```sql
CREATE TABLE pedidos (
  id         SERIAL PRIMARY KEY,
  cliente_id INT    NOT NULL REFERENCES clientes(id),  -- FK: el cliente debe existir
  total      NUMERIC CHECK (total > 0),                -- no puede ser negativo o cero
  estado     VARCHAR NOT NULL                          -- no puede ser NULL
);

-- Esto falla: el motor lo rechaza antes de que el cambio sea visible
INSERT INTO pedidos (cliente_id, total, estado)
VALUES (9999, -50, NULL);
-- ERROR: null value in column "estado" violates not-null constraint
-- ERROR: new row for relation "pedidos" violates check constraint
-- ERROR: insert or update violates foreign key constraint
```

El motor verifica estos constraints al ejecutar cada statement (o al hacer COMMIT si son DEFERRABLE). Si alguno falla, aborta la operación y revierte el estado. Gracias a la Atomicidad, nunca queda visible un estado a medias.

**Lo que depende de la aplicación, lógica de negocio:**

```sql
-- El motor no puede saber por sí solo que:
-- "un usuario suspendido no puede hacer nuevos pedidos"
-- "un descuento no puede superar el precio original del producto"
-- "no se puede enviar un pedido si el stock es insuficiente"
-- Esas reglas se implementan en código de aplicación, en triggers,
-- o como constraints CHECK si el schema las puede expresar.
```

La Consistencia depende de la Atomicidad para funcionar: dentro de una transacción puede haber estados transitoriamente "inconsistentes" (una FK sin su referencia entre dos inserts), pero al llegar al COMMIT todo debe estar en orden, o la transacción falla entera.

#### I — Aislamiento

**El concepto:** cuando varias transacciones corren al mismo tiempo, cada una debe operar sobre un estado consistente, sin ver el trabajo parcial de otras, y sin que sus propias escrituras interfieran con las demás de forma silenciosa.

Sin el nivel de aislamiento adecuado, las transacciones concurrentes pueden producir cinco tipos de resultados incorrectos: dirty read, non-repeatable read, phantom read, lost update y write skew. Cada una, con ejemplos, por qué ocurre y cómo se previene en cada motor, tiene su propia sección a partir del Bloque 3 de este mismo archivo, este es el catálogo completo, no una versión resumida.

| Nivel                | Qué garantiza                                                                                               |
| -------------------- | ----------------------------------------------------------------------------------------------------------- |
| **READ UNCOMMITTED** | Puede leer cambios que otras transacciones aún no confirmaron. Sin protección real.                         |
| **READ COMMITTED**   | Solo ve datos ya confirmados. Nivel mínimo sensato, default de PostgreSQL.                                  |
| **REPEATABLE READ**  | Si lees la misma fila dos veces dentro de tu transacción, obtienes el mismo valor. Default de MySQL InnoDB. |
| **SERIALIZABLE**     | Comportamiento idéntico a ejecutar las transacciones de a una, en serie. Máxima protección, mayor costo.    |

Más aislamiento significa menos anomalías posibles, pero también más coordinación entre transacciones y menor paralelismo.

Esta tabla resuelve el aislamiento como lo hace un motor ACID, con locks y snapshots dentro de un solo nodo. La misma pregunta de fondo, qué tan al día están los datos que estás leyendo frente a lo que otros escriben en paralelo, sigue existiendo en sistemas distribuidos, pero ahí se responde con otro vocabulario. La **consistencia tunable** (Cassandra, DynamoDB) deja que decidas por operación cuántas réplicas deben confirmar una lectura o una escritura: si exiges quorum en ambas, el conjunto de réplicas que confirmó la escritura y el que responde la lectura siempre se solapan en al menos una, así que garantizas leer el valor más reciente, al costo de esperar la respuesta de más de un nodo. La **consistencia eventual** va al otro extremo: ninguna réplica coordina en el momento, la lectura responde de inmediato con lo que tenga esa réplica, y la única garantía es que, si dejas de escribir, todas terminan convergiendo al mismo valor, sin decir cuándo. Aislamiento en un motor ACID y consistencia en un sistema distribuido son dos nombres para la misma capa.

#### D — Durabilidad

**El concepto:** después de que el motor responde con COMMIT exitoso, los cambios son permanentes. No hay condición bajo la cual se pierdan, ni un crash del proceso, ni un corte de luz, ni un fallo del OS un milisegundo después del COMMIT. La confirmación es una promesa irrevocable.

**El problema que resuelve:** sin durabilidad, se podría recibir confirmación de un COMMIT y perder los datos si el servidor se cae inmediatamente después, sin ninguna forma de saber si los datos llegaron realmente a disco.

**Dónde vive:** la durabilidad la implementa el write-ahead log de cada motor (el WAL en PostgreSQL, el redo log en MySQL/Oracle, el journal en MongoDB). El principio universal es que antes de que cualquier dato modificado llegue a disco, el cambio ya debe estar registrado en ese log. El flujo completo de un COMMIT en PostgreSQL, con el rol exacto del buffer pool, el WAL buffer y `fsync()`, está en `postgresql_caso_estudio.md` §4; el trade-off entre `fsync = on` y desactivarlo, con la tabla de riesgo por configuración, está en `postgresql_caso_estudio.md` §5.

#### El mapa completo: mecanismo y capa de cada propiedad

| Propiedad    | Mecanismo que la implementa                                                                                          | Dónde profundizar                   |
| ------------ | -------------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| Atomicidad   | MVCC + versiones de fila (PostgreSQL) o undo log (MySQL), coordinados con el WAL/redo log y el recovery al reiniciar | `postgresql_caso_estudio.md` §4, §6 |
| Consistencia | Constraint enforcement del motor + lógica de aplicación                                                              | Esta misma sección                  |
| Aislamiento  | MVCC (snapshots por transacción) + lock manager + isolation level elegido                                            | Bloque 2 y Bloque 3 de este archivo |
| Durabilidad  | WAL/redo log + fsync + checkpoints + buffer pool                                                                     | `postgresql_caso_estudio.md` §4, §5 |

Atomicidad, Aislamiento y Durabilidad son garantías técnicas que el motor cumple por sí solo, sin requerir nada de quien escribe la aplicación. Consistencia es una garantía parcial, el motor fuerza los constraints técnicos, pero la lógica de negocio que no está en el schema es responsabilidad de la aplicación.

---

## Bloque 2: MVCC, locks y niveles de aislamiento {#2-mvcc-locks}

### Sección 2.1: Las versiones de fila: qué son y dónde viven físicamente {#2-versiones-fila}

Una versión de fila es el estado completo que tuvo esa fila durante una ventana de tiempo concreta, desde la transacción que la creó hasta la transacción que, si acaso, la reemplazó. MVCC, Multi-Version Concurrency Control, es la técnica de mantener varias de esas versiones al mismo tiempo en vez de conservar solo la más reciente, para que un lector nunca tenga que esperar a un escritor ni al revés. Gracias a eso, la versión anterior de una fila sigue siendo reconstruible mientras algún lector todavía la necesite, en vez de desaparecer en cuanto llega un cambio nuevo. Esto es sobre datos concretos, filas reales en disco. Todavía no es el snapshot, eso viene después. Cada motor logra esa reconstrucción de una forma distinta. Algunos guardan la versión vieja tal cual, junto a la nueva. Otros sobrescriben y mueven el valor anterior a un registro aparte. Ese detalle se ve más abajo, en "dónde vive físicamente". Las tres escrituras dejan una fila versionada, aunque cada una a su manera:

- **INSERT** crea la primera versión de una fila que antes no existía. No reemplaza nada.
- **UPDATE** crea una versión nueva con los datos cambiados. La versión anterior no desaparece de inmediato. Sigue siendo reconstruible mientras algún snapshot todavía la necesite. Dónde queda guardada exactamente, junto al dato actual o movida aparte a un registro de cambios, depende del motor. Ese detalle también se ve más abajo.
- **DELETE** se comporta distinto según la familia de almacenamiento. En motores basados en heap, como PostgreSQL y MongoDB, no crea ninguna versión nueva. Solo marca la versión actual como terminada a partir de esa transacción. La fila deja de existir para transacciones futuras, pero esa versión sigue físicamente ahí hasta que algo la limpie. En motores basados en LSM-tree, como CockroachDB y Bigtable debajo de Spanner, es distinto. Los archivos ya escritos en disco son inmutables, así que no se puede marcar la versión vieja en su lugar. Ahí DELETE sí crea algo nuevo, un tombstone, un registro que dice que esa clave quedó borrada a partir de ese momento. El tombstone se limpia junto con la versión que cubre, cuando la compactación pasa por esa zona del disco.

En un momento dado, la misma fila puede tener varias versiones físicas guardadas al mismo tiempo, cada una con su propio metadato de qué transacción la creó y cuál la terminó. Ese metadato vive pegado a la versión, no es el snapshot, es más bien la etiqueta que el snapshot va a leer más adelante. Esto es sobre la fila completa, no sobre una sola columna. Ejemplo concreto: la fila es `id=1, nombre='Ana', saldo=500`. Un `UPDATE` cambia solo el saldo a 300. El motor no guarda "saldo=300" suelto por ahí, guarda la fila entera otra vez:

- Versión vieja: `id=1, nombre='Ana', saldo=500`
- Versión nueva: `id=1, nombre='Ana', saldo=300`

Ambas versiones tienen los tres valores completos, `id`, `nombre` y `saldo`. La única diferencia entre ellas es el saldo. `id` y `nombre` se repiten igual en las dos, aunque nadie los haya tocado.

Dónde vive físicamente esa versión anterior cambia radicalmente según el motor, y aquí conviene no quedarse solo con el caso de uno:

- **PostgreSQL** guarda la versión vieja completa en la misma tabla, el heap, junto a la nueva. Ambas conviven en el mismo archivo hasta que vacuum limpia la que ya nadie necesita.
- **MySQL InnoDB y Oracle** solo guardan la versión más reciente en la tabla. La versión anterior no se queda ahí. Se reconstruye a partir de un registro aparte, un undo log en InnoDB, o undo segments en el undo tablespace de Oracle. Oracle usa Automatic Undo Management desde la versión 9i, y es el modo recomendado hoy en día. Ese registro guarda cómo deshacer el cambio, no una copia completa de la fila. Ya se mencionó esto en la sección de Atomicidad, en Bloque 1.
- **SQL Server**, con snapshot isolation o RCSI activado, tampoco guarda versiones viejas en la tabla. Las envía a un almacén de versiones separado en `tempdb`.
- **MongoDB** usa su motor WiredTiger, e igual que PostgreSQL, mantiene copias completas de versiones anteriores del documento hasta que ya nadie las necesita.
- **CockroachDB y Google Spanner** están en la misma familia que PostgreSQL y MongoDB. Cada escritura crea una versión nueva con un timestamp más alto en el mismo almacén, Pebble en el caso de CockroachDB, sin sobrescribir la anterior. Un proceso de garbage collection las recicla después de una ventana configurable. Por defecto son 1 hora en Spanner, aunque se puede ajustar hasta una semana.

El patrón general, sin importar el motor: alguien tiene que poder reconstruir "cómo se veía este dato antes" para el lector cuyo snapshot todavía lo necesita. Cada motor decide si guarda esa versión anterior completa junto al dato actual (PostgreSQL, MongoDB, CockroachDB, Spanner), o si la reconstruye bajo demanda a partir de un registro de cambios aparte (MySQL, Oracle, SQL Server). Es la misma capa resuelta con dos estrategias físicas distintas.

---

### Sección 2.2: El snapshot: el criterio, y por qué es "de toda la base de datos" {#2-snapshot}

Con varias versiones de una fila coexistiendo, hace falta algo que decida cuál le toca ver a cada transacción. Eso es el snapshot: una regla que tu transacción recibe al arrancar (o en cada statement, según el nivel de aislamiento) y usa para elegir, entre las versiones que ya existen, cuál es la correcta para ella. A diferencia de las versiones, que son datos guardados por fila, el snapshot no tiene nada que ver con ninguna fila en particular.

En PostgreSQL, un snapshot son literalmente tres números, sin ninguna referencia a tablas ni filas: `xmin` (el ID de transacción activo más bajo en ese instante), `xmax` (el primer ID de transacción todavía no asignado) y `xip` (la lista de IDs de transacciones que seguían en curso justo cuando se tomó el snapshot). La documentación oficial lo dice así: al escanear una tabla, cualquier tabla, el motor compara el `xmin`/`xmax` de cada versión de fila contra esos tres números para decidir si es visible. El mismo criterio, sin modificarse, decide la visibilidad de una fila en `cuentas` y de una fila en `usuarios`, porque los IDs de transacción son un contador único y global para toda la base de datos, no algo que exista por separado en cada tabla. Por eso "cubre toda la base de datos": no porque copie nada, sino porque es un único criterio que se aplica de forma idéntica sin importar qué tabla toques.

Otros motores resuelven la misma pregunta, "¿qué tan al día está lo que estoy leyendo?", con su propio mecanismo, pero todos comparten la misma idea de fondo: un único punto de referencia global, no algo por tabla.

- **MySQL InnoDB** arma un "read view": la lista de transacciones activas en el momento en que empieza la lectura. Ese read view se usa para caminar el undo log de cualquier fila, de cualquier tabla, y decidir qué versión reconstruir.
- **Oracle** usa el SCN (System Change Number), un contador único y creciente para toda la instancia. Una lectura consistente es, literalmente, "dame los datos tal como estaban en el SCN=N", aplicado igual sin importar la tabla.
- **SQL Server**, con snapshot isolation, asigna un número de secuencia de transacción global y lee del almacén de versiones en `tempdb` con ese mismo número, otra vez sin importar la tabla.
- **MongoDB** (WiredTiger) le asigna a la transacción un "read timestamp" único, que se usa igual para cualquier documento de cualquier colección que toque esa transacción.
- **CockroachDB** le asigna a cada transacción un timestamp de Hybrid Logical Clock (HLC, reloj físico más un contador lógico). Una versión es visible si su timestamp de escritura es menor al HLC de la transacción que lee, el mismo criterio sin importar el rango o la tabla.
- **Google Spanner** usa TrueTime para asignarle a cada transacción un timestamp de lectura, acotado por la incertidumbre del reloj, aplicado igual a cualquier fila de cualquier tabla en cualquier nodo.

La dificultad de este mecanismo escala con la arquitectura. En un motor de un solo nodo, tener "un único contador global" es trivial, vive en un solo proceso, en una sola máquina. En una base de datos distribuida en varios nodos físicos, la misma idea se vuelve un problema genuinamente difícil: ponerse de acuerdo sobre qué significa "el mismo instante" cuando cada nodo tiene su propio reloj, que puede desincronizarse. Google Spanner resolvió esto con **TrueTime**, un reloj sincronizado con incertidumbre acotada y explícita (respaldado por relojes atómicos y GPS en los datacenters de Google), que le permite a Spanner asignar timestamps globalmente significativos y esperar ("commit wait") lo que dure esa incertidumbre antes de confirmar. CockroachDB resuelve el mismo problema sin relojes atómicos, con **Hybrid Logical Clocks** (HLC): un timestamp que combina el reloj físico de cada nodo con un contador lógico que preserva el orden causal entre eventos de nodos distintos, a cambio de renunciar al "commit wait" de Spanner. Es la misma pregunta de fondo, "¿cuál es el instante que define mi snapshot?", resuelta con más esfuerzo cuantas más máquinas físicas hay que coordinar.

Línea de tiempo concreta, sobre una fila de saldo que empieza en 500:

```
Tiempo     #20 (tu transacción)              #21                              #22
────────────────────────────────────────────────────────────────────────────────────────────────

10:00:00   BEGIN
           └─ recibe su snapshot: ve todo
              lo confirmado hasta ahora;
              #21 (en curso) no se ve
              todavía

10:00:01                                     UPDATE saldo=300
                                              └─ MVCC no borra saldo=500
                                                 (versión A). Crea saldo=300
                                                 (versión B) y marca A como
                                                 reemplazada por #21

10:00:02                                     COMMIT
                                              └─ versión B ya es oficial y
                                                 visible para cualquier
                                                 transacción que empiece de
                                                 aquí en adelante

10:00:03   SELECT saldo
           └─ ve 500 (versión A): su
              snapshot es de las 10:00:00,
              antes de que #21 confirmara

10:00:04                                                                      BEGIN + SELECT saldo
                                                                                └─ ve 300 (versión B):
                                                                                   su snapshot sí incluye
                                                                                   a #21 como confirmada

────────────────────────────────────────────────────────────────────────────────────────────────
Fila en disco:   versión A (saldo=500)  creada 10:00:00 · reemplazada 10:00:01 por #21
                 versión B (saldo=300)  creada 10:00:01 · vigente desde entonces
```

Las dos versiones ya existían físicamente en la tabla desde el `UPDATE` de #21, antes de que #20 o #22 leyeran nada. Ningún `SELECT` crea una copia nueva, solo recorre las versiones que ya estaban ahí y usa su snapshot para elegir la correcta. Es un solo snapshot por transacción (o por statement, según el nivel de aislamiento), no uno por fila, pero esa misma regla se compara contra los metadatos de cada fila que consultas, en cualquier tabla, mientras dure tu transacción.

¿La versión vieja (saldo=500) es basura una vez que #21 confirmó 300? Todavía no. Mientras exista alguna transacción activa cuyo snapshot pueda necesitarla, como la #20 de este ejemplo, el motor tiene que conservarla. Solo se vuelve basura reciclable cuando ya ninguna transacción viva podría pedirla, y eso es exactamente lo que hace vacuum en PostgreSQL: por eso una transacción "idle in transaction" olvidada causa table bloat, mientras su snapshot siga activo, ninguna versión vieja que ese snapshot pudiera necesitar se puede reciclar.

#### Qué hace exactamente el COMMIT

El `COMMIT` no distribuye ni avisa nada a nadie. Lo único que hace es marcar la transacción como confirmada en un registro compartido que cualquier transacción puede consultar (el commit log, `pg_xact` en PostgreSQL). Es una bandera que se deja puesta ahí, no un mensaje que se envía.

A partir de ese momento, que una transacción vea o no la versión nueva depende exclusivamente de su propio snapshot, no de qué tan reciente sea el `COMMIT`:

- **#22** (`BEGIN` a las 10:00:04, después del `COMMIT` de #21 a las 10:00:02): su snapshot consulta el commit log justo al arrancar, ve que #21 ya está confirmada, y por eso su lectura muestra la versión nueva (300).
- **#20** (snapshot fijado desde las 10:00:00, antes de que #21 siquiera empezara): aunque lee a las 10:00:03, después del `COMMIT` de #21, sigue viendo la versión vieja (500). Su snapshot ya había fijado, desde el inicio, qué transacciones cuentan como confirmadas para él, y #21 no estaba en esa lista porque en ese momento todavía seguía en curso. El `COMMIT` de #21 no cambia retroactivamente lo que #20 ya tenía decidido.

Por eso no es correcto imaginar el `COMMIT` como un evento que todas las demás transacciones "ven" al mismo tiempo. No hay una notificación ni un instante compartido de visibilidad. Cada transacción decide por su cuenta, comparando su propio snapshot contra el commit log, en el momento exacto en que hace su propia lectura. Dos transacciones pueden leer la misma fila en el mismo instante físico y obtener respuestas distintas, simplemente porque sus snapshots se tomaron en momentos distintos.

**De aquí en adelante, el resto de esta sección y los Bloques 6 y 7 se enfocan deliberadamente en el mecanismo de locks de los cuatro motores relacionales clásicos (PostgreSQL, MySQL InnoDB, Oracle, SQL Server).** X locks, S locks, FOR UPDATE, FOR SHARE, gap locks e intent locks son vocabulario específico de esa familia. Los motores NoSQL y distribuidos ya quedaron ubicados en el mapa universal de arriba, cada uno bajo la arquitectura que realmente usa (MongoDB y CockroachDB con MVCC, Neo4j con 2PL, Cassandra y DynamoDB con Quorum más CAS), y no vuelven a aparecer en el detalle de locks que sigue porque ese detalle no les aplica, MongoDB no tiene gap locks ni intent locks, y Cassandra no tiene X locks porque no hay una sola escritura compitiendo con otra de la misma forma. El vocabulario específico de MongoDB, DynamoDB, Cassandra y Redis para transacciones distribuidas y sharding se retoma en el Bloque 8. Este mismo cambio de alcance, de universal a específicamente relacional, se repite entre secciones a lo largo de esta guía y siempre por la misma razón: la mecánica interna de locks solo tiene sentido dentro de la familia de motores que efectivamente la usa.

---

### Sección 2.3: Los locks: los dos tipos base y su relación con MVCC {#2-locks-base}

Un lock es una señal que el motor registra en memoria sobre una fila para coordinar el acceso concurrente. Todo lo que sigue en esta guía, gap locks, intent locks, locks de predicado, se construye sobre los dos tipos más básicos, los únicos que hacen falta para entender qué compite con qué. Empecemos por ahí, y al final de esta sección hay un mapa de todos los demás tipos y dónde se explican.

Los dos tipos base son estos, y la diferencia entre ellos define exactamente qué queda bloqueado y qué no.

**La regla de una línea, antes que nada:** el X lock excluye a todo el que quiera hacer algo sobre esa fila, excepto las lecturas MVCC sin lock. El S lock solo excluye a quien quiera escribir, otros lectores coordinados con FOR SHARE sí continúan sin esperar.

- **X lock (Exclusive):** lo adquieren `INSERT`, `UPDATE`, `DELETE` y `SELECT FOR UPDATE`.
- **S lock (Shared):** lo adquiere `SELECT FOR SHARE`, y también un `SELECT` normal en SQL Server sin RCSI.

Qué puede hacer T2 mientras T1 tiene uno de estos locks activo sobre la misma fila:

| Operación de T2                      | T1 tiene X lock | T1 tiene S lock |
| ------------------------------------ | --------------- | --------------- |
| SELECT normal (MVCC)                 | continúa        | continúa        |
| SELECT normal en SQL Server sin RCSI | espera          | continúa        |
| SELECT FOR SHARE                     | espera          | continúa        |
| SELECT FOR UPDATE                    | espera          | espera          |
| INSERT / UPDATE / DELETE             | espera          | espera          |

Dos filas necesitan una aclaración aparte porque la tabla por sí sola no explica el porqué:

- **SELECT normal (MVCC) siempre continúa**, sin importar qué lock tenga T1, porque lee desde su propio snapshot y nunca consulta los locks de fila. Esto aplica en PostgreSQL, MySQL InnoDB y Oracle.
- **SELECT normal en SQL Server sin RCSI no se comporta como MVCC, se comporta como una lectura con lock.** Pide su propio S lock, así que espera si T1 tiene X (S y X son incompatibles) y continúa si T1 tiene S (S y S sí son compatibles entre sí). Con RCSI activo, SQL Server deja de pedir ese lock y se comporta igual que la primera fila de la tabla.

**Los locks ocurren en tres situaciones:**

| Situación      | Qué lo dispara                               | Qué implica                                                                                                                                                                                       |
| -------------- | -------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Automático** | Toda escritura: `INSERT`, `UPDATE`, `DELETE` | El motor pone X lock en las filas afectadas y lo libera al COMMIT o ROLLBACK. No necesitas configurar nada, ocurre siempre.                                                                       |
| **Indirecto**  | El nivel de aislamiento                      | Decide qué snapshot usas para leer y si el motor agrega locks adicionales de forma automática, por ejemplo los gap locks en MySQL RR. No controlas los locks directamente, controlas la política. |
| **Explícito**  | `SELECT ... FOR UPDATE` en tu SQL            | Le pide al motor que ponga el X lock desde la lectura, no desde el UPDATE. Solo es necesario cuando lees un valor y luego escribes basándote en él.                                               |

La mayoría del tiempo solo te afecta el caso 1: cada write tiene su lock automático y tú nunca lo ves porque dura microsegundos. El caso 3 aparece cuando el patrón read-modify-write crea el problema del lost update que se explica más abajo.

**El mapa completo de tipos de lock que existen en esta guía.** S y X son la base que ya vio arriba, la que hace falta para entender todo lo demás. El resto son variaciones sobre el mismo problema, un nivel de detalle distinto, no un concepto nuevo:

| Tipo de lock                    | Qué resuelve                                                                                                 | Dónde se explica                                                            |
| ------------------------------- | ------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------- |
| **S (shared / compartido)**     | Coordinar lectores entre sí sin bloquear otros lectores                                                      | Aquí arriba                                                                 |
| **X (exclusive)**               | Que solo una transacción escriba una fila a la vez                                                           | Aquí arriba                                                                 |
| **U (update)**                  | Evitar el deadlock clásico de dos lectores que intentan escalar a X al mismo tiempo, exclusivo de SQL Server | Bloque 6, "Tipos de locks"                                                  |
| **Intent locks (IS, IX, SIX)**  | Que un `ALTER TABLE` sepa si hay row locks activos sin escanear cada fila, verificando solo la tabla         | Bloque 6, "Intent locks: el protocolo de jerarquía"                         |
| **Lock escalation**             | Cuándo el motor decide cambiar muchos row locks por uno solo de tabla, y qué dispara ese cambio              | Bloque 6, "Lock escalation"                                                 |
| **Gap locks / next-key locks**  | Bloquear el espacio entre filas, no solo las filas que ya existen, para prevenir phantom reads               | Bloque 7, "Gap locks en MySQL"                                              |
| **Locks de predicado (SIREAD)** | Registrar qué leyó una transacción sin bloquear a nadie, para detectar write skew al confirmar               | Se ve más adelante, en "El patrón universal" (Bloque 5, SSI), y en Bloque 7 |
| **Advisory locks**              | Locks que la aplicación define y usa para su propia lógica, sin estar ligados a ninguna fila ni tabla        | Bloque 6, "Advisory locks en PostgreSQL"                                    |

---

### Sección 2.4: Qué pasa con un UPDATE normal {#2-update-normal}

```sql
UPDATE cuentas SET saldo = saldo - 100 WHERE id = 1;
```

Sin `BEGIN` explícito, este statement corre en **autocommit**: el motor abre una transacción implícita, ejecuta el UPDATE y hace COMMIT inmediatamente.

Lo que ocurre internamente:

```
Motor recibe UPDATE id=1
  → adquiere X lock (exclusive) sobre la fila id=1
  → ejecuta la modificación
  → COMMIT automático
  → libera el X lock

Duración total del lock: microsegundos.
```

Si otra transacción intenta modificar id=1 en ese mismo instante, espera ese microsegundo. No percibirás este comportamiento en condiciones normales.

**Lo que el X lock del UPDATE NO resuelve:** evita que dos UPDATEs corran literalmente en el mismo instante, pero no evita que dos transacciones hayan leído el mismo valor antes de hacer el UPDATE. Ese es un problema diferente — el lost update — que se describe más abajo.

---

### Sección 2.5: Por qué el SELECT normal no bloquea {#2-select-no-bloquea}

En motores con MVCC (PostgreSQL, MySQL InnoDB, Oracle), un `SELECT` sin modificadores **no adquiere ningún lock**. Lee desde un snapshot:

```sql
-- T1 y T2 pueden correr esto al mismo tiempo sin bloquearse:
SELECT saldo FROM cuentas WHERE id = 1;
```

MVCC mantiene versiones anteriores de cada fila. Cuando T1 lee, el motor le muestra la versión del dato que existía en el punto de tiempo de su snapshot, sin importar si T2 está modificando esa fila en ese preciso instante. Lectores y escritores no compiten.

**SQL Server sin RCSI es la excepción:** usa S locks en lecturas. Un SELECT largo puede bloquear writes concurrentes mientras corre. Con RCSI activo, SQL Server se comporta como PostgreSQL.

---

### Sección 2.6: El problema: MVCC no protege el patrón read-modify-write {#2-read-modify-write}

MVCC resuelve las lecturas, pero crea un problema al leer un valor, calcular algo en el backend y luego escribirlo:

```
Estado en disco: id=1, saldo=1000

T1: BEGIN;  SELECT saldo = 1000  (snapshot de T1, sin lock)
T2: BEGIN;  SELECT saldo = 1000  (snapshot de T2, sin lock)

T1: calcula 1000 - 200 = 800
T2: calcula 1000 - 300 = 700

T1: UPDATE cuentas SET saldo = 800 → COMMIT
T2: UPDATE cuentas SET saldo = 700 → COMMIT

Saldo final: 700.  Debería ser 500.
El descuento de T1 se perdió.
```

T2 no sabía del cambio de T1 porque su snapshot era anterior al commit de T1. Este es el **lost update**: ninguna transacción hizo nada incorrecto desde su propia perspectiva; el problema es estructural al patrón.

---

### Sección 2.7: SELECT ... FOR UPDATE: adquirir el lock desde la lectura {#2-for-update}

`FOR UPDATE` resuelve esto adquiriendo el X lock en el momento del SELECT, antes de calcular el nuevo valor:

```sql
BEGIN;
SELECT saldo FROM cuentas WHERE id = 1 FOR UPDATE;
-- X lock activo sobre id=1.
-- Ninguna otra txn puede modificar ni hacer FOR UPDATE sobre id=1.
UPDATE cuentas SET saldo = saldo - 200 WHERE id = 1;
COMMIT;
-- X lock liberado.
```

Esta sintaxis `FOR UPDATE` funciona igual en PostgreSQL, MySQL InnoDB y Oracle. SQL Server no tiene la cláusula `FOR UPDATE`, el mismo lock se pide con un hint sobre la tabla:

```sql
-- SQL Server: equivalente a FOR UPDATE
SELECT saldo FROM cuentas WITH (UPDLOCK, ROWLOCK) WHERE id = 1;
```

`UPDLOCK` adquiere el mismo lock de actualización que un `SELECT FOR UPDATE`, y `ROWLOCK` fuerza granularidad de fila en vez de dejar que el motor decida escalar a página o tabla.

Con FOR UPDATE, T2 espera a que T1 termine y luego lee el valor ya actualizado:

```
T1: BEGIN;  SELECT saldo=1000 FOR UPDATE  → adquiere X lock

T2: BEGIN;  SELECT saldo FROM cuentas WHERE id=1 FOR UPDATE
    → intenta adquirir X lock → ESPERA

T1: UPDATE saldo = 800;  COMMIT  → libera X lock

T2: adquiere X lock
    lee saldo = 800  (valor actualizado por T1)
    UPDATE saldo = 800 - 300 = 500;  COMMIT

Saldo final: 500.  Correcto.
```

---

### Sección 2.8: Por qué SELECT FOR UPDATE debe estar dentro de una transacción {#2-for-update-transaccion}

```sql
-- MAL: sin BEGIN explícito (autocommit)
SELECT saldo FROM cuentas WHERE id = 1 FOR UPDATE;
-- El motor adquiere X lock... y lo libera inmediatamente (autocommit).
UPDATE cuentas SET saldo = saldo - 200 WHERE id = 1;
-- Este UPDATE corre en otra transacción separada. No hay protección.
```

En autocommit, el lock se adquiere y se libera antes de que llegues al siguiente statement. Otra transacción puede modificar la fila en ese hueco.

```sql
-- BIEN: BEGIN mantiene el lock activo entre el SELECT y el UPDATE
BEGIN;
SELECT saldo FROM cuentas WHERE id = 1 FOR UPDATE;  -- lock activo
-- ... lógica de negocio en el backend ...
UPDATE cuentas SET saldo = saldo - 200 WHERE id = 1;
COMMIT;  -- lock liberado
```

**Regla:** los locks adquiridos dentro de una transacción se liberan al COMMIT o ROLLBACK, nunca antes.

---

### Sección 2.9: SELECT ... FOR SHARE: permitir lectores concurrentes, bloquear escritores {#2-for-share}

`FOR UPDATE` adquiere un X lock — excluye a cualquier otra transacción que quiera leer con lock o escribir sobre esa fila. Pero hay casos donde múltiples transacciones necesitan leer el mismo dato de forma coordinada, sin modificarlo, y solo necesitan garantizar que nadie lo modifique mientras tanto.

`FOR SHARE` (S lock) resuelve esto: múltiples transacciones pueden tener S lock sobre la misma fila simultáneamente, pero ninguna puede escribir mientras el S lock esté activo.

La diferencia importa porque el X lock de FOR UPDATE serializa a los lectores entre sí — si T1 tiene FOR UPDATE sobre el cliente 5, T2 no puede leer ese cliente con lock aunque solo quiera validar su existencia. FOR SHARE evita esa serialización innecesaria: deja que múltiples lectores coexistan, solo bloquea a quien quiera escribir.

```
Compatibilidad de locks en una misma fila:
  S + S → compatible   (dos lectores con FOR SHARE no se bloquean)
  S + X → incompatible (un escritor espera a que todos los S terminen)
  X + X → incompatible (solo un escritor a la vez)
```

**Caso de uso: validar integridad referencial antes de insertar**

```sql
-- T1 y T2 quieren insertar pedidos para el cliente id=5.
-- El cliente debe existir y no ser eliminado mientras se valida.

-- T1:
BEGIN;
SELECT id FROM clientes WHERE id = 5 FOR SHARE;
-- S lock sobre cliente 5. T2 también puede obtener S lock: no se bloquean.
-- Pero DELETE o UPDATE sobre cliente 5 espera hasta que ambas terminen.
INSERT INTO pedidos(cliente_id, total) VALUES (5, 150.00);
COMMIT;

-- T2 (concurrente):
BEGIN;
SELECT id FROM clientes WHERE id = 5 FOR SHARE;
-- También obtiene S lock. No espera a T1.
INSERT INTO pedidos(cliente_id, total) VALUES (5, 300.00);
COMMIT;

-- T3 intentando eliminar el cliente mientras T1 y T2 están activas:
DELETE FROM clientes WHERE id = 5;
-- Espera: hay S locks activos. No puede proceder hasta COMMIT de T1 y T2.
```

Si T1 y T2 hubieran usado FOR UPDATE, T2 esperaría a T1 aunque no haya ningún conflicto real entre ellas, ambas solo quieren insertar pedidos distintos. FOR SHARE captura la intención correcta: "coordino mis lecturas sin bloquear a otros lectores".

**La sintaxis `FOR SHARE` no existe igual en los cuatro motores:**

- **PostgreSQL:** `FOR SHARE`.
- **MySQL:** `FOR SHARE` desde 8.0.1. Las versiones anteriores usan `LOCK IN SHARE MODE`, sintaxis más antigua que MySQL sigue aceptando.
- **Oracle:** no tiene un `SELECT ... FOR SHARE`. Sus lectores MVCC nunca bloquean a nadie por defecto, así que el problema que resuelve FOR SHARE (coordinar lectores sin bloquearse entre sí) ya está resuelto sin pedir ningún lock explícito.
- **SQL Server:** no tiene `FOR SHARE`. El equivalente es el hint `WITH (HOLDLOCK, ROWLOCK)`, que retiene el S lock hasta el COMMIT en vez de liberarlo al terminar el statement.

---

### Sección 2.10: Qué locks toma la base de datos y cuándo se liberan {#2-locks-cuando-liberan}

```
Operación                    Lock adquirido        Cuándo se libera
─────────────────────────────────────────────────────────────────────
SELECT normal (MVCC)         ninguno               —
SELECT ... FOR SHARE         S lock por fila       COMMIT o ROLLBACK
SELECT ... FOR UPDATE        X lock por fila       COMMIT o ROLLBACK
INSERT / UPDATE / DELETE     X lock por fila       COMMIT o ROLLBACK
ALTER TABLE / DROP TABLE     lock de tabla         cuando termina el DDL
```

En **SQL Server sin RCSI**:

```
SELECT normal                S lock por fila       fin del statement (RC)
                                                   COMMIT o ROLLBACK (RR/SER)
```

Un SELECT largo en SQL Server sin RCSI mantiene S locks durante toda la query, lo que puede bloquear INSERTs o UPDATEs concurrentes sobre esas filas.

---

### Sección 2.11: Timeouts de lock: qué pasa cuando la espera es demasiado larga {#2-timeouts}

Cuando una transacción espera un lock que otra tiene activo, espera indefinidamente por defecto. En producción, una espera indefinida degrada la aplicación: las conexiones del pool se acumulan esperando, nuevas requests no pueden obtener conexiones, y el sistema se satura. Esto es especialmente problemático con FOR UPDATE, ya que si el proceso que tiene el lock cuelga o tarda más de lo esperado, todos los que esperan esa fila también se quedan colgados.

Los tres motores permiten configurar un timeout después del cual la espera se cancela con error:

```sql
-- PostgreSQL: cancelar si esperamos más del límite
SET lock_timeout = '5s';     -- por sesión
SET lock_timeout = '500ms';  -- admite milisegundos

-- O acotado a una sola transacción (sobreescribe el valor de sesión):
BEGIN;
SET LOCAL lock_timeout = '3s';
SELECT * FROM cuentas WHERE id = 1 FOR UPDATE;
-- Si espera más de 3s: ERROR: canceling statement due to lock timeout
COMMIT;

-- MySQL InnoDB
SET innodb_lock_wait_timeout = 10;  -- segundos; default 50

-- SQL Server
SET LOCK_TIMEOUT 5000;  -- milisegundos; -1 = espera indefinida (default)

-- Oracle: no hay timeout de sesión, se pide por statement
SELECT * FROM cuentas WHERE id = 1 FOR UPDATE NOWAIT;   -- falla de inmediato
SELECT * FROM cuentas WHERE id = 1 FOR UPDATE WAIT 5;   -- espera hasta 5s
-- Si se agota el WAIT: ORA-30006: resource busy; acquire with WAIT timeout expired
```

El error que recibe el backend es **recoverable**: la fila que necesitabas estaba bloqueada temporalmente por otra transacción. La respuesta correcta es reintentar desde el principio de la operación, igual que con un deadlock. El motor garantiza que la transacción no dejó ningún cambio a medias — fue abortada limpiamente.

```
Errores de lock → reintentar:
  PostgreSQL: ERROR "canceling statement due to lock timeout" (SQLSTATE 55P03)
  MySQL:      ERROR 1205 (HY000): Lock wait timeout exceeded
  SQL Server: Msg 1222 / SQLSTATE HYT00
  Oracle:     ORA-30006: resource busy; acquire with WAIT timeout expired

Errores de deadlock → reintentar:
  PostgreSQL: ERROR "deadlock detected" (SQLSTATE 40P01)
  MySQL:      ERROR 1213 (40001): Deadlock found
  SQL Server: Msg 1205
  Oracle:     ORA-00060: deadlock detected while waiting for resource

Ambos tipos son transitorios. El backend debe tener lógica de retry para ambos.
```

Reintentar aquí no significa reenviar solo el último statement. El motor ya hizo ROLLBACK de la transacción completa, así que cualquier lectura o escritura previa dentro de esa misma transacción también se perdió, hay que rehacer la transacción entera desde el BEGIN, no continuar donde se quedó. El código concreto, con backoff y un límite de intentos, se documenta en Bloque 7: es el mismo patrón que usa para los errores de serialización y deadlock, solo cambia qué excepción captura tu backend. En Python (psycopg2) es `LockNotAvailable`; en Java con Spring es `CannotAcquireLockException`, la misma clase para los cuatro motores de esta lista, Spring traduce el código específico de cada uno a esa excepción portable.

---

### Sección 2.12: UPDATE atómico vs SELECT FOR UPDATE {#2-update-vs-for-update}

Antes de usar `FOR UPDATE`, verificar si el problema se resuelve con un update relativo. Estas son las dos herramientas, cada una con su propio código y su propio criterio de cuándo usarla.

#### UPDATE atómico

```sql
-- El motor serializa internamente. Sin ventana de vulnerabilidad.
UPDATE inventario SET stock = stock - 1 WHERE id = 42 AND stock > 0;
-- Correcto para decrementar, incrementar o ajustar sin lógica condicional en el backend.
```

**Usar update atómico cuando:**

- El nuevo valor es una función simple del valor actual: `col + n`, `col - n`, `col * factor`
- No necesitas el valor actual en el backend para tomar la decisión
- La condición se puede expresar directamente en el WHERE

#### SELECT FOR UPDATE

```sql
-- Cuando necesitas leer antes de decidir en el backend
BEGIN;
SELECT stock, precio, descuento_activo FROM inventario WHERE id = 42 FOR UPDATE;
-- El backend decide: ¿hay stock suficiente? ¿el precio sigue vigente?
UPDATE inventario SET stock = stock - :cantidad WHERE id = 42;
COMMIT;
```

**Usar SELECT FOR UPDATE cuando:**

- Necesitas el valor actual en tu código para tomar una decisión
- La decisión involucra múltiples columnas o filas relacionadas
- La condición de escritura depende de lógica que no puede expresarse en SQL puro

---

### Sección 2.13: El mecanismo: cómo el nivel afecta snapshots {#2-mecanismo-niveles}

Ya vimos qué es un snapshot al principio de esta sección. Ahora veamos cómo cada nivel de aislamiento decide en qué momento exacto se toma.

La diferencia entre los niveles es el punto exacto donde se toma ese snapshot:

- **READ COMMITTED:** cada `SELECT` toma su propio snapshot en el momento exacto en que se ejecuta, no cuando arrancó la transacción. Por eso, si otra transacción confirma un cambio entre dos SELECTs dentro de la misma transacción, el segundo SELECT ya ve ese cambio nuevo, aunque el primero no lo haya visto, cada statement mira el estado más reciente confirmado hasta ese instante.
- **REPEATABLE READ:** el snapshot se toma una sola vez, al inicio de la transacción, y todos los SELECTs de esa misma transacción reutilizan ese mismo snapshot. Por eso, aunque otras transacciones confirmen cambios mientras la tuya sigue abierta, tus SELECTs seguirán viendo el mismo estado que vieron la primera vez, sin importar cuánto tiempo pase, de ahí el nombre del nivel.
- **SERIALIZABLE**: mismo punto que REPEATABLE READ, snapshot al inicio de la transacción, pero suma un mecanismo adicional que ya varía por motor (SSI en PostgreSQL, locking real en MySQL y SQL Server, nada adicional en Oracle), como se ve en la tabla de abajo

El X lock en escrituras (`INSERT / UPDATE / DELETE`) es **igual en todos los niveles**, en todos los motores. El nivel de aislamiento no cambia eso.

Lo que cambia es cómo funcionan las **lecturas**. En PostgreSQL, MySQL InnoDB y Oracle, el `SELECT` normal nunca adquiere un lock, porque los tres implementan MVCC. SQL Server sin RCSI es la excepción: su `SELECT` normal sí adquiere un S lock, y lo que cambia por nivel es cuánto tiempo lo retiene:

| Nivel           | PostgreSQL                                                                                           | MySQL InnoDB                                                                                              | Oracle                                                                            | SQL Server sin RCSI                      |
| --------------- | ---------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- | ---------------------------------------- |
| READ COMMITTED  | Sin lock. Snapshot nuevo en cada statement, dos SELECTs de la misma txn pueden ver valores distintos | Sin lock. Snapshot nuevo en cada statement, igual que PostgreSQL                                          | Sin lock. Snapshot nuevo en cada statement (nivel default)                        | S lock, liberado al final del statement  |
| REPEATABLE READ | Sin lock. Snapshot fijado al inicio de la txn, todos los SELECTs ven el mismo estado                 | Sin lock. Snapshot fijado al inicio de la txn, además agrega gap locks en lecturas bloqueantes (Bloque 7) | No existe este nivel con nombre propio (ver la nota más abajo)                    | S lock, liberado al COMMIT               |
| SERIALIZABLE    | Sin lock. Snapshot + rastreador de dependencias SSI (Bloque 7)                                       | Sin lock, pero cada SELECT se convierte en FOR SHARE, un S lock real                                      | Sin lock. Snapshot-based, sin SSI, no previene write skew (ver la nota más abajo) | S lock + range lock, liberados al COMMIT |

**SQL Server sin RCSI no usa MVCC para leer, por eso es la única columna con locks.** Con RCSI o con el nivel SNAPSHOT activados, enciende su propia versión de MVCC, un version store en `tempdb`, y esta columna pasa a comportarse igual que las otras tres.

**Oracle es un caso aparte: solo tiene READ COMMITTED (default) y SERIALIZABLE, sin REPEATABLE READ como nivel nombrado.** Su SERIALIZABLE es snapshot-based, no SSI, así que detecta conflictos de escritura sobre la misma fila (ORA-08177, igual que el first-committer-wins de PostgreSQL RR) pero no previene write skew entre filas distintas, para eso hace falta bloquear explícitamente con FOR UPDATE.

Esto parece suficiente para evitar todos los problemas. Pero la tabla de anomalías muestra que no lo es:

```
                     Dirty   Non-rep.  Phantom  Lost     Write
                     read    read      read     update   skew
──────────────────────────────────────────────────────────────
READ UNCOMMITTED     Sí      Sí        Sí       Sí       Sí
READ COMMITTED       No      Sí        Sí       Sí †     Sí
REPEATABLE READ      No      No        Sí *     Sí †     Sí *
SERIALIZABLE         No      No        No       No       No ‡

†  prevenido con update atómico o SELECT FOR UPDATE
*  varía por motor (ver la Guía comparativa en Bloque 5)
‡  Oracle es la excepción: su SERIALIZABLE es snapshot-based, no SSI, y
   sigue permitiendo write skew salvo que se bloquee explícitamente con
   FOR UPDATE (ver la nota de Oracle más arriba en esta sección).
```

READ COMMITTED y REPEATABLE READ permiten lost update. Esa es la razón directa de por qué `SELECT FOR UPDATE` existe.

**Por qué el snapshot no resuelve el lost update:**

El snapshot protege tus lecturas mostrándote un estado consistente del pasado. Pero cuando ejecutas el `UPDATE`, el motor lo aplica sobre la versión más reciente de la fila en disco, no sobre tu snapshot, así que si T2 modificó y confirmó esa fila entre tu lectura y tu escritura, tu UPDATE sobreescribe ese cambio sin saberlo.

`SELECT FOR UPDATE` llena ese hueco adquiriendo el X lock desde la lectura. Cualquier otra txn que quiera modificar esa fila espera hasta que hagas COMMIT, así que cuando lees, lees el valor actual, no un snapshot que puede estar obsoleto al momento de escribir.

#### First-committer-wins: qué pasa si dos transacciones escriben la misma fila sin FOR UPDATE, por motor

REPEATABLE READ congela lo que ves, pero no lo que escribes. Tus lecturas siempre usan el snapshot fijado al inicio de la transacción, así que ves el mismo estado sin importar cuánto tiempo pase, pero cuando ejecutas un `INSERT`, `UPDATE` o `DELETE`, el motor no escribe sobre esa foto congelada, escribe directamente sobre la versión más reciente de la fila en disco, sin importar qué tan vieja sea la que tú leíste. Esa diferencia es la raíz del problema. Mientras dos transacciones solo están leyendo y calculando, cada una ve su propio snapshot y no tiene forma de saber que la otra existe, así que el motor tampoco tiene nada que detectar todavía. El conflicto solo aparece cuando una de las dos ejecuta su escritura, porque ahí es cuando por fin toca la fila real en vez de su copia congelada, y es justo ahí donde el motor puede, o no, darse cuenta de que esa fila ya cambió desde que la leíste.

A la transacción que llega primero a ese punto y confirma sin problema se le llama la "first-committer", la primera en confirmar, y su cambio gana, queda escrito. Lo que varía por motor es qué le pasa a la segunda, la que intenta escribir después con un valor ya obsoleto, si el motor se da cuenta y la rechaza, o si la deja pasar sin avisar, tal como muestra la tabla de abajo. El escenario es el mismo en todos los casos, el saldo en disco es 1000, T1 lo lee bajo su propio snapshot, T2 lo actualiza a 500 y confirma, y T1 calcula 1000 - 200 = 800 sobre un valor ya obsoleto e intenta escribirlo:

| Motor o enfoque                               | Qué pasa cuando T1 intenta escribir el valor obsoleto                                               | Resultado                                                                                    |
| --------------------------------------------- | --------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| PostgreSQL, REPEATABLE READ, sin FOR UPDATE   | El UPDATE falla ahí mismo, sin llegar siquiera a pedir COMMIT                                       | SQLSTATE 40001. Saldo correcto en 500, T1 debe reintentar (releer, recalcular, escribir 300) |
| MySQL InnoDB, REPEATABLE READ, sin FOR UPDATE | El UPDATE se ejecuta y confirma sin ninguna advertencia                                             | Saldo queda en 800, el cambio de T2 desaparece, corrupción silenciosa                        |
| Oracle, SERIALIZABLE, sin FOR UPDATE          | Mismo mecanismo que PostgreSQL, un nivel más arriba porque Oracle no tiene REPEATABLE READ          | ORA-08177. Saldo correcto, debe reintentar                                                   |
| SQL Server, REPEATABLE READ sin RCSI          | T2 no puede completar su UPDATE hasta que T1 confirme, porque el S lock que T1 retiene se lo impide | La trampa nunca ocurre. El costo es más bloqueo, no un dato incorrecto                       |
| SQL Server, nivel SNAPSHOT                    | Mismo mecanismo que PostgreSQL, porque ahí sí lee por versiones en vez de locks                     | Error 3960. Saldo correcto, debe reintentar                                                  |
| Cualquier motor, con `SELECT FOR UPDATE`      | T1 lee el valor actual (500) bajo X lock, T2 espera en vez de adelantarse                           | Saldo termina en 300, sin error ni corrupción                                                |

PostgreSQL y Oracle actúan como red de seguridad: si vas a escribir con un valor obsoleto, el motor lo detecta y te obliga a corregirlo, al precio de un retry. MySQL no tiene esa red, el write incorrecto pasa sin ningún aviso. SQL Server evita el problema desde la raíz reteniendo el lock de lectura, al precio de más bloqueo en vez de un retry ocasional.

Para transacciones de solo lectura (reportes, auditorías), REPEATABLE READ es seguro en cualquier motor y ninguna fila de esta tabla aplica, porque ahí nunca hay un UPDATE que dependa del valor leído.

### Sección 2.14: Cuándo usar cada nivel: guía práctica con advertencias {#2-guia-niveles}

**READ COMMITTED** _(default en PostgreSQL, SQL Server, Oracle)_

Correcto para la mayoría de operaciones OLTP. Cada statement ve el estado más reciente confirmado en ese instante. El costo es casi cero.

| Tipo        | Detalle                                                                                                                                                                                                                                                                                   |
| ----------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| ✓ Seguro    | Updates atómicos (`SET col = col + n`), `SELECT FOR UPDATE` en read-modify-write, transacciones cortas (milisegundos)                                                                                                                                                                     |
| ⚠ Peligroso | El patrón read-modify-write: leer un valor, calcular el nuevo valor en el backend, y escribirlo con `UPDATE` sin haber bloqueado la fila con `FOR UPDATE` desde la lectura. Si otra transacción escribe esa misma fila en el intervalo, su cambio se pierde sin ningún error, lost update |
| ⚠ Tradeoff  | Dos SELECTs consecutivos en la misma txn pueden ver datos distintos si otra txn confirmó entre ellos. Para reportes multi-tabla esto puede producir resultados inconsistentes                                                                                                             |

**REPEATABLE READ** _(default en MySQL InnoDB)_

El nivel para transacciones que hacen muchas lecturas de varias tablas y necesitan que todas sean consistentes entre sí, sin escribir nada basándose en esas lecturas. En READ COMMITTED, dos SELECTs consecutivos en la misma transacción pueden ver estados distintos porque cada uno toma un snapshot fresco, así que si otra transacción confirmó entre ellos, el segundo SELECT ya ve el cambio. En REPEATABLE READ el snapshot se fija al inicio y todos los SELECTs ven el mismo punto del tiempo, sin poner ningún lock y sin bloquear a nadie. No es una alternativa a SELECT FOR UPDATE, resuelve un problema diferente (consistencia entre lecturas, no seguridad en read-modify-write). Ver Bloque 3 para la comparación directa.

| Tipo        | Motor      | Detalle                                                                                                                                                                                                                  |
| ----------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| ✓ Seguro    | Todos      | Transacciones de solo lectura (reportes, auditorías, facturas), updates atómicos, `SELECT FOR UPDATE` en read-modify-write                                                                                               |
| ⚠ Peligroso | MySQL      | Read-modify-write sin FOR UPDATE produce lost update silencioso, el motor acepta el write incorrecto sin error ni advertencia. Regla: si lees para luego escribir, siempre FOR UPDATE                                    |
| ⚠ Peligroso | MySQL      | INSERTs concurrentes frecuentes sobre las mismas filas producen deadlocks por gap locks. Considerar bajar a RC para esas cargas específicas                                                                              |
| ⚠ Tradeoff  | PostgreSQL | Read-modify-write sin FOR UPDATE falla con SQLSTATE 40001 en el propio UPDATE, no hace falta llegar al COMMIT. El dato queda correcto, pero el backend debe reintentar. PostgreSQL protege del dato incorrecto, MySQL no |

**SERIALIZABLE**

Previene todas las anomalías incluyendo write skew. El precio es retries (PostgreSQL SSI) o lock contention severo (MySQL = FOR SHARE en cada SELECT).

| Tipo                   | Motor                | Detalle                                                                                                                                                                                                                                                                             |
| ---------------------- | -------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| ✓ Seguro               | Todos                | Write skew donde FOR UPDATE sobre el conjunto leído es complejo, operaciones financieras con invariantes multi-fila                                                                                                                                                                 |
| ⚠ Peligroso            | Todos                | No implementar retry: las transacciones fallan con error opaco                                                                                                                                                                                                                      |
| ⚠ Límite de FOR UPDATE | Todos                | Alcanza para write skew sobre filas que ya existen (UPDATE), pero falla si depende de filas que todavía no existen (INSERT) o que van a desaparecer (DELETE), porque no hay nada que bloquear. Ahí SERIALIZABLE es la única solución a nivel de aislamiento, no un extra redundante |
| ⚠ Peligroso            | MySQL                | SERIALIZABLE en carga read-heavy equivale a S locks en todo, el rendimiento degrada al nivel de bases de datos pre-MVCC. Alternativa: RR + SELECT FOR UPDATE explícito donde sea necesario                                                                                          |
| ⚠ Tradeoff             | PostgreSQL vs. MySQL | PostgreSQL rastrea dependencias con SSI sin bloquear lecturas, menor impacto. MySQL convierte cada SELECT en FOR SHARE implícito, mayor impacto                                                                                                                                     |

**Caso especial: Oracle solo tiene dos niveles**

Oracle no ofrece REPEATABLE READ ni READ UNCOMMITTED como niveles con nombre propio. Solo existen READ COMMITTED (default) y SERIALIZABLE.

| Tipo        | Detalle                                                                                                                                                                                                                                             |
| ----------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| ✓ Seguro    | Igual que en los demás motores para READ COMMITTED: updates atómicos y SELECT FOR UPDATE en read-modify-write                                                                                                                                       |
| ⚠ Peligroso | Asumir que SERIALIZABLE en Oracle se comporta como en PostgreSQL. Es snapshot-based, no SSI, y sigue permitiendo write skew entre filas distintas (ver la nota de Oracle en la sección anterior)                                                    |
| ⚠ Tradeoff  | Si la aplicación necesita el snapshot estable de REPEATABLE READ, todas las lecturas de la txn viendo el mismo punto del tiempo, la única forma de lograrlo en Oracle con un nivel nombrado es subir a SERIALIZABLE y asumir el riesgo de ORA-08177 |

**READ UNCOMMITTED**

| Tipo        | Motor               | Detalle                                                                                                                                                                                                                                          |
| ----------- | ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| ⚠ Peligroso | Todos               | Casi nunca en producción. Caso de uso teórico: analytics aproximados donde ver datos no confirmados es aceptable (contadores de visitas, métricas sin precisión exacta). Nunca para operaciones que luego escriben o toman decisiones de negocio |
| Nota        | PostgreSQL / Oracle | No existe este nivel, se mapea silenciosamente a READ COMMITTED                                                                                                                                                                                  |
| Nota        | SQL Server          | No toma ningún lock en este nivel, el mismo comportamiento que su hint NOLOCK, y por eso permite leer datos no confirmados de otras transacciones                                                                                                |

---

### Sección 2.15: Los ORM no cambian nada de esto, solo lo empaquetan {#2-orm}

Todo lo que esta guía estableció, MVCC, locks, OCC, SSI, CAS, el costo de sostener una transacción abierta, aplica exactamente igual sin importar si escribe SQL a mano o pasa por Django, Rails, Spring o Prisma. Un ORM es una capa de traducción: convierte `save()` en un `UPDATE`, `atomic()` en un `BEGIN`/`COMMIT`, pero no le pide nada distinto al motor por debajo, y el motor no sabe ni le importa si la sentencia vino de un ORM o de una consola SQL.

Lo único que sí cambia entre frameworks es qué tan fácil o difícil lo hacen para pedir las herramientas manuales que ya vimos, `FOR UPDATE`, u optimistic locking con columna de versión. Django y Rails las exponen con un método directo (`select_for_update()`, `with_lock`); Spring lo hace con una anotación (`LockModeType.PESSIMISTIC_WRITE`, `@Version`); Prisma es el caso más débil de los cuatro, no tiene API nativa para `FOR UPDATE`, exige salir a SQL crudo. Ningún ORM activa estas protecciones por defecto: el patrón `find → modificar → save` es exactamente el patrón vulnerable al lost update, sin importar el framework. Para la sintaxis exacta de cada patrón en SQL puro, ver `guia_transacciones_db.md` §18.

---

## Bloque 3: Anomalías de concurrencia: referencia {#3-anomalias}

En Bloque 1 se introdujeron estas anomalías como contexto para ACID. La literatura de bases de datos, formalizada en el estándar SQL-92 y extendida por Berenson, Bernstein y Gray (1995), las divide en dos categorías según dónde se manifiesta el problema: anomalías de lectura y anomalías de escritura. Esa distinción es la clave para no confundirlas entre sí.

**Anomalías de lectura** T1 no necesita escribir nada para que ocurran. El problema es que lo que T1 lee es inconsistente, ve datos incorrectos, datos que cambiaron, o conjuntos de filas que cambiaron. La clave de distinción entre las tres es el **alcance** de lo que cambia:

| Anomalía            | Alcance        | T2 hace...                                               | Prevenida desde                                   |
| ------------------- | -------------- | -------------------------------------------------------- | ------------------------------------------------- |
| Dirty read          | Una fila       | Nada aún (sin COMMIT)                                    | READ COMMITTED                                    |
| Non-repeatable read | Una fila       | UPDATE + COMMIT                                          | REPEATABLE READ                                   |
| Phantom read        | Conjunto/rango | INSERT, DELETE, o UPDATE que cambia si una fila califica | SERIALIZABLE (gap locks en MySQL REPEATABLE READ) |

**Anomalías de escritura** T1 escribe algo incorrecto porque su decisión se basó en lecturas que ya no representan el estado real. La clave de distinción entre las dos es si el conflicto ocurre sobre la **misma fila** o sobre **filas distintas**:

| Anomalía    | Conflicto                    | T2 hace...                           | Prevenida con                                    |
| ----------- | ---------------------------- | ------------------------------------ | ------------------------------------------------ |
| Lost update | Write-write en la misma fila | Escribe la misma fila                | FOR UPDATE, update atómico, SERIALIZABLE         |
| Write skew  | Writes en filas distintas    | Escribe una fila distinta a la de T1 | SERIALIZABLE, FOR UPDATE sobre el conjunto leído |

Los mecanismos concretos que previenen cada anomalía (snapshots, locks, FOR UPDATE) se explican en Bloque 2. SSI se explica en Bloque 7.

El paper de Berenson, Bernstein y Gray que formaliza esta taxonomía define un sexto fenómeno que no aparece en esta referencia: **Dirty Write (P0)**, T1 modifica una fila y T2 la modifica de nuevo antes de que T1 haga COMMIT o ROLLBACK. Se excluye del catálogo porque, en la práctica, ningún motor SQL lo permite bajo ningún nivel de aislamiento: incluso el locking más débil sostiene locks de escritura de larga duración que bloquean a cualquier otra transacción hasta el COMMIT o ROLLBACK, el propio paper lo señala así. Es un fenómeno real en la teoría, pero que nunca llega a manifestarse en un motor SQL real.

---

### Sección 3.1: Anomalías de lectura {#3-anomalias-lectura}

Las tres anomalías de lectura se distinguen por el **alcance** de lo que cambió entre las dos lecturas de T1:

- **Dirty read**: T1 lee un dato que T2 aún no confirmó. El problema no es que el dato cambió — es que nunca debió ser visible.
- **Non-repeatable read**: T1 lee la misma **fila por su identidad** (por PK) dos veces y obtiene valores distintos porque T2 hizo UPDATE y confirmó entre ambas lecturas.
- **Phantom read**: T1 ejecuta la misma **condición** dos veces y obtiene filas distintas porque T2 cambió qué filas califican esa condición (INSERT, DELETE, o UPDATE sobre el campo del WHERE).

La frontera precisa: si la consulta accede a una fila específica por PK y el valor cambió → non-repeatable read. Si la consulta usa una condición y el conjunto de resultados cambió → phantom read.

Un caso que genera confusión: **¿qué pasa si T1 lee una fila por PK y T2 la elimina?**
La segunda lectura de T1 devuelve 0 filas — la fila desapareció. Aunque T1 accedió por PK,
esto se clasifica como **phantom read**, no non-repeatable read. La razón: el valor de la
fila no cambió — la fila dejó de pertenecer al conjunto de resultados de esa condición.
Non-repeatable read requiere que la fila exista en ambas lecturas pero con valores distintos;
aquí la fila directamente no existe en la segunda lectura.

### Sección 3.2: Dirty read {#3-dirty-read}

_(ocurre en: READ UNCOMMITTED · prevenido desde: READ COMMITTED)_

```
T1 modifica una fila pero no ha confirmado.
T2 lee esa modificación.
T1 hace ROLLBACK.
T2 usó un dato que nunca existió permanentemente.

T1: BEGIN ─── UPDATE saldo=300 ──────────────── ROLLBACK ──►
T2: ────────────────────────── SELECT ve 300 ────────────────►
                                       ↑ dato que T1 deshizo

Ocurre en:    READ UNCOMMITTED
Prevenido en: READ COMMITTED y superiores

Nota: PostgreSQL y Oracle no implementan READ UNCOMMITTED real.
      Solicitar ese nivel equivale a READ COMMITTED en esos motores.

Nota: SQL Server en este nivel no toma ningún lock, ni siquiera el S lock
      que sí usa en READ COMMITTED, el mismo comportamiento que su hint
      NOLOCK. Por eso puede leer datos no confirmados de otras transacciones.
```

**Casi nunca tiene sentido en producción.** El único caso de uso real es analytics aproximado donde ver datos no confirmados es aceptable, contadores de visitas, métricas sin precisión exacta. Nunca para operaciones que luego escriben o toman decisiones de negocio basadas en lo leído.

### Sección 3.3: Non-repeatable read {#3-non-repeatable-read}

_(ocurre en: READ COMMITTED · prevenido con: REPEATABLE READ o SELECT FOR UPDATE)_

El problema: T1 lee la misma fila dos veces dentro de la misma transacción y obtiene valores distintos.

La causa: en READ COMMITTED, cada SELECT toma un snapshot fresco del estado actual confirmado en ese preciso instante. Si T2 confirma un cambio entre el primer y el segundo SELECT de T1, el segundo SELECT ve el nuevo valor. READ COMMITTED fue diseñado para garantizar que siempre leas el dato más reciente confirmado — y esa misma característica es la que produce la inconsistencia.

```
T1: BEGIN ─── SELECT saldo=500 ─────────────── SELECT saldo=300 ─── COMMIT ─►
T2: ──────────────────────────── UPDATE saldo=300 ── COMMIT ─────────────────►

Por qué ocurre en READ COMMITTED:
  primer  SELECT de T1 → snapshot A → ve 500
  T2 confirma, el estado en disco cambia a 300
  segundo SELECT de T1 → snapshot B (nuevo, fresco) → ve 300
  READ COMMITTED garantiza que siempre ves el dato confirmado más reciente.
  Eso mismo es lo que produce la inconsistencia dentro de la transacción.
```

Hay dos formas de prevenirlo. El mecanismo es completamente distinto en cada una:

```
Con REPEATABLE READ — snapshot fijo para toda la transacción:

T1: BEGIN ─── SELECT saldo=500 ──────────────────────── SELECT saldo=500 ─── COMMIT ─►
                  ↑ snapshot fijado al inicio                 mismo snapshot → sigue 500
T2: ─────────────────────────── UPDATE saldo=300 ─── COMMIT ─────────────────────────►
                                 T2 confirma sin problema. T1 no ve el cambio.
```

T1 ve 500 en ambas lecturas porque su snapshot quedó congelado al inicio de la transacción. T2 no está bloqueada — confirmó su cambio sin ningún problema. T1 simplemente opera en su propia foto del tiempo y no ve nada que ocurra después.

El tradeoff: cuando T1 termina, el saldo real en disco es 300, pero T1 nunca lo supo. REPEATABLE READ es correcto cuando T1 solo lee — para reportes, auditorías, consultas que no escriben nada. Pero si T1 va a calcular y escribir basándose en ese saldo, estará partiendo de un valor que ya no es el actual. Eso es el patrón del lost update, que se explica en Bloque 2.

```
Con SELECT FOR UPDATE en READ COMMITTED — bloquea la fila desde la primera lectura:

T1: BEGIN ─── SELECT saldo=500 FOR UPDATE ──────────────── SELECT saldo=500 ─── COMMIT ─►
                                    ↑ X lock                    ↑ sigue 500 porque nadie
                                                                  pudo modificarla
T2: ────────────────── UPDATE saldo=300 ─── ESPERA ──────────────────────────────────►
                                            no puede confirmar hasta que T1 haga COMMIT
```

Aquí T2 no puede ejecutar su UPDATE mientras T1 tenga el X lock activo. Cuando T1 relee el saldo, sigue siendo 500 — no porque tenga un snapshot congelado, sino porque nadie pudo modificar la fila mientras T1 trabajaba con ella.

**Por qué SELECT FOR UPDATE es la solución correcta cuando T1 va a escribir:** con REPEATABLE READ, T2 ya confirmó su cambio (saldo=300) antes de que T1 termine. Si T1 luego escribe basándose en el saldo que leyó, está sobreescribiendo un cambio más reciente. Con FOR UPDATE, T2 no pudo confirmar nada — T1 operó con el valor real en todo momento. La decisión que toma T1 y lo que escribe parten del estado actual de la fila, no de una foto del pasado.

**¿Por qué entonces existe REPEATABLE READ si SELECT FOR UPDATE hace el trabajo mejor?**

Porque resuelven problemas distintos. SELECT FOR UPDATE no es un reemplazo de REPEATABLE READ — es una herramienta para un caso de uso diferente.

SELECT FOR UPDATE sirve cuando vas a leer **una fila específica** para luego **modificarla**. El lock tiene sentido: hay una sola fila en juego, vas a escribirla, y necesitas que nadie la cambie entre tu lectura y tu escritura.

REPEATABLE READ sirve cuando una transacción hace **muchas lecturas** de varias tablas y necesita que todas sean consistentes entre sí, **sin escribir nada basándose en ellas**. El ejemplo concreto: un reporte financiero que lee de tres tablas distintas.

```
Reporte de fin de mes en READ COMMITTED:

T1 (reporte):
  SELECT SUM(saldo) FROM cuentas      → 10,000   ← snapshot A
  ... lógica del reporte ...
  SELECT SUM(monto) FROM movimientos  → 500       ← snapshot B (nuevo)

Entre los dos SELECTs, T2 confirmó una transferencia.
snapshot A no incluye la transferencia. snapshot B sí.
El reporte suma datos de dos momentos distintos → resultado inconsistente.

Reporte de fin de mes en REPEATABLE READ:

T1 (reporte):
  SELECT SUM(saldo) FROM cuentas      → 10,000   ← snapshot fijado al inicio
  SELECT SUM(monto) FROM movimientos  → 450       ← mismo snapshot
  SELECT COUNT(*) FROM clientes       → 80        ← mismo snapshot

Todos los SELECTs ven el mismo momento del tiempo. El reporte es internamente
consistente aunque otras transacciones confirmen datos mientras se ejecuta.
```

**¿Por qué no usar SELECT FOR UPDATE para el reporte?** Porque tendrías que poner X lock en cada fila que lees. Si el reporte recorre 10,000 cuentas, 50,000 movimientos y 80 clientes, estás bloqueando 60,080 filas durante todo el tiempo que tarda el reporte. Ninguna transacción puede modificar ninguna de esas filas mientras tanto. En una aplicación web con usuarios activos, esto paraliza el sistema.

REPEATABLE READ no pone ningún lock. Usa un snapshot del pasado sin bloquear a nadie. Lectores y escritores conviven sin interferirse. El costo es prácticamente cero.

```
Cuándo usar cada uno:
  REPEATABLE READ    → muchas lecturas de varias tablas, sin escribir basándose en ellas
                       reportes, auditorías, generación de documentos, batch jobs
                       necesitas consistencia entre SELECTs, no el valor más reciente

  SELECT FOR UPDATE  → leer UNA fila (o pocas) para luego modificarlas
                       necesitas el valor actual y que nadie lo cambie mientras decides
                       el lock es puntual, dura solo mientras procesas esa fila
```

### Sección 3.4: Phantom read {#3-phantom-read}

_(ocurre en: READ COMMITTED · en REPEATABLE READ solo con locking reads o en motores lock-based · prevenido en: SERIALIZABLE o gap locks)_

Anomalía de **lectura**. T1 ejecuta la misma consulta dos veces sobre un conjunto de filas y obtiene resultados distintos — no porque el valor de una fila cambió, sino porque cambió **qué filas pertenecen al conjunto**.

La frontera con non-repeatable read en una línea: non-repeatable read es sobre el **valor** de una fila conocida; phantom read es sobre la **existencia o calificación** de filas en un rango.

```
Non-repeatable read — T1 sabe qué fila quiere y su valor cambia:
  T1: SELECT saldo FROM cuentas WHERE id = 7   → 500
  T2: UPDATE cuentas SET saldo = 300 WHERE id = 7; COMMIT
  T1: SELECT saldo FROM cuentas WHERE id = 7   → 300
  La fila id=7 siempre estuvo ahí — solo cambió su valor.

Phantom read — T1 consulta por condición y el conjunto cambia:
  T1: SELECT * FROM cuentas WHERE saldo > 400  → 3 filas
  T2: INSERT INTO cuentas (saldo) VALUES (500); COMMIT
  T1: SELECT * FROM cuentas WHERE saldo > 400  → 4 filas
  Ninguna fila existente cambió — apareció una fila nueva que califica.
```

El mecanismo que produce las tres formas de phantom read:

```
INSERT — aparece una fila que antes no existía:
  T1: BEGIN ─── COUNT(*)=5 ──────────────── COUNT(*)=6 ─── COMMIT ─►
  T2: ───────────────────── INSERT ── COMMIT ─────────────────────────►

DELETE — desaparece una fila que sí existía:
  T1: BEGIN ─── COUNT(*)=5 ──────────────── COUNT(*)=4 ─── COMMIT ─►
  T2: ───────────────────── DELETE ── COMMIT ─────────────────────────►

UPDATE que cambia si una fila califica la condición:
  T1: SELECT * FROM empleados WHERE salario > 50,000 → 5 filas
  T2: UPDATE empleado_10 SET salario = 60,000  (antes tenía 40,000)  COMMIT
  T1: SELECT * FROM empleados WHERE salario > 50,000 → 6 filas
      El empleado_10 no cambió de identidad — cambió si pertenece al conjunto.
```

En PostgreSQL y MySQL, REPEATABLE READ sí protege contra esto para un SELECT normal. Retomando el ejemplo de arriba: si T1 corriera bajo REPEATABLE READ en vez de READ COMMITTED, esa segunda consulta (`SELECT * FROM cuentas WHERE saldo > 400`) seguiría devolviendo **3 filas, no 4**, aunque T2 ya haya insertado y confirmado la cuarta. La razón: el snapshot de T1 se fijó en el primer `SELECT`, y esa fila nueva simplemente no existía todavía en ese instante. El snapshot no le pregunta "¿esta fila califica la condición?", le pregunta "¿esta fila ya existía cuando arranqué?", y la respuesta es no, sin importar si se busca esa fila por PK o por una condición como esta. Por eso da igual que la fila nueva cumpla `saldo > 400`, para el snapshot de T1 esa fila todavía no existe.

El phantom read tal como se ilustró arriba (pasando de 3 a 4 filas) es, en la práctica, un problema de READ COMMITTED, donde cada `SELECT` toma un snapshot nuevo y sí llega a ver la fila que insertó T2. Donde el phantom read SÍ vuelve a aparecer, incluso bajo REPEATABLE READ, es en el momento de escribir con SELECT FOR UPDATE, que se explica a continuación.

**¿Cuándo importa?**

Cuando T1 toma una decisión basada en cuántas filas existen bajo una condición, o en si alguna fila existe. El phantom read hace que esa información sea incorrecta para cuando T1 la usa:

```
"¿Ningún empleado tiene permiso de superadmin aún?"
  T1: COUNT(superadmins) = 0 → "puedo asignar el primero"
  T2: INSERT otro superadmin
  T1 ve: hay dos superadmins cuando debería haber solo uno

"¿Hay asientos disponibles?"
  T1: COUNT(asientos libres) = 1 → "sí, puedo reservar"
  T2: INSERT reserva para ese asiento
  T1 ve: el asiento ya no está disponible cuando intenta reservar
```

**¿SELECT FOR UPDATE ayuda?**

Depende del motor, porque el problema es el rango del índice, no el valor de una fila:

|                                                                  | MySQL InnoDB (REPEATABLE READ + FOR UPDATE)                                                                      | PostgreSQL (REPEATABLE READ + FOR UPDATE)                                                                                                                         |
| ---------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Mecanismo**                                                    | Next-key lock: un record lock sobre cada fila que califica, más un gap lock sobre el rango del índice consultado | Solo bloquea las filas existentes que califican la condición                                                                                                      |
| **Qué queda bloqueado**                                          | Las filas existentes **y** los espacios (gaps) entre ellas                                                       | Solo las filas existentes; ningún bloqueo sobre el rango del índice                                                                                               |
| **¿T2 puede insertar una fila nueva que califique?**             | No, el gap lock se lo impide                                                                                     | Sí, sin ningún problema                                                                                                                                           |
| **¿El conjunto puede cambiar mientras T1 "lo tiene bloqueado"?** | No                                                                                                               | Sí: la condición que motivó la decisión de T1 puede volverse falsa por una fila que T1 nunca llega a ver (reaparece como write skew más adelante en esta sección) |
| **Costo / efecto secundario**                                    | Los gap locks generan deadlocks frecuentes con INSERTs concurrentes en el mismo rango                            | Ninguno de ese tipo. Para prevenir el phantom read hace falta subir a SERIALIZABLE                                                                                |

Solo SERIALIZABLE con SSI en PostgreSQL previene esto sin usar locks de rango. En vez de bloquear físicamente un rango del índice, SSI usa **predicate locks** (SIREAD locks): registros que marcan qué datos leyó cada transacción, para detectar al confirmar si el resultado habría sido imposible en cualquier orden serial. La diferencia con los gap locks de MySQL es importante: los SIREAD locks de PostgreSQL **nunca bloquean a nadie ni pueden causar deadlocks**, solo marcan dependencias y abortan una de las transacciones si detectan un ciclo al COMMIT. Los gap locks de MySQL, en cambio, sí son locks reales que hacen esperar a otras transacciones, y por eso sí pueden generar deadlocks. Bloque 7 explica el mecanismo completo de ambos.

En la práctica, la mayoría de los equipos toleran el phantom read en REPEATABLE READ porque sus transacciones no dependen de que el conjunto sea inmutable. Solo se sube a SERIALIZABLE cuando la corrección del conjunto es crítica.

---

### Sección 3.5: Anomalías de escritura {#3-anomalias-escritura}

Las anomalías de escritura ocurren cuando T1 escribe algo incorrecto porque su decisión se basó en lecturas que ya no representan el estado real al momento de escribir. Siempre involucran al menos un write de T1. La clave de distinción entre las dos es dónde ocurre el conflicto:

- **Lost update**: T1 y T2 escriben a la **misma fila**. Hay un write-write conflict sobre el mismo dato. Algunos motores pueden detectar esto.
- **Write skew**: T1 y T2 escriben a **filas distintas**. No hay write-write conflict en ningún dato específico. El motor no puede detectar el problema sin SERIALIZABLE.

### Sección 3.6: Lost update {#3-lost-update}

_(anomalía de escritura · write-write conflict en la misma fila)_

T1 y T2 leen el mismo valor, calculan de forma independiente, y la escritura de una sobreescribe el resultado de la otra. El efecto de uno de los dos writes se pierde como si no hubiera ocurrido. El desenlace de este ejemplo depende del motor: así es como termina en MySQL REPEATABLE READ o en READ COMMITTED de cualquier motor, donde nadie protesta. En PostgreSQL REPEATABLE READ, el `UPDATE` de T2 falla con SQLSTATE 40001 apenas se ejecuta, no llega a corromper el dato (el porqué está justo después del ejemplo).

```
Estado inicial: saldo = 1000

T1: BEGIN → SELECT saldo = 1000 → calcula 1000 - 200 = 800 → UPDATE saldo = 800 → COMMIT
T2: BEGIN → SELECT saldo = 1000 → calcula 1000 - 300 = 700 → UPDATE saldo = 700 → COMMIT

Saldo final (MySQL RR / READ COMMITTED): 700. El descuento de T1 (200) desapareció.
Debería ser: 500 (1000 - 200 - 300).
```

El conflicto es write-write sobre la **misma fila**. Por eso algunos motores pueden detectarlo: PostgreSQL REPEATABLE READ lo detecta cuando el UPDATE de la segunda transacción, bloqueado mientras esperaba el lock de la primera, se reactiva tras el COMMIT de esa primera transacción (first-committer-wins, SQLSTATE 40001), no cuando la segunda transacción llega a su propio COMMIT. MySQL REPEATABLE READ no lo detecta y acepta ambos writes silenciosamente.

La diferencia con write skew es que aquí el conflicto es en el mismo dato. En write skew, T1 y T2 escriben a filas distintas, no hay conflicto directo que el motor pueda ver.

READ COMMITTED y REPEATABLE READ no previenen el lost update automáticamente. Requiere intervención explícita. El mecanismo completo, las diferencias entre motores y los patrones de solución están en Bloque 2.

```
Prevenido con: Update atómico (SET col = col + n) — el motor serializa internamente
               SELECT FOR UPDATE — bloquea la fila desde la lectura
               PostgreSQL REPEATABLE READ — detecta el conflicto en el propio UPDATE
               de la segunda transacción, no hace falta llegar al COMMIT (first-committer-wins)
               SERIALIZABLE — previene todo
```

### Sección 3.7: Write skew {#3-write-skew}

_(anomalía de escritura · writes en filas distintas, sin conflicto detectable)_

Anomalía de **escritura**. Cada transacción lee un estado compartido, toma una decisión individualmente válida basándose en él, y escribe a su propia fila. El problema: el resultado combinado de ambas escrituras viola una invariante, pero el motor no puede detectarlo porque no hubo write-write conflict en ninguna fila específica.

La distinción con lost update es exactamente esta: en lost update, T1 y T2 escriben a la **misma fila**, hay un conflicto directo que algunos motores detectan. En write skew, T1 y T2 escriben a **filas distintas**, no hay conflicto write-write en ningún dato concreto, y READ COMMITTED y REPEATABLE READ no tienen mecanismo para detectar que las dos decisiones eran colectivamente incorrectas.

La distinción con phantom read: phantom read es una anomalía de **lectura**, T1 no necesita escribir nada para que ocurra. Write skew requiere que **AMBAS** transacciones escriban. El phantom read puede ser el mecanismo que lleva al write skew (si lo que T1 leyó fue un conjunto que luego cambió), pero write skew también ocurre cuando T2 solo modifica filas existentes, no inserta nada nuevo.

```
Regla: siempre debe haber al menos 1 doctor de guardia.
Estado inicial: Doctor_A = en_guardia, Doctor_B = en_guardia

T1 (Doctor A pide salir):           T2 (Doctor B pide salir):
  SELECT COUNT(*) = 2 → "puedo"       SELECT COUNT(*) = 2 → "puedo"
  UPDATE doctor_A = fuera             UPDATE doctor_B = fuera
  COMMIT                              COMMIT

Resultado: 0 doctores de guardia. La regla fue violada.
```

T1 y T2 escribieron a **filas distintas** (doctor_A y doctor_B). El motor no vio ningún write-write conflict. Cada transacción fue individualmente correcta: ambas leyeron "hay 2 doctores, puedo salir" y eso era verdad en el momento de leer. El problema es que las dos decisiones juntas ya no lo son.

```sql
-- Solución 1: SERIALIZABLE (SSI detecta el ciclo de dependencias, Bloque 7)
BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;
SELECT COUNT(*) FROM guardias WHERE en_guardia = true;
UPDATE guardias SET en_guardia = false WHERE doctor_id = 1;
COMMIT;

-- Solución 2: FOR UPDATE sobre el conjunto que informó la decisión
BEGIN;
SELECT COUNT(*) FROM guardias WHERE en_guardia = true FOR UPDATE;
UPDATE guardias SET en_guardia = false WHERE doctor_id = 1;
COMMIT;
```

```
Ocurre en:    READ COMMITTED, REPEATABLE READ
Prevenido en: SERIALIZABLE · SELECT FOR UPDATE sobre el conjunto leído
```

`SELECT FOR UPDATE ... WHERE en_guardia = true` bloquea **cada fila** que devuelve la consulta, no solo la que vas a modificar. Si hay 100 doctores en guardia, se bloquean los 100. En tablas grandes, esto puede ser muy agresivo. SERIALIZABLE con SSI (PostgreSQL) es más quirúrgico: rastrea dependencias sin bloquear, y aborta solo si detecta que el ciclo de lectura-escritura hace imposible un orden serial.

#### La diferencia fundamental: UPDATE modifica filas existentes, INSERT crea filas nuevas, y eso cambia todo

El ejemplo del doctor parece demostrar que FOR UPDATE resuelve el write skew. Pero ese ejemplo tiene una característica silenciosa: el conflicto viene de que T1 y T2 **modifican filas que ya existen**. Cuando el conflicto viene de un **INSERT**, el análisis es completamente diferente.

**Por qué FOR UPDATE funciona en el ejemplo del doctor (UPDATE a filas existentes):**

```
Estado: Doctor_A = en_guardia, Doctor_B = en_guardia

T1: SELECT * FROM guardias WHERE en_guardia = true FOR UPDATE
    → devuelve 2 filas → bloquea Doctor_A Y Doctor_B

T2: SELECT * FROM guardias WHERE en_guardia = true FOR UPDATE
    → intenta leer las mismas filas → ESPERA (están bloqueadas por T1)

T1: UPDATE Doctor_A SET en_guardia = false → COMMIT → libera locks

T2: ahora adquiere locks, re-lee → solo Doctor_B en guardia → COUNT = 1
    → "no puedo salir, sería el último" → la invariante se preservó ✓
```

FOR UPDATE funciona aquí porque las filas que informan la decisión **ya existen** y ambas transacciones las tocan. El lock de T1 detiene a T2 hasta que el estado cambia.

**Por qué FOR UPDATE falla cuando el conflicto viene de INSERT (filas nuevas):**

```
Invariante: máximo 1 reserva por horario.
Estado inicial: ninguna reserva para el slot '9am'.

T1: SELECT * FROM reservas WHERE horario = '9am' FOR UPDATE
    → devuelve 0 filas → no hay nada que bloquear → 0 locks adquiridos

T2: SELECT * FROM reservas WHERE horario = '9am' FOR UPDATE
    → devuelve 0 filas → no hay nada que bloquear → 0 locks adquiridos

T1: "no existe reserva, puedo crear" → INSERT INTO reservas (horario) VALUES ('9am')
T2: "no existe reserva, puedo crear" → INSERT INTO reservas (horario) VALUES ('9am')

T1: COMMIT → éxito
T2: COMMIT → éxito (en PostgreSQL sin SERIALIZABLE)

Resultado: 2 reservas para '9am'. Invariante violada.
FOR UPDATE no ayudó: bloqueó el conjunto vacío. No había filas que bloquear.
```

Este es el punto crítico: **FOR UPDATE solo puede bloquear filas que existen en el momento de la lectura**. No puede bloquear la "ausencia de filas" ni el derecho a insertar en un rango. Cuando tu decisión es "si no hay nada, inserto", FOR UPDATE da falsa seguridad: adquiere 0 locks y los dos INSERTs pasan sin ninguna colisión.

SERIALIZABLE con SSI detecta este caso porque rastrea dependencias lógicas entre transacciones, no locks físicos sobre filas:

```
SSI detecta el ciclo:
  T1 leyó el predicado (horario='9am') y luego escribió en él  →  rw-dependency: T2→T1
  T2 leyó el predicado (horario='9am') y luego escribió en él  →  rw-dependency: T1→T2

  T1 ──rw──► T2 ──rw──► T1  → ciclo → una de las dos es abortada al COMMIT
```

**El mismo problema con DELETE, y por qué es más sutil que INSERT:**

Con INSERT el problema era simple: FOR UPDATE sobre 0 filas = 0 locks = ambas transacciones
pasan sin colisión. Con DELETE el mecanismo es diferente, pero el resultado final puede
ser igualmente incorrecto. Hay dos patrones:

**Patrón 1: DELETE incondicional (la aplicación no re-evalúa el invariante tras el lock)**

```
Invariante: siempre debe haber al menos 1 médico de guardia.
Estado: Dra. Ana (en_guardia=true), Dr. Bob (en_guardia=true)

T1 (Ana quiere irse):
    SELECT * FROM guardia WHERE en_guardia=true FOR UPDATE
    → adquiere lock sobre [Ana, Bob] → COUNT = 2 → "puedo irme"
    UPDATE guardia SET en_guardia=false WHERE doctor_id='Ana' → COMMIT

T2 (administrador elimina a Bob del sistema por jubilación):
    SELECT * FROM guardia WHERE doctor_id='Bob' FOR UPDATE → ESPERA
    (T1 tiene lock sobre Bob)

    [T1 hace COMMIT: Ana sale, solo Bob queda en guardia, COUNT=1]

    T2 adquiere lock → re-lee la fila de Bob
    → pero el código de T2 no evalúa "¿sigue siendo el único de guardia?"
    → simplemente ejecuta el DELETE como le fue instruido
    DELETE FROM guardia WHERE doctor_id='Bob' → COMMIT

Resultado: 0 médicos de guardia. Invariante violada.
FOR UPDATE hizo que T2 esperara. Pero el delay no cambió la DECISIÓN de T2:
T2 tenía la instrucción "eliminar a Bob" y la ejecutó sin importar el nuevo estado.
```

El punto crítico: **FOR UPDATE protege durante el lock, no dicta la lógica de la aplicación**.
T2 esperó, pero su código no re-verificó la invariante global. El motor no puede saber que
T2 debería haber abortado, solo T2 lo sabe.

Si T2 fuera escrito correctamente (re-evaluando el invariante después de adquirir el lock):

```sql
BEGIN;
SELECT COUNT(*) FROM guardia WHERE en_guardia=true FOR UPDATE;
-- si count <= 1: no se puede eliminar → ROLLBACK
-- si count > 1: continuar
DELETE FROM guardia WHERE doctor_id='Bob';
COMMIT;
```

Con ese código, FOR UPDATE sí protegería. El problema es que DELETE es naturalmente
más "ciego" que UPDATE: quien dice "dar de baja a Bob" a veces no conoce la invariante
global del conjunto.

Con SERIALIZABLE el motor detecta el ciclo automáticamente, sin que el código necesite
hacer esa verificación:

```
T1 leyó guardia (incluyendo Bob) y T2 escribió guardia (eliminó Bob): rw-dependency T2→T1
T2 leyó guardia (Bob existía) y T1 escribió guardia (marcó Ana=false): rw-dependency T1→T2
Ciclo → una de las dos es abortada
```

**Patrón 2: DELETE multi-tabla (T1 no puede lockear todo lo que informa su decisión)**

```
Invariante: aprobar préstamo solo si el cliente no tiene deudas Y tiene al menos 1 garantía.

T1 (evalúa préstamo para cliente 5):
    SELECT * FROM deudas    WHERE cliente_id=5 FOR UPDATE → 0 deudas   ✓
    SELECT * FROM garantias WHERE cliente_id=5            → 1 garantía ✓  ← sin FOR UPDATE
    → "cumple ambas condiciones" → INSERT INTO prestamos VALUES (5, 50000) → COMMIT

T2 (cancela la única garantía de cliente 5, en paralelo):
    DELETE FROM garantias WHERE cliente_id=5   ← T1 no la bloqueó → T2 no espera
    COMMIT

Si T2 commitea después de que T1 leyó garantias pero antes de que T1 commitee:
  T1 en RR: su snapshot sigue mostrando 1 garantía → aprueba → COMMIT
  Resultado: préstamo aprobado pero sin garantía activa. Invariante violada.

FOR UPDATE sobre 'deudas' no protegió la lectura de 'garantias'.
T2 operó libremente sobre una tabla que T1 no bloqueó.
```

En este patrón el problema es estructural: es imposible lockear "todo lo que puede cambiar"
sin saber de antemano qué escribirá T2. SERIALIZABLE resuelve esto trazando dependencias
lógicas entre lecturas y escrituras, no locks físicos.

**La tabla completa de cuándo cada herramienta es suficiente:**

| Escenario                                                                              | FOR UPDATE                                     | SERIALIZABLE                                                     |
| -------------------------------------------------------------------------------------- | ---------------------------------------------- | ---------------------------------------------------------------- |
| Lost update (T1 y T2 modifican la misma fila)                                          | ✓ Suficiente                                   | ✓ Suficiente                                                     |
| Write skew: conflicto por UPDATE (el conjunto a bloquear existe y es enumerable)       | ✓ Suficiente (bloquea filas existentes)        | ✓ Suficiente (detecta ciclo)                                     |
| Write skew: conflicto por INSERT (lees 0 filas y decides insertar)                     | ✗ Falla (bloquea vacío, 0 locks)               | ✓ Necesario (predicate tracking vía SSI)                         |
| Write skew: conflicto por DELETE (tu decisión depende de que ciertas filas NO existan) | ✗ Parcial (no puede bloquear lo que no existe) | ✓ Necesario                                                      |
| Write skew: conjunto muy grande (ej. 100 doctores de guardia)                          | ✗ Agresivo (bloquea los 100)                   | ✓ Quirúrgico (no bloquea a nadie, solo aborta si hay ciclo real) |
| Write skew: conjunto no enumerable al momento de leer (ej. COUNT(\*) > 0)              | ✗ Imposible (¿qué bloqueas?)                   | ✓ Necesario                                                      |

**Regla práctica:**

- Si el conflicto es write-write sobre la misma fila → `FOR UPDATE`
- Si el conflicto involucra filas que no existen aún → `SERIALIZABLE`
- Si el conjunto a bloquear es grande o difícil de enumerar en una sola query → `SERIALIZABLE`

**La solución real para el caso INSERT es un UNIQUE constraint + manejo de error:**

Para el caso específico de "solo debe existir una reserva por horario", la herramienta correcta no es FOR UPDATE ni SERIALIZABLE, es un constraint de base de datos:

```sql
ALTER TABLE reservas ADD CONSTRAINT uq_horario UNIQUE (horario);

-- Ahora el segundo INSERT falla con error de constraint, sin importar
-- el nivel de aislamiento ni si se usa FOR UPDATE.
-- El constraint se verifica al INSERT, antes del COMMIT.
```

SERIALIZABLE es necesario para invariantes que no se pueden expresar como constraints sobre una sola tabla: invariantes que cruzan múltiples filas, múltiples tablas, o que dependen de la ausencia de filas bajo una condición compleja.

### Sección 3.8: Resumen {#3-resumen}

| Tipo                                   | Anomalía                     | Ocurre en                                                    | Prevenida con                                                                                                                          |
| -------------------------------------- | ---------------------------- | ------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------- |
| Lectura (no requiere write de T1)      | Dirty read                   | READ UNCOMMITTED                                             | READ COMMITTED o superior                                                                                                              |
| Lectura (no requiere write de T1)      | Non-repeatable read          | READ COMMITTED                                               | REPEATABLE READ (snapshot) · SELECT FOR UPDATE (lock)                                                                                  |
| Lectura (no requiere write de T1)      | Phantom read                 | READ COMMITTED · REPEATABLE READ solo con locking reads\*    | SERIALIZABLE · gap locks en MySQL RR con FOR UPDATE                                                                                    |
| Escritura (T1 escribe algo incorrecto) | Lost update (misma fila)     | READ COMMITTED (read-modify-write) · MySQL RR sin FOR UPDATE | Update atómico · SELECT FOR UPDATE · PostgreSQL REPEATABLE READ detecta el conflicto (first-committer-wins)                            |
| Escritura (T1 escribe algo incorrecto) | Write skew (filas distintas) | READ COMMITTED, REPEATABLE READ                              | SERIALIZABLE (siempre) · FOR UPDATE solo si el conflicto es UPDATE a filas existentes; falla con INSERT (0 locks) o DELETE multi-tabla |

\* En PostgreSQL RR los phantom reads en SELECTs normales no ocurren, el snapshot cubre toda la base de datos, no solo las filas ya conocidas. En MySQL RR tampoco ocurren en SELECTs normales, por la misma razón; los gap locks solo entran en juego con FOR UPDATE. En SQL Server RR sin RCSI sí ocurren, porque ese modo es lock-based, no MVCC.

Las anomalías de lectura se previenen subiendo el nivel de aislamiento, cada nivel elimina una capa de inconsistencia en las lecturas. Las anomalías de escritura no se previenen automáticamente con el nivel: lost update requiere intervención explícita del programador (FOR UPDATE o update atómico), y write skew requiere SERIALIZABLE o FOR UPDATE sobre el conjunto que informó la decisión. La siguiente sección explica los mecanismos concretos: qué son los locks, cómo funciona MVCC, y cómo los niveles conectan ambas cosas.

Estas cinco anomalías están nombradas y definidas para motores SQL con niveles de aislamiento, pero el problema de fondo, una transacción que decide con datos que ya dejaron de ser ciertos, no es exclusivo de SQL. Cada sistema no relacional lo resuelve parcialmente y casi siempre con otro vocabulario:

- **MongoDB**: las transacciones multi-documento usan snapshot isolation (equivalente a REPEATABLE READ). Su propia documentación reconoce que no dan aislamiento serializable completo y dejan el write skew a cargo de la aplicación, usando ese mismo término.
- **Cassandra**: sus Lightweight Transactions (vía Paxos, un protocolo de consenso que hace que varios nodos se pongan de acuerdo sobre un valor) dan consistencia linealizable, pero solo dentro de una partition key. Un write skew entre partitions distintas no tiene ninguna protección nativa. Cassandra ni siquiera usa ese término, habla de "linearizable consistency" y "CAS" (Compare-And-Swap: verificar el valor actual antes de escribir, y solo escribir si no cambió).
- **DynamoDB**: `TransactWriteItems` ofrece aislamiento serializable, pero solo entre los ítems incluidos en esa transacción explícita. Fuera de ese grupo de ítems, el mismo problema reaparece igual que en SQL sin SERIALIZABLE.
- **Redis**: `WATCH` + `MULTI`/`EXEC` es optimistic locking, previene el lost update sobre la clave vigilada. Pero si el invariante depende de varias claves y no vigilas todas, el write skew ocurre exactamente igual que con `FOR UPDATE` sobre un conjunto vacío en SQL.

El patrón se repite en los cuatro: cada motor resuelve alguna de estas anomalías, casi nunca todas a la vez, y casi nunca con el mismo nombre que usa SQL.

---

## Bloque 4: Savepoints y transacciones anidadas {#4-savepoints}

### Sección 4.1: El problema que resuelven los savepoints {#4-problema}

Dentro de una transacción, si cualquier statement falla, la transacción queda **abortada** en PostgreSQL, no puedes seguir usándola ni hacer commits parciales:

```sql
BEGIN;
INSERT INTO pedidos(usuario_id, total) VALUES (10, 500);  -- OK

INSERT INTO items(pedido_id, producto_id) VALUES (1, 9999);
-- ERROR: FK violation (producto 9999 no existe)

-- A partir de aquí la transacción está "abortada"
INSERT INTO items(pedido_id, producto_id) VALUES (1, 45);
-- ERROR: current transaction is aborted, commands ignored until end of transaction
COMMIT;
-- no hace nada; la txn ya estaba muerta
```

Sin savepoints, si quieres manejar errores parciales debes abortar toda la transacción y empezar de nuevo. Con savepoints, puedes marcar un punto de retorno y deshacer solo desde ese punto.

### Sección 4.2: Qué es un savepoint y cómo se usa {#4-que-es}

Un savepoint es un **marcador dentro de una transacción** que permite hacer rollback parcial: deshacer operaciones desde ese punto hasta el momento actual, y continuar con el resto de la transacción.

```sql
BEGIN;

INSERT INTO pedidos(usuario_id, total) VALUES (10, 500);  -- OK

SAVEPOINT sp1;  -- ← marca un punto de retorno aquí

INSERT INTO items(pedido_id, producto_id) VALUES (1, 9999);
-- FK violation → el statement falla, pero la txn NO muere

ROLLBACK TO SAVEPOINT sp1;
-- ↑ deshace el INSERT fallido y vuelve al estado de sp1
--   la transacción sigue abierta y usable

INSERT INTO items(pedido_id, producto_id) VALUES (1, 45);  -- OK

RELEASE SAVEPOINT sp1;  -- libera el savepoint de memoria (opcional pero limpio)

COMMIT;
-- queda: pedido + item con producto_id=45
```

Después del error no hay ningún reintento automático: el motor nunca vuelve a intentar el statement que falló por su cuenta, esa decisión es siempre de la aplicación. En PostgreSQL, mientras tanto, el error deja la transacción entera marcada como abortada: cualquier statement nuevo que mandes antes de resolver eso, aunque sea perfectamente válido, el motor lo rechaza con `current transaction is aborted, commands ignored until end of transaction`. `ROLLBACK TO SAVEPOINT` es lo único que limpia ese estado sin perder lo anterior al savepoint, deshace el statement fallido y deja la transacción usable de nuevo. A partir de ahí, qué hacer es enteramente decisión de la aplicación: reintentar con datos corregidos (el segundo INSERT del ejemplo, con `producto_id=45`), saltarse esa fila y continuar, o abortar todo con un `ROLLBACK` completo.

Este envenenamiento de toda la transacción ante cualquier error no es universal, varía por motor:

| Motor            | Un error de constraint (FK/unique/check) sin savepoint activo                                                          |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------- |
| **PostgreSQL**   | Aborta toda la transacción. Ningún statement corre hasta un `ROLLBACK`, completo o a un savepoint.                     |
| **MySQL InnoDB** | Solo falla ese statement. La transacción sigue viva, puedes seguir operando sin necesitar un savepoint.                |
| **SQL Server**   | Igual que MySQL por default. Con `SET XACT_ABORT ON`, un error sí aborta toda la transacción, igual que en PostgreSQL. |
| **Oracle**       | Igual que MySQL: el statement falla, la transacción sigue utilizable.                                                  |

### Sección 4.3: Cómo los implementa el motor: WAL y sub-transacciones {#4-implementacion}

Los savepoints no son gratis, tienen un costo real que viene de cómo el motor los implementa internamente. Cada `SAVEPOINT` registra un sub-transaction ID en el log de durabilidad, y cada `ROLLBACK TO SAVEPOINT` escribe un registro de undo hasta ese punto y aplica las operaciones inversas sobre las páginas en el buffer pool, no es gratis: genera escrituras adicionales, y con muchos savepoints o rollbacks frecuentes dentro de una transacción larga, el log puede crecer de forma significativa. El MVCC Engine (Bloque 2) usa esos mismos sub-transaction IDs para decidir qué versiones de fila quedan descartadas tras el rollback parcial, invisibles incluso para la propia transacción. El mecanismo completo en PostgreSQL, con los registros de WAL exactos y el rol de `xmin`, está en `postgresql_caso_estudio.md` §4 y §6.

### Sección 4.4: Locks y savepoints: un comportamiento crítico {#4-locks}

Un lock es una señal que el motor pone sobre una fila para coordinar el acceso concurrente: mientras está activo, cualquier transacción que quiera escribir esa misma fila, o leerla con un lock explícito (`FOR UPDATE`/`FOR SHARE`), tiene que esperar a que se libere. Un `SELECT` normal no espera nada, lee desde su propio snapshot MVCC sin competir con el lock. El mecanismo completo, con los dos tipos de lock y qué espera a qué, está en Bloque 2.

**ROLLBACK TO SAVEPOINT NO libera los locks** adquiridos después del savepoint. Los locks solo se liberan en COMMIT o ROLLBACK completo de la transacción.

```sql
BEGIN;
SELECT * FROM productos WHERE id = 45 FOR UPDATE;  -- X lock sobre fila 45

SAVEPOINT sp1;
SELECT * FROM productos WHERE id = 99 FOR UPDATE;  -- X lock sobre fila 99

ROLLBACK TO SAVEPOINT sp1;
-- Las escrituras se deshacen...
-- PERO: ambos X locks (fila 45 Y fila 99) siguen activos.
-- Ningún otro puede modificar estas filas hasta que esta txn haga COMMIT.

COMMIT;  -- recién aquí se liberan ambos locks
```

Esto importa en producción: si usas ROLLBACK TO SAVEPOINT en un loop procesando filas, los locks se **acumulan** en cada iteración. Con muchas iteraciones puedes terminar sosteniendo cientos de locks simultáneamente, bloqueando a otras transacciones que necesiten esas filas.

### Sección 4.5: Savepoints ≠ transacciones anidadas reales {#4-no-son-anidadas}

PostgreSQL no tiene transacciones anidadas verdaderas. Los savepoints simulan ese comportamiento pero con una diferencia fundamental: **RELEASE SAVEPOINT no hace un commit real**. Si más adelante haces ROLLBACK de la transacción completa, todo se deshace, incluyendo lo que ocurrió antes de un savepoint liberado.

```
Intuición incorrecta:

  BEGIN
    SAVEPOINT sp1
      INSERT ...
    RELEASE sp1      ← "commit del savepoint" ← NO ES UN COMMIT REAL
  ROLLBACK           ← revierte TODO, incluyendo el INSERT anterior al RELEASE

La realidad:

  Un savepoint es solo un marcador de retorno.
  RELEASE simplemente dice "ya no necesito poder volver aquí".
  Solo COMMIT de la transacción exterior confirma los cambios.
```

Contrasta con SQL Server, que tiene `@@TRANCOUNT` y transacciones parcialmente anidables (aunque ROLLBACK sin nombre siempre revierte hasta el nivel 0).

### Sección 4.6: Cuándo usarlos {#4-cuando-usarlos}

| Caso de uso                                                                                                   | Para qué sirve el savepoint ahí                                                                                                                                                                                                                      |
| ------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Procesar un lote con errores individuales (ej. 100 filas a insertar, algunas con FK violations)               | Sin savepoint, la primera fila que falla aborta las 100. Con un `SAVEPOINT` antes de cada insert, solo se descarta la fila que falló (`ROLLBACK TO SAVEPOINT`) y el lote sigue en la misma transacción: las válidas quedan, las inválidas se saltan. |
| Patrón try-then-fallback (upsert manual: intentar INSERT, y si choca con una unique constraint, hacer UPDATE) | El `INSERT` fallido no mata la transacción porque hay un savepoint antes. Se revierte solo ese intento (`ROLLBACK TO SAVEPOINT`) y se reintenta con `UPDATE`, más barato que abortar toda la transacción y reabrirla desde cero.                     |

### Sección 4.7: Por motor {#4-por-motor}

| Motor            | Savepoints   | Observación                                                 |
| ---------------- | ------------ | ----------------------------------------------------------- |
| **PostgreSQL**   | Sí           | Completos. `ROLLBACK TO` no libera locks.                   |
| **MySQL InnoDB** | Sí           | Mismo comportamiento que PostgreSQL.                        |
| **SQL Server**   | Sí (parcial) | `SAVE TRANSACTION`. `ROLLBACK` sin nombre = rollback total. |
| **Oracle**       | Sí           | `SAVEPOINT nombre; ROLLBACK TO nombre;`                     |
| **SQLite**       | Sí           | Dentro de transacciones explícitas.                         |

```sql
-- PostgreSQL y MySQL (InnoDB)
SAVEPOINT nombre;
ROLLBACK TO SAVEPOINT nombre;
RELEASE SAVEPOINT nombre;       -- libera el marcador; no hace commit

-- SQL Server
SAVE TRANSACTION nombre;
ROLLBACK TRANSACTION nombre;    -- solo deshace hasta el savepoint
-- OJO: ROLLBACK TRANSACTION sin nombre aborta TODA la transacción
-- SQL Server no tiene equivalente a RELEASE
```

---

## Bloque 5: El patrón universal: aislamiento y consistencia en cualquier motor {#5-patron-universal}

Ya vimos en Bloque 2 y Bloque 3 cómo PostgreSQL, MySQL, SQL Server y Oracle resuelven el aislamiento con MVCC, locks y niveles de aislamiento. Esta sección da un paso atrás y generaliza el mismo problema a cualquier sistema con acceso concurrente a datos compartidos, sea relacional, documental, de wide-column o distribuido, separándolo en tres preguntas independientes que todos tienen que responder, aunque le pongan nombres distintos a la respuesta. De aquí en adelante se usa "fila" como término genérico para lo que cada sistema llama distinto (fila en SQL, documento en MongoDB, ítem en DynamoDB, clave en Redis, fila dentro de una partition en Cassandra).

### Sección 5.1: Pregunta 1, escrituras concurrentes sin lectura de por medio {#5-pregunta-1}

La pregunta más básica. ¿Qué pasa cuando dos escrituras tocan la misma fila al mismo
tiempo, sin que medie ninguna lectura? Se reduce a algo simple. ¿El motor coordina antes
de escribir, o simplemente escribe encima sin preguntar?

- **Coordina.** Una escritura espera a que la otra termine (lock exclusivo, se explica más arriba en Bloque 2, "Los locks: los dos tipos base y su relación con MVCC"), o el motor detecta el choque internamente y reintenta solo. Es lo que hacen los motores relacionales (con un lock) y MongoDB, a través de WiredTiger (su motor de almacenamiento por defecto): al escribir, detecta un `WriteConflict` si otra transacción ya tiene una modificación pendiente sobre el mismo documento. Fuera de una transacción explícita, WiredTiger reintenta la operación de forma transparente; dentro de una transacción multi-documento, el `WriteConflict` se propaga y es la aplicación la que debe reintentar. En ningún caso se pierde una escritura.
- **No coordina.** Cassandra, DynamoDB (sin condition expression) y Redis (con un `SET` plano) no hacen esperar a nadie ni detectan nada: la escritura que llega después sobreescribe, y la otra desaparece sin ningún error ni aviso (last-write-wins).

Esto conecta directo con no tener MVCC: los tres del segundo grupo no guardan versiones anteriores del dato, como sí vimos arriba con `INSERT`/`UPDATE`/`DELETE` dejando versiones físicas. Al sobreescribir, no queda ningún rastro recuperable de lo que había antes. Si vas a escribir "a ciegas" (sin condición) sobre uno de estos motores, evitar perder datos es responsabilidad tuya: usar sus herramientas de escritura condicional (Lightweight Transactions en Cassandra, condition expressions en DynamoDB, `WATCH` en Redis, todas en la Pregunta 3).

### Sección 5.2: Pregunta 2, qué le muestro a quien lee mientras otros escriben {#5-pregunta-2}

No hay una tercera opción neutral. La lectura espera a que la escritura termine, o sigue
de largo con una versión ligeramente vieja. Aquí sí compiten arquitecturas genuinamente
distintas.

| Alcance                    | Arquitectura                                                                                                                                     | Cómo funciona                                                                                                                                                                                                                                                                                               | Costo                                                                                                                           | Ejemplos                                                                                                                                                                                                                      |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Un solo nodo               | Basada en versiones (**MVCC**, David P. Reed, tesis doctoral en el MIT, 1978)                                                                    | El lector nunca espera, recibe una foto consistente del pasado (mecanismo exacto en "Las versiones de fila" y "El snapshot", en Bloque 2, arriba). La formalización de este nivel como "Snapshot Isolation" viene del mismo paper que ya citamos para las anomalías, Berenson, Bernstein, Gray et al., 1995 | Espacio y limpieza: guardar versiones viejas mientras algún lector las necesite, y reciclarlas después                          | PostgreSQL, MySQL InnoDB, Oracle, SQL Server (snapshot isolation), MongoDB                                                                                                                                                    |
| Un solo nodo               | Basada en bloqueo de lectura (**2PL**, Two-Phase Locking, Eswaran, Gray, Lorie y Traiger, 1976, con locks compartidos)                           | El lector toma un lock compartido (S lock): bloquea en las dos direcciones, un escritor espera al lector, y un lector nuevo espera a un escritor con lock exclusivo (X lock) activo                                                                                                                         | Tiempo de espera compartido entre lectores y escritores                                                                         | SQL Server sin RCSI (Read Committed Snapshot Isolation); SQLite en modo rollback-journal, donde el lock es sobre el archivo completo, no por fila, aunque solo se vuelve totalmente exclusivo justo antes y durante el commit |
| Varias réplicas            | Basada en quorum (David K. Gifford, "Weighted Voting for Replicated Data", 1979)                                                                 | No hay snapshot ni lock: la consistencia se compra exigiendo respuesta a un número mínimo de réplicas (el desarrollo completo, con el solape entre réplicas de lectura y escritura, está en Bloque 1)                                                                                                       | Latencia de red, no bloqueo local                                                                                               | Cassandra, DynamoDB                                                                                                                                                                                                           |
| Un solo nodo, un solo hilo | Basada en ejecución serial (**Serial**, Stonebraker, Madden et al., "The End of an Architectural Era", VLDB 2007; arquitectura H-Store / VoltDB) | No hace falta que el lector espere ni que reciba una versión vieja, porque nunca hay dos operaciones corriendo al mismo tiempo. Un solo hilo ejecuta un comando de principio a fin antes de tocar el siguiente, así que no existe la lectura concurrente con una escritura que el problema busca resolver   | Cero paralelismo real: todo el rendimiento depende de qué tan rápido sea ese único hilo, no de cuántos núcleos tenga la máquina | Redis, en su modo estándar de un solo nodo                                                                                                                                                                                    |

### Sección 5.3: Pregunta 3, cómo evito que una escritura se base en una decisión obsoleta {#5-pregunta-3}

Es distinta de la Pregunta 1. Ahí bastaba con que dos escrituras no se corrompieran entre
sí; aquí el problema es que la decisión detrás de la escritura ya puede ser incorrecta
antes de que ese mecanismo siquiera entre en juego, porque hubo una lectura de por medio
cuyo resultado pudo quedar obsoleto.

| Alcance                       | Arquitectura                                                                          | Cómo funciona                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | Costo                                                                                | Ejemplos                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| ----------------------------- | ------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Depende de qué bloquees       | Pesimista: locking (**2PL**)                                                          | `SELECT FOR UPDATE` (o equivalente) extiende el lock hasta la lectura, no solo la escritura. Cubre la misma fila siempre. Cubre también filas distintas solo si bloqueas el conjunto completo relevante para tu decisión, y solo si ese conjunto ya existe y se puede enumerar de antemano, con los límites exactos ya vistos en la tabla del Bloque 3, falla con INSERT y con conjuntos no enumerables                                                                                                                                                                                                                                                                                              | Concurrencia perdida; con conjuntos grandes de filas escala mal (tabla del Bloque 3) | Disponible como herramienta manual en prácticamente cualquier motor SQL                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| Un solo nodo, misma fila      | Optimista: **Optimistic Concurrency Control** (**OCC**, Kung y Robinson, 1981)        | Cada fila tiene su propia marca de su última modificación, sin bloquear a nadie con ella. Al escribir sobre esa misma fila, el conflicto se detecta ahí, no al leer: si esa fila fue modificada y confirmada por alguien más desde que empezaste, tu escritura se aborta, aunque tus lecturas nunca hayan mostrado ese cambio. Esa marca no tiene que venir de MVCC, puede ser metadata que el motor ya traía por otra razón, o algo aparte que la aplicación agrega ella misma                                                                                                                                                                                                                      | Transacciones abortadas que hay que reintentar                                       | PostgreSQL y Oracle reutilizan su propia metadata de versiones de MVCC para esto, sin necesitar nada aparte (first-committer-wins en PostgreSQL, ver "Las versiones de fila" arriba). MongoDB y SQL Server, con snapshot isolation, hacen lo mismo con su propia metadata de versiones. El "optimistic locking" que muchos ORMs implementan a mano con una columna `version` logra lo mismo sin ningún MVCC de por medio, funciona sobre cualquier motor, incluso uno sin soporte nativo para versiones. Redis hace lo mismo con `WATCH`, vigilando una clave, sin necesitar consenso porque es de un solo nodo |
| Un solo nodo, filas distintas | Optimista: **Serializable Snapshot Isolation** (**SSI**, Cahill, Röhm y Fekete, 2008) | El motor anota, para cada fila, qué transacción la leyó. Esto no es parte de MVCC, es un mecanismo aparte llamado predicate lock (SIREAD lock en PostgreSQL) que no bloquea a nadie, solo registra el acceso. Si T2 escribe una fila que T1 ya había leído, eso por sí solo es normal, no significa ningún problema. El problema aparece cuando esa misma relación se cierra también en la otra dirección. Si T1 a su vez escribió una fila que T2 ya había leído, mientras las dos transacciones seguían corriendo al mismo tiempo, ningún orden serial puede acomodar las dos relaciones a la vez. El motor detecta ese cruce en ambos sentidos y aborta una de las dos transacciones al confirmar | Igual que el anterior, más el costo de mantener el registro de lecturas              | PostgreSQL, en su nivel SERIALIZABLE, para conflictos entre **filas distintas** (write skew)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| Varias réplicas               | Optimista: **Compare-And-Swap** (**CAS**) vía consenso distribuido (Paxos, Raft)      | El dato vive en varias copias, así que antes de aplicar el CAS con seguridad, las réplicas involucradas tienen que ponerse de acuerdo sobre el valor actual mediante un protocolo de consenso                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | Igual que los anteriores, más la latencia de red del consenso entre réplicas         | Cassandra, Lightweight Transactions vía Paxos, sobre una partition; DynamoDB, condition expressions, sobre un ítem                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |

Una aclaración importante sobre esta tabla: describe qué pasa **automáticamente**, sin que el código pida nada explícito. `SELECT FOR UPDATE` está disponible como herramienta manual en prácticamente cualquier motor SQL, sin importar qué tan optimista sea su comportamiento nativo. Lo que varía por motor es qué pasa si **no lo usas**: PostgreSQL (en REPEATABLE READ o SERIALIZABLE), Oracle (en su nivel SERIALIZABLE) y CockroachDB detectan el conflicto solos; MySQL InnoDB, en cambio, no lo detecta en ningún nivel, y sin FOR UPDATE deja pasar el dato incorrecto en silencio.

### Sección 5.4: El costo de sostener la transacción abierta {#5-costo-sostener}

Una tercera arquitectura, aparte de las dos anteriores. Las dos tablas de arriba responden qué le muestro a quien lee y cómo protejo una decisión. Esta responde una pregunta distinta: mientras la transacción sigue abierta, ¿qué le cuesta al motor mantenerla así? A diferencia de MVCC, 2PL, Quorum, Serial, OCC, SSI y CAS, estas seis categorías no vienen de un paper académico con autor y año, son una clasificación práctica, construida a partir de hechos ya verificados de cada motor contra su propia documentación oficial, no una arquitectura con nombre propio en la literatura. Y a diferencia de los identificadores de arriba, un motor puede caer en más de una categoría a la vez, porque estos costos no son mutuamente excluyentes: MySQL retiene versiones en su undo log y además sostiene gap locks más tiempo; Spanner y CockroachDB coordinan entre nodos y, al usar MVCC para leer, también retienen versiones con su propia ventana de recolección de basura:

| Categoría                                 | Causa de fondo                                                                                                                                         | Riesgo concreto si se sostiene la transacción abierta                                                                                                                                                                                                                                                                                                    | Motores                                                                                                        |
| ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| **Retención de versiones**                | MVCC necesita conservar versiones viejas de una fila mientras algún snapshot activo todavía pueda necesitarlas                                         | Degradación gradual (bloat en PostgreSQL, undo log creciendo en MySQL, espacio en `tempdb` en SQL Server, presión de cache en MongoDB) hasta un error duro en el peor caso (`ORA-01555` en Oracle, o rechazo de la lectura si ya pasó la ventana de garbage collection en Spanner/CockroachDB), o un archivo de log que crece sin límite (WAL en SQLite) | PostgreSQL, MySQL, Oracle, SQL Server (snapshot isolation), SQLite (WAL), MongoDB, Google Spanner, CockroachDB |
| **Escalamiento y concentración de locks** | Sin snapshot que separe al lector del escritor, el lock es el único mecanismo, y sostenerlo más tiempo o sobre más datos lo hace crecer                | Bloqueo de golpe a todos los demás, por escalamiento automático a partir de un umbral (5000 locks en SQL Server, verificado con Microsoft Learn), por gap locks sostenidos más tiempo (MySQL), o por concentración en un punto caliente de la topología (nodos "hub" en Neo4j)                                                                           | SQL Server (default), SQLite (rollback journal), MySQL, Neo4j                                                  |
| **Coordinación distribuida entre nodos**  | La transacción tiene que ponerse de acuerdo con nodos físicamente distintos, a veces en otra región geográfica                                         | El costo crece con cuántos nodos toca y qué tan lejos están; el más caro de los seis en términos absolutos, aunque ambos motores ofrecen una salida más barata para solo lectura                                                                                                                                                                         | Google Spanner, CockroachDB                                                                                    |
| **Capa transaccional añadida**            | La unidad atómica nativa del motor es más pequeña que una transacción, un documento o un ítem, y la transacción agrupa varias por encima de esa unidad | Costo extra medible por transacción: presión de cache en WiredTiger (MongoDB) o el doble de write capacity units, verificado con la documentación de AWS (DynamoDB)                                                                                                                                                                                      | MongoDB, DynamoDB                                                                                              |
| **Sin transacción de sesión real**        | El motor nunca ofreció un constructo de transacción multi-sentencia con aislamiento real más allá de su unidad mínima de datos                         | La única alternativa (un batch) da atomicidad pero no aislamiento fuera de esa unidad mínima, y la propia documentación oficial lo marca como antipatrón de rendimiento si se fuerza                                                                                                                                                                     | Cassandra                                                                                                      |
| **Sin estado de sesión que sostener**     | No hay snapshot ni locks de fila que mantener, un solo hilo ejecuta todo en el orden en que llega                                                      | El único costo es la espera de otros clientes en la cola, no hay ningún estado adicional que el motor deba sostener                                                                                                                                                                                                                                      | Redis                                                                                                          |

### Sección 5.5: Guía comparativa, las preguntas 2 y 3 por motor {#5-guia-comparativa}

La Pregunta 1 no aparece como columna: ya quedó resuelta arriba (todos evitan perder una escritura silenciosamente, excepto Cassandra, DynamoDB y Redis sin herramientas condicionales). Cada celda arranca con los identificadores en negritas de todas las arquitecturas que ese motor tiene disponibles para la Pregunta 3, no solo la que protege por defecto, usando el mismo vocabulario definido en las dos tablas de arriba (**MVCC**, **2PL**, **Quorum**, **Serial** para la Pregunta 2; **OCC**, **SSI**, **CAS** para la Pregunta 3). Cuando un motor combina varias, aparecen todos los identificadores que le aplican. Dos calificadores distinguen matices dentro del mismo identificador: "manual" marca que esa protección no ocurre por defecto, solo si tú mismo pides `FOR UPDATE` o su equivalente (MySQL, Oracle en READ COMMITTED, PostgreSQL en cualquier nivel, SQL Server con snapshot isolation); "trivial" marca que el motor sí protege automáticamente, pero no porque coordine escritores compitiendo entre sí, sino porque nunca permite que exista más de un escritor a la vez (SQLite). Cuando **2PL** aparece sin ningún calificador (SQL Server por defecto, Google Spanner, CockroachDB) es el tercer caso posible: ahí el bloqueo es automático, por defecto, y sí coordina escritores reales que compiten entre sí, no hace falta pedir nada manual y no hay un solo escritor trivial de por medio. La columna de la Pregunta 3 dice, para cada motor, si su protección cubre solo la misma fila o también filas distintas, write skew, siguiendo el mismo eje de "Alcance" que ya usamos en las dos tablas de arriba. La columna de costo separa lectura de escritura con `|`. "Cuándo conviene" es el veredicto que se saca de leer las columnas anteriores para ese motor. La última columna, "Costo de sostener la transacción abierta", es un eje distinto a todos los anteriores: no pregunta qué te protege mientras la transacción corre, pregunta qué le cuesta al motor mantenerte protegido mientras decides. La diferencia de fondo es si la transacción es la unidad de trabajo nativa del motor (la misma maquinaria de MVCC, locks o undo log que ya usa cualquier sentencia suelta, solo sostenida más tiempo) o si es una capa añadida sobre un motor pensado para operaciones de un solo dato (un documento, un ítem, una partition), con un costo de sesión que no existe en el primer grupo:

| Motor / arquitectura            | Pregunta 2 (lectura)                                                                                                                                                                                                                                                                                                                                                                             | Pregunta 3 (escritura), y qué alcance cubre                                                                                                                                                                                                                                                                                                        | Costo: lectura \| escritura                                                         | Cuándo conviene                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | Costo de sostener la transacción abierta                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| PostgreSQL                      | **MVCC.** Snapshot                                                                                                                                                                                                                                                                                                                                                                               | **OCC + SSI + 2PL, manual.** Cubre la misma fila en REPEATABLE READ, con first-committer-wins. Cubre también filas distintas en SERIALIZABLE, con SSI, protege write skew. `FOR UPDATE` también está disponible como herramienta manual en cualquier nivel                                                                                         | Limpieza de versiones (vacuum) \| aborts en SERIALIZABLE (`SQLSTATE 40001`)         | El más completo entre los relacionales clásicos: lecturas nunca bloqueadas y, en SERIALIZABLE, la única protección automática contra write skew sin bloquear a nadie. Esa protección no es gratis: ese mismo nivel tiene overhead real de CPU y una tasa de abortos notablemente más alta que REPEATABLE READ                                                                                                                                                                                                                                                                                                                                                                                                                                                       | **Retención de versiones.** Nativa: no hay diferencia estructural entre una transacción de una fila y una de mil, es la misma maquinaria de MVCC y WAL que ya usa cualquier sentencia suelta. El único costo de sostenerla abierta es que vacuum no puede limpiar versiones que el propio snapshot todavía podría necesitar. Si va a operar shardeado con Citus, ver Bloque 8: entre shards solo da atomicidad, no las garantías de esta fila                                                                                                                                                                                                                                                                                                                                     |
| MySQL InnoDB                    | **MVCC.** Snapshot                                                                                                                                                                                                                                                                                                                                                                               | **2PL, manual.** Con FOR UPDATE. Cubre el conjunto que bloquees, con los límites ya vistos en Bloque 3, falla con INSERT o con conjuntos no enumerables. Sin FOR UPDATE, no cubre nada, en ningún nivel. No tiene OCC ni SSI nativos, es la única arquitectura que ofrece                                                                          | Undo log \| deadlocks por gap locks                                                 | Su lectura es MVCC, tan rápida como PostgreSQL, pero en escritura exige disciplina propia: sin `FOR UPDATE` explícito, ninguna decisión basada en una lectura queda protegida, en ningún nivel de aislamiento, a diferencia de PostgreSQL u Oracle que sí ofrecen algo automático. Si además sus transacciones tienden a ser largas, aquí se paga doble: undo log creciendo y gap locks concentrándose al mismo tiempo, dos riesgos que otros motores de esta tabla no combinan. Y si sube a su nivel SERIALIZABLE esperando el comportamiento no bloqueante de PostgreSQL, no lo obtiene: MySQL convierte cada `SELECT` en `FOR SHARE`, lectores y escritores compiten por el lock igual que en un motor sin MVCC                                                  | **Retención de versiones + escalamiento y concentración de locks.** Nativa: el undo log y el locking ya están siempre activos, incluso para una sola sentencia suelta. Sostener la transacción abierta más tiempo tiene dos efectos, no uno: retrasa que el purge thread limpie el undo log, y además mantiene los gap locks tomados por más tiempo, la causa ya señalada en esta misma fila de los deadlocks más comunes en producción. Si va a operar shardeado con Vitess, ver Bloque 8: mismo patrón que Citus, atomicidad entre shards sin las garantías de esta fila                                                                                                                                                                                                        |
| Oracle                          | **MVCC.** Snapshot, vía undo segments, en el undo tablespace                                                                                                                                                                                                                                                                                                                                     | **2PL, manual + OCC.** Con FOR UPDATE en READ COMMITTED, mismo alcance y límites que MySQL. Optimista en su nivel SERIALIZABLE, pero ahí solo cubre la misma fila, no previene write skew a pesar del nombre                                                                                                                                       | Undo tablespace \| reintentos en SERIALIZABLE (`ORA-08177`)                         | Igual que MySQL, su lectura MVCC nunca hace esperar; su SERIALIZABLE es optimista pero solo cubre la misma fila, no previene write skew a pesar del nombre, no lo confundas con el de PostgreSQL, que sí lo hace                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | **Retención de versiones.** Nativa: el undo tablespace y el locking son la misma maquinaria para una sentencia suelta o para toda la transacción. Sostenerla abierta más tiempo arriesga `ORA-01555` si una lectura larga necesita una versión que el undo ya reutilizó, un riesgo que se ajusta con `undo_retention`, no un límite fijo                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| SQL Server (default)            | **2PL.** Bloqueo, S locks                                                                                                                                                                                                                                                                                                                                                                        | **2PL.** Cubre la misma fila mientras el lock esté activo. Cubre también filas distintas si subes a su propio SERIALIZABLE, basado en locks. Es la protección nativa, no hace falta pedir nada manual                                                                                                                                              | Bloquea escritores mientras lee \| bloquea lectores mientras escribe                | Conviene cuando prefieres el modelo de locks tradicional y previsible sobre paralelismo de lectura; con carga de lectura intensiva, aquí es donde más se siente la espera                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | **Escalamiento y concentración de locks.** Nativa: los locks ya son la maquinaria de cualquier sentencia suelta. Sostener la transacción abierta más tiempo, tocando más filas, arriesga la escalación automática de locks de fila a un lock de tabla completo, verificado con Microsoft Learn: se dispara a partir de 5000 locks sobre una sola referencia a la tabla, revisado cada 1250 locks nuevos, bloqueando de golpe a todos los demás                                                                                                                                                                                                                                                                                                                                    |
| SQL Server (snapshot isolation) | **MVCC.** Version store en `tempdb`, la base de datos temporal compartida de SQL Server                                                                                                                                                                                                                                                                                                          | **OCC + 2PL, manual.** Cubre solo la misma fila. No previene write skew, para eso hace falta el SERIALIZABLE basado en locks de la fila de arriba, no este modo por versiones. Los locking hints manuales (`UPDLOCK`) siguen disponibles incluso con snapshot isolation activo                                                                     | Espacio en `tempdb` \| aborts por conflicto                                         | La forma de tener lecturas tipo MVCC sin salir de SQL Server; sigue sin proteger write skew, para eso hace falta su propio SERIALIZABLE basado en locks                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | **Retención de versiones.** Nativa: el version store en `tempdb` es la misma maquinaria para una sentencia suelta o para toda la transacción. Sostenerla abierta más tiempo solo consume más espacio en `tempdb`, la base de datos temporal compartida por todas las bases del mismo servidor, así que el costo de una transacción larga no queda aislado a su propia base                                                                                                                                                                                                                                                                                                                                                                                                        |
| SQLite (rollback journal)       | **2PL, trivial.** Bloqueo del archivo completo                                                                                                                                                                                                                                                                                                                                                   | **2PL, trivial.** Un solo escritor a la vez. Cubre cualquier alcance de forma trivial, no hay ningún otro escritor con quien competir                                                                                                                                                                                                              | Lectores bloqueados durante el commit \| cero paralelismo entre escritores          | Ideal para un solo escritor real: desktop, embebido, tests; en cuanto haya más de un escritor concurrente de verdad, este no es tu motor                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | **Escalamiento y concentración de locks, trivial.** Nativa: cualquier escritura ya es una transacción implícita. No hay costo adicional por agrupar varias en una sola, solo el mismo bloqueo del archivo completo, sostenido por más tiempo, sin escalamiento que calcular porque ya es el máximo posible desde la primera escritura                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| SQLite (WAL)                    | **MVCC.** Snapshot para lectores                                                                                                                                                                                                                                                                                                                                                                 | **2PL, trivial.** Un solo escritor a la vez. Mismo alcance trivial que el modo rollback journal                                                                                                                                                                                                                                                    | Ninguno para lectores \| un solo escritor a la vez                                  | Mejora clara sobre el modo anterior si necesitas que los lectores no esperen al escritor; sigue habiendo un solo escritor a la vez para toda la base                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | **Retención de versiones.** Nativa, misma maquinaria que el modo rollback journal, pero con su propio riesgo documentado: mientras un lector siga activo, el checkpoint no puede truncar el archivo WAL, así que ese archivo crece sin límite hasta que la transacción termine, un problema de espacio en disco, no de bloqueo, este es el único motor de la tabla donde una transacción larga puede agotar el disco en vez de solo hacer esperar a alguien                                                                                                                                                                                                                                                                                                                       |
| MongoDB                         | **MVCC.** Snapshot, WiredTiger                                                                                                                                                                                                                                                                                                                                                                   | **OCC.** Cubre solo la misma fila, el documento. El write skew queda a cargo de la aplicación                                                                                                                                                                                                                                                      | Limpieza de versiones \| write skew sin protección automática                       | Cubre la misma fila, el documento, igual de limitado que Oracle SERIALIZABLE o SQL Server con snapshot isolation; el write skew entre documentos distintos queda completamente a cargo de tu aplicación, sin ninguna ayuda nativa. Si sus transacciones multi-documento tienden a ser largas, pagan dos costos a la vez: la sobrecarga de no ser la unidad nativa del motor, y la presión de cache de retener versiones mientras el snapshot sigue abierto                                                                                                                                                                                                                                                                                                          | **Capa transaccional añadida + retención de versiones.** Añadida: el documento individual es la unidad atómica nativa, la transacción multi-documento es una capa encima. Además, como lee con MVCC, mientras dura, WiredTiger mantiene el snapshot fijo, y las escrituras nuevas de otras transacciones se acumulan en cache sin poder liberarse hasta que termine, el mismo problema de fondo que el vacuum de PostgreSQL, aquí como presión de cache en vez de bloat en disco. Si cruza shards, no se conforma con atomicidad, como Citus o Vitess sobre motores relacionales: coordina con timestamps causalmente consistentes más two-phase commit, y con `readConcern: "snapshot"` sí da una vista consistente entre shards (mapa completo en Bloque 8)                     |
| Cassandra                       | **Quorum**                                                                                                                                                                                                                                                                                                                                                                                       | **CAS.** Vía Paxos, Lightweight Transactions, LWT. Cubre solo la misma partition key. Sin protección entre partitions distintas                                                                                                                                                                                                                    | Latencia de red \| sin protección entre partitions distintas                        | Para cuando disponibilidad y escritura distribuida importan más que consistencia inmediata; usa LWT solo para el puñado de casos que de verdad necesitan esa protección, es deliberadamente costosa                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | **Sin transacción de sesión real.** No existe una transacción de sesión abierta con varias sentencias como en los motores anteriores. Lo más cercano es un batch: da atomicidad, pero solo da aislamiento si toca una sola partition. Con varias partitions, otros clientes pueden leer resultados parciales mientras el batch corre, y la documentación oficial lo marca como un antipatrón de rendimiento que hay que evitar                                                                                                                                                                                                                                                                                                                                                    |
| DynamoDB                        | **Quorum** (o lectura fuertemente consistente opcional)                                                                                                                                                                                                                                                                                                                                          | **CAS.** Condition expressions, cubren un solo ítem. `TransactWriteItems` extiende esto hasta 100 ítems explícitos en la misma llamada, pero no un conjunto abierto como SSI                                                                                                                                                                       | Latencia en lectura fuerte \| alcance limitado a la transacción                     | Conviene dentro del ecosistema AWS cuando tu unidad de conflicto es un ítem, o hasta 100 explícitos por transacción; no esperes algo como SSI sobre un conjunto abierto de datos                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | **Capa transaccional añadida.** Añadida: el ítem individual es la unidad atómica nativa, igual que en MongoDB, pero con una diferencia real frente al resto de esta columna: `TransactWriteItems` es una sola llamada atómica, no una sesión que se mantiene abierta entre varios round-trips. El costo no crece porque la transacción "dure más", es fijo por llamada, el doble de write capacity units que una escritura condicional normal sobre el mismo ítem, por las dos escrituras internas (prepare y commit) que exige la garantía transaccional                                                                                                                                                                                                                         |
| Redis                           | **Serial.** Sin snapshot, lectura directa, un solo hilo                                                                                                                                                                                                                                                                                                                                          | **OCC.** Con `WATCH` + `MULTI`/`EXEC`. Cubre las claves que vigiles explícitamente. Si el invariante depende de una clave que no vigilaste, no hay protección                                                                                                                                                                                      | Sin aislamiento de lectura \| sin protección si no vigilas todas las claves         | La opción cuando la velocidad importa más que la coordinación automática; tú vigilas con `WATCH` cada clave relevante, si olvidas una, no hay ninguna protección                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | **Sin estado de sesión que sostener.** Prácticamente nativa: `MULTI`/`EXEC` solo encola comandos y los ejecuta de corrido sin que otro cliente se intercale, porque el motor ya es de un solo hilo. El costo real es que un batch grande hace esperar más a los demás clientes en la cola, no una capa de sesión aparte que mantener. En Redis Cluster (repartido en varios shards), esto solo aplica dentro de un mismo shard: si `MULTI`/`EXEC` toca claves de shards distintos, Redis rechaza la transacción completa con `CROSSSLOT` antes de ejecutar nada, no ofrece ninguna versión débil entre shards como Citus o Vitess (mapa completo en Bloque 8)                                                                                                                     |
| Google Spanner                  | **MVCC.** Snapshot con timestamp global (TrueTime)                                                                                                                                                                                                                                                                                                                                               | **2PL.** Con locks reales más two-phase commit entre nodos. Cubre cualquier alcance, incluyendo filas distintas y write skew, por diseño, external consistency                                                                                                                                                                                     | Esperar la incertidumbre de TrueTime \| two-phase commit entre nodos                | Cuando necesitas consistencia fuerte real entre nodos en distintas regiones; protege cualquier alcance, incluido write skew, por diseño, pero eso cuesta esperar la incertidumbre del reloj de TrueTime en cada escritura y coordinar two-phase commit entre nodos. Para cargas de solo lectura tiene una salida aparte y mucho más barata: las read-only transactions no toman locks, no esperan TrueTime ni 2PC, y pueden leer de la réplica más cercana en vez de la líder, a cambio de una antigüedad acotada en el dato. Evite transacciones inusualmente largas: retiene versiones solo por una ventana configurable, 1 hora por defecto, hasta una semana como máximo, y una que se pase de esa ventana puede fallar al no encontrar la versión que necesita | **Coordinación distribuida entre nodos + retención de versiones.** Nativa a su diseño distribuido, pero esa nativa cuesta más que en un solo nodo: mantiene locks reales tomados en cada nodo involucrado hasta el commit, más el two-phase commit entre ellos, más la espera de la incertidumbre de TrueTime antes de poder confirmar, un reloj que depende de infraestructura de GPS y relojes atómicos que solo Google mantiene, por eso Spanner solo existe como servicio administrado, no autohospedado. Como también usa MVCC para leer, retiene versiones viejas por una ventana configurable, 1 hora por defecto, hasta una semana como máximo, y una transacción que sostenga un snapshot más allá de esa ventana se arriesga a que la versión que necesita ya no exista |
| CockroachDB                     | **MVCC.** Snapshot, lectura sin lock                                                                                                                                                                                                                                                                                                                                                             | **2PL + SSI.** Híbrido, write intents más detección inmediata de conflictos. Cubre cualquier alcance, incluyendo filas distintas y write skew, por defecto, con su propia versión distribuida de SSI                                                                                                                                               | Ninguno adicional en lectura \| reintentos bajo contención alta                     | Consistencia fuerte distribuida similar a Spanner, pero con una operación más parecida a PostgreSQL y sin locks bloqueantes en lectura; a cambio, bajo contención alta paga con más reintentos. Esos reintentos muchas veces ni los ve su aplicación: el motor los hace solo, del lado del servidor, siempre que el resultado a devolver sea menor a 16KiB por defecto. Para solo lectura tiene el mismo mecanismo que Spanner con otro nombre, follower reads, sirviendo desde la réplica más cercana con antigüedad acotada. Su ventana de retención (GC TTL, 25 horas por defecto) es más generosa que la de Spanner, pero sigue siendo finita: una transacción excepcionalmente larga puede toparse con ella igual                                              | **Coordinación distribuida entre nodos + retención de versiones.** Nativa a su diseño distribuido: los write intents que deja cada escritura son justamente el estado que sostiene la transacción mientras dura, sin locks bloqueantes de lectura. A diferencia de Spanner, resuelve el orden entre nodos con HLC, un reloj lógico por software, sin depender de hardware especial, lo que le permite existir autohospedado. Bajo contención alta, sostenerla más tiempo aumenta la probabilidad de reintentos. Y como también usa MVCC para leer, tiene su propio GC TTL, 25 horas por defecto: una transacción que intente leer una versión ya recolectada no se bloquea en silencio, recibe un error directo                                                                   |
| Neo4j                           | No encaja en ninguna de las cuatro arquitecturas de arriba. No hay snapshot (no es MVCC), no hay lock de lectura (no es el 2PL de esta tabla, que sí exige que el lector tome un lock), no hay réplicas (no es Quorum), y es genuinamente multi-hilo (no es Serial). El lector solo ve el valor confirmado más reciente, sin ningún mecanismo de coordinación, expuesto a lecturas no repetibles | **2PL.** Locks de escritura exclusivos y automáticos, acumulados durante la transacción y liberados todos hasta el commit, la definición original de Eswaran, Gray, Lorie y Traiger, 1976. Protegen la escritura puntual, pero no una decisión basada en una lectura previa, para eso hace falta el lock manual vía la API, igual que `FOR UPDATE` | Ninguno en lectura \| locks de escritura hasta el commit, con detección de deadlock | Elígelo cuando el problema es genuinamente de grafo; en transacciones acepta que no hay coordinación de lectura, y que proteger una decisión sobre una lectura previa requiere locks manuales                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | **Escalamiento y concentración de locks.** Nativa: los locks de nodo y relación son la misma maquinaria de cualquier escritura suelta. A diferencia de SQL Server, aquí el riesgo no escala con cuántas filas toca una transacción, sino con la topología del grafo: en nodos "hub" muy conectados, una transacción larga sobre uno de esos nodos concentra locks de muchas otras transacciones distintas, incluso si ninguna de ellas es especialmente grande                                                                                                                                                                                                                                                                                                                    |

### Sección 5.6: Sharding no es lo mismo que nacer distribuido {#5-sharding-vs-distribuido}

Todos los motores distribuidos de esta tabla, Spanner, CockroachDB, Cassandra y DynamoDB, resolvieron el problema de la concurrencia distribuida desde el diseño. Pero hay una tercera categoría que no aparece como fila propia, porque no es un motor nuevo, es una capa que se instala sobre un motor de un solo nodo ya conocido: Citus sobre PostgreSQL, o Vitess sobre MySQL. Ambos reparten los datos en varios nodos (shards) y, cuando una transacción toca solo uno, se comportan exactamente igual que el motor de base, con las mismas garantías que ya vio en la fila de PostgreSQL o MySQL de esta misma tabla. El cambio radical ocurre en cuanto una transacción cruza más de un shard. Ahí, tanto Citus como Vitess usan two-phase commit para garantizar atomicidad, pero 2PC por sí solo no da aislamiento. Ninguno de los dos tiene un mecanismo de snapshot distribuido como el TrueTime de Spanner o el HLC de CockroachDB, así que una transacción entre shards cae a READ COMMITTED como máximo, sin importar que el motor de base soporte SERIALIZABLE en un solo nodo, y es posible que una consulta vea el resultado parcial de un commit todavía en curso entre shards. Los propios mantenedores de ambos proyectos lo explican igual: dar un snapshot distribuido real exige pagar viajes de red extra o esperar un reloj, el mismo costo que Spanner y CockroachDB sí asumieron desde el diseño, y que Citus y Vitess decidieron no pagar a cambio de rendimiento.

Esto cambia la decisión de fondo: si su carga se puede diseñar para que cada transacción viva dentro de un solo shard (el caso típico es particionar por cliente o por tenant, de forma que "todo lo de un cliente" quede junto), una capa de sharding sobre PostgreSQL o MySQL le da exactamente las mismas garantías transaccionales que ya conoce de esos motores, con mucha menos complejidad operativa que migrar a un motor nacido distribuido. Si su carga necesita, como caso común y no como excepción, transacciones que crucen varias particiones con aislamiento fuerte garantizado, ahí sí necesita un motor nacido distribuido como Spanner o CockroachDB, porque el sharding instalado encima de un motor de un solo nodo, por diseño, no se lo va a dar.

Pero no todos los motores con shards resuelven esto de la misma forma, hay tres caminos distintos, no dos. **MongoDB, en un clúster shardeado, no sigue el patrón débil de Citus y Vitess**: construyó su propio protocolo de snapshot distribuido, con timestamps causalmente consistentes entre shards más two-phase commit para el conmit atómico. Con `readConcern: "snapshot"`, una transacción que cruza shards sí obtiene una vista consistente entre ellos, no solo atomicidad, mucho más parecido a lo que hacen Spanner o CockroachDB que a lo que hace Citus, aunque paga el mismo costo de sesión que ya vimos en su propia fila. **Redis Cluster va por un tercer camino, todavía más estricto**: ni siquiera intenta dar una transacción débil entre shards, la rechaza de inmediato. Si `MULTI`/`EXEC` toca claves en distintos shards (technicamente, distintos hash slots), Redis aborta la transacción completa con el error `CROSSSLOT`, antes de ejecutar nada. La única forma de tener una transacción multi-clave real en Redis Cluster es forzar, con un hash tag, que esas claves compartan el mismo slot, por ejemplo `{cliente:123}:saldo` y `{cliente:123}:historial`, para que Redis las trate como si vivieran en un solo shard. Es la opción más restrictiva de las tres, pero también la más honesta: nunca le da una garantía más débil sin avisarle.

### Sección 5.7: Un puente de vocabulario con el resto de la industria {#5-vocabulario}

Todo lo que esta sección construyó corresponde a una distinción real, con respaldo citable, no solo una forma de hablar. Kleppmann, en _Designing Data-Intensive Applications_, distingue explícitamente la serializabilidad, una propiedad de aislamiento sobre transacciones que pueden tocar varias filas, de la **linearizability** (Herlihy y Wing, "Linearizability: A Correctness Condition for Concurrent Objects", ACM Transactions on Programming Languages and Systems, 12(3), 463-492, 1990), una propiedad de consistencia sobre un solo dato. Lo que llamamos Pregunta 2 y Pregunta 3, MVCC, 2PL, OCC, SSI, es lo que esa distinción llama **isolation levels**. Lo que llamamos Quorum y CAS distribuido es lo que llama **consistency models**, con linearizability como su estándar más alto, y en esta tabla solo lo alcanza el camino de CAS vía consenso (LWT en Cassandra, Paxos, Raft), no un `QUORUM` simple sin eso. Esta distinción, aislamiento para una copia, consistencia para varias, es exactamente la misma que ya separamos en dos ejes desde el inicio de esta sección, solo que ahora con el nombre y la fuente que va a encontrar en cualquier conversación técnica seria sobre el tema.

### Sección 5.8: Resumen rápido de alcance {#5-resumen-alcance}

Para no tener que releer toda la tabla. Cinco motores cubren filas distintas y protegen write skew de forma automática, pero llegan ahí por dos caminos distintos.

- **Bloqueando físicamente.** SQL Server, con su propio SERIALIZABLE basado en locks. SQLite, trivialmente, por ser un solo escritor. Google Spanner, que mantiene los locks de lectura activos hasta el COMMIT.
- **Sin bloquear, detectando el conflicto y abortando al confirmar.** PostgreSQL en SERIALIZABLE, con Serializable Snapshot Isolation (SSI), y CockroachDB, con su propia versión distribuida del mismo mecanismo. Esta vía no hace esperar a nadie, pero no es gratis. En PostgreSQL, el propio paper que implementó esta técnica (Ports y Grittner, "Serializable Snapshot Isolation in PostgreSQL", VLDB 2012) mide el overhead real de CPU por el rastreo de cada lectura: 10-20% en SIBENCH, un microbenchmark construido específicamente para medir el costo de la snapshot isolation serializable, y solo 5% en el benchmark estándar de procesamiento transaccional de la Transaction Processing Performance Council, conocido como TPC-C, con las optimizaciones de solo lectura activas, además de una tasa de abortos más alta que REPEATABLE READ por el mismo rastreo de dependencias.

El resto de los motores de la tabla, sin protección automática de write skew, lo resuelve a mano, con `FOR UPDATE` sobre el conjunto completo o el equivalente de cada uno.

### Sección 5.9: El patrón detrás de la tabla {#5-patron-tabla}

En arquitecturas de bloqueo puro (SQL Server sin RCSI, SQLite rollback journal), el mismo mecanismo responde la Pregunta 2 y la Pregunta 3, un solo lock hace ambos trabajos. En arquitecturas MVCC (PostgreSQL, MySQL, Oracle, MongoDB, entre otros), son dos mecanismos separados: el snapshot resuelve la lectura, y algo completamente distinto (first-committer-wins, SSI, o un lock explícito) resuelve la escritura. En MVCC, leer nunca compite con nada; en bloqueo puro, leer y escribir compiten por el mismo recurso.

### Sección 5.10: Una precisión más sobre la Pregunta 2 {#5-precision-pregunta-2}

El identificador de la columna de lectura describe una lectura normal, sin lock. Pero MySQL, Oracle y PostgreSQL también permiten una lectura con lock (`FOR UPDATE`, `FOR SHARE`), y ahí el comportamiento se bifurca en dos grupos, no es el mismo en los tres. En MySQL InnoDB, y en Oracle bajo READ COMMITTED, una lectura con lock deja de usar el snapshot: si la fila que buscas fue modificada y confirmada por otra transacción mientras esperabas el lock, la lectura espera a que esa transacción termine y entonces te devuelve la versión más reciente ya confirmada, no la del inicio de la tuya, en silencio, sin ningún error. PostgreSQL en REPEATABLE READ o SERIALIZABLE, y Oracle en su propio SERIALIZABLE, hacen lo opuesto: si la fila cambió mientras esperabas, abortan la transacción con un error de serialización en vez de darte una versión que tu snapshot original no debería poder ver. SQL Server con snapshot isolation se comporta igual, aborta con el error de conflicto de actualización (3960). La diferencia importa en la práctica: en el primer grupo, una lectura con lock puede mostrarte, sin avisarte, datos más nuevos que los de tu snapshot; en el segundo grupo, eso nunca pasa, el motor prefiere fallar de forma visible antes que romper la garantía del snapshot.

Ninguna arquitectura es gratis: cada una cambia bloqueo por espacio, latencia por simplicidad, o reintentos por concurrencia. Elegir una no es una decisión técnica aislada, es aceptar conscientemente cuál de esos costos conviene pagar para el patrón de acceso de la aplicación.

---

## Bloque 6: Granularidad de locks e intent locks {#6-locks-granularity}

Esta sección profundiza en el vocabulario de locks que ya viste en Bloque 2, pero mirando más de cerca tres preguntas distintas, cada una con su propio alcance.

La primera es universal en los cuatro motores relacionales de esta guía (PostgreSQL, MySQL InnoDB, SQL Server, Oracle), aunque cada uno le pone un nombre distinto. Cubre a qué nivel se aplica un lock, fila, tabla o rango, y cómo el motor sabe si un DDL como `ALTER TABLE` puede proceder sin tener que escanear cada fila una por una, eso son los intent locks, presentes en los cuatro motores con nombres propios (`RowExclusiveLock` en PostgreSQL, IS/IX en MySQL, TM locks en Oracle).

La segunda es específica de un solo motor. Lock escalation, el mecanismo que convierte muchos row locks en un solo lock de tabla, solo existe en SQL Server. PostgreSQL y MySQL InnoDB nunca escalan así, sin importar cuántas filas toque una transacción.

La tercera vuelve a ser universal, aunque la sintaxis y el comportamiento cambian por motor. Cubre las variantes de `FOR UPDATE` (`NOWAIT`, `SKIP LOCKED`), qué pasa cuando dos transacciones caen en deadlock y quién lo pierde, y cómo diagnosticar en producción qué transacción está bloqueando a cuál. Cierra con los advisory locks, una herramienta que nació en PostgreSQL pero que los otros tres motores resuelven con su propio mecanismo equivalente.

### Sección 6.1: Jerarquía de granularidad {#6-jerarquia}

Los locks se aplican en distintos niveles porque distintas operaciones afectan distintos alcances.

**Una aclaración antes de ver el diagrama, sobre cómo leerlo:** las flechas de anidamiento muestran qué contiene a qué, no que un lock en un nivel bloquee automáticamente todo lo de abajo en cualquier objeto de ese nivel. Un lock siempre es sobre un objeto concreto, y solo bloquea lo que está contenido dentro de ese objeto específico. `ALTER TABLE pedidos` bloquea la tabla `pedidos` y, con ella, todas sus filas, pero no toca la tabla `clientes` aunque viva en el mismo schema. `CREATE TABLE` bloquea el catálogo del schema para evitar colisiones de nombre, pero no bloquea ninguna tabla que ya exista ahí. La única excepción real es el nivel de BASE DE DATOS, ahí no hay nada más específico que targetear, así que ese lock sí cubre la instancia completa.

```
BASE DE DATOS
  Operaciones: backup completo, shutdown, actualizaciones del motor.
  Rarísimo en operación normal. Estas operaciones afectan a todos los
  schemas y tablas a la vez, así que el lock, cuando existe, cubre la
  instancia completa, no tiene sentido a ningún nivel más fino.

  └── SCHEMA
        Operaciones: CREATE TABLE, ALTER SCHEMA.
        Estas operaciones modifican el catálogo del schema, qué tablas
        existen dentro de él, no el contenido de una tabla ya creada.
        Por eso el lock es sobre el catálogo, no sobre ninguna tabla en
        particular. DROP TABLE no vive aquí, porque borra una tabla
        específica, así que su lock es a nivel TABLA, un nivel más abajo.

      └── TABLA
            Operaciones: ALTER TABLE, TRUNCATE, DROP TABLE.
            Estas operaciones cambian la definición o la existencia de la
            tabla, no el contenido de filas puntuales, así que un row lock
            no protege lo que realmente está en juego. Y como afectan a
            todas las filas por igual, adquirir un lock por cada una sería
            tan costoso como bloquear la tabla completa, sin ninguna
            ventaja real.

          └── PÁGINA (8-16 KB, ~8-30 filas)
                SQL Server puede escalar a page lock cuando acumula row locks.
                Es un punto intermedio antes de llegar a un lock de tabla
                completo, útil cuando una transacción toca muchas filas
                concentradas en pocas páginas, pero no toda la tabla.
                PostgreSQL y MySQL InnoDB no usan page locks en DML.

              └── FILA (row lock)
                    Estándar para INSERT / UPDATE / DELETE.
                    T1 modifica fila 1, T2 modifica fila 2, no hay bloqueo entre ellas.

                  └── RANGO (gap lock)
                        Un row lock protege una fila que ya existe, pero no puede
                        proteger el espacio vacío entre dos filas, ahí no hay nada
                        que lockear. El gap lock resuelve justo eso, bloquea el
                        hueco entre filas existentes para que nadie inserte una
                        fila nueva ahí y produzca un phantom read.
                        Usado por MySQL InnoDB RR.
                        PostgreSQL no usa range locks; SSI detecta el conflicto (Bloque 7).
                        SQL Server tiene su propio key-range lock, pero solo en
                        SERIALIZABLE, no en REPEATABLE READ.
                        Oracle no bloquea rangos en ningún nivel; su SERIALIZABLE
                        es snapshot-based y evita phantoms sin lockear nada (Bloque 7).
```

**Por qué cada motor eligió su estrategia:**

| Motor                 | Estrategia central                                                                      | Consecuencia práctica                                                                                                                                                                                                                                                                                     |
| --------------------- | --------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| PostgreSQL            | MVCC para lectores, row-level locking para escritores, sin lock escalation              | Nunca convierte muchos row locks en uno de tabla, sin importar cuántas filas toque una transacción. Sostener millones de locks individuales cuesta más memoria que uno solo, pero evita que una transacción que modifica media tabla bloquee a otra que solo quiere tocar una fila completamente distinta |
| MySQL InnoDB          | MVCC para lectores normales, más gap locks en REPEATABLE READ para lecturas bloqueantes | Previene phantom reads, al precio de más deadlocks                                                                                                                                                                                                                                                        |
| SQL Server (sin RCSI) | S locks en lecturas, X locks en escrituras, lock escalation automática                  | Con RCSI activado, los lectores usan versiones en vez de S locks                                                                                                                                                                                                                                          |
| Oracle                | MVCC desde los años 1980                                                                | Los lectores nunca bloquean a los escritores                                                                                                                                                                                                                                                              |

### Sección 6.2: Tipos de locks {#6-tipos-locks}

S y X ya se explicaron en detalle en "Los locks: los dos tipos base y su relación con MVCC", con su propia matriz de compatibilidad. Lo único que agrega esta sección es el tercer tipo, el Update lock, exclusivo de SQL Server, y qué cambia en esa matriz cuando entra en juego.

```
Update lock (U):  [exclusivo de SQL Server, no existe en PostgreSQL/MySQL/Oracle]
  Lo adquiere: SQL Server cuando detecta el patrón "leer para luego actualizar".
  Se interpreta como: "voy a leer ahora y escalar a X si confirmo el update".
  Compatible con S (permite que otros lean mientras decide si escribe).
  Incompatible con U y X (solo uno puede estar "decidiendo" a la vez).

  Por qué existe: sin U lock, el deadlock clásico en locking tradicional es:
    T1 y T2 leen la misma fila → ambas obtienen S lock (compatibles)
    T1 intenta escalar a X → espera a que T2 libere su S
    T2 intenta escalar a X → espera a que T1 libere su S
    → ciclo → deadlock
  Con U lock: solo una txn puede tener U sobre la fila al mismo tiempo.
  La segunda espera desde el principio, sin llegar al ciclo.
```

**Con U agregado a la matriz que ya conocías** (S y X se comportan igual que antes, U es la fila y columna nueva):

| T2 pide \ T1 tiene | S      | X      | U      |
| ------------------ | ------ | ------ | ------ |
| S                  | pasa   | espera | pasa   |
| X                  | espera | espera | espera |
| U                  | pasa   | espera | espera |

### Sección 6.3: Intent locks: el protocolo de jerarquía {#6-intent-locks}

**El problema:** `ALTER TABLE pedidos ADD COLUMN` necesita lock exclusivo sobre toda la tabla. ¿Cómo sabe si hay row locks activos sin escanear cada fila?

```
Sin intent locks: ALTER TABLE → escanear todas las filas → O(n filas)
Con intent locks: ALTER TABLE → verificar IX lock en la tabla → O(1)
```

Antes de adquirir un row lock, la txn registra un intent lock en la tabla:

```
T1 quiere UPDATE fila 42 de pedidos:
  1. T1 pide IX lock en la tabla ("voy a escribir en alguna fila")
  2. IX concedido
  3. T1 pide X lock en fila 42 → concedido → UPDATE

T2 quiere ALTER TABLE pedidos:
  1. T2 pide X lock en la tabla
  2. Ve IX de T1 → colisión → T2 espera
  Sin escanear ninguna fila.

Tipos:
  IS  (Intent Shared):    "voy a leer algunas filas con S lock"
  IX  (Intent Exclusive): "voy a modificar algunas filas con X lock"
  SIX (Shared + Intent Exclusive): raro; escanea tabla y modifica partes

Compatibilidad (fila = lo que pide T2, columna = lo que ya tiene T1):
       IS    IX    S    SIX    X
  IS [ OK ] [OK ] [OK ] [OK ] [NO]
  IX [ OK ] [OK ] [NO ] [NO ] [NO]
  S  [ OK ] [NO ] [OK ] [NO ] [NO]
  SIX[ OK ] [NO ] [NO ] [NO ] [NO]
  X  [ NO ] [NO ] [NO ] [NO ] [NO]

PostgreSQL: RowShareLock (≈IS) y RowExclusiveLock (≈IX).
Ver con: SELECT * FROM pg_locks WHERE locktype = 'relation';
```

MySQL InnoDB documenta el mismo mecanismo con los mismos nombres, IS e IX a nivel de tabla, visibles en `performance_schema.data_locks` con `lock_type = 'TABLE'`.

Oracle no usa el término "intent lock", pero resuelve el mismo problema con TM locks (locks de tabla) en modo Row Share (RS) y Row Exclusive (RX). Cualquier fila que se modifica adquiere primero un TM lock RX sobre la tabla, y un DDL que pida el lock X de tabla ve ese RX y espera sin escanear filas, el mismo atajo que un intent lock. Ver con: `SELECT * FROM v$lock WHERE type = 'TM';`

### Sección 6.4: Lock escalation (SQL Server) {#6-lock-escalation}

```
T1 modifica 6000 filas en la tabla Pedidos:
  → SQL Server acumula 6000 row locks
  → Supera umbral (~5000) → escala a 1 table lock
  → T2 quiere modificar una fila distinta → bloqueada por el table lock

Cómo mitigarlo:
  ALTER TABLE pedidos SET (LOCK_ESCALATION = DISABLE);
  Reducir el batch size de la operación.
  PostgreSQL y MySQL InnoDB no hacen lock escalation automática.
```

### Sección 6.5: SELECT FOR UPDATE: variantes {#6-for-update-variantes}

```sql
FOR UPDATE             -- X lock; T2 espera si la fila está bloqueada
FOR UPDATE NOWAIT      -- falla inmediatamente si la fila está bloqueada
FOR UPDATE SKIP LOCKED -- salta filas bloqueadas sin esperar
FOR SHARE              -- S lock; otros pueden leer con FOR SHARE pero no modificar
```

`NOWAIT` es útil cuando la semántica del negocio es "si no puedo procesar ahora, no proceso": en lugar de bloquear la transacción y potencialmente saturar el pool de conexiones, el error llega inmediatamente y la capa de aplicación decide qué hacer, intentarlo más tarde, notificar al usuario, o tomar una ruta alternativa.

`SKIP LOCKED` tiene una semántica diferente. No es "falla si está bloqueado" sino "ignora las filas bloqueadas y dame las próximas disponibles". Esto lo hace el mecanismo natural para colas de trabajo con múltiples consumidores concurrentes.

Estas cuatro variantes no tienen la misma sintaxis en los cuatro motores:

| Motor        | Falla si está bloqueada                         | Salta las bloqueadas                             |
| ------------ | ----------------------------------------------- | ------------------------------------------------ |
| PostgreSQL   | `FOR UPDATE NOWAIT`                             | `FOR UPDATE SKIP LOCKED` (9.5+)                  |
| MySQL InnoDB | `FOR UPDATE NOWAIT`                             | `FOR UPDATE SKIP LOCKED` (8.0+)                  |
| Oracle       | `FOR UPDATE NOWAIT`                             | `FOR UPDATE SKIP LOCKED` (documentado desde 11g) |
| SQL Server   | `WITH (UPDLOCK, NOWAIT)` o `SET LOCK_TIMEOUT 0` | `WITH (READPAST)`                                |

SQL Server no tiene una cláusula llamada `SKIP LOCKED`. El hint `READPAST` se le acerca en intención, ignora filas bloqueadas en lugar de esperarlas, pero se combina con `UPDLOCK` y `ROWLOCK` para reproducir el comportamiento completo que en los otros tres motores da `FOR UPDATE SKIP LOCKED` en una sola cláusula.

#### SKIP LOCKED: colas de trabajo concurrentes

Sin SKIP LOCKED, si varios workers intentan procesar la misma cola, todos se bloquean en la primera tarea disponible: Worker B espera a que Worker A termine con la tarea 1 antes de poder tomar la tarea 2. El throughput de la cola queda limitado a un solo worker a la vez aunque haya varios disponibles. SKIP LOCKED resuelve esto: cada worker obtiene directamente una tarea diferente sin esperar a los demás.

```sql
-- Tabla de tareas pendientes
CREATE TABLE tareas (
  id      SERIAL PRIMARY KEY,
  estado  VARCHAR(20) DEFAULT 'pendiente',
  payload JSONB,
  creado  TIMESTAMPTZ DEFAULT NOW()
);

-- Cada worker ejecuta esto dentro de su propia transacción:
BEGIN;

SELECT id, payload
FROM tareas
WHERE estado = 'pendiente'
ORDER BY creado          -- FIFO: la más antigua primero
LIMIT 1
FOR UPDATE SKIP LOCKED;  -- salta las que otro worker ya tiene bloqueadas

-- Si devuelve una fila: procesarla
UPDATE tareas SET estado = 'procesando' WHERE id = :id_obtenido;

COMMIT;
-- El worker procesa el payload fuera de la transacción y luego actualiza a 'completada'.
```

```
Worker A             Worker B             Worker C
─────────────────────────────────────────────────────────────
BEGIN;               BEGIN;               BEGIN;
SELECT ... SKIP LOCKED
→ obtiene tarea 1    SELECT ... SKIP LOCKED
→ X lock sobre id=1  → obtiene tarea 2    SELECT ... SKIP LOCKED
                     → X lock sobre id=2  → obtiene tarea 3
                                          → X lock sobre id=3

Los tres workers progresan en paralelo.
Sin SKIP LOCKED: B y C esperarían a que A libere tarea 1.
```

**Cuándo no usar SKIP LOCKED:** si el orden de procesamiento es un requisito estricto (la tarea 2 no puede iniciarse antes que la tarea 1 termine), SKIP LOCKED no es correcto — saltará tareas temporalmente bloqueadas y dejará huecos en el orden. Para ese caso, un solo worker con FOR UPDATE sin SKIP LOCKED mantiene el orden FIFO estricto.

### Sección 6.6: Deadlocks {#6-deadlocks}

```
T1 tiene X lock sobre fila A
T2 tiene X lock sobre fila B
T1 quiere fila B → espera a T2
T2 quiere fila A → espera a T1
→ ciclo → deadlock
```

El motor aborta una de las dos transacciones para romper el ciclo, pero el criterio para elegir cuál varía por motor, no es universal. SQL Server usa primero el `DEADLOCK_PRIORITY` de sesión, y si hay empate, el costo estimado de deshacer cada transacción. MySQL InnoDB elige la que ha hecho menos trabajo, medido en filas modificadas. Oracle aborta la transacción que detectó el ciclo, no necesariamente la más barata de deshacer. PostgreSQL no garantiza cuál de las dos se aborta. En los cuatro casos el backend recibe un error y debe reintentar.

**Prevención:** adquirir locks siempre en el mismo orden global.

```sql
SELECT id, saldo FROM cuentas WHERE id IN (1, 2) ORDER BY id FOR UPDATE;
```

### Sección 6.7: Ver locks activos en producción {#6-ver-locks}

Los conceptos de lock bloqueante, deadlock y espera no son solo teóricos — cuando una query no termina o la aplicación se vuelve lenta, son exactamente estos mecanismos los que están en juego. Las vistas del sistema permiten ver el estado de los locks en el momento de la consulta e identificar qué proceso está bloqueando a cuál.

```sql
-- PostgreSQL: ver qué query está esperando y qué query la bloquea
SELECT
  blocked_act.pid                  AS pid_bloqueado,
  blocked_act.query                AS query_bloqueada,
  blocking_act.pid                 AS pid_bloqueante,
  blocking_act.query               AS query_bloqueante,
  blocking_act.state               AS estado_bloqueante
FROM pg_locks          blocked
JOIN pg_stat_activity  blocked_act  ON blocked_act.pid  = blocked.pid
JOIN pg_locks          blocking     ON  blocking.transactionid = blocked.transactionid
                                    AND blocking.pid != blocked.pid
JOIN pg_stat_activity  blocking_act ON blocking_act.pid = blocking.pid
WHERE NOT blocked.granted;

-- MySQL: transacciones en espera (requiere schema sys, disponible desde 5.7)
SELECT * FROM sys.innodb_lock_waits\G

-- Deadlocks recientes en MySQL (busca la sección "LATEST DETECTED DEADLOCK"):
SHOW ENGINE INNODB STATUS\G

-- Activar log de todos los deadlocks en MySQL, no solo el último:
SET GLOBAL innodb_print_all_deadlocks = ON;

-- SQL Server: qué sesión está bloqueada y por cuál
SELECT
  req.session_id                        AS sesion_bloqueada,
  req.blocking_session_id               AS sesion_bloqueante,
  SUBSTRING(sqlt.text, 1, 200)         AS query_bloqueada,
  SUBSTRING(sqlt_b.text, 1, 200)       AS query_bloqueante,
  req.wait_type,
  req.wait_time / 1000.0               AS segundos_esperando
FROM sys.dm_exec_requests req
CROSS APPLY sys.dm_exec_sql_text(req.sql_handle)              sqlt
JOIN  sys.dm_exec_sessions ses_b ON ses_b.session_id = req.blocking_session_id
CROSS APPLY sys.dm_exec_sql_text(ses_b.most_recent_sql_handle) sqlt_b
WHERE req.blocking_session_id != 0;

-- Oracle: sesión bloqueada, quién la bloquea, y su SQL (blocking_session desde 11g)
SELECT
  s.sid                AS sid_bloqueada,
  s.blocking_session    AS sid_bloqueante,
  s.event                AS esperando,
  sq.sql_text            AS query_bloqueada
FROM v$session s
JOIN v$sql sq ON sq.sql_id = s.sql_id
WHERE s.blocking_session IS NOT NULL;
```

Estas consultas responden la pregunta de diagnóstico más común en producción: "¿por qué esta query lleva 3 minutos sin terminar?" La respuesta casi siempre es que hay otra transacción que tiene un lock sobre una fila que la primera necesita, y esa otra transacción está haciendo algo — procesando datos, esperando a su propio cliente, o simplemente colgada.

### Sección 6.8: Advisory locks en PostgreSQL {#6-advisory-locks}

Los locks de fila y tabla protegen datos concretos. Pero hay casos donde la exclusión que necesitas no corresponde a ninguna fila: "solo un proceso puede ejecutar el cierre contable mensual", "solo una instancia puede hacer esta migración", "solo un worker puede importar este archivo". Crear una fila ficticia solo para lockeárla funciona, pero es artificioso.

PostgreSQL ofrece **advisory locks**: locks identificados por un entero que tú defines, sin relación con ninguna tabla. El motor los gestiona con las mismas garantías que cualquier otro lock (bloqueo, espera, detección de deadlock, liberación en crash), pero son herramienta de coordinación entre procesos de la aplicación.

```sql
-- Lock de sesión: persiste hasta pg_advisory_unlock() o fin de conexión
-- Útil cuando el proceso necesita mantener el lock entre transacciones
SELECT pg_advisory_lock(12345);
-- ... trabajo exclusivo ...
SELECT pg_advisory_unlock(12345);

-- Lock de transacción: se libera al COMMIT o ROLLBACK, igual que un row lock
BEGIN;
SELECT pg_advisory_xact_lock(12345);
-- ... trabajo exclusivo dentro de la txn ...
COMMIT;  -- lock liberado automáticamente

-- No-wait: útil para "si ya hay alguien corriendo esto, yo no proceso"
SELECT pg_try_advisory_lock(12345);  -- devuelve true (obtuvo el lock) o false

-- Patrón: proceso singleton (solo una instancia debe correr a la vez)
DO $$
BEGIN
  IF pg_try_advisory_lock(20250101) THEN
    PERFORM ejecutar_cierre_mensual();
    PERFORM pg_advisory_unlock(20250101);
  ELSE
    RAISE NOTICE 'Cierre ya en ejecución en otro proceso. Saliendo.';
  END IF;
END;
$$;
```

El entero es el "nombre" del lock. La convención es usar constantes definidas en el código de la aplicación o derivadas de un hash del nombre del proceso, para evitar colisiones accidentales entre locks distintos.

El mismo patrón existe en los otros tres motores con nombres distintos. MySQL ofrece `GET_LOCK(nombre, timeout)`, `RELEASE_LOCK(nombre)` e `IS_FREE_LOCK(nombre)`, con el mismo propósito de coordinación entre procesos de aplicación, aunque el alcance siempre es de sesión, MySQL no tiene una variante ligada a la transacción como `pg_advisory_xact_lock`. SQL Server ofrece `sp_getapplock` / `sp_releaseapplock`, con un parámetro `@LockOwner` que sí permite elegir entre alcance de sesión o de transacción, igual que en PostgreSQL. Oracle resuelve lo mismo con el paquete `DBMS_LOCK`, de uso mucho menos frecuente en código de aplicación moderno que sus equivalentes en los otros tres motores.

---

## Bloque 7: Mecanismos avanzados: gap locks en MySQL y SSI en PostgreSQL {#7-avanzado}

Esta sección profundiza en dos mecanismos que hasta ahora solo se mencionaron de pasada. El primero es cómo MySQL InnoDB bloquea el espacio entre filas, no solo las filas mismas, para prevenir phantom reads en REPEATABLE READ, y por qué eso puede producir deadlocks que no existirían en otro motor. El segundo es cómo PostgreSQL detecta write skew en SERIALIZABLE sin bloquear ninguna lectura, rastreando dependencias en vez de tomar locks. Son los dos casos donde el mismo nivel de aislamiento se comporta más distinto entre motores, así que el mismo patrón de código puede ser seguro en uno y fallar en otro. Cierra con el contrato de retry que ambos mecanismos, más los deadlocks y el first-committer-wins, le exigen al backend, con código funcional en Python, Java y TypeScript para los cuatro motores.

### Sección 7.1: Gap locks en MySQL InnoDB (next-key locks) {#7-gap-locks}

En MySQL InnoDB con REPEATABLE READ, además de bloquear la fila leída en una lectura bloqueante, el motor puede bloquear también el **gap** (espacio en el índice) alrededor de esa fila, para impedir que alguien inserte una fila nueva justo ahí.

```
next-key lock = record lock (fila existente) + gap lock (espacio previo)

Tabla: id = 10, 20, 30, 50  (id es la clave primaria, índice único)
Gaps:  (-∞,10] (10,20] (20,30] (30,50] (50,+∞)

T1: SELECT * FROM tabla WHERE id = 25 FOR UPDATE
→ id=25 no existe → ningún record lock que poner
→ gap lock sobre (20,30): nadie puede insertar ningún id entre 21 y 29

T1: SELECT * FROM tabla WHERE id = 20 FOR UPDATE
→ id=20 sí existe, y es una igualdad sobre un índice único
→ InnoDB optimiza a solo un record lock sobre id=20, sin gap lock
→ a lo sumo una fila puede coincidir, así que bloquear el hueco no protege nada extra
```

El next-key lock completo (record lock + gap lock) aparece cuando la igualdad es sobre un índice que no es único, porque ahí sí podría haber más de una fila coincidiendo, o en búsquedas por rango, donde el motor recorre y bloquea varios gaps consecutivos.

**Por qué existe:** sin gap locks, T2 podría insertar una fila nueva dentro del rango que T1 leyó, produciendo un phantom read. InnoDB previene esto en REPEATABLE READ sin requerir SERIALIZABLE.

**El costo: deadlocks con INSERT**

```sql
-- Solo existen id=1 e id=3

-- T1:
BEGIN;
SELECT * FROM pedidos WHERE id = 2 FOR UPDATE;
-- gap lock en (1,3)

-- T2 (concurrente):
BEGIN;
SELECT * FROM pedidos WHERE id = 2 FOR UPDATE;
-- gap lock en (1,3) ← gap locks son compatibles entre sí

-- T1: INSERT INTO pedidos(id) VALUES (2);
-- → bloqueado por gap lock de T2 → espera

-- T2: INSERT INTO pedidos(id) VALUES (2);
-- → bloqueado por gap lock de T1 → espera

-- DEADLOCK
```

Patrón frecuente en código "insertar si no existe": el SELECT del "¿ya existe?" deja un gap lock; el INSERT posterior choca con el gap lock de la otra transacción.

**Diferencia con PostgreSQL:**

```
PostgreSQL RR: snapshot puro, sin gap locks.
  Phantom reads posibles en RR. Menor riesgo de deadlocks por INSERT concurrente.

MySQL InnoDB RR: gap locks en lecturas bloqueantes.
  Previene phantoms en lecturas bloqueantes.
  Mayor riesgo de deadlocks en INSERT concurrente.

Consecuencia: código sin deadlocks en PostgreSQL puede producir deadlocks
frecuentes al migrarlo a MySQL InnoDB con los mismos patrones.
```

**Soluciones en MySQL:**

```sql
-- Opción 1: READ COMMITTED (desactiva gap locks)
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- Opción 2: upsert atómico (evita el patrón select-then-insert)
INSERT INTO pedidos(id, estado) VALUES (2, 'nuevo')
ON DUPLICATE KEY UPDATE estado = VALUES(estado);

-- Opción 3: retry en el backend cuando ocurre deadlock
```

**Impacto en la elección del motor:** si tu carga tiene INSERT concurrentes frecuentes en las mismas tablas (colas, logs), MySQL InnoDB en RR generará más deadlocks que PostgreSQL. Considera READ COMMITTED en MySQL o migra a PostgreSQL si el workload es INSERT-heavy.

**Dónde encajan SQL Server y Oracle en esta comparación:**

SQL Server tiene un mecanismo equivalente, los key-range locks, pero solo aparecen en SERIALIZABLE, no en su REPEATABLE READ. Sin RCSI, el REPEATABLE READ de SQL Server retiene S locks sobre las filas leídas hasta el COMMIT, pero no bloquea el espacio entre ellas, así que permite phantom reads igual que PostgreSQL en RR. Subir a SERIALIZABLE en SQL Server trae el mismo riesgo de deadlocks por rango que los gap locks de MySQL. Oracle no bloquea gaps en ningún nivel, su SERIALIZABLE es snapshot-based (ver Bloque 2), así que evita phantoms leyendo desde una foto fija del tiempo en vez de bloquear el espacio entre filas.

---

### Sección 7.2: Snapshot Isolation (SI) vs Serializable Snapshot Isolation (SSI) {#7-si-vs-ssi}

**Snapshot Isolation** es lo que la mayoría de motores implementan para REPEATABLE READ:

```
Al inicio de la txn: snapshot del estado actual.
Todas las lecturas usan ese snapshot.
Las escrituras van sobre la versión más reciente.
Dos txns modificando la misma fila: first-committer-wins;
  la segunda recibe error de serialización.

SI previene: dirty reads, non-repeatable reads, lost updates (misma fila).
SI no previene: write skew.
```

**Por qué SI no previene write skew:** SI detecta write-write conflicts (dos txns modificando la **misma fila**). Write skew es distinto: T1 y T2 modifican filas **distintas**. Sin write-write conflict. El problema es que sus decisiones se basaron en una lectura compartida del estado global.

```
Cuenta conjunta: A=$500, B=$500. Regla: total >= 0.

T1 lee total=$1000 → retira $800 de A → escribe A=-$300
T2 lee total=$1000 → retira $800 de B → escribe B=-$300

SI: T1 y T2 modificaron filas distintas → sin conflicto → ambas pasan.
    Total: -$600. Regla violada.

SSI: detecta que T1 leyó el total (incluye B, que T2 modificó).
     Ciclo de dependencias rw detectado → aborta una de las dos.
```

**SSI en PostgreSQL SERIALIZABLE:**

```
2PL (locking tradicional):
  Antes de leer: S lock. Antes de escribir: escalar a X lock.
  Alta correctitud, menor concurrencia, deadlocks frecuentes.

SSI (PostgreSQL SERIALIZABLE):
  Lectura y escritura con MVCC (sin locks en lecturas).
  Rastrea dependencias rw entre transacciones concurrentes.
  Detecta ciclos de dependencias → aborta una txn del ciclo.
  Mayor concurrencia que 2PL; puede requerir retries.
```

Ningún otro motor de los que cubre esta guía implementa SSI. MySQL y SQL Server llegan a SERIALIZABLE por el camino del 2PL tradicional, MySQL convirtiendo cada SELECT en un FOR SHARE implícito, SQL Server sumando key-range locks a sus S locks. Oracle no bloquea ni rastrea dependencias, su SERIALIZABLE es Snapshot Isolation puro, la misma categoría que el REPEATABLE READ de PostgreSQL, así que hereda la misma limitación de SI descrita arriba: previene lost update pero no write skew (ver la nota de Oracle en Bloque 2).

**Dependencias que rastrea SSI:**

```
rw (read-write): T1 leyó datos que T2 luego modificó.
wr (write-read): T1 modificó datos que T2 luego leyó.
ww (write-write): ambas modificaron la misma fila.

Un ciclo indica que no existe orden serial posible:
  T1 ──rw──► T2   (T1 leyó algo que T2 cambió)
  T2 ──rw──► T1   (T2 leyó algo que T1 cambió)
  → una de las dos se aborta
```

**Ejemplo — write skew detectado por SSI:**

```
T1: BEGIN SERIALIZABLE
    SELECT COUNT(*) FROM guardias  ← crea rw-dep con T2
    UPDATE doctor_A = false

T2: BEGIN SERIALIZABLE
    SELECT COUNT(*) FROM guardias  ← crea rw-dep con T1
    UPDATE doctor_B = false

SSI: T1 ──rw──► T2, T2 ──rw──► T1  → ciclo detectado

Una de las dos recibe:
  ERROR: could not serialize access due to read/write dependencies
  HINT: The transaction might succeed if retried.
```

### Sección 7.3: Retry: el contrato que exigen SERIALIZABLE, los deadlocks y el first-committer-wins {#7-retry}

Los errores de serialización y los deadlocks no son bugs — son el motor cumpliendo su contrato: "detecté un conflicto irresolvable; reintenta desde el principio". El motor garantiza que la transacción abortada no dejó ningún cambio a medias. El backend debe tratar estos errores como transitorios y reintentar, igual que reintentaría un timeout de red.

Los errores que requieren retry son los mismos independientemente de cuál anomalía los causó:

```
SQLSTATE 40001 — serialization_failure
  PostgreSQL: "could not serialize access due to read/write dependencies"  ← SSI
              "could not serialize access due to concurrent update"         ← RR first-committer-wins
  MySQL:      "Deadlock found when trying to get lock"  (MySQL usa 40001 para deadlocks)

SQLSTATE 40P01 — deadlock_detected  (PostgreSQL específico)
  PostgreSQL: "deadlock detected"

SQL Server no expone SQLSTATE de forma nativa para esto, usa sus propios
números de mensaje:
  Msg 1205: deadlock detectado, la transacción de menor prioridad o costo
            se aborta (ver la sección de Deadlocks en Bloque 6)
  Msg 1222: lock request timeout period exceeded

Oracle no usa SQLSTATE en su driver nativo, usa sus propios códigos ORA:
  ORA-08177: can't serialize access for this transaction  ← equivalente a 40001
  ORA-00060: deadlock detected while waiting for resource  ← equivalente a 40P01
```

```python
# Python (psycopg2) — retry para serialization failure, deadlock y lock timeout
import time
import psycopg2.errors as pg_errors

MAX_RETRIES = 3

def con_retry(conn, operacion):
    for intento in range(1, MAX_RETRIES + 1):
        try:
            with conn.transaction():
                return operacion(conn)
        except (pg_errors.SerializationFailure,   # SQLSTATE 40001
                pg_errors.DeadlockDetected,        # SQLSTATE 40P01
                pg_errors.LockNotAvailable) as e:  # SQLSTATE 55P03, lock_timeout agotado
            if intento == MAX_RETRIES:
                raise
            time.sleep(0.05 * intento)  # backoff: 50ms, 100ms, 150ms
```

```java
// Java (Spring) — @Retryable para serialization failures, deadlocks y lock timeouts
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.dao.CannotSerializeTransactionException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.CannotAcquireLockException;

// Spring traduce SQLSTATE 40001 → CannotSerializeTransactionException
// Spring traduce SQLSTATE 40P01 → DeadlockLoserDataAccessException
// Spring traduce SQLSTATE 55P03 (lock timeout agotado) → CannotAcquireLockException
@Retryable(
    value = { CannotSerializeTransactionException.class,
              DeadlockLoserDataAccessException.class,
              CannotAcquireLockException.class },
    maxAttempts = 3,
    backoff = @Backoff(delay = 50, multiplier = 2)
)
@Transactional(isolation = Isolation.SERIALIZABLE)
public void operacionCritica(Long id, BigDecimal monto) {
    // Si falla con serialization/deadlock/lock timeout, @Retryable reintenta automáticamente.
}
```

Spring traduce los mismos síntomas contra Oracle, pero repartidos en las tres mismas clases, no todas en una: ORA-08177 se mapea a `CannotSerializeTransactionException`, ORA-00060 (deadlock) a `DeadlockLoserDataAccessException`, y ORA-30006 (lock timeout, la que se explicó arriba en la sección de timeouts de lock) a `CannotAcquireLockException`. El código de retry no cambia, solo el motor detrás.

```typescript
// TypeScript / Node.js — retry manual compatible con Prisma y pg
async function conRetry<T>(fn: () => Promise<T>, maxIntentos = 3): Promise<T> {
  for (let intento = 1; intento <= maxIntentos; intento++) {
    try {
      return await fn();
    } catch (e: any) {
      // Prisma: P2034 = "transaction failed due to a write conflict or deadlock"
      // pg (node-postgres): error.code es el SQLSTATE directamente
      const esReintentable =
        e.code === "P2034" || // Prisma
        e.code === "40001" || // serialization_failure
        e.code === "40P01"; // deadlock_detected
      if (!esReintentable || intento === maxIntentos) throw e;
      await new Promise((r) => setTimeout(r, 50 * intento));
    }
  }
  throw new Error("unreachable");
}

// Uso con SERIALIZABLE:
await conRetry(() =>
  prisma.$transaction(
    async (tx) => {
      /* operaciones */
    },
    { isolationLevel: Prisma.TransactionIsolationLevel.Serializable },
  ),
);
```

**Por qué backoff y no retry inmediato:** si dos transacciones colisionaron, reintentar de inmediato tiene alta probabilidad de colisionar de nuevo — están corriendo en el mismo instante de tiempo. Un retardo pequeño (50ms, 100ms) separa los reintentos y reduce la probabilidad de nueva colisión. No necesita ser exponencial para la mayoría de los casos; lo importante es no reintentar a delay cero.

---

## Bloque 8: Transacciones distribuidas: motores nacidos distribuidos, sharding, y coordinación entre servicios {#8-distribuidas}

Esta sección junta dos preguntas relacionadas pero distintas, y las separa con claridad para no confundirlas. La primera es la que ya respondió la Guía comparativa del Bloque 5: dado un motor que reparte datos en varios nodos, ¿qué tan fuerte es su garantía transaccional entre esos nodos? La segunda es distinta: cuando la transacción cruza servicios o productos diferentes, no nodos del mismo motor, ¿cómo se coordina eso, y qué motor puede siquiera participar?

### Sección 8.1: Mapa visual, ¿el motor nació distribuido, o le instalaron sharding encima? {#8-mapa-visual}

```
              ¿El motor resolvió la concurrencia distribuida desde el diseño,
                     o reparte datos con una capa instalada encima?
                                       │
             ┌─────────────────────────┴─────────────────────────┐
             │                                                     │
      NACIÓ DISTRIBUIDO                                    SHARDING INSTALADO
  (snapshot distribuido real                             (capa sobre un motor de
   desde el origen)                                        un solo nodo conocido)
             │                                                     │
   ┌─────────┴─────────┐                          ┌────────────────┼────────────────┐
   │                    │                          │                │                 │
Consistencia fuerte  Consistencia            Atomicidad sin    Snapshot real     Rechaza cruzar
por diseño           ajustable por quorum    aislamiento real   entre shards      shards

Spanner, CockroachDB Cassandra, DynamoDB     Citus, Vitess     MongoDB shardeado  Redis Cluster
```

| Rama                                                | Motor                    | Qué pasa entre nodos/shards                                                                     |
| --------------------------------------------------- | ------------------------ | ----------------------------------------------------------------------------------------------- |
| Nació distribuido, consistencia fuerte              | Spanner                  | Locks reales + 2PC entre nodos + TrueTime, aislamiento fuerte por diseño                        |
| Nació distribuido, consistencia fuerte              | CockroachDB              | HLC + write intents + su propia SSI distribuida                                                 |
| Nació distribuido, quorum ajustable                 | Cassandra                | Quorum, más CAS vía Paxos (LWT) solo dentro de una partition                                    |
| Nació distribuido, quorum ajustable                 | DynamoDB                 | Quorum, más CAS vía condition expressions, hasta 100 ítems explícitos                           |
| Sharding instalado, atomicidad sin aislamiento real | Citus (sobre PostgreSQL) | 2PC entre workers, solo atomicidad, cae a READ COMMITTED entre shards                           |
| Sharding instalado, atomicidad sin aislamiento real | Vitess (sobre MySQL)     | Mismo patrón que Citus, 2PC sin snapshot distribuido                                            |
| Sharding instalado, snapshot real entre shards      | MongoDB shardeado        | 2PC + timestamps causalmente consistentes, sí da vista consistente entre shards                 |
| Sharding instalado, rechazo explícito               | Redis Cluster            | Aborta con `CROSSSLOT` si la transacción cruza shards, exige hash tag para forzar un solo shard |

Este mapa es la misma clasificación que ya construimos en la nota "sharding no es lo mismo que nacer distribuido" del Bloque 5, aquí en forma visual.

### Sección 8.2: Coordinación entre servicios distintos: el problema que ningún motor resuelve solo {#8-coordinacion-servicios}

Todo lo anterior es sobre un motor coordinando sus propios nodos. Esto es distinto, porque cuando una transacción necesita tocar dos servicios con bases de datos separadas, por ejemplo, un servicio de pedidos y un servicio de pagos, cada uno con su propio motor, ningún motor de esta tabla resuelve eso por sí solo. Ahí entra Two-Phase Commit (2PC) clásico, coordinado por un gestor de transacciones externo (el estándar X/Open XA), con el mismo protocolo de dos fases que ya vimos dentro de Spanner o Citus, pero ahora entre productos distintos:

```
COORDINADOR EXTERNO (gestor de transacciones XA)

  FASE 1: PREPARE                          FASE 2: COMMIT (si todos dijeron sí)
  Coordinador → Servicio A: "¿puedes?"      Coordinador → Servicio A: "confirma"
    A escribe PREPARED, responde sí           A confirma
  Coordinador → Servicio B: "¿puedes?"      Coordinador → Servicio B: "confirma"
    B escribe PREPARED, responde sí           B confirma

  Si el coordinador se cae entre la Fase 1 y la Fase 2:

  ┌─────────────────────────────────────────────┐
  │  Servicio B: PREPARED / in doubt             │
  │  Ya tiene los cambios listos y los locks      │
  │  activos, pero no puede confirmar ni abortar  │
  │  por sí solo. Queda bloqueado hasta que el    │
  │  coordinador vuelva o alguien intervenga.     │
  └─────────────────────────────────────────────┘
```

Este es el mismo "problema del coordinador" ya documentado en `guia_transacciones_db.md` §16, solo que aquí entre productos distintos en vez de nodos del mismo motor. Es la razón exacta por la que la industria se movió hacia otro patrón.

**No todos los motores pueden siquiera participar en esto.** El estándar XA exige que cada participante soporte el protocolo de prepare/commit en dos fases como una operación explícita, no solo como mecanismo interno. Verificado motor por motor:

| Participa en un coordinador XA externo | Motor                                                     | Cómo                                                                                                                           |
| -------------------------------------- | --------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| Sí                                     | PostgreSQL                                                | `PREPARE TRANSACTION 'id'`, después `COMMIT PREPARED` / `ROLLBACK PREPARED`                                                    |
| Sí                                     | MySQL                                                     | `XA START`, `XA END`, `XA PREPARE`, `XA COMMIT` / `XA ROLLBACK`                                                                |
| Sí                                     | Oracle                                                    | Soporte nativo de transacciones distribuidas XA                                                                                |
| Sí                                     | SQL Server                                                | A través de MSDTC (Microsoft Distributed Transaction Coordinator)                                                              |
| No                                     | MongoDB, Cassandra, DynamoDB, Redis, Spanner, CockroachDB | Cada uno resuelve su propia distribución internamente, pero no como participante de un XA ajeno junto a otro producto distinto |

Solo los cuatro relacionales clásicos de esta tabla pueden ser parte de un 2PC clásico entre productos. Ninguno de los motores no relacionales o nacidos distribuidos participa en eso, resuelven su propia consistencia por dentro, no la comparten con un coordinador externo.

### Sección 8.3: La alternativa moderna: Saga y Outbox {#8-saga-outbox}

Por el problema del coordinador bloqueado, la práctica moderna evita 2PC entre servicios y usa dos patrones que ya están documentados en `guia_transacciones_db.md` §16 y §18:

```
SAGA: una secuencia de transacciones locales, cada una con su propia compensación

  Servicio A: reservar inventario ──► Servicio B: cobrar pago ──► Servicio C: enviar
       │ (transacción local,               │ (transacción local,        │ (transacción
       │  ACID normal en su                │  ACID normal en su         │  local normal)
       │  propio motor)                     │  propio motor)             │
       ▼                                    ▼                            ▼
  Si C falla: se ejecutan las compensaciones en reversa
  (cancelar cobro, liberar inventario), no un rollback distribuido real


OUTBOX: la transacción local escribe el cambio Y el mensaje a enviar, atómicamente

  Servicio A, una sola transacción local:
    UPDATE inventario SET stock = stock - 1;
    INSERT INTO outbox (evento) VALUES ('item_reservado');   ← misma transacción, mismo commit
  Un proceso aparte lee la tabla outbox y publica el mensaje al resto de servicios
  (si el proceso falla y reintenta, el mensaje puede duplicarse, por eso el consumidor
  debe ser idempotente, no se necesita 2PC entre servicios en ningún momento)
```

Esto depende cada vez menos del motor, porque ambos patrones solo necesitan que cada servicio tenga una transacción ACID local normal, exactamente la que ya tiene cualquier motor de esta tabla en modo de un solo nodo o un solo shard. La arquitectura de concurrencia que comparamos en toda esta sección, MVCC contra 2PL, OCC contra SSI, deja de ser el criterio relevante en el momento en que la coordinación se mueve del motor a la aplicación. Eso es exactamente lo que ganó la industria al abandonar 2PC clásico entre servicios, ya no filtra qué motor puede usar, cualquiera que ya sepa hacer una transacción local, que es prácticamente todos los de esta tabla, sirve igual de bien como pieza de una Saga o un Outbox.
