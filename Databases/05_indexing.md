# Índices: cómo el motor decide leer los datos

## Escaneos, el query planner, y cuándo un índice realmente ayuda

Esta guía cubre qué pasa **después** de que ya existe un índice: qué tipos de escaneo puede
elegir el motor para responder una query, cómo el optimizer decide entre ellos, qué patrones
de SQL inhabilitan un índice aunque exista, y qué precauciones aplican en producción
(mantenimiento, bloat, locking, foreign keys sin índice). La estructura física del B-Tree y el
B+Tree —qué es un nodo, por qué existe, cómo se ve en disco en cada motor— ya se cubrió en
`04_almacenamiento_motores_relacionales.md` (Bloques 4 al 8). Este archivo continúa justo
donde termina esa guía.

Motores de referencia: PostgreSQL, MySQL InnoDB, SQL Server y Oracle como los cuatro motores
relacionales mayores, con SQLite como caso de motor embebido más simple. Donde el concepto
tiene un equivalente razonable en NoSQL, se compara contra DynamoDB y MongoDB.

Cada **Bloque** es un tema completo en sí mismo. Cada **Sección** dentro de un bloque es un
subtema puntual de ese tema.

**Bloque 1 — Tipos de escaneo: cómo el motor decide leer los datos**

- [Sección 1.1 — Full Table Scan (Seq Scan)](#1-seqscan)
- [Sección 1.2 — Index Scan: B+Tree + heap fetch](#1-indexscan)
- [Sección 1.3 — Index Only Scan: cuando el índice tiene todo lo necesario](#1-indexonly)
- [Sección 1.4 — Bitmap Index Scan: combinar múltiples índices](#1-bitmap)
- [Sección 1.5 — Clustered vs non-clustered: el impacto en el costo del scan](#1-pk-vs-sk)

**Bloque 2 — Cuándo el planner NO usa un índice (aunque exista)**

- [Sección 2.1 — Selectividad y el punto de cruce](#2-selectividad)
- [Sección 2.2 — Funciones y expresiones sobre la columna indexada](#2-funciones)
- [Sección 2.3 — Cast implícito de tipos](#2-cast-implicito)
- [Sección 2.4 — Wildcards en LIKE: prefijo vs sufijo](#2-wildcards)
- [Sección 2.5 — OR entre columnas distintas](#2-or)
- [Sección 2.6 — Negaciones: `<>`, `NOT IN`, `NOT LIKE`](#2-negaciones)
- [Sección 2.7 — NULLs y los índices](#2-nulls)

**Bloque 3 — El query planner y el cost model**

- [Sección 3.1 — El cost model de PostgreSQL: cómo se calcula el costo](#3-costmodel)
- [Sección 3.2 — Estadísticas en PostgreSQL: pg_stats, histogramas, correlación](#3-estadisticas)
- [Sección 3.3 — Estadísticas en otros motores](#3-estadisticas-motores)
- [Sección 3.4 — EXPLAIN y EXPLAIN ANALYZE en PostgreSQL, a fondo](#3-explain)
- [Sección 3.5 — Leer planes de ejecución en otros motores](#3-explain-motores)

**Bloque 4 — Diseñando índices compuestos y especializados**

- [Sección 4.1 — Índices compuestos y la regla del leftmost prefix](#4-compuestos)
- [Sección 4.2 — Índices parciales / filtered indexes](#4-parciales)
- [Sección 4.3 — Covering indexes: INCLUDE y equivalentes](#4-covering)
- [Sección 4.4 — El costo de mantener índices en escrituras](#4-costo-escritura)
- [Sección 4.5 — Índices redundantes: detectarlos antes de que cuesten](#4-redundantes)

**Bloque 5 — Tipos de índice más allá del B+Tree**

- [Sección 5.1 — Hash indexes](#5-hash)
- [Sección 5.2 — Full-text e inverted indexes: GIN vs FULLTEXT vs Oracle Text](#5-fulltext)
- [Sección 5.3 — Bitmap indexes de Oracle, a fondo](#5-oracle-bitmap)
- [Sección 5.4 — Bloom Filters como índice](#5-bloom)
- [Sección 5.5 — Índices geoespaciales](#5-geoespacial)
- [Sección 5.6 — Índices en NoSQL: DynamoDB y MongoDB](#5-nosql)

**Bloque 6 — Escala: tablas de mil millones de filas**

- [Sección 6.1 — El límite del B+Tree a escala](#6-limite)
- [Sección 6.2 — BRIN: Block Range Index](#6-brin)
- [Sección 6.3 — Particionamiento como estrategia de índices](#6-particionamiento)
- [Sección 6.4 — UUIDs en B+Tree: por qué degradan el rendimiento](#6-uuid)
- [Sección 6.5 — Estrategia completa para tablas grandes](#6-estrategia)

**Bloque 7 — Producción: mantenimiento y precauciones**

- [Sección 7.1 — Construir índices sin bloquear producción](#7-concurrently)
- [Sección 7.2 — Bloat de índices y reconstrucción](#7-bloat)
- [Sección 7.3 — Foreign keys sin índice: el gotcha silencioso](#7-fk-sin-indice)
- [Sección 7.4 — Índices no usados: detectarlos y eliminarlos](#7-no-usados)
- [Sección 7.5 — Checklist: por qué mi query no usa el índice](#7-checklist)

**Bloque 8 — Comparación final: qué índice usar y cuándo**

- [Sección 8.1 — Tabla comparativa por motor](#8-comparacion)

---

## Bloque 1: Tipos de escaneo: cómo el motor decide leer los datos {#1-tipos-escaneo}

---

### Sección 1.1: Full Table Scan (Seq Scan) {#1-seqscan}

Un Seq Scan lee **todas las páginas del heap en orden físico** (página 0, página 1, página 2,
...). Las filas que no cumplen el filtro se descartan en CPU, no en disco: el motor no sabe
cuáles filas cumplen hasta que las lee.

```sql
EXPLAIN SELECT * FROM productos WHERE precio < 100;
-- Seq Scan on productos  (cost=0.00..2500.00 rows=12543 width=64)
--   Filter: (precio < 100)

-- El motor lee TODAS las páginas aunque solo retorne el 15% de las filas.
```

**Por qué el motor a veces prefiere leer todo antes que usar un índice:** el I/O secuencial
es dramáticamente más barato que el I/O aleatorio, y esa diferencia es la que determina si
vale la pena pagar el costo de navegar un árbol.

| Medio    | I/O secuencial | I/O aleatorio (8KB)      | Secuencial vs aleatorio |
| -------- | -------------- | ------------------------ | ----------------------- |
| HDD      | 200 MB/s       | 150 IOPS ≈ 1.2 MB/s efec | ~166× más rápido        |
| SSD NVMe | 3,000 MB/s     | 500,000 IOPS ≈ 4,000 MB/s | ~menos de 1× (competitivo) |

El motor también puede hacer **read-ahead**: pedir varias páginas al SO antes de necesitarlas,
porque sabe que las va a leer en orden. Con un índice no puede hacerlo, porque no sabe qué
página necesitará después hasta que resuelve la actual.

**El punto de cruce** entre Seq Scan e Index Scan depende de qué porcentaje de la tabla
retorna la query:

| % de filas que retorna la query | Ganador habitual                                    |
| -------------------------------- | ---------------------------------------------------- |
| < 1–5%                           | Index Scan casi siempre gana (pocos heap fetches)     |
| 5–15%                             | Depende del hardware y de la correlación del índice   |
| > 15%                             | Seq Scan casi siempre gana (demasiados heap fetches)  |

```
Ejemplo: tabla de 1,000,000 filas, 10,000 páginas (80MB)
  Seq Scan: 10,000 I/Os secuenciales
  Index Scan retornando 50,000 filas: 50,000 heap fetches aleatorios
  50,000 × 0.01ms (SSD) = 500ms   vs   Seq Scan en SSD ≈ 27ms
  → El planner elige Seq Scan correctamente
```

PostgreSQL también puede paralelizar el Seq Scan repartiendo páginas entre varios workers:

```sql
SET max_parallel_workers_per_gather = 4;
EXPLAIN SELECT COUNT(*) FROM ventas;
-- Finalize Aggregate
--   → Gather (workers=4)
--       → Partial Aggregate
--           → Parallel Seq Scan on ventas
-- Cada worker lee un subconjunto de páginas → speedup de hasta 4× (limitado por I/O y CPU)
```

**El mismo concepto con otro nombre en cada motor:**

| Motor      | Nombre del full scan en el plan          |
| ---------- | ------------------------------------------ |
| PostgreSQL | `Seq Scan`                                 |
| MySQL      | `type: ALL` (peor tipo de acceso posible)  |
| SQL Server | `Table Scan` (heap) / `Clustered Index Scan` (si hay clustered index, lee sus hojas en orden) |
| Oracle     | `TABLE ACCESS FULL`                        |
| SQLite     | `SCAN TABLE`                               |

Nota sobre SQL Server y Oracle: como sus tablas suelen ser clustered index / IOT (ver
Sección 1.5), "escanear toda la tabla" en realidad significa recorrer las hojas del árbol
clustered en orden físico, no un heap separado. El costo relativo sigue la misma lógica: leer
todo secuencialmente vs. saltar por el árbol.

---

### Sección 1.2: Index Scan: B+Tree + heap fetch {#1-indexscan}

```
Query: SELECT * FROM productos WHERE id = 42

Paso 1: Recorrer el árbol B+Tree
  Raíz → nodo interno → ... → nodo hoja
  Nodo hoja: [42, ctid=(5,4)]
  → 3-4 lecturas del árbol (páginas del índice)

Paso 2: Heap fetch
  Ir al heap: leer página 5, tuple 4
  → 1 lectura de la tabla (I/O aleatorio si la página no está en cache)

Paso 3: Visibility check (MVCC)
  Verificar t_xmin y t_xmax de la tuple
  ¿Es visible para esta transacción?
  → Si sí: devolver la fila
  → Si no: buscar versión anterior (si aplica)

Total: 4-5 I/Os para encontrar UNA fila específica.
```

El heap fetch es el paso caro. Si el índice retorna muchos ctids y esos ctids apuntan a
páginas dispersas del heap, cada uno puede costar un I/O aleatorio independiente:

```
Query: SELECT * FROM pedidos WHERE fecha BETWEEN '2024-01-01' AND '2024-01-31'
  → El índice retorna 50,000 ctids
  → Si las filas de enero están dispersas por todo el heap: hasta 50,000 I/Os aleatorios
  → Si las filas de enero están concentradas en pocas páginas (inserción cronológica):
    muchos ctids caen en la misma página → mucho menos I/O real

Esto es la CORRELACIÓN (ver Sección 3.2): qué tan ordenado está el heap respecto al índice.
```

El planner favorece Index Scan cuando: la selectividad es alta (pocas filas), la correlación
es alta (las filas resultado están concentradas en pocas páginas), la tabla es pequeña, o
`random_page_cost` es bajo (SSD). Lo evita cuando la selectividad es baja, la correlación es
baja, o la tabla completa ya vive en `shared_buffers` (ahí un Seq Scan es casi gratis).

**El mismo mecanismo con otro nombre y otras variantes por motor:**

| Motor      | Cómo aparece en el plan                                                        |
| ---------- | -------------------------------------------------------------------------------- |
| PostgreSQL | `Index Scan using idx_x`                                                         |
| MySQL      | `type: ref` (igualdad), `range` (rango), `eq_ref` (join 1:1 por PK/unique). `Extra: Using index condition` = Index Condition Pushdown: el filtro extra se evalúa en el motor de almacenamiento antes de subir la fila, ahorrando el viaje de vuelta |
| SQL Server | `Index Seek` + `Key Lookup` (el "heap fetch" de SQL Server: ir al clustered index o al heap por el resto de las columnas) |
| Oracle     | `INDEX RANGE SCAN` seguido de `TABLE ACCESS BY INDEX ROWID`                       |
| SQLite     | `SEARCH tabla USING INDEX idx_x`                                                  |

El `Key Lookup` de SQL Server y el `TABLE ACCESS BY INDEX ROWID` de Oracle son exactamente el
mismo problema que el heap fetch de PostgreSQL: un salto adicional, potencialmente aleatorio,
para completar la fila. La solución en los tres casos es la misma: un covering index
(Sección 4.3).

---

### Sección 1.3: Index Only Scan: cuando el índice tiene todo lo necesario {#1-indexonly}

En un Index Scan normal siempre hay un heap fetch, tanto para traer la fila completa como
para el visibility check de MVCC. El **Index Only Scan** elimina ese heap fetch cuando todas
las columnas del SELECT ya están en el índice.

```sql
CREATE INDEX idx_email ON usuarios (email);

-- Index Only Scan: email está en el índice, no necesita ir al heap
SELECT email FROM usuarios WHERE email = 'ana@example.com';

-- Index Scan normal: nombre NO está en el índice → necesita heap fetch
SELECT nombre FROM usuarios WHERE email = 'ana@example.com';
```

El Index Only Scan todavía necesita resolver visibilidad MVCC, y normalmente eso exige leer
el heap. PostgreSQL lo evita con la **visibility map**: un bitmap de 2 bits por página del
heap (`all-visible`, `all-frozen`). Si la página está marcada `all-visible`, todas sus tuples
son visibles para cualquier transacción y el escaneo no necesita tocar el heap. Si no está
marcada (hay escrituras recientes sin vacuum), el Index Only Scan degrada a un heap fetch por
fila, igual que un Index Scan normal.

```sql
EXPLAIN (ANALYZE, BUFFERS) SELECT email FROM usuarios WHERE email LIKE 'ana%';
-- Index Only Scan using idx_email on usuarios
--   Index Cond: ((email >= 'ana') AND (email < 'anb'))
--   Heap Fetches: 0    ← ideal: sin heap fetches
--   Heap Fetches: 842  ← muchas tuples fuera de la visibility map → va al heap
```

Conclusión práctica: para que el Index Only Scan sea efectivo, la tabla debe ser
relativamente estable (pocos INSERT/UPDATE recientes sin vacuum) o haber corrido `VACUUM`
recientemente. La forma de garantizar que el índice tenga todas las columnas que hacen falta
es el **covering index** (INCLUDE), que se trata a fondo en la Sección 4.3.

**Por motor:** MySQL llama a esto `Extra: Using index` (covering index) — no expone un
contador equivalente a "Heap Fetches" porque InnoDB no tiene el concepto de visibility map: el
índice secundario ya trae suficiente información para resolver la lectura sin ir al clustered
index en la mayoría de los casos. SQL Server lo muestra como un `Index Seek` sin `Key Lookup`
adyacente en el plan. Oracle lo llama `INDEX FAST FULL SCAN` cuando recorre el índice completo
sin ir a la tabla (equivalente a un Index Only Scan sin condición de rango).

---

### Sección 1.4: Bitmap Index Scan: combinar múltiples índices {#1-bitmap}

Un Index Scan solo puede usar **un** índice a la vez. Si una query filtra por dos columnas
con índices separados, PostgreSQL puede combinar ambos construyendo bitmaps en memoria:

```sql
CREATE INDEX idx_region    ON pedidos (region);
CREATE INDEX idx_categoria ON pedidos (categoria);

SELECT * FROM pedidos WHERE region = 'Norte' AND categoria = 'Electrónica';
```

```
Paso 1: BitmapIndexScan sobre idx_region = 'Norte'
  [página_0: 0, página_1: 1, página_2: 0, página_3: 1, página_4: 1, ...]

Paso 2: BitmapIndexScan sobre idx_categoria = 'Electrónica'
  [página_0: 1, página_1: 1, página_2: 0, página_3: 0, página_4: 1, ...]

Paso 3: BitmapAnd (AND bit a bit)
  [página_0: 0, página_1: 1, página_2: 0, página_3: 0, página_4: 1, ...]
  → Solo las páginas 1 y 4 pueden tener filas que cumplan ambas condiciones

Paso 4: BitmapHeapScan
  Lee las páginas 1 y 4 en ORDEN FÍSICO (no aleatorio)
  → Verifica región='Norte' AND categoria='Electrónica' en cada fila candidata
```

```sql
EXPLAIN SELECT * FROM pedidos WHERE region = 'Norte' AND categoria = 'Electrónica';
-- BitmapAnd
--   Bitmap Index Scan on idx_region      (Index Cond: region = 'Norte')
--   Bitmap Index Scan on idx_categoria   (Index Cond: categoria = 'Electrónica')
-- → Bitmap Heap Scan on pedidos
--     Recheck Cond: ((region = 'Norte') AND (categoria = 'Electrónica'))
```

`BitmapOr` hace lo mismo para condiciones OR, incluso entre columnas **distintas** (ver
Sección 2.5 — el mito de que "OR siempre rompe el índice"):

```sql
SELECT * FROM pedidos WHERE region = 'Norte' OR region = 'Sur';
-- BitmapOr
--   Bitmap Index Scan on idx_region (region = 'Norte')
--   Bitmap Index Scan on idx_region (region = 'Sur')
-- → Bitmap Heap Scan
```

| Selectividad                    | Plan que suele elegir el planner                          |
| -------------------------------- | ------------------------------------------------------------ |
| Alta (pocas filas)               | Index Scan — pocos heap fetches, no vale construir un bitmap  |
| Media (5–15%)                    | Bitmap Scan — ordenar los heap fetches vale la pena           |
| Baja (>15%)                      | Seq Scan — leer toda la tabla es más eficiente                |
| Múltiples condiciones moderadas  | BitmapAnd / BitmapOr — combina índices que solos no bastan     |

**Una distinción importante entre motores:** en PostgreSQL, "Bitmap Index Scan" es una
operación del **plan**, no un tipo de índice — el bitmap se construye en memoria a partir de
un B-Tree normal, en el momento de ejecutar la query, y se descarta al terminar. MySQL tiene
el equivalente bajo el nombre **index merge** (`Using union`, `Using intersect`,
`Using sort_union` en `EXTRA`): combina rangos de varios índices B-Tree existentes de la misma
forma. Ninguno de los dos requiere un tipo de índice especial para esto.

Oracle, en cambio, tiene un **bitmap index** que es una estructura persistente en disco (no
una operación de plan sobre un B-Tree) — se trata a fondo en la Sección 5.3, porque resuelve
un problema relacionado pero distinto: columnas de baja cardinalidad que se consultan
constantemente en combinación. SQL Server no tiene un equivalente directo a BitmapAnd/index
merge: normalmente resuelve el mismo caso con un índice compuesto o forzando al optimizer con
hints, y si no hay un índice que cubra ambas columnas, suele terminar en un scan.

---

### Sección 1.5: Clustered vs non-clustered: el impacto en el costo del scan {#1-pk-vs-sk}

La estructura completa de clustered index vs heap + índices separados ya se explicó por motor
en `04_almacenamiento_motores_relacionales.md` (Bloque 8, con tabla comparativa completa
PostgreSQL / MySQL InnoDB / SQL Server / Oracle IOT / SQLite / DynamoDB / MongoDB). Lo que
importa para este archivo es el efecto directo sobre el costo del Index Scan de la
Sección 1.2:

- **PostgreSQL** (heap + índices separados): cualquier índice, incluida la PK, necesita un
  heap fetch para traer la fila completa. Todos los índices son "iguales" — no hay uno más
  barato que otro estructuralmente.
- **MySQL InnoDB / SQL Server con clustered index / Oracle IOT**: buscar por la clave
  clustered (normalmente la PK) **no** necesita un segundo salto — la hoja del árbol clustered
  ya contiene la fila completa. Pero buscar por un índice secundario sí necesita dos
  recorridos de árbol (secundario → valor de la clave clustered → árbol clustered), en vez de
  un recorrido + heap fetch como en PostgreSQL.
- El comando `CLUSTER` de PostgreSQL reordena el heap físicamente según un índice dado, pero es
  una operación **puntual**: los siguientes INSERT/UPDATE no mantienen ese orden. No es lo
  mismo que un clustered index de MySQL/SQL Server, que mantiene el orden automáticamente en
  cada escritura.

En la práctica, esto significa que el mismo query "busca por PK" es más barato en MySQL/SQL
Server/Oracle-IOT (un solo árbol) que en PostgreSQL (árbol + heap), pero el mismo query "busca
por un índice secundario y trae columnas fuera de ese índice" paga un costo similar en ambos
modelos, solo que como dos recorridos de árbol en un caso y como recorrido + heap fetch en el
otro.

---

## Bloque 2: Cuándo el planner NO usa un índice (aunque exista) {#2-cuando-no}

Esta es la pregunta que más se hace en producción: "tengo un índice en esa columna, ¿por qué
el planner sigue haciendo Seq Scan?". Las respuestas caen en dos categorías: (a) decisiones de
costo basadas en selectividad — el planner podría usar el índice pero calcula que no conviene
— y (b) casos donde el índice **no puede** usarse en absoluto porque la forma del predicado no
es "sargable" (search-argument-able): no se puede traducir a un rango de búsqueda sobre las
claves ordenadas del árbol.

---

### Sección 2.1: Selectividad y el punto de cruce {#2-selectividad}

Ya cubierto en detalle en la Sección 1.1 y en el cost model (Sección 3.1): si la query
retorna un porcentaje alto de la tabla, el índice existe pero el planner decide, correctamente,
que un Seq Scan es más barato. Esto **no** es un caso de "el índice no sirve" — es el planner
haciendo su trabajo. La forma de confirmarlo es leer el `EXPLAIN` (Sección 3.4) y comparar
`rows` estimado contra la selectividad real de la condición.

---

### Sección 2.2: Funciones y expresiones sobre la columna indexada {#2-funciones}

```sql
CREATE INDEX idx_email ON usuarios (email);

-- NO usa el índice: el árbol está ordenado por email, no por UPPER(email)
SELECT * FROM usuarios WHERE UPPER(email) = 'ANA@EXAMPLE.COM';

-- SÍ usa el índice: el filtro se aplica sobre la columna cruda
SELECT * FROM usuarios WHERE email = 'ana@example.com';
```

La regla es siempre la misma en cualquier motor: **una función o expresión aplicada a la
columna** rompe la relación entre el predicado y el orden físico del árbol, porque el árbol
está ordenado por el valor crudo de la columna, no por el resultado de la función. El planner
no sabe (ni intenta deducir) que `UPPER(email) = 'ANA@EXAMPLE.COM'` es equivalente a un rango
sobre `email`.

La solución es un **índice de expresión**, que indexa el resultado de la función en vez de la
columna cruda:

```sql
-- PostgreSQL: índice de expresión
CREATE INDEX idx_email_upper ON usuarios (UPPER(email));
SELECT * FROM usuarios WHERE UPPER(email) = 'ANA@EXAMPLE.COM';  -- ahora SÍ usa el índice

-- MySQL 8.0.13+: functional key part (misma idea, sintaxis distinta)
CREATE INDEX idx_email_upper ON usuarios ((UPPER(email)));

-- SQL Server: no hay índice de expresión directo, se logra con una computed column indexada
ALTER TABLE usuarios ADD email_upper AS UPPER(email) PERSISTED;
CREATE INDEX idx_email_upper ON usuarios (email_upper);

-- Oracle: función-based index, el mecanismo original de este patrón (desde Oracle 8i)
CREATE INDEX idx_email_upper ON usuarios (UPPER(email));

-- SQLite: expression index (3.9+), misma sintaxis que PostgreSQL
CREATE INDEX idx_email_upper ON usuarios (UPPER(email));
```

**Precaución al cambiar de función**: el índice de expresión solo sirve para consultas que usan
exactamente esa expresión, con los mismos argumentos y en el mismo orden textual (algunos
motores normalizan la expresión, otros no). `WHERE UPPER(email) = ?` usa el índice;
`WHERE UPPER(TRIM(email)) = ?` no, porque es una expresión distinta.

---

### Sección 2.3: Cast implícito de tipos {#2-cast-implicito}

Este es el gotcha más silencioso de los cuatro motores, porque el SQL "se ve" correcto y a
veces hasta retorna el resultado correcto — solo que sin usar el índice.

```sql
CREATE TABLE pedidos (
    codigo   VARCHAR(20) PRIMARY KEY,   -- ej. '000123'
    total    NUMERIC(10,2)
);
CREATE INDEX idx_codigo ON pedidos (codigo);

-- PROBLEMA: comparar una columna VARCHAR contra un literal numérico
SELECT * FROM pedidos WHERE codigo = 123;
-- El motor necesita convertir CADA valor de la columna a número (o el número a texto,
-- según reglas de precedencia de tipos) antes de poder comparar.
-- Si la conversión se aplica a la COLUMNA, el índice deja de ser sargable: es equivalente
-- a WHERE CAST(codigo AS NUMERIC) = 123, y eso cae en el mismo problema de la Sección 2.2.
```

La regla que aplica en los cuatro motores: **si el cast/conversión se aplica sobre el
LITERAL, el índice sigue funcionando; si se aplica sobre la COLUMNA, se rompe.** El problema es
que el motor, no el usuario, decide de qué lado aplica la conversión, según las reglas de
precedencia de tipos de cada uno — y esas reglas no siempre favorecen a la columna indexada.

- **SQL Server** es el más documentado en este punto: comparar una columna `nvarchar` contra un
  literal `varchar` (o viceversa, según la precedencia de tipos de datos) provoca una
  conversión implícita que SQL Server aplica del lado de menor precedencia — si ese lado es la
  columna indexada, el `Index Seek` esperado se convierte en `Index Scan` o `Table Scan`. Es
  una causa extremadamente común de queries lentas en producción que "deberían" usar el
  índice.
- **MySQL**: comparar una columna entera contra un literal de texto es seguro (el literal se
  convierte, no la columna). Comparar una columna de texto contra un literal numérico puede
  forzar la conversión de la columna completa, inhabilitando el índice — el caso clásico es un
  campo de teléfono o código postal guardado como `VARCHAR` comparado con un número sin
  comillas.
- **PostgreSQL** es más estricto en tipos: muchas comparaciones entre tipos incompatibles
  simplemente fallan en vez de convertir implícitamente, lo cual evita este problema en varios
  casos, pero no en todos (ej. `numeric` vs `text` explícitamente casteado en la app).
- **Oracle** también convierte implícitamente según reglas de precedencia de tipos, y el mismo
  patrón (columna `VARCHAR2` comparada con un literal numérico) puede hacer que el CBO decida
  no usar el índice.

La mitigación es siempre la misma: castear el **literal**, nunca depender de la conversión
implícita, y mantener los tipos de columna y de parámetro alineados desde el diseño del
esquema.

---

### Sección 2.4: Wildcards en LIKE: prefijo vs sufijo {#2-wildcards}

```sql
CREATE INDEX idx_nombre ON productos (nombre);

-- SÍ usa el índice: equivalente a un rango [ 'Lap', 'Lap' + 1 )
SELECT * FROM productos WHERE nombre LIKE 'Lap%';

-- NO usa el índice B-Tree: no hay forma de acotar un rango cuando el prefijo es desconocido
SELECT * FROM productos WHERE nombre LIKE '%top';
SELECT * FROM productos WHERE nombre LIKE '%pro%';
```

Un B-Tree está ordenado por prefijo. `LIKE 'Lap%'` se traduce internamente a
`nombre >= 'Lap' AND nombre < 'Lap' + 1_caracter`, que es un rango perfectamente navegable en
el árbol. `LIKE '%top'` no tiene ningún prefijo fijo del cual partir: el motor tendría que
mirar cada valor del árbol de todas formas, así que ni siquiera lo intenta y va directo a un
scan completo.

Esto aplica igual en PostgreSQL, MySQL, SQL Server, Oracle y SQLite: es una limitación
estructural del B-Tree, no una decisión de un optimizer en particular. Las soluciones también
son estructurales, no trucos de SQL:

| Motor      | Estructura para búsquedas de substring / sufijo                                    |
| ---------- | -------------------------------------------------------------------------------------- |
| PostgreSQL | Extensión `pg_trgm` (trigramas) + índice GIN o GiST — indexa fragmentos de 3 caracteres |
| MySQL      | Índice `FULLTEXT` (indexa palabras completas, no substrings arbitrarios)                |
| SQL Server | Full-Text Search (`CONTAINS`/`FREETEXT`, catálogo de texto completo aparte)              |
| Oracle     | Oracle Text (índice `CONTEXT`)                                                          |
| SQLite     | Módulo `FTS5` (virtual table de full-text)                                              |

Ninguna de estas estructuras es un B-Tree: son índices invertidos (ver Sección 5.2),
diseñados específicamente para este patrón.

---

### Sección 2.5: OR entre columnas distintas {#2-or}

La creencia común es que "OR siempre rompe el índice". Es más preciso decir: **un OR usa
índice solo si CADA rama del OR tiene, por su cuenta, un camino indexado**; si una sola rama
cae en una columna sin índice, el motor no puede garantizar corrección usando solo índices y
recurre a un scan completo para esa rama (y frecuentemente para la query entera).

```sql
CREATE INDEX idx_region    ON pedidos (region);
CREATE INDEX idx_categoria ON pedidos (categoria);

-- Cada rama tiene su propio índice → PostgreSQL puede resolverlo con BitmapOr (Sección 1.4)
SELECT * FROM pedidos WHERE region = 'Norte' OR categoria = 'Electrónica';

-- 'notas' no tiene índice → esta rama fuerza un scan, y normalmente arrastra a toda la query
SELECT * FROM pedidos WHERE region = 'Norte' OR notas LIKE '%urgente%';
```

MySQL resuelve el primer caso con **index merge (union)**, el mismo mecanismo mencionado en la
Sección 1.4. SQL Server y Oracle también pueden combinar caminos de acceso para un OR entre
columnas indexadas distintas, pero son más conservadores: si el optimizer no encuentra un plan
de combinación que le resulte más barato que un scan, elige el scan directamente, incluso
cuando técnicamente ambas ramas tendrían índice. Cuando esto pasa en producción, reescribir el
OR como un `UNION` de dos queries independientes (cada una filtrando por su propia columna
indexada) suele forzar un plan más barato, porque cada mitad del `UNION` se optimiza por
separado.

---

### Sección 2.6: Negaciones: `<>`, `NOT IN`, `NOT LIKE` {#2-negaciones}

A diferencia de los casos anteriores, esto **no** es una limitación técnica del B-Tree — es
casi siempre una decisión de costo, y por eso se explica junto a la selectividad (Sección 2.1)
en vez de junto a los casos no-sargables.

```sql
SELECT * FROM pedidos WHERE estado <> 'cancelado';
-- Técnicamente equivalente a: estado < 'cancelado' OR estado > 'cancelado'
-- Dos rangos son navegables en el árbol, PERO casi siempre esto retorna la
-- mayoría de las filas de la tabla (poca selectividad) → el planner prefiere Seq Scan.
```

`NOT IN (...)` y `NOT LIKE 'prefijo%'` caen en la misma categoría: son técnicamente
traducibles a un conjunto de rangos, pero en la práctica casi siempre excluyen pocas filas y
retienen la mayoría, así que el costo estimado de un scan completo termina siendo menor. La
excepción es cuando la negación es sobre un conjunto de valores raros (`estado NOT IN
('pendiente')` en una tabla donde el 99% de las filas ya están en `'pendiente'`) — ahí sí
conviene y el planner normalmente lo detecta solo, con buenas estadísticas (Sección 3.2).

Un caso aparte y sí técnico: `NOT LIKE '%patrón%'` con wildcard al inicio hereda exactamente
la limitación de la Sección 2.4 — ninguna negación arregla el problema de que no hay prefijo
fijo del cual partir.

---

### Sección 2.7: NULLs y los índices {#2-nulls}

Este es el punto donde los motores difieren de verdad, no solo en el nombre del plan sino en
el comportamiento correcto de la query.

```sql
CREATE INDEX idx_telefono ON usuarios (telefono);
SELECT * FROM usuarios WHERE telefono IS NULL;
```

- **PostgreSQL**, **MySQL InnoDB** y **SQL Server** almacenan entradas de índice para filas con
  valor `NULL` en la columna indexada. `IS NULL` puede resolverse con un Index Scan normal en
  los tres.
- **Oracle es la excepción real**: un B-Tree index de Oracle **no almacena una entrada** para
  una fila donde **todas** las columnas indexadas son `NULL`. Esto significa que
  `WHERE telefono IS NULL` sobre un índice de una sola columna **no puede** resolverse con ese
  índice — no es que el planner elija no usarlo, es que la fila literalmente no está en el
  árbol. El workaround típico es indexar una columna adicional que nunca sea NULL junto a la
  que sí lo es (`CREATE INDEX idx ON usuarios (telefono, 0)` con una expresión constante, o un
  índice compuesto con otra columna NOT NULL), para que la entrada compuesta nunca sea
  "totalmente NULL" y el motor sí la guarde.
- Esta misma peculiaridad de Oracle se puede usar a favor: es la base del truco de índice
  parcial "simulado" con un índice de expresión que se explica en la Sección 4.2.

---

## Bloque 3: El query planner y el cost model {#3-planner}

---

### Sección 3.1: El cost model de PostgreSQL: cómo se calcula el costo {#3-costmodel}

PostgreSQL es un optimizador basado en costos (CBO): genera varios planes candidatos, estima
el costo de cada uno y ejecuta el de menor costo estimado.

```
Query: SELECT * FROM pedidos WHERE cliente_id = 42 AND fecha > '2024-01-01'

Planes candidatos:
  Plan A: Seq Scan → Filter (cliente_id=42 AND fecha>...)
  Plan B: Index Scan por idx_cliente → Filter fecha>...
  Plan C: Index Scan por idx_fecha → Filter cliente_id=42
  Plan D: BitmapAnd de idx_cliente + idx_fecha → Bitmap Heap Scan
  Plan E: Index Scan por idx_cliente_fecha (índice compuesto)

Para cada plan: costo = costo_I/O (páginas × costo_por_página) + costo_CPU (filas × costo_por_fila)
```

```sql
SHOW seq_page_cost;        -- 1.0   costo de leer una página secuencial (referencia base)
SHOW random_page_cost;     -- 4.0   costo de leer una página aleatoria (default calibrado para HDD)
SHOW cpu_tuple_cost;       -- 0.01  costo de procesar una fila en CPU
SHOW cpu_index_tuple_cost; -- 0.005 costo de procesar una entrada de índice
SHOW cpu_operator_cost;    -- 0.0025 costo de evaluar un operador (=, <, etc.)
```

```
Costo de un Seq Scan (10,000 páginas, 1,000,000 filas):
  = 1.0 × 10,000 + 0.01 × 1,000,000 = 10,000 + 10,000 = 20,000

Costo de un Index Scan (selectividad 0.1% = 1,000 filas):
  costo_árbol ≈ altura × seq_page_cost = 4 × 1.0 = 4.0
  páginas_heap ≈ N_filas × (1 - correlación) ≈ 1,000 (peor caso: todas distintas)
  = 4.0 + 4.0 × 1,000 + 0.01 × 1,000 = 4 + 4,000 + 10 = 4,014
  → El planner elige Index Scan (4,014 < 20,000)

Si la selectividad fuera 15% = 150,000 filas:
  páginas_heap se clampa a 10,000 (no puede exceder páginas totales de la tabla)
  = 4 + 4.0 × 10,000 + 0.01 × 150,000 = 4 + 40,000 + 1,500 = 41,504
  → Seq Scan (20,000) < Index Scan (41,504) → el planner elige Seq Scan ✓
```

**El problema de `random_page_cost = 4.0` en SSDs**: ese default está calibrado para HDD,
donde el acceso aleatorio es realmente ~4× más caro que el secuencial. En SSD NVMe la
diferencia real es de 2-3×, no 4×, así que el planner sobreestima el costo del I/O aleatorio y
puede elegir Seq Scan cuando un índice sería más rápido:

```sql
ALTER SYSTEM SET random_page_cost = 1.1;  -- ajuste recomendado para SSD
SELECT pg_reload_conf();
```

**El mismo problema, con otro nombre, en cada motor.** Todos los CBO modernos necesitan una
noción del costo relativo de I/O aleatorio vs. secuencial, aunque no la expongan igual de
directo:

| Motor      | Dónde vive el equivalente de `random_page_cost`                                              |
| ---------- | ----------------------------------------------------------------------------------------------- |
| PostgreSQL | `random_page_cost` / `seq_page_cost` (parámetros explícitos, editables)                          |
| MySQL      | Tablas `mysql.server_cost` y `mysql.engine_cost` (desde 5.7), con costos configurables por engine |
| SQL Server | Cardinality Estimator interno (propietario, no documentado en detalle); se puede influir con `sys.dm_db_index_physical_stats` y hints, no con un parámetro directo de costo de I/O |
| Oracle     | System statistics vía `DBMS_STATS.GATHER_SYSTEM_STATS`, que capturan `sreadtim`/`mreadtim` (tiempos reales de lectura secuencial vs. aleatoria medidos en el hardware real) |
| SQLite     | Estimador simple basado en `ANALYZE` (`sqlite_stat1`/`sqlite_stat4`); mucho menos sofisticado, sin distinción explícita de costo aleatorio vs. secuencial |

Oracle es el más "científico" en este punto: en vez de un parámetro fijo como PostgreSQL,
mide directamente en el hardware real cuánto tarda un I/O secuencial vs. uno aleatorio y
alimenta esos números al CBO.

---

### Sección 3.2: Estadísticas en PostgreSQL: pg_stats, histogramas, correlación {#3-estadisticas}

```sql
ANALYZE productos;             -- recalcula estadísticas de esta tabla
VACUUM ANALYZE productos;      -- vacuum + analyze juntos
```

PostgreSQL toma una muestra aleatoria (tamaño controlado por `default_statistics_target`) y
calcula, por columna:

```sql
SELECT attname, n_distinct, null_frac, avg_width, correlation,
       most_common_vals, most_common_freqs, histogram_bounds
FROM pg_stats
WHERE tablename = 'pedidos' AND attname = 'fecha';
```

- **`n_distinct`**: número de valores únicos. Positivo = cantidad exacta; negativo = fracción
  del total (`-1.0` = todas las filas son distintas). El planner estima selectividad de una
  igualdad como `1 / n_distinct`.
- **Most Common Values (MCV)**: para columnas con pocos valores muy frecuentes
  (`most_common_vals = ['México','US','Colombia']`, `most_common_freqs = [0.42, 0.31, 0.12]`),
  el planner usa la frecuencia real de ese valor en vez de asumir distribución uniforme.
- **Histogramas**: para predicados de rango (`BETWEEN`, `<`, `>`), PostgreSQL divide el rango
  de valores en buckets de igual frecuencia (`histogram_bounds`) e interpola.
- **Correlación**: `1.0` = datos perfectamente ordenados en el heap según esa columna; `0.0` =
  orden aleatorio. Es la variable que determina cuántos heap fetches del Index Scan
  (Sección 1.2) van a caer en la misma página vs. en páginas distintas.

```sql
SELECT attname, correlation FROM pg_stats
WHERE tablename = 'pedidos' ORDER BY abs(correlation) DESC;
```

**El problema de asumir independencia entre columnas.** Por defecto, el planner asume que las
columnas no están correlacionadas entre sí: estima la selectividad combinada de
`WHERE pais = 'México' AND ciudad = 'CDMX'` multiplicando las selectividades individuales,
como si fueran eventos independientes. Pero `ciudad = 'CDMX'` casi siempre implica
`pais = 'México'` — no son independientes — y esa suposición incorrecta puede llevar a
estimaciones de filas muy alejadas de la realidad, y con ellas a planes equivocados.
PostgreSQL permite corregirlo con estadísticas extendidas:

```sql
CREATE STATISTICS stats_pais_ciudad (dependencies, ndistinct, mcv)
    ON pais, ciudad FROM usuarios;
ANALYZE usuarios;
-- 'dependencies' captura que ciudad determina pais con alta probabilidad
-- 'ndistinct' captura cuántos valores distintos hay en la COMBINACIÓN (pais, ciudad)
-- 'mcv' captura las combinaciones más comunes de (pais, ciudad) juntas
```

Cuándo forzar el planner para depurar o corregir estadísticas desactualizadas:

```sql
ALTER TABLE pedidos ALTER COLUMN fecha SET STATISTICS 500;  -- default: 100, más muestra
ANALYZE pedidos;

EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM pedidos WHERE fecha > '2024-01-01';
-- si 'rows' estimado difiere mucho de 'actual rows', las estadísticas están desactualizadas

SET enable_seqscan = OFF;   -- fuerza al planner a usar un índice si existe (solo para depurar)
EXPLAIN SELECT * FROM tabla WHERE campo = 'valor';
RESET enable_seqscan;
```

---

### Sección 3.3: Estadísticas en otros motores {#3-estadisticas-motores}

Todos los CBO necesitan la misma información básica (cardinalidad, distribución de valores,
correlación), pero la profundidad y la forma de recolectarla varían bastante:

| Motor      | Comando de recolección                                        | Histogramas                                     | Multivariadas / correlación entre columnas             |
| ---------- | --------------------------------------------------------------- | ------------------------------------------------- | --------------------------------------------------------- |
| PostgreSQL | `ANALYZE`                                                        | Sí, de igual frecuencia                            | `CREATE STATISTICS` (dependencies, ndistinct, mcv)         |
| MySQL      | `ANALYZE TABLE`                                                   | Desde 8.0, vía `ANALYZE TABLE ... UPDATE HISTOGRAM` | No hay un equivalente directo a `CREATE STATISTICS`         |
| SQL Server | Automático (`AUTO_CREATE_STATISTICS`/`AUTO_UPDATE_STATISTICS`) o manual con `CREATE STATISTICS` | Sí, hasta 200 pasos, visibles con `DBCC SHOW_STATISTICS` | Estadísticas multi-columna nativas al crear el índice compuesto |
| Oracle     | `DBMS_STATS.GATHER_TABLE_STATS`                                   | Sí (frequency, height-balanced, hybrid según el caso) | `DBMS_STATS.CREATE_EXTENDED_STATS` (equivalente directo al `CREATE STATISTICS` de PostgreSQL) |
| SQLite     | `ANALYZE` (llena `sqlite_stat1`, opcionalmente `sqlite_stat4` con histogramas) | Limitado, solo con `sqlite_stat4` habilitado         | No soportado                                              |

Un dato práctico: antes de InnoDB persistir estadísticas en disco (`innodb_stats_persistent`,
default desde 5.6), MySQL recalculaba estimaciones de cardinalidad a partir de una muestra
pequeña de páginas (`innodb_stats_sample_pages`, default 8) en cada reinicio o cuando
cambiaba el tamaño de la tabla, lo que producía planes inconsistentes entre ejecuciones. Con
estadísticas persistentes, ese problema se reduce, pero la granularidad sigue siendo más
tosca que la de PostgreSQL u Oracle (no hay histogramas reales hasta MySQL 8.0). SQL Server y
Oracle son, en la práctica, los dos motores con la maquinaria estadística más completa de los
cuatro.

---

### Sección 3.4: EXPLAIN y EXPLAIN ANALYZE en PostgreSQL, a fondo {#3-explain}

```sql
EXPLAIN SELECT ...                          -- plan ESTIMADO (no ejecuta la query)
EXPLAIN ANALYZE SELECT ...                  -- EJECUTA la query, plan + estadísticas reales
EXPLAIN (ANALYZE, BUFFERS) SELECT ...       -- + información de I/O (hits/reads)
EXPLAIN (ANALYZE, FORMAT JSON) SELECT ...   -- para herramientas como explain.dalibo.com
```

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.nombre, COUNT(pe.id) AS total_pedidos
FROM productos p
JOIN pedidos pe ON pe.producto_id = p.id
WHERE p.categoria = 'Electrónica'
GROUP BY p.id, p.nombre;

-- HashAggregate  (cost=15234.56..15334.56 rows=10000 width=72)
--               (actual time=234.123..235.456 rows=8943 loops=1)
--   Buffers: shared hit=2341 read=1234
--   → Hash Join  (cost=4234.00..14234.00 rows=80000 width=44)
--               (actual time=45.234..198.123 rows=89430 loops=1)
--       → Seq Scan on pedidos pe  (cost=0.00..9000.00 rows=500000 width=12)
--                                 (actual time=0.012..87.234 rows=500000 loops=1)
--       → Hash  (cost=3984.00..3984.00 rows=20000 width=36)
--             → Index Scan using idx_cat on productos p
--                              (cost=0.42..3984.00 rows=20000 width=36)
--                              (actual time=0.234..38.123 rows=19234 loops=1)
--                 Index Cond: ((categoria)::text = 'Electrónica'::text)
```

Cómo leer cada campo:

- `cost=X..Y`: `X` es el costo de arrancar (antes de la primera fila), `Y` el costo total.
  Unidades arbitrarias, basadas en `seq_page_cost = 1.0` como referencia.
- `rows=N` / `width=N`: filas y ancho promedio **estimados** por ese nodo.
- `actual time=X..Y` / `actual rows=N` / `loops=N`: solo con `ANALYZE` — tiempo real en ms y
  filas reales, multiplicado por cuántas veces se ejecutó el nodo.
- `Buffers: shared hit=N` (desde `shared_buffers`, barato) vs. `shared read=N` (desde disco,
  caro).
- Si `rows` estimado difiere mucho de `actual rows`: las estadísticas están desactualizadas
  (`ANALYZE` pendiente) y el planner está tomando decisiones sobre datos incorrectos.

| Nodo               | Qué hace                                                     | Bueno cuando                                  | Malo cuando            |
| ------------------- | -------------------------------------------------------------- | ------------------------------------------------ | ------------------------- |
| Nested Loop         | por cada fila del lado izquierdo, busca en el derecho          | el lado derecho es pequeño y tiene índice        | el derecho es grande (O(n²)) |
| Hash Join           | construye tabla hash del lado menor, escanea el mayor           | ninguno de los dos lados está ordenado           | poca memoria (`work_mem`) para la tabla hash |
| Merge Join          | intercala dos lados ya ordenados, como un merge sort            | ambos lados tienen índice en la columna de join, o ya vienen de un Sort | uno de los lados no está ordenado |
| Sort                | ordena el resultado (ORDER BY, Merge Join, etc.)                 | cabe en `work_mem`                                | debe hacer sort en disco  |
| Hash Aggregate      | GROUP BY usando tabla hash                                       | cardinalidad de grupos moderada                   | demasiados grupos distintos |
| Gather              | recopila resultados de workers paralelos                        | Parallel Seq/Index Scan disponible                | overhead de coordinación domina |

**JIT compilation**: para queries costosas, PostgreSQL puede compilar expresiones a código
nativo con LLVM (`SHOW jit`, umbral en `jit_above_cost`). Reduce el overhead de interpretación
en queries analíticas grandes, pero añade latencia de compilación en queries cortas — por eso
el umbral existe.

---

### Sección 3.5: Leer planes de ejecución en otros motores {#3-explain-motores}

| Motor      | Plan estimado                          | Plan con ejecución real                                         |
| ---------- | ------------------------------------------ | -------------------------------------------------------------------- |
| PostgreSQL | `EXPLAIN`                                   | `EXPLAIN ANALYZE` / `EXPLAIN (ANALYZE, BUFFERS)`                       |
| MySQL      | `EXPLAIN`                                   | `EXPLAIN ANALYZE` (desde 8.0.18) o `EXPLAIN FORMAT=JSON` con métricas de costo |
| SQL Server | `SET SHOWPLAN_XML ON` / plan estimado en SSMS | "Include Actual Execution Plan" + `SET STATISTICS IO, TIME ON`          |
| Oracle     | `EXPLAIN PLAN FOR ...` + `SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY)` | `DBMS_XPLAN.DISPLAY_CURSOR` (plan real de una ejecución ya ocurrida)   |
| SQLite     | `EXPLAIN QUERY PLAN`                        | No distingue estimado vs. real — no hay motor de estadísticas de ejecución tan rico como los anteriores |

```sql
-- MySQL
EXPLAIN SELECT * FROM pedidos WHERE cliente_id = 42;
-- id | select_type | table   | type | possible_keys | key         | rows | Extra
-- 1  | SIMPLE      | pedidos | ref  | idx_cliente   | idx_cliente | 12   | Using index condition

-- SQL Server
SET STATISTICS IO ON;
SELECT * FROM pedidos WHERE cliente_id = 42;
-- Table 'pedidos'. Scan count 1, logical reads 4, physical reads 0 ...

-- Oracle
EXPLAIN PLAN FOR SELECT * FROM pedidos WHERE cliente_id = 42;
SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY);
-- Id  Operation                   Name          Rows   Cost
-- 0   SELECT STATEMENT                          12     4
-- 1    TABLE ACCESS BY INDEX ROWID PEDIDOS       12     4
-- 2     INDEX RANGE SCAN            IDX_CLIENTE   12     2

-- SQLite
EXPLAIN QUERY PLAN SELECT * FROM pedidos WHERE cliente_id = 42;
-- SEARCH pedidos USING INDEX idx_cliente (cliente_id=?)
```

Una diferencia importante de SQL Server sobre el resto: históricamente ofreció dos
Cardinality Estimators distintos (el "legacy CE" de SQL Server 2008 y el "nuevo CE" desde
2014), y el nivel de compatibilidad de la base de datos (`ALTER DATABASE ... SET
COMPATIBILITY_LEVEL`) puede cambiar qué estimador usa el optimizer — la misma query puede
recibir estimaciones de filas distintas, y por lo tanto planes distintos, solo por ese
parámetro. SQL Server también tiene **Query Store**, que graba el historial de planes
ejecutados y su desempeño real a lo largo del tiempo — útil para detectar cuándo un plan
"se rompió" después de un cambio de estadísticas o de un `ALTER INDEX`, algo que ni
PostgreSQL ni MySQL exponen de forma nativa.

---

## Bloque 4: Diseñando índices compuestos y especializados {#4-diseno}

---

### Sección 4.1: Índices compuestos y la regla del leftmost prefix {#4-compuestos}

```sql
CREATE INDEX idx_a_b ON tabla (a, b);
```

Un índice compuesto en `(a, b)` está ordenado primero por `a`, y dentro de cada valor de `a`,
por `b`. Eso determina qué queries pueden usarlo:

- **Sí usan el índice**: `WHERE a = 'x'` (solo el prefijo), `WHERE a = 'x' AND b = 'y'`
  (ambas columnas), `WHERE a BETWEEN 1 AND 10` (rango en `a`, sin poder usar `b` para acotar
  más), `ORDER BY a, b` cuando ya se filtra por `a`.
- **No usan el índice**: `WHERE b = 'y'` (sin filtrar por `a` primero), o cualquier condición
  que empiece por una columna que no sea la primera del índice.

Esta es la **regla del leftmost prefix**: un índice `(a, b, c)` sirve para predicados que
usan `a`, o `a` y `b`, o `a`, `b` y `c` — siempre empezando desde la izquierda — pero nunca
para un predicado que empiece por `b` o por `c` sin `a`. No es una política de PostgreSQL en
particular: es una propiedad estructural de cómo se ordenan las claves en cualquier B-Tree, y
por eso se cumple igual en MySQL, SQL Server, Oracle y SQLite.

---

### Sección 4.2: Índices parciales / filtered indexes {#4-parciales}

```sql
-- Solo el 5% de los pedidos está 'pendiente'. Indexar todos es desperdiciar espacio.
CREATE INDEX idx_pedidos_pendientes ON pedidos (cliente_id)
    WHERE estado = 'pendiente';

-- La query debe incluir la condición del índice parcial para poder usarlo:
SELECT * FROM pedidos WHERE cliente_id = 42 AND estado = 'pendiente';
```

El índice queda ~20× más pequeño si solo cubre el 5% relevante de las filas, lo que reduce el
costo de mantenerlo en cada escritura (Sección 4.4) y su huella en cache.

**No todos los motores soportan esto igual:**

| Motor      | Soporte de índice parcial                                                          |
| ---------- | ---------------------------------------------------------------------------------- |
| PostgreSQL | Nativo: `CREATE INDEX ... WHERE condición`                                          |
| SQL Server | Nativo, misma idea con otro nombre: `CREATE INDEX ... WHERE condición` (filtered index) |
| MySQL      | **No soportado.** No existe forma nativa de indexar solo un subconjunto de filas.    |
| Oracle     | No hay sintaxis de índice parcial directa — se simula con un índice de expresión que retorna `NULL` para las filas que no interesa indexar |
| SQLite     | Nativo desde 3.8: `CREATE INDEX ... WHERE condición`                                 |

El truco de Oracle conecta directamente con la peculiaridad de la Sección 2.7 (Oracle no
almacena entradas totalmente `NULL`):

```sql
-- Oracle: "índice parcial" simulado con función-based index
CREATE INDEX idx_pendientes ON pedidos (
    CASE WHEN estado = 'pendiente' THEN cliente_id END
);
-- Para filas con estado != 'pendiente', la expresión evalúa a NULL → Oracle NO la indexa
-- Para filas con estado = 'pendiente', la expresión evalúa a cliente_id → sí se indexa
SELECT * FROM pedidos WHERE
    CASE WHEN estado = 'pendiente' THEN cliente_id END = 42;
```

MySQL no tiene ningún equivalente directo; el workaround habitual ahí es una columna generada
(`GENERATED ALWAYS AS`) combinada con un índice normal sobre esa columna, aceptando que sigue
indexando todas las filas (solo cambia qué valor se indexa, no cuántas filas).

---

### Sección 4.3: Covering indexes: INCLUDE y equivalentes {#4-covering}

```sql
-- Sin INCLUDE: 'nombre' no está en el índice → siempre necesita heap fetch
CREATE INDEX idx_email ON usuarios (email);
SELECT email, nombre FROM usuarios WHERE email = 'ana@example.com';  -- Index Scan, no Index Only

-- Con INCLUDE: 'nombre' vive en las hojas del índice, pero NO participa en el árbol
CREATE INDEX idx_email_covering ON usuarios (email) INCLUDE (nombre, creado_en);
SELECT email, nombre, creado_en FROM usuarios WHERE email = 'ana@example.com';  -- Index Only Scan
```

La diferencia entre agregar una columna como parte de la **clave** del índice o como columna
**INCLUDE**:

```sql
CREATE INDEX idx_a (col_a, col_b)          -- col_b participa en el árbol → queda ordenada
CREATE INDEX idx_b (col_a) INCLUDE (col_b) -- col_b solo en las hojas → no queda ordenada
```

| Cuándo agregar como columna CLAVE                       | Cuándo agregar con INCLUDE                                 |
| ---------------------------------------------------------- | -------------------------------------------------------------- |
| Aparece en `WHERE` o `ORDER BY`                             | Solo aparece en `SELECT`                                        |
| Necesitas que el árbol quede ordenado por esa columna       | No necesitas ordenarla, solo tenerla accesible                  |
| Haces rangos sobre ella                                     | Es una columna grande que no necesita ordenación                |
| Costo: infla los nodos internos → árbol más alto             | Ventaja: nodos internos compactos → mismo fan-out, árbol más bajo |

**Equivalentes por motor:**

- **SQL Server**: sintaxis idéntica, `CREATE INDEX ... INCLUDE (...)` — el mismo concepto,
  columnas solo en el nivel hoja del nonclustered index.
- **MySQL**: no existe una cláusula `INCLUDE`. La única forma de lograr un covering index es
  agregar las columnas extra como parte de la clave compuesta (todas ordenan el árbol, sin la
  optimización de nodos internos compactos). En InnoDB esto es menos costoso de lo que parece
  para índices secundarios, porque sus hojas ya cargan implícitamente el valor de la PK
  (Sección 1.5) — pero columnas adicionales del `SELECT` sí hay que declararlas explícitamente
  en la clave para lograr `Using index`.
- **Oracle**: tampoco distingue key vs. included columns — para un covering index se agregan
  todas las columnas necesarias como parte de la clave del índice, igual que en MySQL.
- **SQLite**: no soporta `INCLUDE`; mismo patrón que MySQL/Oracle, todas las columnas van en
  la clave compuesta.

En la práctica, PostgreSQL y SQL Server son los únicos dos motores de esta lista que permiten
separar "qué ordena el árbol" de "qué vive en la hoja" — en el resto, un covering index siempre
paga el costo de un árbol más alto si agrega muchas columnas extra.

---

### Sección 4.4: El costo de mantener índices en escrituras {#4-costo-escritura}

```sql
INSERT INTO pedidos (cliente_id, producto_id, fecha, estado, total) VALUES (...);
-- Con 5 índices en 'pedidos':
--   1. Insertar la fila en el heap
--   2-6. Actualizar los B+Tree de idx_pk, idx_cliente, idx_fecha, idx_estado, idx_total
-- → 6 estructuras que mantener por cada escritura
```

```sql
SELECT indexname FROM pg_indexes WHERE tablename = 'pedidos';
```

**Una mitigación específica de MySQL InnoDB que vale la pena conocer**: el **change buffer**.
Cuando se actualiza un índice secundario **no único** y la página correspondiente no está en
el buffer pool, InnoDB no la trae de disco de inmediato — encola el cambio en el change buffer
(en memoria y en disco) y lo aplica en batch la próxima vez que esa página se lea de todas
formas, o durante el proceso en background. Esto convierte varias escrituras aleatorias
potenciales en una sola lectura futura, reduciendo el costo de mantener índices secundarios en
cargas de escritura intensiva. No aplica a índices únicos (ahí InnoDB necesita verificar
unicidad de inmediato, así que no puede diferir el chequeo).

---

### Sección 4.5: Índices redundantes: detectarlos antes de que cuesten {#4-redundantes}

Por la regla del leftmost prefix (Sección 4.1), un índice `(a)` es completamente redundante si
ya existe un índice `(a, b)`: cualquier query que pudiera usar `(a)` también puede usar el
prefijo de `(a, b)`. Mantener ambos solo duplica el costo de escritura de la Sección 4.4 sin
ganar nada en lectura.

```sql
-- Redundante: idx_solo_a nunca aporta nada que idx_a_b no cubra ya
CREATE INDEX idx_a_b   ON tabla (a, b);
CREATE INDEX idx_solo_a ON tabla (a);
```

- **PostgreSQL** no tiene una vista nativa para detectarlo automáticamente; se compara
  manualmente la definición de los índices (`pg_indexes`) buscando prefijos duplicados, o se
  usa una extensión de terceros.
- **MySQL** sí trae una vista lista para esto en el schema `sys`:
  `sys.schema_redundant_indexes`, que lista exactamente los pares índice-redundante /
  índice-que-lo-cubre.
- **SQL Server** no tiene una vista dedicada, pero se puede construir la misma detección
  cruzando `sys.indexes` y `sys.index_columns` por prefijo de columnas.

Y para el caso de índices que sí son necesarios estructuralmente pero nunca se usan en la
práctica, ver la Sección 7.4.

---

## Bloque 5: Tipos de índice más allá del B+Tree {#5-tipos-indice}

---

### Sección 5.1: Hash indexes {#5-hash}

Un hash index resuelve **solo igualdad** (`=`), nunca rangos ni `ORDER BY`, a cambio de una
búsqueda O(1) en vez de O(log n).

- **PostgreSQL** soporta `CREATE INDEX ... USING hash`. Antes de la versión 10 no eran
  WAL-logged (no sobrevivían un crash de forma confiable), lo cual los hizo poco populares;
  desde PG10 son seguros, pero en la práctica casi nunca superan a un B-Tree lo suficiente
  como para justificar perder soporte de rangos.
- **MySQL**: el motor `MEMORY` sí soporta hash indexes explícitos para tablas en RAM. Pero el
  hash index más relevante en MySQL es interno y transparente: el **Adaptive Hash Index (AHI)**
  de InnoDB, que detecta páginas de un B-Tree consultadas muy frecuentemente por igualdad y
  construye automáticamente una tabla hash en memoria apuntando directo a esas páginas del
  buffer pool, acelerando búsquedas repetidas sin que el usuario declare nada
  (`innodb_adaptive_hash_index`, se puede desactivar si genera contención en `mutex` bajo alta
  concurrencia).
- **SQL Server**: no hay hash index para tablas normales en disco; sí existen para tablas
  memory-optimized (In-Memory OLTP), como estructura de acceso alternativa al B-Tree.
- **Oracle** y **SQLite**: no exponen un tipo de índice hash como estructura declarable por el
  usuario.

---

### Sección 5.2: Full-text e inverted indexes: GIN vs FULLTEXT vs Oracle Text {#5-fulltext}

Ya se mencionó en la Sección 2.4 que buscar substrings o palabras dentro de texto libre
necesita una estructura distinta al B-Tree: un **índice invertido**, que mapea cada palabra (o
fragmento) hacia la lista de filas donde aparece — el mismo principio que el índice de un
libro.

| Motor      | Estructura                              | Qué indexa                                                          |
| ---------- | ------------------------------------------ | ------------------------------------------------------------------------ |
| PostgreSQL | GIN (Generalized Inverted Index)            | `tsvector` (full-text), arrays, JSONB (`@>`, `?`, `?|`)                    |
| PostgreSQL | GiST                                         | Rangos (`tstzrange`), geometría, `EXCLUDE` constraints, también full-text (más compacto, más lento en lookup que GIN) |
| MySQL      | `FULLTEXT`                                   | Texto libre, modo natural language o boolean, sobre InnoDB o MyISAM        |
| SQL Server | Full-Text Search (catálogo separado)          | `CONTAINS`/`FREETEXT` sobre columnas de texto                              |
| Oracle     | Oracle Text (`CONTEXT`, `CTXCAT`)             | Búsqueda de texto completo, con su propio motor de indexación              |
| SQLite     | `FTS5` (virtual table)                       | Full-text search embebido                                                  |
| MongoDB    | Text index                                   | Búsqueda de texto sobre uno o varios campos string                         |

La elección entre GIN y GiST en PostgreSQL es un trade-off clásico: GIN es más rápido para
lookup pero más lento (y más grande) para construir/actualizar; GiST es al revés — más barato
de mantener, algo más lento de consultar. Para datos que casi no cambian (catálogos, tags),
GIN suele ganar; para datos que se actualizan constantemente, GiST puede salir más barato en
conjunto.

---

### Sección 5.3: Bitmap indexes de Oracle, a fondo {#5-oracle-bitmap}

Ya se mencionó en la Sección 1.4 que el "Bitmap Index Scan" de PostgreSQL es una operación de
plan sobre B-Trees normales. Oracle tiene, además, un **tipo de índice** que es literalmente
un bitmap persistente en disco — pensado para exactamente el problema que motivó la Sección
21 original de este archivo: muchas columnas de baja cardinalidad consultadas en combinación.

```sql
CREATE TABLE productos (
    id BIGINT PRIMARY KEY,
    color TEXT, talla TEXT, material TEXT, temporada TEXT
);

-- Oracle
CREATE BITMAP INDEX idx_color ON productos (color);
CREATE BITMAP INDEX idx_talla ON productos (talla);
```

```
Estructura (color con 4 valores posibles: rojo, azul, verde, negro):

  color='rojo'  → bitmap: [1,0,0,1,0,1,0,0,1,0, ...]  (1 bit por fila de la tabla)
  color='azul'  → bitmap: [0,1,0,0,1,0,0,1,0,0, ...]
  color='verde' → bitmap: [0,0,1,0,0,0,1,0,0,1, ...]
  color='negro' → bitmap: [0,0,0,0,0,0,0,0,0,0, ...]

WHERE color='rojo' AND talla='M':
  AND bit a bit entre el bitmap de color='rojo' y el bitmap de talla='M'
  → el resultado es directamente el conjunto de filas que cumplen ambas condiciones
  → extremadamente barato en CPU, y el bitmap comprime muy bien cuando hay pocos valores distintos
```

Esto es exactamente el problema que la Sección 21 original resolvía con Bloom Filters — pero
el bitmap index de Oracle es la solución que de verdad se usa en producción para este patrón,
porque es exacta (no probabilística) y nativa del motor, no una extensión.

**La precaución que hace que casi nadie use bitmap indexes en OLTP**: un bit del bitmap
representa una fila, pero el **lock** que Oracle toma al modificar esa fila no es a nivel de
bit — es a nivel del **segmento de bitmap completo** que contiene ese bit, el cual puede cubrir
cientos o miles de filas. Una sola `UPDATE`/`INSERT`/`DELETE` sobre una fila con una columna
bitmap-indexada puede bloquear efectivamente a todas las demás filas que comparten ese
segmento, hasta que la transacción termine. En un sistema OLTP con escrituras concurrentes
frecuentes sobre esas columnas, esto genera contención severa. Por eso la regla práctica es:

- ✓ Usar bitmap index cuando: la columna tiene baja cardinalidad, la carga es
  predominantemente de lectura (data warehouse, reportes, esquemas estrella con dimensiones de
  baja cardinalidad), y las cargas de escritura son en batch (ETL nocturno), no OLTP continuo.
- ✗ Evitarlo cuando: hay escrituras concurrentes frecuentes sobre la columna indexada — ahí un
  B-Tree normal, aunque menos compacto, no paga ese costo de locking de rango.

---

### Sección 5.4: Bloom Filters como índice {#5-bloom}

```sql
CREATE TABLE productos (
    id BIGSERIAL PRIMARY KEY,
    color TEXT, talla TEXT, material TEXT, pais TEXT, temporada TEXT
);
SELECT * FROM productos
WHERE color='rojo' AND talla='M' AND material='algodón' AND temporada='verano';
```

Un índice compuesto `(color, talla, material, temporada)` tendría baja selectividad en las
primeras columnas (pocos valores posibles) y el árbol sería poco eficiente. `BitmapAnd` de
cuatro índices separados funciona, pero implica cuatro recorridos de árbol.

```
Bloom Filter de m bits y k funciones hash:

Estado inicial: [0,0,0,0,0,0,0,0,0,0]

INSERT 'rojo': hash1=2, hash2=5, hash3=8 → bits 2,5,8 = 1
INSERT 'azul': hash1=1, hash2=5 (colisión, ok), hash3=7 → bits 1,5,7,8 = 1 (acumulado)

LOOKUP 'rojo': bits 2,5,8 están en 1 → "probablemente está" (puede ser false positive)
LOOKUP 'verde': hash1('verde')=3 → bit 3 = 0 → DEFINITIVAMENTE no está (false negative imposible)
```

El Bloom Filter nunca dice "no está" cuando sí cumple (correctitud garantizada); solo puede
decir "puede estar" cuando en realidad no cumple (false positive, que el motor descarta con un
heap fetch de verificación — cuesta tiempo, no corrección).

```sql
CREATE EXTENSION bloom;
CREATE INDEX idx_bloom ON productos
    USING bloom (color, talla, material, temporada)
    WITH (length=80, col1=2, col2=2, col3=3, col4=2);
```

**Cuándo usar Bloom Filter vs. bitmap index de Oracle vs. B-Tree compuesto**, para el mismo
problema de "muchas columnas de baja cardinalidad en AND":

| Motor / estructura                  | Cuándo es la opción realista                                                          |
| ------------------------------------- | ------------------------------------------------------------------------------------------ |
| PostgreSQL: BitmapAnd de índices normales | Primera opción por defecto — no requiere extensión, funciona bien hasta selectividades medias |
| PostgreSQL: extensión `bloom`          | Caso de nicho: muchísimas columnas de baja cardinalidad combinadas, tabla enorme, espacio en disco es la restricción dura |
| Oracle: bitmap index                   | La solución estándar de producción para este patrón en Oracle, casi siempre en esquemas de solo lectura (data warehouse) |
| MySQL / SQL Server                     | No tienen equivalente nativo — se resuelve con índice compuesto aceptando la baja selectividad de las primeras columnas, o con `index merge`/hints |

---

### Sección 5.5: Índices geoespaciales {#5-geoespacial}

Búsquedas de "¿qué puntos caen dentro de esta área?" no son ni igualdad ni rango simple —
necesitan una estructura que particione el espacio en dos o más dimensiones. La familia
técnica más común es el **R-tree** (agrupa geometrías por su bounding box, de forma recursiva,
similar en espíritu a un B+Tree pero para rectángulos en vez de valores escalares).

| Motor      | Estructura                          |
| ---------- | --------------------------------------- |
| PostgreSQL | GiST (con la extensión PostGIS para tipos geométricos completos) |
| MySQL      | `SPATIAL` index (R-tree), sobre InnoDB o MyISAM                   |
| SQL Server | Índices espaciales sobre tipos `geometry`/`geography`             |
| Oracle     | Oracle Spatial (`SDO_GEOMETRY`)                                    |
| MongoDB    | `2dsphere` (esférico, coordenadas geográficas) / `2d` (plano)      |

Este archivo no profundiza más en geoespacial porque es, en la práctica, una especialidad
propia (con su propio motor de tipos y operadores); lo relevante para el tema de índices es
solo reconocer que existe una tercera familia estructural (además de B-Tree e inverted index)
para este tipo de consulta.

---

### Sección 5.6: Índices en NoSQL: DynamoDB y MongoDB {#5-nosql}

El ángulo aquí es puramente estructural — el porqué de modelar accesos con GSI/LSI ya se cubre
en `guia_transacciones_db.md`.

**DynamoDB**: la clave primaria (partition key + opcional sort key) **no** es un B+Tree — es
una tabla hash sobre la partition key. Buscar por partition key es O(1) por hashing, no O(log
n) por recorrido de árbol. Dentro de una misma partition key, los items sí están ordenados
físicamente por el sort key, lo que permite rangos eficientes (`BETWEEN`, `begins_with`) — pero
solo dentro de esa partición; no existe un recorrido ordenado que cruce particiones distintas,
porque no hay un árbol global que las conecte. Un GSI (Global Secondary Index) o LSI (Local
Secondary Index) es, estructuralmente, otra tabla hash+rango con su propia partition key,
mantenida como copia asíncrona (GSI) o síncrona dentro de la misma partición física (LSI) —
no un índice secundario sobre un árbol compartido como en un motor relacional.

**MongoDB**: el índice por defecto sobre `_id` **sí** es un B-Tree (ver el comparativo de
`04_almacenamiento_motores_relacionales.md`, que ya lo ubica junto a PostgreSQL/MySQL/SQL
Server). Sobre esa misma base de B-Tree, MongoDB agrega variantes:

- **Compound index**: mismo concepto y misma regla de leftmost prefix que un índice compuesto
  relacional (Sección 4.1) — `{a: 1, b: 1}` sirve para queries por `a`, o por `a` y `b`, no
  para queries solo por `b`.
- **Multikey index**: cuando el campo indexado es un array, MongoDB crea **una entrada de
  índice por cada elemento del array**, no una por documento. Un documento con un array de 5
  tags genera 5 entradas en el índice apuntando al mismo documento.
- **Text index**: índice invertido para búsqueda de texto libre, mismo principio que GIN/
  FULLTEXT (Sección 5.2).
- **Geospatial** (`2dsphere`/`2d`): mismo principio de la Sección 5.5.
- **Hashed index**: hashea el valor del campo antes de indexarlo. Su uso más importante es
  como shard key: distribuye escrituras uniformemente entre shards aunque el valor original
  sea monótonamente creciente (un `ObjectId` o timestamp), evitando el mismo problema de
  "hotspot de escritura en un extremo del árbol" que un UUID v4 o un ID secuencial le causan a
  un B+Tree (ver Sección 6.4 — es el mismo problema, resuelto con la solución opuesta:
  aleatorizar a propósito en vez de evitar la aleatoriedad).
- **Wildcard index**: indexa dinámicamente todos los campos (o un subconjunto por patrón) de
  documentos con esquema variable, sin tener que declarar cada campo por adelantado —
  relevante justamente porque MongoDB no fuerza un esquema fijo.

---

## Bloque 6: Escala: tablas de mil millones de filas {#6-escala}

---

### Sección 6.1: El límite del B+Tree a escala {#6-limite}

```
Tabla con 1,000,000,000 filas, columna INTEGER (4 bytes):
  Factor de ramificación ≈ 682 hijos por nodo
  Altura ≈ log₆₈₂(1,000,000,000) ≈ 4

  Búsqueda por PK: 4 accesos al árbol + 1 heap fetch = 5 I/Os → aceptable

  El problema real es el TAMAÑO:
    N_hojas ≈ 1B / 682 ≈ 1.47M nodos hoja
    Tamaño del índice ≈ 1.47M × 8KB ≈ 11.8 GB   ← solo un índice
    La tabla en sí: 1B filas × 100 bytes ≈ 93 GB

  Con 5 índices en esa tabla: 5 × ~12GB = 60GB solo en índices
  → cachear todos esos árboles en RAM es prohibitivo
  → cada escritura mantiene 5 árboles de 12GB → mucho I/O
```

---

### Sección 6.2: BRIN: Block Range Index {#6-brin}

BRIN **no** es un B+Tree — indexa **rangos de páginas** del heap, no filas individuales.

```
Range 1 (páginas 0-127):   min=2024-01-01, max=2024-01-03
Range 2 (páginas 128-255): min=2024-01-03, max=2024-01-07
Range 3 (páginas 256-383): min=2024-01-07, max=2024-01-15

WHERE fecha = '2024-01-05':
  → BRIN dice "puede estar en Range 2 (páginas 128-255)"
  → Escanear solo esas 128 páginas, no las 10,000 totales

Tamaño: un B+Tree de 1B filas ≈ 12GB. Un BRIN del mismo dato ≈ 128KB (~100,000× más pequeño).
```

```sql
CREATE INDEX idx_fecha_brin ON eventos USING BRIN (creado_en)
    WITH (pages_per_range = 128);
```

| Cuándo usar BRIN                                                   | Cuándo NO usar BRIN                                            |
| --------------------------------------------------------------------- | ------------------------------------------------------------------ |
| Columna con correlación alta con el orden físico del heap               | Baja correlación (UUID, columnas actualizadas frecuentemente)       |
| Datos insertados cronológicamente (timestamps, IDs secuenciales)         | Búsquedas de alta selectividad donde importa precisión exacta (BRIN puede dar falsos positivos a nivel de rango) |
| Tablas tan grandes que un B+Tree sería prohibitivo en tamaño            |                                                                     |

---

### Sección 6.3: Particionamiento como estrategia de índices {#6-particionamiento}

El particionamiento en sí (mecánica de RANGE/LIST/HASH, partition pruning, límites prácticos)
se cubre a fondo en `06_partitioning_and_sharding.md`. Lo que importa aquí es el efecto
directo sobre los índices: cada partición tiene su **propio** conjunto de índices, físicamente
independientes.

```sql
CREATE INDEX idx_eventos_tipo ON eventos (tipo);
-- → crea automáticamente idx_eventos_2024_01_tipo, idx_eventos_2024_02_tipo, etc.
```

1B filas repartidas en 12 particiones mensuales significan árboles B+Tree de ~83M filas cada
uno (altura 3 en vez de 4), con más probabilidad de que el árbol de la partición activa quepa
completo en cache. El *partition pruning* del planner reduce aún más el trabajo: si la query
trae un filtro de fecha, el motor ni siquiera abre los índices de las particiones fuera de
rango.

---

### Sección 6.4: UUIDs en B+Tree: por qué degradan el rendimiento {#6-uuid}

```
UUID v4 = 128 bits completamente aleatorios. El árbol ordena las claves, y un UUID
aleatorio no predice en qué hoja va a caer el siguiente INSERT.
```

**Problema 1 — page splits frecuentes:**

```
Con BIGSERIAL (secuencial): cada INSERT va a la última hoja del árbol → splits
solo en el extremo derecho, fill factor real ≈ 90–95%.

Con UUID v4 (aleatorio): cada INSERT va a una hoja arbitraria, potencialmente
ya llena → split en el medio, ambas mitades quedan ~50% llenas.
fill factor real ≈ 50–70% → el índice ocupa ~el doble de espacio para los mismos datos.
```

**Problema 2 — cache invalidation:**

```
Con BIGSERIAL: solo las últimas 1-2 páginas del árbol reciben INSERTs → alta cache hit rate.
Con UUID v4: cada INSERT toca una página distinta y arbitraria → si el índice no cabe
completo en cache, cada INSERT es potencialmente un I/O a disco.
```

| Estrategia de PK (tabla de 10M filas) | Inserts/segundo | Tamaño del índice | Cache hit rate |
| ---------------------------------------- | ------------------ | -------------------- | ----------------- |
| BIGSERIAL                                | ~35,000/s           | ~200MB                | ~99%                |
| UUID v7 (time-ordered)                   | ~30,000/s           | ~250MB                | ~98%                |
| UUID v4 (aleatorio)                      | ~8,000/s            | ~400MB                | ~65%                |

La solución cuando se necesita un identificador global único sin sacrificar estas propiedades
es un **UUID ordenado por tiempo**: UUID v7 (RFC 9562, primeros 48 bits = timestamp Unix en
ms), ULID (timestamp + aleatoriedad, codificado en Base32) o CUID2. Todos comparten la misma
propiedad clave: los inserts concurrentes dentro del mismo instante caen en el mismo nodo hoja
o en hojas adyacentes, reproduciendo casi las mismas propiedades de un BIGSERIAL para el
B+Tree.

```sql
CREATE EXTENSION IF NOT EXISTS pg_uuidv7;
SELECT uuid_generate_v7();  -- 018c3f7a-85f9-7000-9b3d-5c0abf2e1234
--                                  ↑↑↑↑↑↑↑↑↑ timestamp
```

Regla práctica: BIGSERIAL cuando no se necesita unicidad global; UUID v7 cuando sí se necesita
unicidad global y el rendimiento de escritura importa; UUID v4 solo cuando el rendimiento de
escritura no importa (tablas append-only tipo log, donde nunca se busca por esa PK).

Nota de cierre del hilo abierto en la Sección 5.6: el hashed shard key de MongoDB resuelve el
problema exactamente al revés — ahí conviene *forzar* aleatoriedad para repartir escrituras
entre shards. La lección compartida es la misma: el patrón de acceso de escritura (secuencial
vs. disperso) determina qué estructura conviene, y "aleatorio" no es malo ni bueno en sí mismo,
depende de si la estructura de abajo es un árbol ordenado (B+Tree, lo penaliza) o una tabla
hash particionada (lo premia).

---

### Sección 6.5: Estrategia completa para tablas grandes {#6-estrategia}

1. Evaluar si de verdad hace falta un B+Tree: si los datos tienen alta correlación temporal y
   las búsquedas son por rango de tiempo, empezar por BRIN.
2. Usar particionamiento para acotar el tamaño de cada árbol individual.
3. Índices parciales para reducir tamaño cuando solo un subconjunto de filas se consulta con
   frecuencia (Sección 4.2).
4. Covering indexes + INCLUDE para evitar heap fetches — en tablas de cientos de GB, el heap
   fetch aleatorio suele dominar el tiempo total de la query (Sección 4.3).
5. Archivar datos históricos desprendiendo particiones viejas
   (`ALTER TABLE eventos DETACH PARTITION eventos_2022`) hacia almacenamiento más barato, en
   vez de mantenerlas indexadas indefinidamente.

---

## Bloque 7: Producción: mantenimiento y precauciones {#7-produccion}

---

### Sección 7.1: Construir índices sin bloquear producción {#7-concurrently}

```sql
CREATE INDEX idx_nuevo ON pedidos (fecha);
-- Adquiere SHARE UPDATE EXCLUSIVE: bloquea ALTER TABLE, VACUUM, otro CREATE/DROP INDEX.
-- NO bloquea SELECT/INSERT/UPDATE/DELETE, pero en tablas grandes puede tardar minutos
-- → inaceptable en producción si compite con otras operaciones de mantenimiento.
```

```sql
CREATE INDEX CONCURRENTLY idx_nuevo ON pedidos (fecha);
-- Fase 1 (rápida): registra el índice como "en construcción", empieza a mantenerlo
--   para escrituras nuevas, espera a que transacciones activas terminen.
-- Fase 2 (lenta, no bloquea escrituras): escanea y construye el árbol; toma ~2-3× más
--   tiempo que la versión normal.
-- Fase 3 (rápida): segunda pasada para capturar cambios concurrentes, marca el índice válido.
```

Riesgos y limitaciones:

```sql
-- Si falla a mitad de camino, queda un índice INVALID
SELECT indexname, indisvalid FROM pg_indexes
JOIN pg_class ON pg_class.relname = indexname WHERE tablename = 'pedidos';
DROP INDEX CONCURRENTLY idx_nuevo;  -- limpiar y reintentar

-- No se puede usar dentro de una transacción
BEGIN; CREATE INDEX CONCURRENTLY idx;  -- ERROR
COMMIT;

DROP INDEX CONCURRENTLY idx_viejo;  -- el DROP también tiene versión sin bloqueo
```

**El mismo problema, resuelto distinto en cada motor:**

| Motor      | Cómo construir/reconstruir sin bloquear escrituras                          |
| ---------- | -------------------------------------------------------------------------------- |
| PostgreSQL | `CREATE INDEX CONCURRENTLY` / `DROP INDEX CONCURRENTLY` / `REINDEX CONCURRENTLY`   |
| MySQL      | Online DDL: `ALTER TABLE ... ADD INDEX ..., ALGORITHM=INPLACE, LOCK=NONE` (InnoDB, desde 5.6) |
| SQL Server | `CREATE INDEX ... WITH (ONLINE = ON)` (Enterprise; soporte parcial en Standard desde 2019) |
| Oracle     | `CREATE INDEX ... ONLINE`                                                          |
| SQLite     | No aplica de la misma forma — motor embebido, un solo escritor a la vez            |

---

### Sección 7.2: Bloat de índices y reconstrucción {#7-bloat}

Igual que el heap (ver bloat y vacuum en `03_transacciones_concurrencia_aislamiento.md`), un
B+Tree acumula espacio muerto: cada `UPDATE`/`DELETE` dejan entradas obsoletas que `VACUUM`
puede marcar reutilizables, pero **no** compacta el archivo del índice de vuelta al sistema
operativo. Con suficientes escrituras, un índice puede terminar ocupando varias veces su
tamaño "necesario", con fill factor bajo y más niveles de los que harían falta con los datos
compactados.

| Motor      | Cómo detectar bloat                                          | Cómo reconstruir                                    |
| ---------- | ---------------------------------------------------------------- | ---------------------------------------------------- |
| PostgreSQL | Comparar tamaño real (`pg_relation_size`) vs. estimado, o extensiones como `pgstattuple` | `REINDEX INDEX idx` (bloquea) / `REINDEX INDEX CONCURRENTLY idx` (PG12+, no bloquea) |
| MySQL      | `information_schema` no expone fragmentación de índice de forma directa; se infiere por tamaño del `.ibd` vs. filas | `OPTIMIZE TABLE` (reconstruye tabla e índices; online en InnoDB moderno) |
| SQL Server | `sys.dm_db_index_physical_stats` → `avg_fragmentation_in_percent` | `< 30%`: `ALTER INDEX ... REORGANIZE` (online, liviano). `> 30%`: `ALTER INDEX ... REBUILD` (puede ser `ONLINE = ON`) |
| Oracle     | `ANALYZE INDEX ... VALIDATE STRUCTURE` + `INDEX_STATS`             | `ALTER INDEX ... REBUILD [ONLINE]`                    |

---

### Sección 7.3: Foreign keys sin índice: el gotcha silencioso {#7-fk-sin-indice}

Una foreign key exige que el motor pueda verificar rápidamente, en cada operación sobre la
tabla padre, si existen filas hijas que la referencian (para `ON DELETE`/`ON UPDATE`, o
simplemente para el chequeo de integridad). Esa verificación necesita un índice del lado
**hijo**, sobre la columna que referencia — y no todos los motores lo crean solos.

```sql
CREATE TABLE pedidos (id BIGINT PRIMARY KEY, cliente_id BIGINT REFERENCES clientes(id));
-- PostgreSQL y SQL Server: NO crean automáticamente un índice sobre pedidos.cliente_id
-- Solo garantizan que exista índice del lado del PADRE (clientes.id, por la PK/UNIQUE)

DELETE FROM clientes WHERE id = 42;
-- Sin índice en pedidos.cliente_id: el motor debe recorrer TODA la tabla pedidos
-- para verificar que ningún hijo referencia a este cliente antes de permitir el DELETE
-- → Seq Scan/Table Scan en cada DELETE/UPDATE del padre, y en muchos motores, un lock
--   de rango o de tabla más agresivo del que el usuario espera mientras dura la verificación
```

**MySQL InnoDB es la excepción**: exige un índice sobre la columna que referencia una FK y, si
no se declara uno explícitamente, **lo crea automáticamente**. Esto evita el problema por
diseño, a costa de que cada FK trae un índice implícito que hay que tener en cuenta al contar
cuántos índices mantiene la tabla (Sección 4.4).

La recomendación práctica, válida en PostgreSQL, SQL Server y Oracle: **siempre indexar
manualmente el lado hijo de cada foreign key**, salvo que esa columna ya sea el prefijo
izquierdo de otro índice existente (Sección 4.1).

---

### Sección 7.4: Índices no usados: detectarlos y eliminarlos {#7-no-usados}

Un índice que nunca se usa en lecturas solo cuesta: espacio en disco y en cache, más el costo
de mantenerlo en cada escritura (Sección 4.4).

```sql
-- PostgreSQL
SELECT schemaname, relname, indexrelname, idx_scan
FROM pg_stat_user_indexes
WHERE relname = 'pedidos' ORDER BY idx_scan ASC;
-- idx_scan = 0 → nunca se ha usado desde el último reset de estadísticas → candidato a DROP
```

```sql
-- MySQL: vista lista en el schema 'sys'
SELECT * FROM sys.schema_unused_indexes;

-- SQL Server: cruzar los índices existentes contra su historial de uso
SELECT i.name, s.user_seeks, s.user_scans, s.user_lookups
FROM sys.indexes i
LEFT JOIN sys.dm_db_index_usage_stats s
    ON i.object_id = s.object_id AND i.index_id = s.index_id
WHERE s.user_seeks IS NULL AND s.user_scans IS NULL;  -- nunca consultado
```

Precaución antes de eliminar: estas estadísticas suelen resetearse en un reinicio del motor
(o al reconstruir el índice), así que un `idx_scan = 0` en un servidor que reinició hace una
hora no prueba nada — hay que confirmar contra una ventana de tiempo que cubra el ciclo
completo de uso real (incluyendo picos mensuales, reportes de fin de mes, etc.) antes de
eliminar un índice en producción.

---

### Sección 7.5: Checklist: por qué mi query no usa el índice {#7-checklist}

```
¿Existe el índice y cubre la(s) columna(s) del predicado?
  NO → crear el índice (Bloque 4)
  SÍ ↓

¿El predicado tiene una función/expresión sobre la columna? (§2.2)
  SÍ → crear índice de expresión, o reescribir el predicado
  NO ↓

¿Hay un cast implícito entre el tipo de la columna y el del literal/parámetro? (§2.3)
  SÍ → castear el literal explícitamente, o alinear tipos
  NO ↓

¿Es un LIKE con wildcard al inicio ('%texto')? (§2.4)
  SÍ → un B-Tree no puede resolverlo; usar trigram/full-text/FTS según el motor
  NO ↓

¿Es un OR donde alguna rama cae en una columna sin índice? (§2.5)
  SÍ → indexar esa columna, o reescribir como UNION de dos queries indexadas
  NO ↓

¿Es una negación (<>, NOT IN, NOT LIKE) sobre una condición poco selectiva? (§2.6)
  SÍ → es una decisión de costo válida del planner, no un bug — confirmar con EXPLAIN (§3.4)
  NO ↓

¿Es un IS NULL en Oracle sobre un índice de una sola columna? (§2.7)
  SÍ → Oracle no almacena filas totalmente NULL; agregar columna NOT NULL al índice
  NO ↓

Selectividad real de la condición, vs. lo que el planner ESTIMA (EXPLAIN, §3.4):
  ¿'rows' estimado muy distinto de 'actual rows'? → estadísticas desactualizadas, correr ANALYZE
  ¿La condición retorna >15% de la tabla? → el planner probablemente tiene razón al usar Seq Scan
```

---

## Bloque 8: Comparación final: qué índice usar y cuándo {#8-final}

---

### Sección 8.1: Tabla comparativa por motor {#8-comparacion}

| Tipo de índice          | Estructura                        | Motores donde existe                                              | Cuándo usarlo                                                                 |
| -------------------------- | ------------------------------------ | ---------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| B-Tree / B+Tree             | Árbol balanceado                     | PostgreSQL, MySQL InnoDB, SQL Server, Oracle, SQLite, MongoDB (`_id` y compound) | Default para casi todo: `=`, `<`, `>`, `BETWEEN`, `LIKE 'x%'`, `ORDER BY`, `UNIQUE` |
| Hash                        | Tabla hash                            | PostgreSQL (`USING hash`), MySQL `MEMORY` engine, InnoDB AHI (interno, no declarable), SQL Server (solo In-Memory OLTP) | Solo igualdad exacta; raramente justifica perder soporte de rangos               |
| GIN / inverted              | Índice invertido                     | PostgreSQL (GIN)                                                        | Arrays, JSONB, full-text (`@>`, `?`, `?|`)                                        |
| GiST                        | Árbol balanceado generalizado         | PostgreSQL                                                              | Rangos, geometría, `EXCLUDE` constraints, full-text (alternativa a GIN)           |
| FULLTEXT / Full-Text Search | Índice invertido especializado        | MySQL (`FULLTEXT`), SQL Server (Full-Text Search), Oracle Text, SQLite (`FTS5`), MongoDB (text index) | Búsqueda de texto libre, no substrings arbitrarios                                |
| BRIN                        | Rangos de páginas (min/max por bloque)| PostgreSQL                                                              | Datos con alta correlación física (timestamps de inserción, series de tiempo)     |
| Bitmap index                | Bitmap persistente en disco           | Oracle                                                                   | Columnas de baja cardinalidad, consultas combinadas, carga predominantemente de lectura (evitar en OLTP con escrituras concurrentes, §5.3) |
| Bloom filter                | Filtro probabilístico compacto        | PostgreSQL (extensión `bloom`)                                          | Nicho: muchísimas columnas de baja cardinalidad combinadas, espacio en disco es la restricción dura |
| R-tree / espacial           | Partición recursiva del espacio       | PostgreSQL+PostGIS (GiST), MySQL (`SPATIAL`), SQL Server (`geometry`/`geography`), Oracle Spatial, MongoDB (`2dsphere`/`2d`) | Consultas geoespaciales (dentro de un área, cercanía)                             |
| Hash (shard key)            | Tabla hash particionada               | MongoDB (hashed index), DynamoDB (partition key)                        | Distribuir escrituras uniformemente entre shards/particiones, evitar hotspots     |
