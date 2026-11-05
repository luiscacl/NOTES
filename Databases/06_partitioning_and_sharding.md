# Particionamiento y sharding: de una tabla dentro de un nodo a datos repartidos entre servidores

## Cómo una tabla deja de caber en un solo lugar, y qué cambia cuando eso pasa

Esta guía cubre el problema completo de dividir datos que ya no caben cómodos en su forma original: primero dentro de un mismo servidor (table partitioning), después entre varios servidores (sharding), y lo que pasa cuando una operación de negocio necesita tocar más de un servidor a la vez (transacciones distribuidas). Motor de referencia para partitioning: PostgreSQL, por ser el más transparente en exponer el mecanismo, con comparaciones constantes contra MySQL InnoDB. Para sharding: MongoDB, Cassandra y DynamoDB como sistemas donde el sharding es nativo, y Vitess (sobre MySQL) y Citus (sobre PostgreSQL) como capas de sharding instaladas sobre un motor relacional. Para sistemas nacidos distribuidos de punta a punta: CockroachDB y Google Spanner.

Cada **Bloque** es un tema completo en sí mismo. Cada **Sección** dentro de un bloque es un subtema puntual de ese tema.

**Bloque 1 — Qué es table partitioning y por qué existe**

- [Sección 1.1 — Partitioning nunca cruza la red](#1-nunca-cruza-red)
- [Sección 1.2 — Cómo lo implementa PostgreSQL por dentro](#1-implementacion-pg)
- [Sección 1.3 — Declarative vs. el método viejo basado en herencia](#1-declarative-vs-legacy)

**Bloque 2 — Métodos de partitioning**

- [Sección 2.1 — RANGE](#2-range)
- [Sección 2.2 — LIST](#2-list)
- [Sección 2.3 — HASH y KEY](#2-hash-key)
- [Sección 2.4 — Composite: particionar una partición](#2-composite)

**Bloque 3 — El ciclo de vida de una fila particionada**

- [Sección 3.1 — Qué pasa físicamente en un INSERT](#3-insert-routing)
- [Sección 3.2 — Partition pruning: dónde se recupera la inversión](#3-pruning)
- [Sección 3.3 — La diferencia de pruning entre PostgreSQL y MySQL](#3-pruning-diferencia)
- [Sección 3.4 — Índices: replicados pero físicamente independientes](#3-indices)
- [Sección 3.5 — Constraints y la regla de la partition key obligatoria](#3-constraints)
- [Sección 3.6 — Mantenimiento: la otra mitad del beneficio](#3-mantenimiento)
- [Sección 3.7 — Cómo consultar una tabla particionada correctamente](#3-como-consultar)

**Bloque 4 — Límites, particiones DEFAULT y foreign keys**

- [Sección 4.1 — Particiones DEFAULT: qué son y su riesgo real](#4-default)
- [Sección 4.2 — Sub-partitioning y sus límites prácticos](#4-sub-particionamiento)
- [Sección 4.3 — Cuántas particiones son demasiadas](#4-cuantas-particiones)
- [Sección 4.4 — Foreign keys hacia y desde tablas particionadas](#4-foreign-keys)

**Bloque 5 — Migrar una tabla existente a particionada**

- [Sección 5.1 — Por qué no existe una conversión in-place](#5-no-in-place)
- [Sección 5.2 — El procedimiento estándar, paso a paso](#5-procedimiento)
- [Sección 5.3 — ATTACH PARTITION: la excepción rápida](#5-attach-partition)
- [Sección 5.4 — pg_partman: automatizar creación y retención](#5-pg-partman)

**Bloque 6 — Partition-wise joins, BRIN, y cuándo no particionar**

- [Sección 6.1 — Partition-wise joins y aggregates](#6-partition-wise)
- [Sección 6.2 — BRIN en particiones de series temporales](#6-brin)
- [Sección 6.3 — Cuándo NO conviene particionar](#6-cuando-no)
- [Sección 6.4 — Novedades recientes: PostgreSQL 17](#6-pg17)

**Bloque 7 — Qué es sharding y en qué se diferencia de partitioning**

- [Sección 7.1 — La diferencia es física, no conceptual](#7-diferencia-fisica)
- [Sección 7.2 — Tabla comparativa completa](#7-tabla-comparativa)

**Bloque 8 — Shard key: tipos y consistent hashing**

- [Sección 8.1 — Range sharding](#8-range-sharding)
- [Sección 8.2 — List sharding](#8-list-sharding)
- [Sección 8.3 — Hash sharding](#8-hash-sharding)
- [Sección 8.4 — El problema del hash simple, y consistent hashing](#8-consistent-hashing)
- [Sección 8.5 — Virtual nodes](#8-vnodes)

**Bloque 9 — Arquitectura de enrutamiento y co-location**

- [Sección 9.1 — Router / Query Coordinator](#9-router)
- [Sección 9.2 — Metadata / Catalog service](#9-metadata-service)
- [Sección 9.3 — Co-location: la decisión de diseño más importante](#9-co-location)

**Bloque 10 — Hot shards**

- [Sección 10.1 — Causas](#10-causas)
- [Sección 10.2 — Mitigaciones](#10-mitigaciones)
- [Sección 10.3 — Dónde se refleja el costo real](#10-costo-real)

**Bloque 11 — Sharding en MongoDB**

- [Sección 11.1 — Arquitectura del cluster shardeado](#11-arquitectura)
- [Sección 11.2 — Chunks y el balancer](#11-chunks-balancer)
- [Sección 11.3 — Shard key selection](#11-shard-key-selection)
- [Sección 11.4 — Zones](#11-zones)
- [Sección 11.5 — Resharding](#11-resharding)
- [Sección 11.6 — Transacciones multi-shard](#11-transacciones)

**Bloque 12 — Sharding en Cassandra y DynamoDB**

- [Sección 12.1 — Cassandra: partition key vs. clustering key](#12-cassandra-keys)
- [Sección 12.2 — Cassandra: token ranges y vnodes](#12-cassandra-tokens)
- [Sección 12.3 — Cassandra: modelado query-first y wide partitions](#12-cassandra-modelado)
- [Sección 12.4 — DynamoDB: partition key, sort key y límites físicos](#12-dynamodb-keys)
- [Sección 12.5 — DynamoDB: adaptive capacity y write sharding](#12-dynamodb-adaptive)
- [Sección 12.6 — DynamoDB: GSI vs. LSI](#12-dynamodb-gsi-lsi)

**Bloque 13 — Sharding sobre motores relacionales: Vitess y Citus**

- [Sección 13.1 — MySQL partitioning: lo que cambia respecto a PostgreSQL](#13-mysql-partitioning)
- [Sección 13.2 — Vitess: arquitectura](#13-vitess-arquitectura)
- [Sección 13.3 — Vitess: vindexes](#13-vitess-vindexes)
- [Sección 13.4 — Vitess: resharding online con VReplication](#13-vitess-resharding)
- [Sección 13.5 — Citus: arquitectura y distribution column](#13-citus-arquitectura)
- [Sección 13.6 — Citus: reference tables y co-location](#13-citus-reference-tables)
- [Sección 13.7 — Citus: schema-based sharding](#13-citus-schema-based)

**Bloque 14 — Motores nacidos distribuidos: CockroachDB y Spanner**

- [Sección 14.1 — CockroachDB: ranges y Raft](#14-cockroachdb-ranges)
- [Sección 14.2 — CockroachDB: parallel commits](#14-cockroachdb-parallel-commits)
- [Sección 14.3 — Spanner: TrueTime y commit wait](#14-spanner-truetime)
- [Sección 14.4 — Spanner: splits y Paxos](#14-spanner-splits)

**Bloque 15 — Functional partitioning / federación**

- [Sección 15.1 — Definición y diferencia con sharding](#15-definicion)
- [Sección 15.2 — El costo de coordinación no depende del patrón](#15-costo-coordinacion)
- [Sección 15.3 — Combinación práctica en sistemas grandes](#15-combinacion)

**Bloque 16 — Transacciones distribuidas: Two-Phase Commit**

- [Sección 16.1 — Por qué son estructuralmente más caras](#16-por-que-caras)
- [Sección 16.2 — Mecánica completa](#16-mecanica)
- [Sección 16.3 — Desglose de costos](#16-costos)

**Bloque 17 — Alternativas a 2PC**

- [Sección 17.1 — Consensus distribuido en vez de un coordinador único](#17-consensus)
- [Sección 17.2 — Saga pattern](#17-saga)

**Bloque 18 — CAP theorem: dónde entra exactamente**

- [Sección 18.1 — Recordatorio preciso del teorema](#18-recordatorio)
- [Sección 18.2 — Por qué en partitioning no aparece nunca](#18-por-que-no-en-partitioning)
- [Sección 18.3 — Replicación dentro de cada shard: otra capa de CAP](#18-replicacion-shard)

**Bloque 19 — Framework de decisión**

- [Sección 19.1 — Qué problema resuelve cada técnica](#19-que-resuelve)
- [Sección 19.2 — Preguntas guía para decidir](#19-preguntas-guia)
- [Sección 19.3 — Resumen ejecutivo en una frase por concepto](#19-resumen-ejecutivo)

---

**Alcance de esta guía**

Qué es universal aquí. La necesidad de dividir datos cuando un solo nodo ya no alcanza, en volumen o en throughput, aplica a cualquier motor con crecimiento suficiente. Los tres ejes que aparecen una y otra vez (dónde viven físicamente los fragmentos, si hay red entre ellos, y qué le cuesta a una transacción cruzar esa frontera) son universales, sin importar el nombre que cada motor le ponga al mecanismo.

Qué es específico de una implementación concreta. Los nombres (`relfilenode`, chunk, range, vnode), los límites numéricos exactos (8192 particiones en MySQL, chunks de 128 MB en MongoDB, ranges de 512 MB en CockroachDB) y los mecanismos de coordinación (Raft en CockroachDB, TrueTime en Spanner, VReplication en Vitess) son decisiones de cada motor, no verdades universales.

Qué queda fuera de esta guía. El detalle de replicación dentro de un solo nodo (streaming replication, replica sets sin sharding) y el detalle completo de MVCC, locks y niveles de aislamiento se asumen como conocimiento previo. Aquí solo se retoman cuando el sharding o las transacciones distribuidas los ponen en juego de una forma distinta a como ya se explicaron.

---

## Bloque 1: Qué es table partitioning y por qué existe {#1-que-es-partitioning}

### Sección 1.1: Partitioning nunca cruza la red {#1-nunca-cruza-red}

Table partitioning es la división de una tabla lógica en múltiples tablas físicas independientes, llamadas particiones, todas dentro del mismo proceso de motor de base de datos, el mismo disco o volumen adjunto, y el mismo espacio de memoria compartida (shared buffers en PostgreSQL, buffer pool en InnoDB). No hay red involucrada en ningún punto de este mecanismo.

Esta distinción no es un detalle técnico menor, es la línea real que separa la complejidad barata de la complejidad cara. Todo lo que ocurre dentro de un mismo proceso de base de datos es barato y no arriesga inconsistencia entre nodos, porque hay una sola autoridad de commit, un solo WAL, un solo lock manager. En cuanto los datos cruzan la frontera de red entre procesos independientes (que es exactamente lo que hace el sharding, cubierto desde el Bloque 7), aparecen latencia, coordinación explícita, y el riesgo estructural que describe el teorema CAP.

Por eso esta guía avanza de lo local a lo distribuido: primero todo lo que se puede resolver sin tocar una tarjeta de red (Bloques 1 a 6), después lo que exige coordinarse con otro proceso (Bloques 7 en adelante).

### Sección 1.2: Cómo lo implementa PostgreSQL por dentro {#1-implementacion-pg}

En PostgreSQL, una tabla particionada "padre" no tiene almacenamiento propio, es un objeto puramente lógico en el catálogo (`pg_class` con `relkind = 'p'`), sin archivo de datos asociado. Cada partición hija sí es una tabla física completa, con su propio `relfilenode`, su propio archivo en `$PGDATA/base/<db_oid>/`, sus propios índices, su propia tabla TOAST para valores grandes, y sus propias estadísticas en `pg_statistic`.

```
UN SOLO SERVIDOR, UN SOLO PROCESO DE POSTGRESQL
┌───────────────────────────────────────────────────────────────┐
│  Memoria compartida (shared_buffers)                          │
│  un solo WAL · un solo lock manager · un solo catálogo        │
└───────────────────────────────────────────────────────────────┘

  ventas                    ← tabla padre: solo existe en pg_class,
  (tabla lógica)               relkind = 'p', SIN archivo propio
     │
     ├── ventas_2024        → archivo físico propio (relfilenode)
     │                         + sus propios índices B-tree
     ├── ventas_2025_q1      → archivo físico propio (relfilenode)
     │                         + sus propios índices B-tree
     └── ventas_2025_q2      → archivo físico propio (relfilenode)
                               + sus propios índices B-tree

  Los tres archivos viven en el mismo disco, dentro del mismo
  $PGDATA/base/<db_oid>/. Un SELECT que toca varias particiones
  hace varias lecturas de disco locales, nunca un salto de red.
```

Particionar no crea una estructura de almacenamiento especial, reutiliza el mecanismo normal de tablas de PostgreSQL (heap, páginas, tuples, exactamente lo que ya cubre el Bloque 1 de `04_almacenamiento_motores_relacionales.md`), solo que agrupadas bajo un enrutador lógico común.

MySQL InnoDB, en cambio, es clustered index por defecto: la tabla completa vive dentro de un B+Tree ordenado por la clave primaria. Cada partición en MySQL es, por lo tanto, su propio árbol clustered independiente y completo, no un heap con índices separados como en PostgreSQL. La fila y su entrada de índice primario viven en la misma estructura física por partición.

### Sección 1.3: Declarative vs. el método viejo basado en herencia {#1-declarative-vs-legacy}

PostgreSQL soporta dos formas de particionar, y solo una es la recomendada hoy.

**Declarative partitioning** (`PARTITION BY`, desde PostgreSQL 10) es el método moderno. El motor se encarga de enrutar cada fila a su partición correcta, de aplicar pruning automáticamente, y de propagar índices y constraints. Es el método que cubre el resto de este bloque.

**Inheritance-based partitioning** (el método anterior a PostgreSQL 10) usa tablas hijas conectadas por herencia normal de PostgreSQL, con `CHECK` constraints que describen el rango de cada una, y triggers o reglas escritas a mano para enrutar cada `INSERT` a la tabla correcta. La documentación oficial es explícita sobre por qué casi nadie debería usar este método hoy: los triggers "pueden ser complicados de escribir, y van a ser mucho más lentos que el tuple routing que declarative partitioning hace internamente". Tampoco tiene pruning nativo, depende del parámetro `constraint_exclusion` evaluando los `CHECK` constraints, y ese pruning solo ocurre en tiempo de planificación, nunca en tiempo de ejecución.

El único motivo real para seguir usando el método viejo es una necesidad específica que declarative no cubre, como columnas adicionales por partición o herencia múltiple. Fuera de ese caso, la recomendación oficial es declarative sin excepción.

---

## Bloque 2: Métodos de partitioning {#2-metodos-partitioning}

### Sección 2.1: RANGE {#2-range}

Cada partición cubre un intervalo continuo de valores de la partition key. PostgreSQL almacena los límites de cada partición en `pg_class.relpartbound`, como un rango `[inicio, fin)`, donde el inicio es inclusivo y el fin es exclusivo. El caso de uso dominante es series temporales (logs, transacciones, eventos), donde la mayoría de las consultas y del mantenimiento (purgar datos viejos) se organizan de forma natural por fecha.

MySQL soporta RANGE de forma equivalente, con una extensión útil que PostgreSQL no necesita de la misma forma: **RANGE COLUMNS**, que permite usar columnas de tipo `DATE`, `DATETIME` o cadena directamente como límite, sin tener que convertirlas primero a un entero mediante una expresión.

```
RANGE — tabla ventas, particionada por fecha
(las dos particiones son archivos distintos DENTRO DEL MISMO servidor, sin red entre ellas)

   fila (fecha='2024-03-15')         fila (fecha='2025-06-01')
               │                                 │
               ▼                                 ▼
  ┌──────────────────────────┐      ┌──────────────────────────┐
  │ ventas_2024              │      │ ventas_2025              │
  │ [2024-01-01, 2025-01-01) │      │ [2025-01-01, 2026-01-01) │
  └──────────────────────────┘      └──────────────────────────┘
```

### Sección 2.2: LIST {#2-list}

Cada partición corresponde a un conjunto explícito y discreto de valores, por ejemplo país, tipo de cliente, o `tenant_id` en un sistema multi-tenant. Es útil cuando el criterio de negocio ya define categorías naturales y finitas, y cuando se necesita aplicar reglas o constraints distintos por categoría sin romper el esquema compartido.

MySQL también ofrece la extensión equivalente, **LIST COLUMNS**, con el mismo beneficio que RANGE COLUMNS: usar directamente columnas no enteras sin una expresión de conversión.

```
LIST — tabla clientes, particionada por país
(las tres particiones son archivos distintos DENTRO DEL MISMO servidor, sin red entre ellas)

  fila (pais='MX')      fila (pais='US')       fila (pais='CA')
         │                    │                      │
         ▼                    ▼                      ▼
  ┌─────────────┐      ┌─────────────┐      ┌─────────────────┐
  │ clientes_mx │      │ clientes_us │      │ clientes_norte  │
  │ IN ('MX')   │      │ IN ('US')   │      │ IN ('CA', 'GT') │
  └─────────────┘      └─────────────┘      └─────────────────┘
```

### Sección 2.3: HASH y KEY {#2-hash-key}

**HASH** aplica una función hash determinística sobre la partition key y asigna la fila según el resultado módulo el número de particiones declarado. No hay ningún criterio de negocio detrás, el único objetivo es distribuir el volumen de forma uniforme entre N particiones cuando no existe un criterio natural de rango o lista, o cuando ese criterio generaría particiones desbalanceadas.

MySQL diverge de PostgreSQL en un punto que vale la pena distinguir con cuidado. MySQL ofrece **HASH** y, por separado, **KEY**. En `PARTITION BY HASH`, el usuario debe proveer una expresión que devuelva un entero (o `NULL`). En `PARTITION BY KEY`, MySQL usa su propia función hash interna, y acepta cualquier tipo de columna excepto `TEXT`/`BLOB`, sin exigir que el usuario escriba una expresión entera. La diferencia central es esa: HASH exige una expresión entera provista por el usuario, KEY solo necesita el nombre de la columna y MySQL garantiza internamente el resultado entero. Ambos tienen además una variante **LINEAR** (`LINEAR HASH`, `LINEAR KEY`) que usa un algoritmo distinto basado en potencias de 2, más rápido para añadir o quitar particiones pero con un balanceo menos uniforme entre ellas.

```
HASH — 4 particiones, sin ningún criterio de negocio detrás
(las cuatro particiones son archivos distintos DENTRO DEL MISMO servidor, sin red entre ellas)

  hash(id)%4=0  hash(id)%4=1  hash(id)%4=2  hash(id)%4=3
      │           │           │           │
      ▼           ▼           ▼           ▼
  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
  │ part_0 │  │ part_1 │  │ part_2 │  │ part_3 │
  └────────┘  └────────┘  └────────┘  └────────┘

  Filas con id=4, 8, 12... e id=101, 205... pueden caer en la misma
  partición sin ninguna relación de negocio entre ellas, ese es el punto.
```

### Sección 2.4: Composite: particionar una partición {#2-composite}

PostgreSQL permite particionar una partición, por ejemplo RANGE por fecha en el nivel superior y, dentro de cada rango de fecha, sub-particionar por LIST de región. Esto es útil cuando se necesita partition pruning efectivo sobre dos dimensiones distintas de consulta al mismo tiempo, a costa de una jerarquía de catálogo más compleja de mantener. Desde PostgreSQL 17, esto se extiende a combinar dos criterios de particionamiento distintos (por ejemplo RANGE y HASH) en la misma jerarquía sin depender de extensiones de terceros.

MySQL llama a esto **subpartitioning**, con una restricción que PostgreSQL no tiene: solo las tablas particionadas por RANGE o LIST pueden subparticionarse, y el nivel inferior debe ser obligatoriamente HASH o KEY. Un detalle de sintaxis verificado: al usar `SUBPARTITION BY KEY`, a diferencia de `PARTITION BY KEY`, la columna debe especificarse explícitamente, no se puede omitir.

```
COMPOSITE — RANGE por fecha, y dentro de cada rango, LIST por región
(toda la jerarquía vive DENTRO DEL MISMO servidor, ningún nivel implica red)

  ventas (padre)
     │
     ├── ventas_2024 (RANGE)               ← 1er nivel: por fecha
     │      ├── ventas_2024_norte (LIST)   ← 2do nivel: por región
     │      └── ventas_2024_sur (LIST)
     │
     └── ventas_2025 (RANGE)
            ├── ventas_2025_norte (LIST)
            └── ventas_2025_sur (LIST)

  Una query con WHERE fecha = '2024-06-01' AND region = 'norte'
  hace pruning en las dos dimensiones a la vez: descarta ventas_2025
  completo, y dentro de ventas_2024 descarta también ventas_2024_sur.
```

---

## Bloque 3: El ciclo de vida de una fila particionada {#3-ciclo-de-vida}

### Sección 3.1: Qué pasa físicamente en un INSERT {#3-insert-routing}

Cuando el planner recibe un `INSERT` contra la tabla padre, ejecuta un proceso de routing determinístico antes de tocar disco. Evalúa el valor de la columna o columnas de partition key en la fila entrante, lo compara contra los límites registrados en el catálogo (`pg_partitioned_table` más el `relpartbound` de cada hijo), y determina la partición destino. Si ninguna partición cubre ese valor y no existe una partición DEFAULT, el `INSERT` falla con un error. Una vez resuelta la partición, el motor escribe la fila directamente en el archivo físico de esa partición, genera WAL local solo para esa escritura, y actualiza únicamente los índices de esa partición específica.

Este cálculo de routing tiene un costo de CPU marginal, evaluar un predicado contra un catálogo pequeño, insignificante frente al beneficio en lectura que se obtiene después.

```
INSERT INTO ventas (fecha, monto) VALUES ('2025-06-15', 500);
              │
              ▼
  1. Evaluar la partition key de la fila entrante: fecha = '2025-06-15'
              │
              ▼
  2. Comparar contra los límites del catálogo:
       ventas_2024     [2024-01-01, 2025-01-01)  → no coincide
       ventas_2025_q1  [2025-01-01, 2025-04-01)  → no coincide
       ventas_2025_q2  [2025-04-01, 2025-07-01)  → SÍ coincide
              │
              ▼
  3. Escribir la fila directo en ventas_2025_q2, generar WAL
     solo ahí, y actualizar solo los índices de esa partición.
```

### Sección 3.2: Partition pruning: dónde se recupera la inversión {#3-pruning}

Pruning es la eliminación de particiones del plan de ejecución antes de escanearlas, basada en que el optimizador puede demostrar matemáticamente que esas particiones no pueden contener filas que satisfagan el predicado del `WHERE`.

Hay dos momentos posibles para hacer esta eliminación. **Pruning en planning time** ocurre cuando el valor del predicado es una constante literal conocida al momento de planear la query, por ejemplo `WHERE fecha = '2026-03-15'`. El `EXPLAIN` ni siquiera muestra las particiones descartadas, desaparecen del plan directamente. **Pruning en execution time** ocurre cuando el valor viene de un parámetro, una subquery, o una función volátil, y no puede resolverse hasta que la query ya está corriendo. PostgreSQL soporta esto desde la versión 11 (runtime pruning), y el `EXPLAIN ANALYZE` muestra las particiones descartadas como "Never Executed".

El efecto físico es una reducción literal de I/O. En vez de escanear un archivo de cientos de gigabytes, el motor abre y lee únicamente el archivo, o archivos si hay varios índices, de la partición relevante. Esto no es una optimización lógica abstracta, es la diferencia entre leer 2 GB y leer 500 GB del disco.

```
SELECT * FROM ventas WHERE fecha = '2025-06-01';

  Sin partition pruning                    Con partition pruning
  (tabla monolítica, 500 GB)                (misma tabla, particionada por fecha)

  ┌──────────────────┐
  │ escanea TODO el  │                ventas_2024      → descartada, 0 bytes leídos
  │ archivo completo │                ventas_2025_q1   → descartada, 0 bytes leídos
  │ (500 GB)         │                ventas_2025_q2   → ✓ única leída (unos 2 GB)
  └──────────────────┘
                                      Solo se abre el archivo de la partición
                                      que puede contener la fecha buscada.
```

La consecuencia práctica es dura: si las queries reales no filtran consistentemente por la partition key, particionar no solo no ayuda, puede ser peor que una tabla monolítica, porque se agrega el overhead de routing sin ganar pruning, y además se fragmentan los índices en N estructuras separadas que hay que escanear todas.

### Sección 3.3: La diferencia de pruning entre PostgreSQL y MySQL {#3-pruning-diferencia}

Aquí hay una asimetría real entre motores que vale la pena marcar con precisión, porque afecta directamente qué tan bien se comporta cada uno con queries que no usan constantes literales.

MySQL hace pruning por *constant-folding* en tiempo de optimización (evalúa la expresión de partición contra constantes durante el planning) y por conversión de rangos cortos a listas `IN` cuando el rango es menor al número de particiones. Desde MySQL 8.0, esto se extiende a pruning con las funciones `YEAR()`, `TO_DAYS()` y `TO_SECONDS()` aplicadas sobre columnas `DATE`/`DATETIME`. Pero, a diferencia de PostgreSQL 11 en adelante, la documentación oficial de MySQL no describe un mecanismo equivalente de runtime pruning para valores que solo se conocen cuando la query ya está corriendo (parámetros, subqueries). Esto significa que una query parametrizada (el patrón típico de cualquier backend con prepared statements) puede beneficiarse de pruning en PostgreSQL y no beneficiarse de la misma forma en MySQL, dependiendo de cómo llegue el valor.

### Sección 3.4: Índices: replicados pero físicamente independientes {#3-indices}

Desde PostgreSQL 11, un `CREATE INDEX` sobre la tabla padre se propaga automáticamente a cada partición existente y a las futuras. Pero esto es una conveniencia de catálogo, no un índice compartido: cada partición mantiene su propia estructura B-tree física, en su propio archivo. No existe, de forma nativa, un índice global que cubra todas las particiones con una sola estructura.

La consecuencia directa es concreta: si una query no puede hacer pruning (no filtra por partition key) pero sí usa una columna indexada, el motor tiene que recorrer el índice de cada partición por separado y combinar los resultados. Esto aparece en el plan como un `Append` sobre N `Index Scan`, y es más costoso que un único índice sobre una tabla no particionada del mismo volumen total.

MySQL InnoDB, al ser clustered index por partición, no tiene esta distinción de la misma forma: cada partición ES su propio árbol completo (datos e índice primario en la misma estructura), así que el mismo razonamiento aplica pero sobre el árbol completo de cada partición, no solo sobre un índice secundario.

### Sección 3.5: Constraints y la regla de la partition key obligatoria {#3-constraints}

| Elemento | ¿Se comparte entre particiones? |
| --- | --- |
| Columnas (nombre, tipo, orden) | Sí, forzado. Un `ALTER TABLE` en el padre se propaga a todas |
| Índices | Se replican automáticamente, pero son estructuras físicas independientes por partición |
| CHECK / NOT NULL | Pueden diferir por partición, útil para reglas específicas de un subconjunto de datos |
| PRIMARY KEY / UNIQUE | Deben incluir la partition key como parte de la clave compuesta |
| Triggers | Se pueden definir a nivel padre (se propagan) o individualmente por partición |

La restricción de `PRIMARY KEY` merece una explicación mecánica, porque no es un capricho del motor. Ni PostgreSQL ni MySQL pueden garantizar unicidad global de una columna (por ejemplo `id`) sin escanear todas las particiones en cada `INSERT`, porque cada partición mantiene su índice de forma aislada, sin coordinación entre ellas. Por eso ambos motores obligan a que la clave de unicidad incluya la partition key, así la unicidad se verifica dentro del alcance de una sola partición, que es lo único que el motor puede comprobar de forma barata. La documentación oficial de PostgreSQL lo dice de forma literal: la clave no puede incluir expresiones ni llamadas a funciones, y sus columnas deben incluir todas las columnas de la partition key. La misma regla aplica a los exclusion constraints.

### Sección 3.6: Mantenimiento: la otra mitad del beneficio {#3-mantenimiento}

**DROP de partición** es una operación de metadata (borrar un archivo del filesystem y quitar la entrada del catálogo), prácticamente instantánea, sin generar WAL proporcional al volumen de filas. Compárese con `DELETE FROM tabla WHERE fecha < X` sobre una tabla monolítica: eso escanea filas, genera WAL por cada fila borrada, deja dead tuples, y requiere `VACUUM` posterior para recuperar el espacio.

**VACUUM y ANALYZE independientes**: cada partición se limpia y actualiza sus estadísticas por separado. Una partición con mucho churn (la del mes actual, por ejemplo) se limpia sin bloquear ni afectar a particiones frías e inactivas.

**Estadísticas del optimizador más precisas**: al ser por partición, se evita el problema de estimaciones de cardinalidad distorsionadas que ocurre en tablas monolíticas con distribuciones de datos heterogéneas entre distintos rangos de fecha o categorías.

### Sección 3.7: Cómo consultar una tabla particionada correctamente {#3-como-consultar}

Un ejemplo completo fija el patrón de principio a fin, desde crear la tabla hasta consultarla.

```sql
-- 1. Crear la tabla padre, puramente lógica, sin almacenamiento propio (Sección 1.2)
CREATE TABLE ventas (
    id      BIGINT GENERATED ALWAYS AS IDENTITY,
    fecha   DATE NOT NULL,
    monto   NUMERIC NOT NULL
) PARTITION BY RANGE (fecha);

-- 2. Crear las particiones hijas, cada una con su propio rango
CREATE TABLE ventas_2024    PARTITION OF ventas
    FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');
CREATE TABLE ventas_2025_q1 PARTITION OF ventas
    FOR VALUES FROM ('2025-01-01') TO ('2025-04-01');
CREATE TABLE ventas_2025_q2 PARTITION OF ventas
    FOR VALUES FROM ('2025-04-01') TO ('2025-07-01');

-- 3. Insertar y consultar siempre contra el padre, nunca contra la hija
INSERT INTO ventas (fecha, monto) VALUES ('2025-06-15', 500);

SELECT * FROM ventas WHERE fecha = '2025-06-15';
```

El `INSERT` y el `SELECT` apuntan los dos a `ventas`, la tabla padre, nunca a `ventas_2025_q2` por su nombre propio. Así se consulta una tabla particionada en cualquier motor con partitioning nativo, y es el motor el que resuelve el routing en el `INSERT` (Sección 3.1) y el pruning en el `SELECT` (Sección 3.2) por su cuenta. El código de la aplicación no necesita saber cuántas particiones existen ni cómo se llaman.

Vale la pena aclarar qué significa exactamente "sin almacenamiento propio" (Sección 1.2), porque se presta a una confusión razonable. No significa que un `SELECT` contra el padre regrese vacío, significa que el padre no tiene su propio archivo de datos, las filas viven físicamente en las hijas, pero el resultado de la consulta sigue siendo el conjunto completo de filas de todas las particiones relevantes, presentado como si viniera de una sola tabla normal. `SELECT * FROM ventas` sin ningún `WHERE` recorre las tres particiones y devuelve sus filas combinadas, exactamente el mismo resultado que devolvería una tabla monolítica con esos mismos datos, la única diferencia es de dónde en disco vino cada fila, no cuántas filas aparecen.

Inspeccionar la estructura del padre, en vez de sus datos, se ve igual que en cualquier tabla normal. En `psql`, `\d ventas` muestra las tres columnas declaradas (`id`, `fecha`, `monto`) como en cualquier tabla, más dos secciones que no aparecen en una tabla sin particionar, la línea `Partition key: RANGE (fecha)` y un bloque `Partitions:` que lista cada hija con su rango exacto:

```
                                    Table "public.ventas"
 Column |  Type   | Collation | Nullable |              Default
--------+---------+-----------+----------+------------------------------------
 id     | bigint  |           | not null | generated always as identity
 fecha  | date    |           | not null |
 monto  | numeric |           | not null |
Partition key: RANGE (fecha)
Partitions: ventas_2024 FOR VALUES FROM ('2024-01-01') TO ('2025-01-01'),
            ventas_2025_q1 FOR VALUES FROM ('2025-01-01') TO ('2025-04-01'),
            ventas_2025_q2 FOR VALUES FROM ('2025-04-01') TO ('2025-07-01')
```

Ese `Partitions:` es la señal visual de que `ventas` es un objeto lógico, no una tabla con archivo propio. `information_schema.columns` y consultas similares reportan las mismas tres columnas que cualquier tabla, porque el catálogo describe columnas a nivel del objeto lógico, no del archivo físico.

¿Se puede consultar la partición hija directamente, con `SELECT * FROM ventas_2025_q2 WHERE fecha = '2025-06-15'`? Sí, funciona, porque cada partición es una tabla física completa y normal (Sección 1.2), y de hecho esa consulta se salta por completo el trabajo de pruning porque ya declara de antemano dónde buscar. Pero no es el patrón recomendado para las consultas de una aplicación, por tres razones concretas.

Primero, rompe la abstracción. Si el esquema de particionamiento cambia más adelante, otros rangos de fecha, otra columna, otro método, cualquier consulta escrita contra el nombre de una partición específica se rompe en silencio, mientras que las consultas contra el padre siguen funcionando sin cambios.

Segundo, no cubre particiones futuras de forma automática. Una consulta contra `ventas_2025_q2` nunca va a ver las filas que se inserten en `ventas_2025_q3` una vez que esa partición nueva exista, mientras que una consulta contra `ventas` siempre cubre las particiones que existan en ese momento, sin importar cuándo se crearon.

Tercero, el único caso donde consultar una partición por su nombre tiene sentido real es mantenimiento operativo, no lógica de aplicación, por ejemplo correr `VACUUM` sobre una sola partición con mucho churn, o inspeccionar el tamaño en disco de una partición específica antes de decidir archivarla. Ese es exactamente el tipo de tarea puntual que ya describe la Sección 3.6, no algo que deba aparecer en el camino normal de una query de negocio.

MySQL cierra esta misma pregunta por construcción. Una tabla particionada en MySQL tiene un solo nombre visible a nivel SQL, no existe un nombre de tabla hija distinto que se pueda consultar por accidente. MySQL sí ofrece una sintaxis explícita para forzar una consulta contra una partición puntual, `SELECT * FROM ventas PARTITION (p_2025_q2) WHERE fecha = '2025-06-15'`, pero es un hint que el desarrollador tiene que escribir a propósito, reservado para el mismo tipo de mantenimiento operativo del párrafo anterior, no para el camino normal de una consulta.

Sobre si la tabla padre puede quedar vacía, en declarative partitioning no es que "pueda quedar" vacía, está vacía siempre, por diseño, no hay ningún momento en el que tenga datos propios y después se vacíe. La Sección 1.2 ya lo estableció, el padre no tiene `relfilenode` ni archivo propio en absoluto. Cualquier `INSERT` contra el padre se enruta de forma obligatoria a una partición hija (Sección 3.1), o falla si ninguna partición cubre ese valor y no existe una partición DEFAULT (Sección 4.1), pero nunca queda almacenado en el padre mismo.

La única excepción real es el método viejo basado en herencia (Sección 1.3). Ahí el padre sí es una tabla física normal, así que técnicamente puede recibir filas de forma directa si alguien hace un `INSERT` sin pasar por los triggers de enrutamiento escritos a mano, dejando datos huérfanos que ninguna tabla hija declara cubrir. Ese es precisamente el tipo de bug silencioso que declarative partitioning elimina de raíz al no darle almacenamiento propio al padre.

---

## Bloque 4: Límites, particiones DEFAULT y foreign keys {#4-limites-y-fk}

### Sección 4.1: Particiones DEFAULT: qué son y su riesgo real {#4-default}

Una partición DEFAULT captura cualquier fila cuyo valor de partition key no coincide con los límites de ninguna otra partición. Es una red de seguridad útil, pero tiene un riesgo documentado que conviene conocer de antemano: si más adelante se intenta crear una partición nueva cuyo rango se solapa con datos ya residentes en la DEFAULT, PostgreSQL rechaza la operación con el error "partition would overlap partition", y para verificarlo tiene que escanear la partición DEFAULT completa.

La mitigación recomendada oficialmente es agregar, antes de adjuntar la nueva partición, un `CHECK` constraint en la DEFAULT que excluya explícitamente el rango que va a cubrir la partición nueva. Y como buena práctica general, no conviene dejar datos acumulándose en la DEFAULT a largo plazo, es mejor moverlos periódicamente a particiones propias.

### Sección 4.2: Sub-partitioning y sus límites prácticos {#4-sub-particionamiento}

PostgreSQL soporta sub-partitioning de forma nativa, con una advertencia textual explícita en su propia documentación: cualquiera de los dos niveles "puede llevar fácilmente a un número excesivo de particiones, así que conviene tener moderación". El mismo límite práctico que aplica al primer nivel (cubierto en la siguiente sección) aplica al total de nodos hoja en toda la jerarquía, no solo al primer nivel de división.

### Sección 4.3: Cuántas particiones son demasiadas {#4-cuantas-particiones}

PostgreSQL no impone un límite duro, pero su propia documentación es explícita sobre el rango práctico: "el planner generalmente es capaz de manejar jerarquías de partición de hasta unos pocos miles de particiones razonablemente bien, siempre que las queries típicas permitan que el planner elimine todas menos un pequeño número de particiones". Con el método viejo basado en herencia, ese límite práctico baja a "quizás un centenar" de tablas hijas. La advertencia oficial resume el espíritu correcto: "nunca asumas que más particiones son mejores que menos particiones, ni viceversa".

Cada partición carga su propia metadata en memoria por cada sesión que la toca, así que el consumo de RAM crece con el número de particiones tocadas, no solo con el volumen de datos. El parámetro moderno que controla el pruning es `enable_partition_pruning` (activado por defecto), mientras que `constraint_exclusion` es el mecanismo legacy basado en evaluar `CHECK` constraints, que solo actúa en tiempo de planificación, nunca en tiempo de ejecución, y por lo tanto es estrictamente más limitado.

MySQL, en cambio, sí impone un límite duro y documentado: **8192 particiones** como máximo por tabla (fuera de NDB Cluster), contando también las subparticiones.

### Sección 4.4: Foreign keys hacia y desde tablas particionadas {#4-foreign-keys}

Aquí los dos motores toman caminos opuestos.

**PostgreSQL**, desde la versión 12, sí permite que una tabla particionada sea referenciada por una foreign key desde otra tabla, siempre que tenga una `PRIMARY KEY` o `UNIQUE` válida bajo la regla de la Sección 3.5. Una tabla particionada también puede tener foreign keys propias hacia tablas regulares u otras tablas particionadas. Hubo bugs históricos poco después de esa introducción (constraints no clonados correctamente a particiones de niveles inferiores, problemas con self-referencing foreign keys) que ya están corregidos en versiones posteriores.

**MySQL InnoDB prohíbe por completo las foreign keys en tablas particionadas**, sin excepción, y son cuatro reglas explícitas y documentadas: ninguna tabla InnoDB particionada puede tener foreign keys propias; ninguna tabla que tenga foreign keys puede particionarse; ninguna tabla InnoDB puede tener una foreign key que apunte a una tabla particionada; y ninguna tabla particionada puede tener columnas referenciadas por foreign keys externas. Es una limitación categórica, no una regla con matices como en PostgreSQL.

MySQL tampoco soporta particionamiento en tablas temporales, tablas de log, columnas espaciales (`POINT`/`GEOMETRY`), ni en columnas con índices `FULLTEXT`.

---

## Bloque 5: Migrar una tabla existente a particionada {#5-migrar-tabla}

### Sección 5.1: Por qué no existe una conversión in-place {#5-no-in-place}

PostgreSQL no ofrece una conversión in-place. No existe un `ALTER TABLE` que tome una tabla ya poblada y la reorganice en particiones automáticamente, porque hacerlo implica reescribir físicamente cada fila en un archivo distinto según su partition key, no es un cambio de metadata.

Esto tiene una consecuencia que conviene dejar explícita, porque es fácil imaginar un estado intermedio que en realidad no existe. La tabla vieja nunca "se convierte" en el padre particionado conservando parte de sus propias filas mientras el resto ya vive en hijas. El padre nuevo siempre es un objeto distinto, creado vacío desde cero (Sección 1.2), la tabla vieja con todo su histórico sigue siendo una tabla normal aparte hasta el momento del rename, y termina eliminada por completo (Sección 5.2, pasos 4 y 5). No hay ningún punto del proceso, ni antes ni después de la migración, donde una sola tabla esté "medio particionada".

Tres escenarios reales se le parecen, pero son distintos y vale la pena no confundirlos entre sí.

El primero es la ventana de transición de la migración misma. Mientras se copian los datos (paso 2) y todavía no se hace el rename (paso 4), sí existen dos tablas al mismo tiempo, la vieja completa y la nueva particionada recibiendo solo las escrituras capturadas por el trigger o la replicación lógica del paso 3. Es un estado real, pero transitorio por definición, dura lo que dura la migración y desaparece en el rename.

El segundo es la partición DEFAULT (Sección 4.1). Una fila que no coincide con ningún rango o lista declarado no se pierde ni se queda en el padre, cae en la partición DEFAULT, que es una partición hija normal con su propio archivo físico. Puede sentirse como "datos sin organizar todavía", pero sigue siendo almacenamiento de una hija, nunca del padre.

El tercero es una decisión de arquitectura deliberada, dejar el histórico viejo en una tabla completamente aparte, sin particionar, y particionar solo los datos nuevos hacia adelante, típicamente porque migrar todo el histórico de una sola vez sale demasiado caro. Esto es válido y común, pero en ese caso son dos tablas independientes y sin relación de padre-hijo entre ellas, la archivada y la particionada, no una sola tabla parcialmente particionada. Se parece más al patrón de functional partitioning del Bloque 15 que al mecanismo de partitioning en sí.

### Sección 5.2: El procedimiento estándar, paso a paso {#5-procedimiento}

1. Crear la tabla nueva particionada, con el padre vacío y las particiones hijas con sus límites ya definidos.
2. Migrar los datos con `INSERT INTO nueva SELECT * FROM vieja`, idealmente por lotes para no saturar el WAL de golpe, o usando herramientas como `pg_partman` o `pgloader` para automatizar el proceso.
3. Configurar un trigger en la tabla vieja, o replicación lógica, para capturar escrituras nuevas durante la ventana de migración y evitar pérdida de datos.
4. Una vez sincronizadas, renombrar ambas tablas (`ALTER TABLE vieja RENAME TO vieja_old`, `ALTER TABLE nueva RENAME TO vieja`). Esto sí es una operación de metadata rápida, pero solo funciona porque el trabajo pesado de copiar datos ya se hizo antes.
5. Eliminar la tabla vieja una vez confirmada la migración.

### Sección 5.3: ATTACH PARTITION: la excepción rápida {#5-attach-partition}

Si los datos ya estaban separados manualmente en tablas independientes antes de decidir particionar (por ejemplo `ventas_2024` y `ventas_2025`, gestionadas a mano con `UNION ALL`), es posible engancharlas como particiones sin reescribir datos. PostgreSQL solo valida que cumplan el rango declarado. Esto es rápido porque es una operación de metadata, pero solo aplica cuando los datos ya estaban físicamente separados de antemano, no sirve como atajo para el caso general de la sección anterior.

### Sección 5.4: pg_partman: automatizar creación y retención {#5-pg-partman}

`pg_partman` es una extensión, no viene incluida en el core de PostgreSQL, que automatiza tres cosas que de otra forma habría que scriptear a mano: la creación de particiones futuras por adelantado, la retención y `DROP` automático de particiones viejas según una política configurable (por intervalo de tiempo o por cantidad), y la conversión de una tabla existente a particionada o viceversa. Incluye su propio background worker para no depender de un cron externo, aunque también se puede invocar desde `pg_cron` si se prefiere esa vía.

---

## Bloque 6: Partition-wise joins, BRIN, y cuándo no particionar {#6-joins-brin-cuando-no}

### Sección 6.1: Partition-wise joins y aggregates {#6-partition-wise}

Cuando dos tablas particionadas comparten exactamente el mismo esquema de particionamiento (mismos límites, mismo método) sobre la columna del `JOIN`, PostgreSQL puede ejecutar el join partición por partición en paralelo, en vez de materializar ambas tablas completas antes de unirlas. Esto reduce el uso de memoria de trabajo (`work_mem`) y permite paralelismo real entre particiones independientes. Lo mismo aplica a un `GROUP BY` sobre la partition key, el motor puede agregar dentro de cada partición y solo combinar los resultados parciales al final (partition-wise aggregate), en vez de un único agregado monolítico sobre todos los datos. Desde PostgreSQL 17, este mismo mecanismo de push-down se extiende también a los aggregates, algo que antes solo existía para joins.

Este es uno de los puntos donde PostgreSQL es más flexible que MySQL: en MySQL, el aprovechamiento de partition pruning con predicados que involucran funciones sobre la columna de partición ha sido, históricamente, más limitado, aunque mejoró notablemente desde la versión 8.0 (Sección 3.3).

### Sección 6.2: BRIN en particiones de series temporales {#6-brin}

BRIN (Block Range Index) indexa rangos de bloques físicos, guardando el mínimo y el máximo por rango, no filas individuales. Esto ahorra más del 99% del espacio en disco comparado con un B-tree en tablas grandes, a cambio de ser un índice "lossy": no puede garantizar unicidad, así que no soporta índices únicos.

BRIN funciona especialmente bien dentro de particiones de series temporales porque el particionamiento ya garantiza, por diseño, que los datos de cada partición están correlacionados físicamente (se insertaron en orden creciente, de forma append-only). Esa correlación es exactamente la condición que BRIN necesita para ser efectivo, así que particionar por fecha y usar BRIN dentro de cada partición son dos decisiones que se refuerzan mutuamente.

### Sección 6.3: Cuándo NO conviene particionar {#6-cuando-no}

Particionar no es gratis ni siempre beneficioso. Los escenarios donde el costo supera al beneficio:

- **Tabla pequeña**, que cabe cómoda en el buffer pool o `shared_buffers`. El overhead de routing y la fragmentación de índices en N estructuras no se compensa con ningún ahorro de I/O real.
- **Queries que no filtran por partition key de forma consistente**. Sin pruning, cada query termina escaneando todas las particiones, peor que un solo índice sobre una tabla monolítica.
- **Over-partitioning**, demasiadas particiones pequeñas. El catálogo crece, el planner tarda más en construir el plan porque evalúa límites contra cientos o miles de particiones, y el overhead de metadata empieza a dominar (Sección 4.3).
- **Foreign keys que referencian la tabla particionada** desde otra tabla no particionada de forma directa y simple. PostgreSQL lo soporta desde la versión 12, pero con reglas más estrictas que en una tabla normal, y conviene validar el caso específico antes de asumir que funcionará igual.

### Sección 6.4: Novedades recientes: PostgreSQL 17 {#6-pg17}

Además del push-down de partition-wise aggregate ya mencionado, PostgreSQL 17 agregó soporte de exclusion constraints e identity columns en tablas particionadas, mejoró el pruning con funciones inmutables que envuelven la partition key, soporte nativo de sub-particionamiento combinando dos criterios distintos (por ejemplo RANGE más HASH) sin depender de extensiones de terceros, y replicación lógica completa de tablas particionadas.

---

## Bloque 7: Qué es sharding y en qué se diferencia de partitioning {#7-que-es-sharding}

### Sección 7.1: La diferencia es física, no conceptual {#7-diferencia-fisica}

Sharding es la distribución horizontal de las filas de una tabla, o de un grupo de tablas relacionadas que comparten shard key, entre múltiples servidores o procesos de base de datos independientes, cada uno con su propio disco, su propio WAL, su propio lock manager, su propia memoria.

La diferencia con partitioning no es conceptual, ambos dividen filas según un criterio. La diferencia es física: partitioning nunca sale de un proceso; sharding, por definición, siempre implica más de un proceso, y por lo tanto siempre implica red entre ellos. Esa sola diferencia es la que activa todo lo que sigue en el resto de esta guía, desde el teorema CAP hasta el costo de una transacción distribuida.

```
     TABLE PARTITIONING                                 SHARDING
      un solo servidor                    varios servidores, conectados por red

┌──────────────────────────┐  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ Servidor único           │  │ Servidor A      │  │ Servidor B      │  │ Servidor C      │
│                          │  │ (shard 1)       │  │ (shard 2)       │  │ (shard 3)       │
│ ┌─────────┐  ┌─────────┐ │  │ disco propio    │  │ disco propio    │  │ disco propio    │
│ │ part. 1 │  │ part. 2 │ │  │ WAL propio      │  │ WAL propio      │  │ WAL propio      │
│ └─────────┘  └─────────┘ │  │ lock mgr propio │  │ lock mgr propio │  │ lock mgr propio │
│                          │  └─────────────────┘  └─────────────────┘  └─────────────────┘
│ un solo WAL, un solo     │           │                    │                    │
│ lock manager para ambas  │           └─────── red ────────┴─────── red ────────┘
└──────────────────────────┘

  Nunca hay red entre particiones. Una query          Siempre hay red entre shards. Una query
  que toca 2 particiones hace 2 lecturas               que toca 2 shards paga latencia de red
  de disco locales, nada más.                          más el costo de fan-out y merge.
```

### Sección 7.2: Tabla comparativa completa {#7-tabla-comparativa}

| Aspecto | Table Partitioning | Sharding |
| --- | --- | --- |
| Dónde viven los fragmentos | Mismo servidor, mismo proceso | Servidores y procesos distintos |
| Coordinación de catálogo | Un solo motor conoce todo | Requiere una capa de routing/metadata externa |
| ¿Aplica el teorema CAP? | No, no hay red entre fragmentos | Sí, hay red real entre nodos |
| ¿Requiere 2PC para una transacción multi-fragmento? | No, WAL y lock manager únicos | Sí, si la transacción cruza shards |
| Costo de reorganizar fragmentos | Bajo, es I/O local únicamente | Alto, implica transferencia de datos por red |

---

## Bloque 8: Shard key: tipos y consistent hashing {#8-shard-key}

### Sección 8.1: Range sharding {#8-range-sharding}

Cada shard cubre un intervalo continuo, por ejemplo `user_id` de 1 a 1,000,000 en el shard 1, de 1,000,001 a 2,000,000 en el shard 2. La ventaja es que permite range queries eficientes y la posibilidad de archivar o desactivar shards completos. El riesgo principal aparece cuando el criterio de rango coincide con el patrón temporal del tráfico, por ejemplo IDs autoincrementales o fechas: el shard más reciente concentra desproporcionadamente la escritura, lo que se conoce como hot shard (Bloque 10).

```
RANGE SHARDING — por user_id (3 servidores físicos distintos, conectados por red)

┌──────────────────────┐  ┌──────────────────────┐  ┌────────────────────────┐
│ Shard 1 (Servidor A) │  │ Shard 2 (Servidor B) │  │ Shard 3 (Servidor C)   │
│ user_id 1-1,000,000  │  │ user_id 1,000,001-   │  │ user_id 2,000,001-     │
│                      │  │ 2,000,000            │  │ 3,000,000 (los nuevos) │
└──────────────────────┘  └──────────────────────┘  └────────────────────────┘
                                                                 ▲
                                              los usuarios nuevos (IDs más altos)
                                              siempre golpean este shard: hot shard
```

### Sección 8.2: List sharding {#8-list-sharding}

Asignación explícita por valores discretos, como país o tenant. Da control regulatorio explícito, por ejemplo mantener los datos de la Unión Europea en un shard o región específica por cumplimiento de GDPR, pero hereda el mismo riesgo de desbalance si la distribución real de negocio no es uniforme entre categorías.

```
LIST SHARDING — por país (3 servidores físicos distintos, conectados por red)

┌──────────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐
│ Shard EU (Frankfurt)     │  │ Shard US (Virginia) │  │ Shard APAC (Tokio)  │
│ país IN ('DE','FR','ES') │  │ país IN ('US','CA') │  │ país IN ('JP','SG') │
└──────────────────────────┘  └─────────────────────┘  └─────────────────────┘

  Control regulatorio explícito: los datos de la UE nunca salen de ese shard.
```

### Sección 8.3: Hash sharding {#8-hash-sharding}

`hash(shard_key) % N` determina el shard destino. Al ser pseudoaleatorio y uniforme, distribuye la carga de escritura y el volumen de forma pareja sin importar la distribución real de los datos de negocio. El costo es perder por completo la localidad y el orden: una query de rango (`BETWEEN`, `ORDER BY` sobre la shard key) ya no puede aprovechar ningún pruning, requiere fan-out completo a todos los shards.

```
HASH SHARDING — hash(user_id) % 3 (3 servidores físicos distintos, conectados por red)

┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐
│ Shard 0             │  │ Shard 1             │  │ Shard 2             │
│ hash(user_id)%3 = 0 │  │ hash(user_id)%3 = 1 │  │ hash(user_id)%3 = 2 │
└─────────────────────┘  └─────────────────────┘  └─────────────────────┘

  Distribución uniforme, pero WHERE user_id BETWEEN X AND Y ya no
  puede saltarse ningún shard, hay que preguntarles a los 3 siempre.
```

### Sección 8.4: El problema del hash simple, y consistent hashing {#8-consistent-hashing}

El problema con un hash simple es que cambiar N, agregar o quitar un servidor, reasigna la ubicación de prácticamente todas las filas existentes, porque el resultado del módulo cambia para casi cualquier valor de key cuando cambia el divisor. Esto convierte agregar un solo servidor nuevo en una migración masiva de datos.

**Consistent hashing** resuelve esto mapeando tanto los shards como las keys a puntos en un espacio circular, un anillo, típicamente de 0 a 2³²−1 o 2⁶⁴−1. Cada shard ocupa uno o más puntos en el anillo (posiciones calculadas con `hash(nombre_del_shard)`). Cada key se ubica en el anillo con `hash(key)`, y se asigna al primer shard que se encuentre recorriendo el anillo en sentido horario. Cuando se agrega un nuevo shard, solo se reasignan las keys que caían en el segmento del anillo que ahora pertenece al nuevo nodo, en promedio solo 1/N de los datos totales se mueven, en vez de casi el 100%.

```
EL ANILLO DE CONSISTENT HASHING
(representado como una línea que se cierra sobre sí misma: el final vuelve al inicio)

0 ─────●───────────●─────────────●────────────●──────── 2³²−1 (= 0, el anillo se cierra aquí)
      key1      Shard A        key3         Shard B
                    │                          │
        key1 busca hacia la derecha   key3 y key7 buscan hacia la derecha
        y encuentra Shard A           y encuentran Shard B primero

  Si se agrega un Shard C justo antes de Shard B, SOLO las keys entre
  Shard A y el nuevo Shard C se reasignan a él. El resto del anillo
  (incluyendo key1 → Shard A) no se entera del cambio, no se mueve.
```

### Sección 8.5: Virtual nodes {#8-vnodes}

Sistemas como Cassandra y DynamoDB usan una variante con **virtual nodes** (vnodes): en vez de que cada servidor físico ocupe un solo punto en el anillo, ocupa cientos de puntos virtuales distribuidos. Esto mejora el balanceo de carga cuando los servidores tienen capacidades distintas, y suaviza aún más el movimiento de datos al escalar. En Cassandra, el número de vnodes por nodo se controla con `num_tokens`, y aunque el valor histórico por defecto fue 256, las guías más recientes recomiendan valores bastante menores, como 16, porque mejoran el rendimiento de streaming al mover datos entre nodos. Más vnodes dan mejor balanceo pero también más overhead de gestión de tokens, es un trade-off, no una mejora sin costo.

```
SIN VNODES (1 punto por servidor)          CON VNODES (4 puntos por servidor)

0 ───●─────────────●──────── 2³²−1        0 ─●──●───●─────●──●───●────●──●─── 2³²−1
    Shard A       Shard B                    A1  B1  A2    B2 A3  B3   A4 B4

  Shard A ocupa un solo tramo grande.        Los tramos de A y B quedan
  Si A es más lento que B, igual              entrelazados y repartidos,
  recibe la misma fracción del anillo.         sin importar el orden en
                                                que se agregaron los nodos.
```

---

## Bloque 9: Arquitectura de enrutamiento y co-location {#9-arquitectura-enrutamiento}

### Sección 9.1: Router / Query Coordinator {#9-router}

Sharding no funciona "solo". Necesita una capa adicional que no existe en un motor de base de datos monolítico: un componente que recibe la query del cliente, calcula o consulta a qué shard o shards debe ir según la shard key presente en el predicado, reenvía la query, y si es necesario agrega resultados de múltiples shards (fan-out más merge). Ejemplos concretos de este rol: `mongos` en MongoDB, el coordinator node en Citus, `vtgate` en Vitess.

```
                Cliente
                   │
                   ▼
        ┌────────────────────┐
        │ Router             │  ← mongos / vtgate / Citus coordinator
        │ consulta el        │
        │ metadata service   │
        │ para saber a dónde │
        │ enrutar            │
        └────────────────────┘
             │     │    │
     ┌───────┘     │    └────────┐
     ▼             ▼             ▼
┌─────────┐   ┌─────────┐   ┌─────────┐
│ Shard 1 │   │ Shard 2 │   │ Shard 3 │
└─────────┘   └─────────┘   └─────────┘

  Query de un solo shard: cliente → router → shard → router → cliente (1 salto extra).
  Query cross-shard: el router hace fan-out a varios shards y arma el merge del resultado.
```

Esta capa introduce un salto de red adicional incluso para queries de un solo shard (cliente → router → shard → router → cliente), comparado con hablar directo a un motor monolítico. Es un costo fijo pequeño pero real, que los sistemas bien diseñados minimizan colocando el router cerca de los shards y con caching agresivo del mapeo de metadata en el cliente o en el propio router.

### Sección 9.2: Metadata / Catalog service {#9-metadata-service}

Mantiene el mapeo actualizado de qué rango, hash o lista corresponde a qué shard físico. Este servicio es en sí mismo un punto crítico, típicamente se implementa con un sistema de consenso (etcd, ZooKeeper, o un catálogo replicado con Raft), porque si el catálogo se desincroniza del estado real, el router puede enviar queries al shard equivocado.

### Sección 9.3: Co-location: la decisión de diseño más importante {#9-co-location}

Cuando se shardean varias tablas relacionadas usando la misma shard key (por ejemplo usuarios y pedidos, ambas por `user_id`), se garantiza que todos los datos de una misma entidad de negocio vivan físicamente en el mismo shard. Esto convierte JOINs que de otra forma serían cross-shard (caros, con fan-out) en JOINs locales dentro de un solo servidor, tan baratos como en un motor no distribuido.

```
SIN co-location                        CON co-location (misma shard key)

usuarios (shard por user_id)           usuarios (shard por user_id)
pedidos (shard por pedido_id)          pedidos (shard TAMBIÉN por user_id)

┌──────────────┐   ┌───────────────┐   ┌───────────────────────┐
│ Shard 1      │   │ Shard 1       │   │ Shard 1               │
│ usuarios     │   │ pedidos       │   │ usuarios (user 1-100) │
│ (user 1-100) │   │ (pedido 1-50) │   │ + pedidos de esos     │
└──────────────┘   └───────────────┘   │ mismos usuarios       │
                                       └───────────────────────┘

El pedido de un usuario puede vivir    El JOIN usuarios + pedidos
en un shard distinto al del usuario:   por user_id siempre se
el JOIN por user_id puede cruzar shards.   resuelve LOCAL, sin red.
```

Herramientas como Citus formalizan esto con el concepto de tablas distribuidas co-localizadas: si dos tablas se distribuyen con la misma columna de distribución y el mismo número de shards, el planner puede hacer push-down del JOIN completo a cada nodo trabajador sin necesidad de mover datos entre ellos en tiempo de ejecución (Sección 13.6).

La calidad de una shard key no se mide por qué tan bien distribuye el volumen de almacenamiento, se mide por qué porcentaje de las operaciones reales y frecuentes del sistema se resuelven dentro de un solo shard.

---

## Bloque 10: Hot shards {#10-hot-shards}

### Sección 10.1: Causas {#10-causas}

Un hot shard ocurre cuando, a pesar de una partición "correcta" según el criterio elegido, la carga real (CPU, I/O, conexiones concurrentes) se concentra desproporcionadamente en un servidor. Las causas típicas son:

- **Range sharding sobre una columna monótonamente creciente** (timestamps, IDs autoincrementales): todo el tráfico de escritura reciente golpea el último shard.
- **List sharding sobre una categoría con distribución real desigual**, por ejemplo el 70% de los usuarios concentrados en un solo país.
- **Claves de acceso "célebres"** (celebrity keys): en sistemas tipo red social, un usuario con millones de seguidores concentra lecturas y escrituras en un solo shard sin importar el método usado.

```
CARGA REAL POR SHARD — range sharding por timestamp

  Shard 1 (datos de hace un mes)  ██                    5% del tráfico de escritura
  Shard 2 (datos de la semana)    ██                    5% del tráfico de escritura
  Shard 3 (datos de HOY)          ████████████████████  90% del tráfico  ← hot shard

  Los tres shards tienen el mismo tamaño de datos en disco, pero el
  shard 3 recibe casi todo el tráfico de escritura, porque cualquier
  INSERT nuevo cae ahí por diseño (el timestamp siempre es creciente).
```

### Sección 10.2: Mitigaciones {#10-mitigaciones}

- Usar hash o consistent hashing para dispersar sin depender de la semántica del dato.
- Aplicar **salting**: agregar un sufijo aleatorio o calculado a la shard key para partir artificialmente una clave caliente en sub-claves. DynamoDB documenta oficialmente este patrón bajo el nombre de *write sharding* (Sección 12.5), con dos variantes concretas: un sufijo aleatorio, que maximiza la distribución de escritura pero obliga a hacer fan-out en la lectura, o un sufijo calculado (por ejemplo un hash del identificador módulo un número fijo), que logra la misma distribución de escritura pero permite reconstruir la clave exacta para leer sin fan-out.

```
SIN salting                      CON salting (sufijo calculado, hash % 3)

partition key = "2026-07-22"     "2026-07-22#0" (1/3 del tráfico)
             │                   "2026-07-22#1" (1/3 del tráfico)
             ▼                   "2026-07-22#2" (1/3 del tráfico)
      ┌─────────────┐               │         │         │
      │ 1 partición │               ▼         ▼         ▼
      │ recibe TODO │            ┌────┐    ┌────┐    ┌────┐
      │ el tráfico  │            │ #0 │    │ #1 │    │ #2 │
      │ del día     │            └────┘    └────┘    └────┘
      └─────────────┘
```
- **Rebalanceo dinámico**, donde el sistema detecta shards sobrecargados y migra rangos activamente. Es lo que hace Vitess con sus resharding workflows online (Sección 13.4), y también el mecanismo de adaptive capacity de DynamoDB (Sección 12.5).

### Sección 10.3: Dónde se refleja el costo real {#10-costo-real}

| Tipo de operación | Dentro de 1 shard | Cruza N shards |
| --- | --- | --- |
| Lectura | Tan barata como un motor normal, el router va directo | Fan-out más merge en el coordinador; la latencia es la del shard más lento |
| Escritura | Commit local normal, sin coordinación externa | Requiere 2PC o equivalente: latencia, locks retenidos, y riesgo de bloqueo |

El costo de sharding, entonces, no es un impuesto fijo que se paga siempre, es directamente proporcional a qué porcentaje del tráfico real de la aplicación necesita cruzar la frontera entre shards. Una shard key bien diseñada, alineada con los patrones de acceso reales y no solo con el volumen de almacenamiento, puede mantener ese porcentaje bajo incluso a gran escala.

---

## Bloque 11: Sharding en MongoDB {#11-mongodb}

### Sección 11.1: Arquitectura del cluster shardeado {#11-arquitectura}

Un cluster shardeado de MongoDB tiene tres piezas. Los **shards** son donde viven los datos, y cada uno debe desplegarse como un replica set, no como un servidor único, para tener alta disponibilidad. Los **config servers** guardan el metadata completo del cluster (la lista de chunks por shard y los rangos que define cada uno, permisos, y locks distribuidos), y también deben desplegarse como un replica set (CSRS), sin árbitros ni miembros con delay, con todos sus índices habilitados; las escrituras usan `writeConcern: "majority"` y las lecturas `readConcern: "majority"`. Si el CSRS pierde su primary y no puede elegir uno nuevo, el metadata queda de solo lectura (no hay splits ni migraciones de chunks), aunque los shards siguen sirviendo lecturas y escrituras con normalidad. **`mongos`** actúa como el query router: cachea el metadata de los config servers y lo usa para enrutar cada operación al shard correcto, ocultando toda esta complejidad al cliente.

Desde MongoDB 8.0, existe la opción de **config shards**, donde el config server puede además guardar datos de aplicación, reduciendo el número de nodos necesarios, aunque un config server dedicado sigue siendo lo recomendado cuando se necesita alta disponibilidad exigente.

```
                      Aplicación
                           │
                           ▼
                      ┌────────┐
                      │ mongos │  ← query router, cachea el metadata
                      └────────┘
                           │ consulta el metadata
                           ▼
               ┌───────────────────────┐  ← replica set: qué chunk vive
               │ Config Servers (CSRS) │     en qué shard
               │ replica set           │
               └───────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│ Shard 1       │  │ Shard 2       │  │ Shard 3       │  ← cada shard es, a su vez,
│ (replica set) │  │ (replica set) │  │ (replica set) │     un replica set completo
└───────────────┘  └───────────────┘  └───────────────┘     (primary + secondaries)
```

### Sección 11.2: Chunks y el balancer {#11-chunks-balancer}

Un **chunk** es un rango contiguo de valores de shard key, con un límite inferior inclusivo y uno superior exclusivo, asignado a un shard. El tamaño por defecto verificado es **128 MB**. Cuando una colección se puebla por primera vez, empieza como un solo chunk grande que el balancer va dividiendo; una colección vacía sin zonas definidas, con hashed sharding, arranca con 2 chunks por shard por defecto, mientras que con ranged sharding arranca con un solo chunk vacío que cubre todo el rango.

Un **split** ocurre automáticamente cuando un chunk excede el tamaño configurado. El **balancer** migra chunks entre shards cuando la diferencia de datos entre el shard más grande y el más chico de una colección supera un umbral documentado: al menos 3 veces el tamaño de rango configurado, lo que con el default de 128 MB significa 384 MB de diferencia mínima antes de disparar una migración.

Existe un caso especial documentado, los **chunks "jumbo"**: chunks que exceden el tamaño configurado pero no se pueden dividir porque representan un solo valor de shard key (baja cardinalidad en ese punto). Se resuelven con `refineCollectionShardKey`, con un resharding completo (Sección 11.5), o limpiando el flag jumbo manualmente. Desde MongoDB 8.0, `sh.shardAndDistributeCollection()` combina shardear y reshardear en un solo paso, para distribuir de inmediato sin esperar al balancer en background.

```
ANTES del balanceo                          DESPUÉS (el balancer migró un chunk)

  Shard 1: [chunk A][chunk B][chunk C]        Shard 1: [chunk A][chunk B]
  Shard 2: (vacío)                            Shard 2: [chunk C]

  diferencia = 3 chunks vs 0                  diferencia = 2 vs 1
  supera el umbral → dispara migración        dentro del umbral → se detiene

  Un chunk "jumbo" (un solo valor de shard key) queda marcado aparte
  y no participa en el split normal, aunque exceda el tamaño límite.
```

### Sección 11.3: Shard key selection {#11-shard-key-selection}

MongoDB formaliza tres criterios oficiales para elegir una shard key, más específicos que simplemente "un hash uniforme":

- **Cardinalidad**: cuántos valores distintos puede tomar la key. Alta cardinalidad por sí sola no garantiza buena distribución.
- **Frecuencia**: qué tan seguido se repite un mismo valor. Baja frecuencia por sí sola tampoco lo garantiza.
- **Monotonicidad**: si el valor crece o decrece de forma monótona (timestamps, IDs autoincrementales), los inserts nuevos se concentran en un solo chunk, el del extremo del rango, generando un hot shard de escritura (Bloque 10).

**Ranged sharding** es eficiente cuando la key tiene alta cardinalidad, baja frecuencia, y cambia de forma no monótona, y permite range queries eficientes. **Hashed sharding** es la solución que MongoDB recomienda explícitamente cuando hay que shardear por un campo monótono, como un timestamp: distribuye las escrituras de forma uniforme pero impide range queries eficientes, porque valores relacionados quedan esparcidos entre shards. También existen las **compound shard keys**, que combinan varios campos para mejorar la cardinalidad o la distribución sin perder la utilidad de un rango.

### Sección 11.4: Zones {#11-zones}

Una zone es una agrupación nombrada de shards, a la que se le asignan rangos de shard key. Los usos documentados incluyen requisitos multi-región (por ejemplo, mantener datos de la Unión Europea y de Estados Unidos en zonas separadas por cumplimiento normativo), tiering por hardware (datos calientes en almacenamiento NVMe rápido, datos fríos en disco mecánico), y aislamiento por tenant o cliente. El balancer respeta las zonas primero, y balancea dentro de cada zona después. Si una zona no tiene ningún shard asignado pero existen datos que caen en su rango, esos chunks quedan sin poder migrar.

### Sección 11.5: Resharding {#11-resharding}

Antes de MongoDB 5.0, la shard key de una colección era, en la práctica, inmutable, y cambiarla exigía una migración manual completa. Desde la versión 5.0, `sh.reshardCollection()` permite cambiar la shard key de una colección ya shardeada. Tiene restricciones documentadas: solo se puede reshardear una colección a la vez; si la colección tiene un índice único, la nueva shard key debe satisfacer ese constraint; no está soportado en los tiers gratuitos o serverless (M0/Flex); y el proceso consume recursos reales de disco, I/O y CPU mientras dura.

### Sección 11.6: Transacciones multi-shard {#11-transacciones}

MongoDB usa un **2PC coordinado por uno de los propios shards**, designado como coordinador para esa transacción. La fase prepare envía `prepareTransaction` a cada shard participante, lo que bloquea los documentos afectados, y solo cuando todos confirman, el coordinador envía `commitTransaction` a todos. El límite de tiempo por defecto es **60 segundos**, controlado por `transactionLifetimeLimitSeconds`, y debe modificarse en todos los miembros de cada replica set shard para que aplique de forma consistente. Se puede afinar con `maxTimeMS` al llamar `commitTransaction`, pero ese valor nunca puede exceder `transactionLifetimeLimitSeconds`.

---

## Bloque 12: Sharding en Cassandra y DynamoDB {#12-cassandra-dynamodb}

### Sección 12.1: Cassandra: partition key vs. clustering key {#12-cassandra-keys}

El modelo de datos de Cassandra distingue dos conceptos que conviene no confundir con un simple "shard key". La clave primaria de una tabla CQL se compone de una **partition key**, obligatoria y que puede ser compuesta, más cero o más columnas de **clustering key**, opcionales. La partition key determina en qué nodo (o nodos réplica) vive la fila, vía hash consistente. La clustering key ordena físicamente las filas dentro de esa partición. Todas las filas que comparten la misma partition key viven garantizadamente en el mismo conjunto de nodos réplica, sin importar cuántos valores de clustering key tengan.

```
Tabla CQL: mensajes  (partition key = chat_id, clustering key = timestamp)

  Nodo A (posee el token que le corresponde a chat_id = 42)
  ┌────────────────────────────────────┐
  │ Partición: chat_id = 42            │
  │                                    │
  │ ┌────────────────────────────────┐ │  ← ordenadas por
  │ │ timestamp=10:00 | "hola"       │ │     clustering key
  │ │ timestamp=10:05 | "qué tal"    │ │     DENTRO del nodo
  │ │ timestamp=10:07 | "bien y tú?" │ │
  │ └────────────────────────────────┘ │
  └────────────────────────────────────┘

  Todas las filas de chat_id = 42 viven juntas en el mismo nodo (o los
  mismos nodos réplica), sin importar cuántos mensajes tenga ese chat.
```

### Sección 12.2: Cassandra: token ranges y vnodes {#12-cassandra-tokens}

Cada fila se mapea a un token numérico, el resultado de aplicar hash a la partition key, y cada nodo posee uno o más rangos de tokens del anillo. Con vnodes, cada nodo físico ocupa `num_tokens` posiciones aleatorias en el anillo en vez de una sola, exactamente el mecanismo descrito en la Sección 8.5.

**Replication factor** y **consistency level** son conceptos relacionados pero distintos del particionamiento en sí. El replication factor define cuántas copias existen de cada partición; el consistency level (`ONE`, `QUORUM`, `LOCAL_QUORUM`, `ALL`) define cuántas réplicas deben responder para que una operación se considere exitosa. Con un replication factor de 3, `LOCAL_QUORUM` exige que respondan (3/2)+1, es decir 2 réplicas dentro del datacenter local.

### Sección 12.3: Cassandra: modelado query-first y wide partitions {#12-cassandra-modelado}

Cassandra se modela diseñando las tablas según las queries que se necesitan ejecutar, no según normalización relacional, denormalización deliberada como punto de partida, no como optimización posterior.

Existe un límite práctico documentado sobre el tamaño de una partición: idealmente debería mantenerse bajo 100 MB, y mejor todavía por debajo de 10 MB. El límite técnico absoluto es de 2 mil millones de celdas, pero no es un tamaño usable en la práctica. Una partición sin límite de crecimiento (unbounded) es un anti-patrón explícitamente documentado, porque debe caber en memoria durante la compaction, genera hotspots, y degrada las lecturas. Cada `DELETE` en Cassandra crea un **tombstone**, un marcador de borrado que debe escanearse en cada lectura hasta que la compaction lo purgue definitivamente; el patrón de insertar y borrar continuamente sobre la misma partición genera una acumulación masiva de tombstones que degrada el rendimiento de lectura de esa partición específica.

```
PARTICIÓN SANA (acotada)     PARTICIÓN "UNBOUNDED" (anti-patrón)

partition key: chat_id = 42     partition key: user_id = 7
┌───────────────┐     (todos sus eventos, para siempre)
│ ~200 mensajes │     ┌───────────────────────────────┐
│ unos pocos KB │     │ millones de filas, crece sin  │
└───────────────┘     │ parar + tombstones de DELETEs │
                      │ viejos aún sin compactar      │
                      └───────────────────────────────┘

                      Tiene que caber en memoria durante
                      la compaction: hotspot, lecturas lentas.
```

### Sección 12.4: DynamoDB: partition key, sort key y límites físicos {#12-dynamodb-keys}

Con una clave simple (solo partition key), DynamoDB hashea el valor para determinar la partición física. Con una clave compuesta (partition key más sort key), los ítems que comparten la misma partition key se agrupan físicamente y se ordenan por sort key, lo que AWS llama una "item collection", y no existe un límite superior documentado de valores de sort key por partition key.

Los límites por partición física están verificados con números exactos: cada partición soporta un máximo de **3,000 unidades de lectura por segundo** y **1,000 unidades de escritura por segundo**, y DynamoDB hace un split automático de la partición al llegar a **10 GB** de almacenamiento, o al exceder sostenidamente esos límites de throughput. A diferencia de Cassandra, donde el desarrollador ve directamente el modelo de particiones, en DynamoDB la gestión de particiones es completamente automática y transparente, el usuario nunca las administra a mano.

```
Tabla: pedidos  (partition key = customer_id, sort key = order_date)

  Partición física (todo lo que hashea a customer_id = "C001")
  ┌────────────────────────────────────┐
  │ "item collection" de C001          │
  │ order_date=2026-01-10 → pedido #1  │
  │ order_date=2026-03-22 → pedido #2  │  ← ordenados por sort key
  │ order_date=2026-07-01 → pedido #3  │
  └────────────────────────────────────┘

  Límite físico de esta partición: 3,000 RCU/s, 1,000 WCU/s, 10 GB.
  Al exceder cualquiera de los tres, DynamoDB hace split automático.
```

### Sección 12.5: DynamoDB: adaptive capacity y write sharding {#12-dynamodb-adaptive}

**Adaptive capacity** es el mecanismo que detecta particiones con tráfico desproporcionado (hot partitions) y las divide automáticamente para duplicar la capacidad disponible en esa zona. Por ejemplo, una partición al límite de 1,000 WCU se convierte en dos particiones de 1,000 WCU cada una, 2,000 WCU en total. Aplica tanto en modo on-demand como en modo provisioned, pero no es ilimitado: si se excede la capacidad total provisionada de la tabla completa, de todas formas hay throttling.

Para partition keys con acceso muy concentrado (por ejemplo, la fecha de hoy en una tabla de eventos), AWS documenta oficialmente el patrón de **write sharding**: agregar un sufijo a la partition key. Existen dos variantes documentadas. La primera es un **sufijo aleatorio** (por ejemplo de `.1` a `.200`), que maximiza la distribución de escritura pero obliga a hacer fan-out de N queries más merge en la aplicación para leer todos los ítems de un día. La segunda es un **sufijo calculado** (por ejemplo un hash del identificador del pedido módulo 200), que logra la misma distribución de escritura pero permite reconstruir la partition key exacta para hacer un `GetItem` directo sin fan-out.

### Sección 12.6: DynamoDB: GSI vs. LSI {#12-dynamodb-gsi-lsi}

Un **Global Secondary Index** (GSI) tiene su propio esquema de partición, completamente independiente del de la tabla base, su propio throughput provisionado, y no cuenta contra el límite de 10 GB de la tabla base. Un **Local Secondary Index** (LSI) comparte la partition key de la tabla base, vive dentro de la misma partición física que su ítem correspondiente, y comparte throughput con la tabla base.

```
Tabla base (partition key = customer_id)                       GSI (partition key = status, propio)

┌────────────────────┐                       ┌──────────────────────┐
│ Partición física A │                       │ Partición física X'  │  ← esquema de partición
│ customer_id = C001 │                       │ status = 'pendiente' │     INDEPENDIENTE,
└────────────────────┘                       └──────────────────────┘     su propio throughput

LSI (comparte la partition key de la tabla base)
┌────────────────────┐
│ Partición física A │  ← la MISMA partición física que la tabla
│ customer_id = C001 │     base, comparte su throughput con ella
│ + índice por otro  │
│ sort key           │
└────────────────────┘
```

---

## Bloque 13: Sharding sobre motores relacionales: Vitess y Citus {#13-vitess-citus}

### Sección 13.1: MySQL partitioning: lo que cambia respecto a PostgreSQL {#13-mysql-partitioning}

Antes de entrar en las herramientas de sharding, vale la pena consolidar en un solo lugar las diferencias de MySQL frente a PostgreSQL que ya se mencionaron dispersas en bloques anteriores: los cuatro tipos de partición son equivalentes en espíritu (RANGE, LIST, HASH, y KEY como variante propia de MySQL, Sección 2.3), pero MySQL prohíbe foreign keys en tablas particionadas sin excepción (Sección 4.4), impone un límite duro de 8192 particiones (Sección 4.3), y su partition pruning no cubre runtime pruning para valores conocidos solo en tiempo de ejecución (Sección 3.3). La diferencia física de fondo, InnoDB como clustered index por partición frente al modelo heap de PostgreSQL, ya se explicó en la Sección 1.2, y es la razón última detrás de varias de estas diferencias de comportamiento.

### Sección 13.2: Vitess: arquitectura {#13-vitess-arquitectura}

Vitess nació dentro de YouTube en 2010 para escalar MySQL horizontalmente sin meter lógica de sharding directamente en el código de la aplicación, se liberó como open source en 2011, fue donado a la CNCF en 2018 y se graduó como proyecto CNCF en 2019. Hoy lo usan, además de YouTube, empresas como GitHub, Slack o Square.

Su arquitectura tiene cuatro piezas. **VTGate** es el proxy de routing: recibe la query del cliente, la parsea, y la enruta según el VSchema. **VTTablet** corre junto a cada instancia de MySQL, y da pooling de conexiones y reescritura de queries. El **Topology Service** (típicamente etcd, Consul o ZooKeeper) es el almacén de metadata que mantiene el estado del cluster, la información de shards, y la ubicación de cada tablet, para service discovery. El **VSchema** define la topología de la base de datos y las reglas de sharding. El flujo de una query es siempre: cliente → VTGate → uno o más VTTablet → el shard o shards de MySQL correspondientes.

```
 Aplicación (habla el protocolo de MySQL)
                     ▼
                ┌────────┐
                │ VTGate │  ← parsea, consulta el VSchema, enruta
                └────────┘
                     │
      ┌──────────────┼──────────────┐
      ▼              ▼              ▼
┌───────────┐  ┌───────────┐  ┌───────────┐
│ VTTablet  │  │ VTTablet  │  │ VTTablet  │  ← pooling + reescritura
└───────────┘  └───────────┘  └───────────┘     de queries
      ▼              ▼              ▼
┌───────────┐  ┌───────────┐  ┌───────────┐
│ MySQL     │  │ MySQL     │  │ MySQL     │  ← shard 1, 2, 3
│ (shard 1) │  │ (shard 2) │  │ (shard 3) │
└───────────┘  └───────────┘  └───────────┘

     Topology Service (etcd / Consul / ZooKeeper)
     consultado por VTGate y por cada VTTablet
```

### Sección 13.3: Vitess: vindexes {#13-vitess-vindexes}

Un **Vindex** es un mecanismo más sofisticado que un simple `hash(key) % N`: mapea un valor de columna a un Keyspace ID, y cada shard cubre un rango de esos IDs. El **Primary Vindex** determina en qué shard se inserta una fila, y debe ser único. Los **Secondary Vindex** existen para resolver un problema real: una query que filtra por una columna distinta a la shard key no debería tener que hacer fan-out a todos los shards para encontrar la fila.

Un **lookup vindex** resuelve exactamente eso manteniendo una tabla de respaldo en MySQL que guarda el mapeo columna → Keyspace ID. Al insertar una fila, el vindex primario genera el Keyspace ID, y ese mapeo se guarda en la tabla lookup. En queries posteriores que filtran por esa columna secundaria, VTGate consulta la tabla lookup para saber a qué shard o shards enrutar, en vez de consultarlos todos. El costo de este mecanismo es una escritura adicional por cada `INSERT`, para mantener la tabla lookup sincronizada.

```
INSERT INTO pedidos (id, customer_email) VALUES (501, 'ana@x.com');

  1. Primary Vindex (sobre "id"): hash(501) → Keyspace ID → resuelve a shard 2
  2. Se escribe la fila directo en shard 2
  3. Se agrega a la tabla lookup: 'ana@x.com' → Keyspace ID de shard 2

SELECT * FROM pedidos WHERE customer_email = 'ana@x.com';

  1. VTGate consulta la tabla lookup: 'ana@x.com' → Keyspace ID → shard 2
  2. VTGate envía la query SOLO al shard 2, sin fan-out a los demás
```

### Sección 13.4: Vitess: resharding online con VReplication {#13-vitess-resharding}

El resharding en Vitess es un workflow de cinco etapas, orquestado por **VReplication**. Primero se crea el workflow especificando los shards de origen y de destino. VReplication copia los datos existentes, y en paralelo replica los cambios que van llegando en vivo, para mantener los shards nuevos sincronizados con el tráfico real mientras dura la copia. Cuando están al día, `SwitchTraffic` redirige el tráfico de la aplicación, y VTGate puede *bufferear* queries durante ese corte, así que el impacto visible para el cliente es casi nulo. Finalmente, `complete` limpia los artefactos de replicación y libera los shards de origen. Todo el proceso es reversible con `ReverseTraffic`, y valida los datos migrados con `VDiff` antes de confirmarse.

La ventaja frente a un resharding manual es doble: la copia inicial y la replicación de cambios en vivo ocurren de forma simultánea, no en dos fases separadas, y el corte de tráfico es casi imperceptible gracias al buffering de VTGate.

### Sección 13.5: Citus: arquitectura y distribution column {#13-citus-arquitectura}

Citus es una extensión de PostgreSQL, no un motor aparte. Un nodo **coordinator** recibe todas las queries de la aplicación y las enruta, ya sea a un solo worker si los datos viven en un solo shard, o en paralelo a varios workers, acumulando los resultados después. El coordinator mantiene tablas de metadata (`pg_dist_shard`) que registran a qué worker pertenece cada shard.

Citus asigna filas a shards mediante un hash de la **distribution column**, y `pg_dist_shard` guarda los rangos de hash que le corresponden a cada shard. El número de shards por tabla es configurable al momento de distribuirla, la documentación oficial no fija un valor universal, depende del caso de uso concreto.

```
Aplicación (habla el protocolo de PostgreSQL)
                     ▼
              ┌─────────────┐
              │ Coordinator │  ← guarda pg_dist_shard (metadata)
              └─────────────┘
                     │
      ┌──────────────┼──────────────┐
      ▼              ▼              ▼
┌───────────┐  ┌───────────┐  ┌───────────┐
│ Worker 1  │  │ Worker 2  │  │ Worker 3  │  ← cada uno un
│ shard_101 │  │ shard_102 │  │ shard_103 │     PostgreSQL normal
│ shard_104 │  │ shard_105 │  │ shard_106 │
└───────────┘  └───────────┘  └───────────┘
```

### Sección 13.6: Citus: reference tables y co-location {#13-citus-reference-tables}

Una **reference table** es una tabla completa, no dividida, replicada en un solo shard que vive copiado en todos los workers. Sirve para datos pequeños de referencia (catálogos, tipos enumerados) que necesitan hacer JOIN local sin importar cuál sea la distribution column de la tabla grande con la que se unen. Citus aplica 2PC automáticamente en las modificaciones de una reference table, para garantizar que todas sus copias queden consistentes entre sí.

```
       Worker 1                 Worker 2                 Worker 3
┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐
│ shard_101 (parte    │  │ shard_102 (parte    │  │ shard_103 (parte    │
│ de "pedidos")       │  │ de "pedidos")       │  │ de "pedidos")       │
│                     │  │                     │  │                     │
│ paises (COPIA       │  │ paises (COPIA       │  │ paises (COPIA       │
│ COMPLETA, reference │  │ COMPLETA, reference │  │ COMPLETA, reference │
│ table)              │  │ table)              │  │ table)              │
└─────────────────────┘  └─────────────────────┘  └─────────────────────┘

JOIN pedidos + paises: cada worker lo resuelve LOCAL, porque su
copia de "paises" siempre está ahí mismo, sin importar la distribution
column de "pedidos".
```

La **co-location**, ya introducida de forma general en la Sección 9.3, tiene aquí su forma concreta: si dos tablas comparten la misma distribution column y el mismo número de shards, Citus garantiza que los shards con los mismos valores de partición vivan en el mismo worker, lo que habilita el push-down del JOIN completo sin mover datos. Esto se puede forzar explícitamente con el parámetro `colocate_with` al distribuir una tabla nueva.

### Sección 13.7: Citus: schema-based sharding {#13-citus-schema-based}

Introducido en Citus 12.0 (mediados de 2024), el schema-based sharding cambia el enfoque: en vez de distribuir filas dentro de una tabla, cada tenant obtiene su propio schema de PostgreSQL completo, y solo hace falta cambiar el `search_path` de la sesión, sin modificar ninguna query. El trade-off documentado es explícito: soporta menos tenants por nodo (entre 1 y 10 mil) que el sharding tradicional por filas (1 millón o más), pero es la opción recomendada para aplicaciones que no se pueden reescribir para incluir la distribution column en cada query.

```
Row-based sharding (Sección 13.5)              Schema-based sharding (esta sección)

1 tabla "pedidos", dividida en shards            Worker 1: schema tenant_A (tablas completas)
por hash(tenant_id)                            Worker 2: schema tenant_B (tablas completas)
┌─────────┐ ┌─────────┐ ┌─────────┐            Worker 3: schema tenant_C (tablas completas)
│ shard 1 │ │ shard 2 │ │ shard 3 │
└─────────┘ └─────────┘ └─────────┘            Cambiar de tenant = cambiar el search_path
                                               de la sesión, ninguna query se toca.
Soporta 1 millón+ tenants por nodo

                                               Soporta entre 1,000 y 10,000 tenants
                                               por nodo, cada uno con su propio schema.
```

---

## Bloque 14: Motores nacidos distribuidos: CockroachDB y Spanner {#14-nacidos-distribuidos}

### Sección 14.1: CockroachDB: ranges y Raft {#14-cockroachdb-ranges}

CockroachDB divide los datos en fragmentos llamados **ranges**, sobre un keyspace ordenado globalmente. El tamaño por defecto verificado es **512 MB** (cambió desde 64 MB en versiones anteriores a la 20.1). Cada range se replica, por defecto, **3 veces**, cada réplica en un nodo distinto, coordinadas vía el protocolo de consenso **Raft**. Al alcanzar el límite de tamaño, un range se divide en dos (range split), lo que crea un nuevo grupo Raft con los mismos miembros que tenía el range original.

```
Keyspace ordenado globalmente, dividido en ranges de 512 MB

Range 1 [claves a...m]       Range 2 [claves n...z]
┌─────────────────────┐      ┌─────────────────────┐
│ Nodo A (líder Raft) │      │ Nodo B (líder Raft) │  ← 3 réplicas,
│ Nodo B (réplica)    │      │ Nodo C (réplica)    │     grupos Raft
│ Nodo C (réplica)    │      │ Nodo A (réplica)    │     independientes
└─────────────────────┘      └─────────────────────┘

Al superar 512 MB, el range se divide en dos, y se crea un nuevo
grupo Raft con los mismos 3 nodos que ya lo replicaban.
```

### Sección 14.2: CockroachDB: parallel commits {#14-cockroachdb-parallel-commits}

CockroachDB evita el 2PC clásico usando **write intents**: valores provisionales con un lock, ligados a un registro de transacción. Cuando dos escrituras de la misma transacción van a ranges distintos, ambos alcanzan consenso Raft en paralelo, gracias a lo que CockroachDB llama **parallel commits**: la transacción completa solo necesita una escritura adicional al registro de transacción para marcarse como comprometida, no una ronda síncrona hacia todos los participantes como exige 2PC clásico. Los conflictos entre transacciones se resuelven empujando el timestamp de una de ellas hacia adelante, en vez de bloquear a la otra.

```
2PC clásico (secuencial)                    Parallel commits de CockroachDB

  Fase 1: Prepare en Range A                 Write intent en Range A
  Fase 1: Prepare en Range B                 Write intent en Range B      } EN PARALELO,
  Fase 2: Commit en Range A                        │                        vía Raft
  Fase 2: Commit en Range B                        ▼
  (2 rondas de red, secuenciales)             1 sola escritura adicional al
                                               registro de transacción = COMMIT
                                               (no hace falta una segunda ronda)
```

### Sección 14.3: Spanner: TrueTime y commit wait {#14-spanner-truetime}

**TrueTime** expone el tiempo como un intervalo de incertidumbre `[earliest, latest]`, no como un timestamp puntual, respaldado por GPS y relojes atómicos en cada datacenter de Google. La elección de esas dos fuentes no es casual: se eligieron porque fallan de formas distintas entre sí (el GPS falla por antena o receptor, los relojes atómicos por error de frecuencia), así que es poco probable que ambas fallen al mismo tiempo de la misma manera. El valor de incertidumbre publicado originalmente en el paper de 2012 era de aproximadamente 6 milisegundos; fuentes más recientes indican que ha bajado a menos de 4 milisegundos.

**Commit wait** es la consecuencia directa de este diseño: antes de reportar una transacción como comprometida, Spanner espera activamente a que pase el intervalo de incertidumbre completo, convirtiendo la incertidumbre del reloj en latencia garantizada y medible, a cambio de una consistencia externa real (linearizability) entre transacciones en cualquier parte del mundo.

```
TrueTime: el tiempo no es un punto, es un intervalo

  ───────[ earliest ─────── ahora ─────── latest ]───────
                    │←── ε, unos pocos ms ──→│

  Antes de confirmar el COMMIT, Spanner espera activamente (commit
  wait) a que el intervalo completo ya haya pasado. Así garantiza
  que ningún nodo, en ningún otro lugar del mundo, pueda observar
  un evento "antes" de algo que en realidad ocurrió después.
```

### Sección 14.4: Spanner: splits y Paxos {#14-spanner-splits}

Spanner divide las tablas en **splits**, rangos contiguos de claves. Si un directorio (la unidad de colocación de datos de Spanner) crece demasiado, se fragmenta entre varios grupos **Paxos**. Cada split está asociado a un grupo Paxos que abarca varias zonas geográficas; las réplicas votan en cada escritura de forma síncrona, y un líder por grupo maneja todas las escrituras de su split mientras exista mayoría de réplicas activas.

```
  Directorio (unidad de colocación de datos)
        │ crece demasiado
        ▼

Split 1                          Split 2
┌───────────────────────────┐    ┌───────────────────────────┐
│ Grupo Paxos               │    │ Grupo Paxos               │
│ réplica en zona A (líder) │    │ réplica en zona A         │
│ réplica en zona B         │    │ réplica en zona B (líder) │
│ réplica en zona C         │    │ réplica en zona C         │
└───────────────────────────┘    └───────────────────────────┘

Cada escritura vota de forma síncrona entre las réplicas de su
propio grupo Paxos, sin importar en qué zona geográfica estén.
```

---

## Bloque 15: Functional partitioning / federación {#15-functional-partitioning}

### Sección 15.1: Definición y diferencia con sharding {#15-definicion}

Functional partitioning, también llamado federación o database-per-service en arquitecturas de microservicios, distribuye tablas completas y distintas entre servidores distintos, no divide las filas de una misma tabla. Cada servidor tiene un esquema propio, típicamente correspondiente a un dominio de negocio: usuarios en un servidor, inventario en otro, pagos en un tercero.

| Aspecto | Sharding | Functional Partitioning |
| --- | --- | --- |
| Qué se divide | Filas de una misma tabla lógica | Tablas completas y distintas |
| Esquema entre nodos | Idéntico en todos los shards | Distinto en cada servidor |
| ¿Requiere shard key / routing por fila? | Sí | No, se sabe de antemano qué tabla vive dónde |
| Qué escala | El volumen de una tabla específica | La carga total del sistema, separada por dominio |

```
Sharding (mismo esquema, filas divididas)     Functional partitioning (esquemas distintos)

   Servidor 1        Servidor 2         Servidor Usuarios   Servidor Inventario    Servidor Pagos
┌──────────────┐  ┌───────────────┐     ┌────────────────┐  ┌─────────────────┐  ┌────────────────┐
│ tabla users  │  │ tabla users   │     │ tabla users    │  │ tabla productos │  │ tabla pagos    │
│ (filas 1-1M) │  │ (filas 1M-2M) │     │ tabla perfiles │  │ tabla stock     │  │ tabla facturas │
└──────────────┘  └───────────────┘     └────────────────┘  └─────────────────┘  └────────────────┘
```

### Sección 15.2: El costo de coordinación no depende del patrón {#15-costo-coordinacion}

Este es el hallazgo más importante para decidir entre ambos patrones: el costo de una transacción que necesita escribir en dos procesos de base de datos independientes es el mismo, sin importar si la separación es por sharding o por federación funcional. Lo único que le importa al problema de atomicidad distribuida es una pregunta binaria, ¿la operación de negocio necesita comprometer cambios en más de un proceso de motor de base de datos que no comparte WAL ni lock manager?

Si la respuesta es sí, hace falta 2PC o un protocolo equivalente, con el mismo costo de latencia de red, los mismos locks retenidos durante la ventana de preparación, y el mismo riesgo de bloqueo indefinido si el coordinador falla, sin importar si a la arquitectura se le llama sharding, federación, o simplemente "microservicios con base de datos propia". El nombre del patrón describe cómo se dividen los datos lógicamente. El costo de las transacciones distribuidas depende únicamente de cuántos procesos independientes toca una misma transacción de negocio.

### Sección 15.3: Combinación práctica en sistemas grandes {#15-combinacion}

En arquitecturas a gran escala es común aplicar ambos patrones en capas: primero se separa funcionalmente por dominio (mensajería, perfil, grafo social, pagos, cada uno como un sistema o cluster independiente), y dentro de cada dominio, si una tabla específica excede lo que un solo nodo puede manejar razonablemente, se aplica sharding horizontal sobre esa tabla en particular. Un ejemplo de referencia pública es la separación de sistemas de mensajería, perfil y grafo social en plataformas de redes sociales a gran escala, donde el sistema de mensajes, además, shardea su propia tabla de mensajes por `user_id` debido a un volumen que ni el particionamiento ni la federación por sí solos resolverían.

---

## Bloque 16: Transacciones distribuidas: Two-Phase Commit {#16-2pc}

### Sección 16.1: Por qué son estructuralmente más caras {#16-por-que-caras}

En un solo nodo, una transacción que toca cualquier número de filas, dentro de la misma tabla o entre tablas distintas, se beneficia de una única autoridad de commit: un solo WAL, un solo lock manager, un solo proceso que decide de forma atómica si algo se confirma o se revierte. El costo de una transacción local no depende de cuántas filas toca en términos de coordinación, solo en términos de trabajo real, I/O y CPU.

Cuando los datos que la transacción necesita modificar viven en procesos independientes, esa autoridad única desaparece. Cada proceso tiene su propio WAL y su propio lock manager, y ninguno sabe nada del estado del otro por defecto. El problema deja de ser "cuánto trabajo hay que hacer" y pasa a ser "cómo se ponen de acuerdo dos o más sistemas que no comparten memoria ni disco sobre si algo pasó o no", un problema fundamentalmente distinto, de teoría de sistemas distribuidos, no de motores de bases de datos.

### Sección 16.2: Mecánica completa {#16-mecanica}

Participan un **coordinador** (puede ser un nodo dedicado, o uno de los propios shards con doble rol) y N **participantes** (los procesos o shards involucrados en la transacción).

**Fase 1, Prepare (votación).** El coordinador envía `PREPARE` a cada participante. Cada participante, localmente, aplica los cambios, adquiere los locks necesarios, y escribe su propio WAL, pero no comitea todavía. La transacción queda "preparada", persistida en disco, con locks retenidos, en estado de espera. Cada participante responde `VOTE COMMIT` si logró prepararse con éxito, o `VOTE ABORT` si falló algo (un constraint violado, un deadlock, un timeout). El costo físico clave de esta fase es que cada participante ya hizo `fsync` a disco de su WAL local antes de saber si la transacción global va a confirmarse, es I/O síncrono garantizado sin conocer aún el resultado final.

**Fase 2, Commit o Abort (decisión).** Si todos los participantes votaron `COMMIT`, el coordinador escribe su propia decisión en disco, este es el punto de no retorno del protocolo, y ordena `COMMIT` a todos. Si cualquiera votó `ABORT`, o no respondió dentro del timeout, el coordinador ordena `ABORT` a todos. Cada participante ejecuta el commit o abort real, libera sus locks, hace visibles los cambios, y confirma al coordinador.

```
Coordinador                   Participante A                Participante B

│────────── PREPARE ──────────▶                             │
│───────────────────────── PREPARE ─────────────────────────▶
│                             │ fsync WAL local             │
│                             │ locks adquiridos            │
◀──────── VOTE COMMIT ────────│                             │
◀─────────────────────── VOTE COMMIT ───────────────────────│
│                             │                             │
│
│ (todos votaron COMMIT: escribe la
│  decisión en disco = punto de no retorno)
│
│────────── COMMIT ───────────▶                             │
│───────────────────────── COMMIT ──────────────────────────▶
│                             │ libera locks, hace visible  │
◀──────────── ACK ────────────│                             │
◀─────────────────────────── ACK ───────────────────────────│
```

### Sección 16.3: Desglose de costos {#16-costos}

**Latencia de red multiplicada.** Un commit local implica un solo `fsync`. 2PC implica, como mínimo, dos round-trips completos de red (prepare y commit) hacia cada participante, más el `fsync` local de cada uno en cada fase, y el coordinador tiene que esperar a todos antes de avanzar de fase, el paralelismo entre fases no existe, solo dentro de cada fase entre distintos participantes.

**Locks retenidos durante toda la ventana de incertidumbre.** Un participante que ya votó `COMMIT` no puede liberar sus locks hasta recibir la orden final del coordinador. Si los liberara antes y luego llegara un `ABORT`, ya sería tarde para revertir de forma segura frente a otras transacciones que hubieran leído el estado intermedio. Mientras el coordinador decide, esos locks bloquean cualquier otra transacción que quiera tocar las mismas filas.

**El blocking problem**, el defecto de diseño más citado de 2PC: si el coordinador muere después de recibir los votos pero antes de enviar la decisión final, los participantes quedan en estado *in-doubt* (el término estándar en la literatura, no una simplificación): ya votaron `COMMIT`, ya tienen los locks retenidos, ya persistieron sus cambios localmente, pero no saben si el resultado real fue commit o abort. No pueden decidir unilateralmente sin arriesgar corrupción de datos, deben bloquear indefinidamente hasta que el coordinador se recupere y les informe la decisión real. Esto puede retener locks, y por lo tanto bloquear otras transacciones, por minutos o incluso horas si la recuperación del coordinador se demora.

**Sin paralelismo de commit entre fases.** El throughput total del sistema para transacciones cross-shard queda limitado por el participante más lento, o con mayor latencia de red, en cada fase, no por el promedio de todos ellos.

Existe además el **Three-Phase Commit** (3PC), diseñado específicamente para reducir, no eliminar del todo, el riesgo de bloqueo de 2PC agregando un paso intermedio. En la práctica casi nadie lo usa en sistemas reales, por su propia complejidad y por los trade-offs que introduce.

---

## Bloque 17: Alternativas a 2PC {#17-alternativas-2pc}

### Sección 17.1: Consensus distribuido en vez de un coordinador único {#17-consensus}

Sistemas como CockroachDB y Spanner usan variantes de consenso (Raft en el caso de CockroachDB, Paxos en el de Spanner) para replicar y confirmar transacciones sin depender de un único coordinador cuya caída bloquee todo el sistema. La idea central es que, en vez de que un solo coordinador decida, un quórum de nodos (una mayoría, por ejemplo 3 de 5) tiene que estar de acuerdo, y si un nodo individual falla, el resto del quórum puede seguir operando y elegir un nuevo líder sin bloquear indefinidamente, a diferencia de 2PC, donde la caída del único coordinador sí bloquea a los participantes. Los mecanismos concretos de CockroachDB (parallel commits, Sección 14.2) y de Spanner (TrueTime y commit wait, Sección 14.3) son dos formas distintas de materializar esta misma idea.

```
2PC: coordinador único                       Consenso (Raft / Paxos): quórum

  Coordinador                                  Nodo A ──┐
     ├── Participante A                        Nodo B ──┼── mayoría (2 de 3) decide
     └── Participante B                        Nodo C ──┘

  Si el coordinador muere, los                 Si un nodo cae, el resto del quórum
  participantes quedan bloqueados              sigue operando y puede elegir un
  indefinidamente (estado in-doubt)            nuevo líder sin bloquear el sistema
```

### Sección 17.2: Saga pattern {#17-saga}

El patrón Saga, común en arquitecturas de microservicios con bases de datos separadas (functional partitioning), evita 2PC por completo. En vez de una transacción atómica cruzando servicios, la operación de negocio se modela como una secuencia de transacciones locales, cada una con su propia transacción compensatoria, una operación que deshace el efecto de la anterior si un paso posterior falla.

Hay dos formas de coordinar una Saga. En **orchestration**, un coordinador central, el orquestador, conoce toda la secuencia de pasos y llama explícitamente a cada servicio, decidiendo cuándo ejecutar compensaciones si algo falla a mitad de camino. En **choreography**, no hay coordinador central, cada servicio publica eventos tras completar su paso local, y el siguiente servicio reacciona a esos eventos de forma desacoplada, típicamente sobre un message broker como Kafka o RabbitMQ.

El trade-off es explícito: Saga sacrifica atomicidad estricta (durante la secuencia, el sistema puede estar en un estado intermedio visible para otras operaciones, consistencia eventual, no inmediata) a cambio de no retener locks distribuidos ni depender de un coordinador que pueda bloquear el sistema si falla. Es la opción preferida cuando la operación de negocio puede tolerar una ventana breve de inconsistencia visible, siempre que exista una compensación bien definida para cada paso. Un requisito de diseño que se suele pasar por alto: las transacciones compensatorias deben ser idempotentes y reintentables, porque el propio mecanismo de recuperación de una Saga puede necesitar ejecutarlas más de una vez.

```
Orchestration                                      Choreography

Orquestador                                        Servicio Pedido — evento "PedidoCreado"
 ├─▶ Servicio Pedido (paso 1)                        │
 ├─▶ Servicio Inventario (paso 2)                    ▼
 ├─▶ Servicio Pagos (paso 3, falla)                Servicio Inventario (reacciona)
 └─▶ ejecuta compensaciones                          │ evento "StockReservado"
     en orden inverso (2, luego 1)                   ▼
                                                   Servicio Pagos (reacciona, falla)
                                                     │ evento "PagoFallido"
                                                     ▼
                                                   cada servicio escucha eventos y
                                                   compensa su propio paso, sin
                                                   coordinador central
```

---

## Bloque 18: CAP theorem: dónde entra exactamente {#18-cap-theorem}

### Sección 18.1: Recordatorio preciso del teorema {#18-recordatorio}

CAP aplica exclusivamente a sistemas distribuidos con datos replicados entre nodos que se comunican por red. Ante una partición de red (la P del teorema), donde los nodos siguen vivos pero no pueden comunicarse entre sí (esto no es lo mismo que un nodo caído), el sistema debe elegir entre responder con la garantía de que el dato es el más reciente (Consistency, formalmente linearizability) o responder siempre, aunque el dato pueda estar desactualizado (Availability). Fuera de una partición activa, un sistema bien diseñado ofrece ambas garantías simultáneamente, el trade-off solo se activa durante la partición misma.

```
Partición de red entre nodos (ambos siguen vivos, pero no se ven entre sí)

  Nodo 1 (tiene el dato más reciente)          Nodo 2 (aislado por la partición)
              │                                            │
              ▼                                            ▼
   ¿Responder solo si hay garantía              ¿Responder con lo que tiene,
   de que el dato es el más reciente?           aunque pueda estar desactualizado?
              │                                            │
        Consistency (C)                             Availability (A)
   (puede rechazar la operación)                  (siempre responde algo)
```

### Sección 18.2: Por qué en partitioning no aparece nunca {#18-por-que-no-en-partitioning}

Sharding introduce red real entre los procesos que sostienen los datos, así que CAP aplica de lleno: si el router no puede alcanzar un shard, o una réplica de un shard, por una partición de red, tiene que decidir si responde con lo que tiene disponible (sacrificando actualidad) o falla la operación hasta poder confirmar (sacrificando disponibilidad).

Table partitioning nunca sale de un solo proceso, no hay red entre particiones, así que CAP simplemente no aplica ahí. Esta es la razón estructural, no una coincidencia, por la que 2PC, CAP, y el blocking problem son temas que solo aparecen cuando se cruza la frontera de sharding o de cualquier arquitectura multi-nodo, nunca en partitioning local.

### Sección 18.3: Replicación dentro de cada shard: otra capa de CAP {#18-replicacion-shard}

Es común, y recomendable para tolerancia a fallos, que cada shard individual no sea un único servidor, sino un pequeño clúster con réplicas (un primary más réplicas de solo lectura, o un grupo con consenso tipo Raft). Esto añade una segunda dimensión donde CAP aplica: dentro de un mismo shard, si el primary se vuelve inalcanzable para sus réplicas, el sistema decide si promueve una réplica a primary (con posible pérdida de las últimas escrituras no replicadas, sacrificando durabilidad y consistencia por disponibilidad) o si detiene las escrituras hasta resolver la partición (sacrificando disponibilidad por consistencia). Es un trade-off de CAP distinto y adicional al que ocurre entre shards diferentes coordinados por el router.

```
              Router / coordinador de shards
             │                               │
      CAP aplica aquí                 CAP aplica aquí
  (entre shards, vía red)         (entre shards, vía red)
             │                               │
             ▼                               ▼
          Shard 1                         Shard 2
┌─────────────────────────┐     ┌─────────────────────────┐
│ Primary                 │     │ Primary                 │
│  ├─ réplica async       │     │  ├─ réplica async       │
│  └─ réplica async       │     │  └─ réplica async       │
│ CAP aplica AQUÍ también │     │ CAP aplica AQUÍ también │
│ (dentro del propio      │     │ (dentro del propio      │
│  shard, entre réplicas) │     │  shard, entre réplicas) │
└─────────────────────────┘     └─────────────────────────┘
```

---

## Bloque 19: Framework de decisión {#19-framework-decision}

### Sección 19.1: Qué problema resuelve cada técnica {#19-que-resuelve}

| Técnica | Problema que resuelve | Frontera física | ¿Introduce CAP / 2PC? |
| --- | --- | --- | --- |
| Table Partitioning | Volumen de una tabla dentro de un nodo; mantenimiento (purga, vacuum) eficiente | Ninguna, mismo proceso | No |
| Sharding | Volumen o throughput de una tabla que excede un solo nodo | Red entre procesos independientes | Sí |
| Functional Partitioning | Carga total del sistema completo, separada por dominio de negocio | Red entre procesos independientes | Sí, si una operación cruza dominios |

### Sección 19.2: Preguntas guía para decidir {#19-preguntas-guia}

- ¿El volumen o el churn de una sola tabla ya es un problema, pero cabe en un solo servidor con más disco o RAM? Entonces table partitioning.
- ¿El volumen de una tabla específica ya excede lo que un solo servidor puede sostener razonablemente (RAM para índices, IOPS, CPU)? Entonces sharding de esa tabla, con una shard key elegida según el patrón de acceso real, no solo el volumen.
- ¿La carga total del sistema, muchas tablas y dominios distintos, es el problema, más que una tabla individual? Entonces functional partitioning o database-per-service.
- ¿Qué porcentaje de las operaciones de negocio frecuentes tocarían más de un shard o más de un dominio con la división propuesta? Si es alto, hay que replantear la shard key o considerar co-location antes de implementar nada.
- ¿Las operaciones que cruzan la frontera son transaccionales, necesitan atomicidad estricta, o toleran consistencia eventual? Si toleran eventual, Saga evita el blocking problem de 2PC; si no toleran, hay que asumir el costo de 2PC o de un consenso distribuido.

```
¿Una sola tabla ya no cabe o no rinde en un nodo?
   │
   ├── Sí ──▶ ¿Cabe con más disco/RAM/CPU en el mismo nodo?
   │          │
   │          ├── Sí ──▶ Table Partitioning
   │          │
   │          └── No ──▶ Sharding (elegir shard key según el
                         patrón de acceso real, no solo el volumen)
   │
   └── No ──▶ ¿Es la carga total del sistema, muchos dominios
               distintos, el problema real?
                  │
                  ├── Sí ──▶ Functional partitioning / database-per-service
                  │
                  └── No ──▶ replantear el diagnóstico
```

### Sección 19.3: Resumen ejecutivo en una frase por concepto {#19-resumen-ejecutivo}

**Table partitioning**: reorganización física de una tabla dentro del mismo proceso, sin riesgo distribuido, cuyo beneficio depende enteramente de que las queries reales permitan partition pruning.

**Sharding**: distribución de las filas de una tabla entre procesos independientes; el costo real no es fijo, es proporcional al porcentaje de operaciones que cruzan la frontera de shard, y esa proporción la determina la calidad de la shard key.

**Functional partitioning**: distribución de tablas completas y distintas entre procesos independientes por dominio de negocio; el costo de coordinación transaccional entre dominios es idéntico en naturaleza al de sharding, porque depende solo de cruzar procesos independientes, no del patrón de división usado.

**Transacciones distribuidas** (2PC, Saga, consensus): el precio que se paga, en cualquiera de los dos patrones anteriores, cuando una operación de negocio necesita atomicidad entre procesos que no comparten WAL ni lock manager. 2PC da atomicidad estricta a costa de bloqueo potencial; Saga evita el bloqueo a costa de consistencia eventual; el consenso distribuido (Raft, Paxos) busca dar lo mejor de ambos, eliminando el punto único de falla del coordinador.
