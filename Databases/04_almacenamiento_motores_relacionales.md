# Almacenamiento, índices y query planning en motores relacionales: implementación a profundidad

## Caso de estudio concreto: PostgreSQL, MySQL, SQL Server, Oracle y SQLite, cómo guardan bytes y ejecutan queries por dentro

Esta guía cubre el porqué de las decisiones de diseño de los motores de bases de datos
relacionales. Qué estructuras de datos usan para guardar filas e índices en disco, cómo
esas estructuras determinan el costo de cada operación, y, en los bloques que siguen,
cómo el planner decide qué camino tomar para ejecutar una query. Motor de referencia,
PostgreSQL 15+, con comparaciones constantes contra MySQL InnoDB, SQL Server, Oracle y
SQLite, y contrastes puntuales con motores no relacionales (DynamoDB, MongoDB, Cassandra,
Neo4j, Redis) donde el concepto lo justifica.

Los Bloques 1 a 8, ya escritos, cubren el almacenamiento físico completo. Cómo se guarda
una tabla en páginas y tuples, los distintos modelos de almacenamiento por motor (heap vs.
clustered index), row-store vs. column-store, y los fundamentos de índices y B+Tree hasta
su implementación real por motor. Los Bloques 9 en adelante, todavía planeados, cubren
tipos de scan, el cost model del planner, EXPLAIN, y estrategias de indexado avanzadas.

Qué es universal en esta guía. El concepto de B-tree como estructura de índice, que usan
PostgreSQL, MySQL, SQL Server, Oracle, SQLite y también motores documentales como MongoDB.
Row-based vs. column-based storage como distinción fundamental entre OLTP y OLAP. El
problema de selectividad en la elección de índices.

Qué es específico de motores SQL. El heap file como modelo de almacenamiento (MySQL usa
clustered index, no heap). El query planner y EXPLAIN, ya que los motores NoSQL no tienen
un equivalente directo. Los tipos de índice avanzados de PostgreSQL, GIN, GiST, BRIN.

Qué queda fuera de esta guía. El detalle de buffer pool, WAL y checkpoints se asume como
conocimiento previo. LSM-Tree e inverted index se mencionan como contraste puntual al
final de cada bloque de almacenamiento, pero su mecanismo interno completo no se cubre a
profundidad aquí, el enfoque de este archivo es la implementación física de motores
relacionales.

---

## Tabla de contenido

Cada **Bloque** es un tema completo en sí mismo. Cada **Sección** dentro de un bloque es un subtema puntual de ese tema. Esta guía tiene 22 bloques planeados; los bloques 1 a 8 ya están escritos, el resto está pendiente.

---

**Bloque 1 — Cómo se almacenan las tablas en disco: heaps, páginas y tuples**

- [Sección 1.1 — La idea central: el disco solo tiene bytes](#1-idea-central)
- [Sección 1.2 — El heap file: tabla como colección desordenada de páginas](#1-heap-file)
- [Sección 1.3 — Anatomía de una página, tamaños y estructura por motor](#1-anatomia-pagina)
- [Sección 1.4 — La estructura del item pointer array](#1-item-pointer)
- [Sección 1.5 — Anatomía de una tuple](#1-anatomia-tuple)
- [Sección 1.6 — El ctid: dirección física de una fila (+ equivalente en otros motores)](#1-ctid)
- [Sección 1.7 — El catálogo: cómo el motor interpreta los bytes](#1-catalogo)
- [Sección 1.8 — ¿Cómo se ven realmente los datos en disco?](#1-datos-en-disco)
- [Sección 1.9 — Por qué no hay orden "natural"](#1-sin-orden)
- [Sección 1.10 — Cómo se conectan todas las capas](#1-capas)
- [Sección 1.11 — El camino completo: de SQL a bytes en disco](#1-camino-sql-disco)

**Bloque 2 — Modelos de almacenamiento por motor: heap vs clustered index**

- [Sección 2.1 — El modelo clustered index: MySQL InnoDB](#2-clustered-index)
- [Sección 2.2 — SQL Server: heap y clustered index en el mismo motor](#2-sqlserver)
- [Sección 2.3 — Oracle y SQLite](#2-oracle-sqlite)
- [Sección 2.4 — DynamoDB y MongoDB: sin heap tradicional](#2-nosql)
- [Sección 2.5 — Cuándo la arquitectura determina la decisión](#2-decision)
- [Sección 2.6 — Síntesis visual: arquitectura de almacenamiento por motor](#2-sintesis)

**Bloque 3 — Row-Based vs Column-Based: OLTP vs OLAP**

- [Sección 3.1 — Nota sobre los diagramas de esta sección](#3-nota-diagramas)
- [Sección 3.2 — El modelo de los bloques 1 y 2 tiene nombre: row-store](#3-row-store)
- [Sección 3.3 — La idea central: agrupar por fila vs. agrupar por columna](#3-idea-central)
- [Sección 3.4 — Ambos modelos usan archivos divididos en bloques](#3-archivos-bloques)
- [Sección 3.5 — Qué es precio.col: el bloque de columna](#3-bloque-columna)
- [Sección 3.6 — Cómo se ven los mismos datos en disco](#3-bytes-reales)
- [Sección 3.7 — Cómo accede el motor a esos bytes en cada caso](#3-acceso-bytes)
- [Sección 3.8 — Por qué la compresión es mejor en column-store](#3-compresion)
- [Sección 3.9 — Por qué los motores tomaron decisiones de diseño distintas](#3-decisiones-diseno)
- [Sección 3.10 — Tabla comparativa de costos](#3-tabla-costos)
- [Sección 3.11 — Qué motores usan cada modelo y por qué](#3-motores-por-modelo)
- [Sección 3.12 — La regla práctica, y el porqué real](#3-regla-practica)
- [Sección 3.13 — Resumen visual del bloque: de SQL a disco, de páginas a columnas](#3-resumen-visual)

**Bloque 4 — Qué es un índice y por qué existe**

- [Sección 4.1 — El problema que resuelve](#4-problema)
- [Sección 4.2 — Qué es físicamente un índice](#4-que-es)
- [Sección 4.3 — Por qué es rápido: la estructura de árbol](#4-por-que-rapido)
- [Sección 4.4 — Por qué ocupa espacio adicional](#4-espacio)
- [Sección 4.5 — Por qué no usar otras estructuras](#4-otras-estructuras)
- [Sección 4.6 — Cuándo el índice NO ayuda](#4-no-ayuda)

**Bloque 5 — Por qué los árboles binarios no funcionan en disco**

- [Sección 5.1 — El Binary Search Tree (BST): excelente en memoria, horrible en disco](#5-bst)
- [Sección 5.2 — La solución: aumentar el "ancho" del árbol](#5-ancho-arbol)

**Bloque 6 — El B-Tree original (Bayer & McCreight, 1972)**

- [Sección 6.1 — Definición formal](#6-definicion)
- [Sección 6.2 — Estructura visual de un B-Tree de orden 5](#6-estructura-visual)
- [Sección 6.3 — El problema del B-Tree puro para bases de datos](#6-problema-btree-puro)

**Bloque 7 — El B+Tree: la estructura real que usan los motores**

- [Sección 7.1 — Por qué la linked list de hojas es crítica](#7-linked-list)
- [Sección 7.2 — El factor de ramificación real en PostgreSQL](#7-factor-ramificacion)
- [Sección 7.3 — Algoritmo de eliminación: merges de nodo](#7-eliminacion-merge)

**Bloque 8 — B+Tree en disco: PostgreSQL vs MySQL InnoDB vs SQL Server**

- [Sección 8.1 — PostgreSQL: heap + índice separado](#8-postgresql)
- [Sección 8.2 — MySQL InnoDB: clustered index](#8-mysql)
- [Sección 8.3 — SQL Server: clustered index por defecto](#8-sqlserver)
- [Sección 8.4 — Tabla comparativa](#8-tabla-comparativa)

---

### Sección 1.1: La idea central: el disco solo tiene bytes — el motor es quien los organiza e interpreta {#1-idea-central}

Antes de cualquier diagrama, páginas o tuplas, hay una idea que subyace a absolutamente
todo lo que se explica en Bloque 1, Bloque 2 y Bloque 3. Si esta idea queda clara, el resto es consecuencia
natural. Si no queda clara, los detalles se vuelven una lista de datos sin cohesión.

**En disco no existe ninguna tabla, ninguna fila, ningún tipo de dato. Solo existen bytes.**

Un archivo de PostgreSQL, visto desde el sistema operativo, es exactamente esto:

```
$ hexdump -C /var/lib/postgresql/data/base/16384/24601 | head -4

00000000  00 00 00 00 28 e8 a5 01  00 00 00 00 60 1f e0 1f  |....(.......`...|
00000010  00 20 04 20 00 00 00 00  d8 9f 48 00 00 00 00 00  |. . ......H.....|
00000020  00 00 00 00 00 00 00 00  00 00 00 00 00 00 00 00  |................|
00000030  01 00 03 00 01 09 18 00  00 00 00 00 00 00 00 00  |................|
```

Esos son los bytes reales del archivo. No hay ninguna estructura inherente: no hay
"páginas", no hay "tuples", no hay "columnas", no hay "INT" ni "TEXT". Hay octetos de
bits consecutivos guardados en un medio magnético o flash. El archivo del sistema
operativo no sabe qué contienen esos bytes ni cómo agruparlos.

**El motor de base de datos es el programa que conoce las convenciones de formato.**

Todo motor relacional, sin importar cuál, es un conjunto de reglas propias sobre cómo
escribir y cómo leer esos bytes. Esas reglas son las que dan nombre y significado a lo que
de otro modo son números sin contexto, y cada motor define las suyas: MySQL InnoDB, SQL
Server y Oracle no comparten ni un solo byte de convención con PostgreSQL, aunque los
cuatro resuelvan exactamente el mismo problema. Así es como PostgreSQL, en concreto,
interpreta los primeros bytes de una página:

| Bytes          | Valor crudo (lo que ve el SO) | Lo que interpreta el motor                                                                          |
| -------------- | ----------------------------- | --------------------------------------------------------------------------------------------------- |
| 0–7            | `00 00 00 00 28 e8 a5 01`     | `pd_lsn`: LSN `0x01A5E82800000000`, posición en el WAL de la última modificación de esta página     |
| 8–9            | `00 00`                       | `pd_checksum`: 0, checksum de la página para detectar corrupción silenciosa en disco                |
| 10–11          | `00 00`                       | `pd_flags`: `0x0000`, ningún flag activo                                                            |
| 12–13          | `60 1F`                       | `pd_lower`: `0x1F60` = 8032, el array de item pointers llega hasta ese byte                         |
| 14–15          | `E0 1F`                       | `pd_upper`: `0x1FE0` = 8160, las tuples empiezan ahí y crecen hacia el inicio                       |
| 16–17          | `00 20`                       | `pd_special`: `0x2000` = 8192, fin de página; el área especial está vacía porque es una página heap |
| 24 en adelante | —                             | Item pointer array: cada 4 bytes es un slot con (offset, flags, length) de una tuple                |

El motor escribe esos bytes siguiendo sus convenciones internas, y los lee aplicando las
mismas convenciones para extraer significado. Las convenciones son el motor, y cada uno
tiene las suyas: el desglose completo de qué hace cada motor en su propio header de
página se ve más abajo, en la Capa 2.

**Las capas de interpretación: de bytes crudos a "SELECT devuelve 25"**

Para que el motor pueda ejecutar `SELECT precio FROM ventas WHERE id = 2` y devolver 25,
tiene que aplicar varias capas de interpretación en secuencia. Cada capa convierte bytes
crudos en algo más estructurado.

**Distinción crítica antes de empezar:** el _concepto_ de cada capa es **universal**,
todos los motores relacionales lo necesitan. Lo que cambia de un motor a otro es la
_implementación_: qué campos exactos tiene el header, cuántos bytes ocupa cada entrada,
dónde está el "directorio de filas" dentro de la página, cómo se registra qué transacción
tocó una fila, y qué guarda el índice como "puntero" a la fila. Las siguientes capas
muestran el concepto universal primero y la implementación de PostgreSQL después; donde
la implementación varía significativamente se indica qué hace cada motor.

**Capa 1 — La unidad de I/O: la página o bloque**

**I/O son las siglas de Input/Output** (entrada/salida), el movimiento de datos entre el
CPU y cualquier dispositivo externo a él, ya sea RAM, disco o red. En el contexto de una
base de datos, "hacer I/O" casi siempre se refiere específicamente a mover bytes entre el
disco y la RAM, porque los datos de una tabla viven en disco pero el CPU solo puede operar
sobre bytes que ya están cargados en memoria.

Esto importa muchísimo porque el costo de posicionarse en un punto exacto del disco es
fijo y enorme, casi sin importar cuántos bytes se lean después. Acceder a RAM toma unos
100 nanosegundos. Posicionarse en un disco mecánico (HDD), sumando el seek y la espera a
que el plato gire hasta ahí, toma unos 10 millones de nanosegundos, cien mil veces más
lento. Un SSD no tiene partes móviles, pero cada lectura sigue pagando un overhead fijo de
protocolo y de la granularidad mínima de la memoria flash, así que leer 4 KB al azar toma
unos 20.000 nanosegundos, todavía 200 veces más lento que RAM.

Una vez que el disco ya pagó ese costo fijo de posicionarse, transferir los datos de forma
secuencial es comparativamente barato, así que leer 8 KB en vez de 8 bytes cuesta casi lo
mismo. Pagar el costo fijo para traer un solo byte sería un desperdicio enorme.

El disco físico no sabe leer "la fila con id=2", solo sabe leer sectores de 512 B o 4 KB.
Ningún motor relacional opera byte a byte contra el disco. Todos definen una unidad fija
de I/O y siempre cargan esa unidad completa a RAM antes de tocar cualquier byte dentro de ella.

En PostgreSQL, la página N ocupa los bytes `[N × 8.192, (N+1) × 8.192 − 1]`. Acceder a la
página 5 significa leer el byte 40.960 con longitud 8.192, en O(1). Para leer los 4 bytes
de `precio`, el motor carga los 8.192 bytes completos a RAM y después extrae el campo.
Nunca se escribe un byte suelto a disco, siempre se escribe la página entera.

El concepto de "página" es idéntico en todos los motores. Lo que cambia es el tamaño:

| Motor        | Tamaño de página | Configurable                          |
| ------------ | ---------------- | ------------------------------------- |
| PostgreSQL   | 8 KB             | Solo al compilar (`--with-blocksize`) |
| MySQL InnoDB | 16 KB            | Sí: 4, 8, 16, 32 o 64 KB              |
| SQL Server   | 8 KB             | No, fijo                              |
| Oracle       | 8 KB (default)   | Sí: 2–32 KB por tablespace            |
| SQLite       | 4 KB (default)   | Sí: 512 B–64 KB                       |

Una página más grande reduce los niveles del árbol B+Tree porque caben más claves por
página interna, así que el árbol queda más plano y se necesitan menos I/Os para llegar a
una hoja. El costo es que también carga más datos inútiles a RAM cuando solo se necesita
una fila.

Esto no significa que una página más grande siempre gane. El costo de transferir una
página crece de forma lineal con su tamaño, pero el beneficio, menos niveles de árbol y
menos operaciones de I/O, crece de forma logarítmica. Por eso el tamaño óptimo no es "lo
más grande posible", sino un punto de equilibrio entre ambos, y por eso casi todos los
motores convergen en un rango parecido, entre 4 y 16 KB, en vez de usar páginas de 1 MB o
de 100 bytes.

**Capa 2 — El header de la página: metadatos de navegación**

Una vez que la página está en RAM, el motor necesita saber cuánto espacio libre tiene,
dónde están las filas dentro de ella y cuándo fue modificada por última vez, para WAL y
recovery. Para responder eso reserva los primeros N bytes de cada página como "header",
una estructura con campos en posiciones fijas que el código del motor conoce de antemano.

Ese header tiene que empezar siempre en el mismo byte, sin excepción. El motor todavía no
sabe nada del contenido de la página cuando la carga, así que necesita un punto de partida
fijo y predecible para poder leer cualquier otra cosa dentro de ella. Es el mismo problema
de fondo que resuelve el bootstrap del catálogo en la Capa 6, aplicado aquí a una sola
página en vez de a todo el sistema de tipos.

En PostgreSQL, el `PageHeaderData` ocupa los primeros 24 bytes:

```
bytes 0–7:   pd_lsn       — LSN de la última modificación (WAL)
bytes 8–9:   pd_checksum  — checksum para detectar corrupción
bytes 12–13: pd_lower     — dónde termina el array de item pointers
bytes 14–15: pd_upper     — dónde empiezan las tuples (desde el fondo)
bytes 16–17: pd_special   — offset del área especial (índices)
```

El concepto de header es universal, pero la estructura exacta cambia por completo entre motores:

| Motor        | Header                    | Tamaño              | Campos notables                                                                                         |
| ------------ | ------------------------- | ------------------- | ------------------------------------------------------------------------------------------------------- |
| PostgreSQL   | PageHeaderData            | 24 B                | pd_lsn, pd_lower/pd_upper, pd_special                                                                   |
| MySQL InnoDB | FIL Header + INDEX Header | 74 B                | page_prev/page_next (linked list de hojas), page_lsn, n_recs, max_trx_id                                |
| Oracle       | Block Header              | 84–107 B (variable) | SCN (equivalente al LSN), ITL (lista de transacciones que tocaron el bloque), cada entrada ITL ≈23–24 B |
| SQL Server   | Page Header               | 96 B                | object_id, slot_count, free_data/free_count                                                             |

La diferencia más importante no es de tamaño sino de diseño: Oracle guarda la información
de transacciones en el header del bloque, la ITL, en vez de en cada fila como hace
PostgreSQL con el `HeapTupleHeader`. Y el slot array de SQL Server vive al final de la
página, mientras que en PostgreSQL vive al inicio. Son campos completamente distintos
entre motores, el código de InnoDB no podría leer un archivo de PostgreSQL ni al revés.

**Capa 3 — Localizar una fila dentro de la página**

El propio header de la Capa 2 ya marcó el límite de esta sección. En PostgreSQL,
`pd_lower` apunta exactamente a dónde termina el directorio de filas y empieza el espacio
libre, y `pd_upper` a dónde empiezan las tuples desde el otro extremo. El motor no
adivina ese límite, lo lee directamente del header antes de tocar nada más.

Dentro de una página hay múltiples filas, y sus bytes no tienen un tamaño fijo ni caen en
posiciones predecibles. El motor no puede calcular dónde empieza la fila N con una simple
multiplicación, así que consulta un "directorio" que le dice que la fila N está en el
offset X.

Ese directorio no siempre vive dentro del header de la Capa 2. En PostgreSQL vive justo
después del header, en los bytes 24 en adelante, como una sección propia y separada, no
como parte de él. En Oracle sí es parte del header del bloque, junto con la ITL. En
InnoDB y en SQL Server vive en el extremo opuesto de la página, al fondo, lejos de su
respectivo header. Esto aplica igual si el motor llegó ahí navegando un índice o si está
recorriendo toda la página en un sequential scan.

| Motor        | Directorio                             | Granularidad                    | Costo de acceso                                                        |
| ------------ | -------------------------------------- | ------------------------------- | ---------------------------------------------------------------------- |
| PostgreSQL   | ItemId array (al inicio de la página)  | 4 B por fila (denso)            | O(1): leer el slot, extraer lp_off                                     |
| MySQL InnoDB | Page Directory (al fondo de la página) | 2 B cada 4–8 records (disperso) | O(log g + g), g ≈ 6: búsqueda binaria + recorrido de la lista enlazada |
| Oracle       | Row Directory (en el block header)     | 2 B por fila                    | O(1): offset exacto                                                    |
| SQL Server   | Slot Array (al final de la página)     | 2 B por fila                    | O(1): offset exacto                                                    |

PostgreSQL, Oracle y SQL Server comparten el mismo concepto: una entrada por fila con el
offset exacto y acceso O(1). Solo cambia dónde vive el directorio dentro de la página.
InnoDB es la excepción real: no tiene una entrada por fila, los records están enlazados
entre sí (cada record header apunta con `next_record` al siguiente) y el Page Directory
solo indexa cada 4-8 records para acotar la búsqueda binaria antes de recorrer la lista.

**Capa 4 — Metadatos de transacción por fila: visibilidad MVCC**

Imagina dos transacciones al mismo tiempo. La transacción A empieza a leer la fila con
`id = 2` justo cuando la transacción B la está actualizando y todavía no ha hecho commit.
El directorio de la Capa 3 le dio a A la dirección exacta de esa fila, pero eso no le dice
nada sobre si los bytes que hay ahí ahora mismo son seguros de leer. Sin ningún metadato
adicional, A podría terminar leyendo una fila a medio escribir, o los cambios de B
después de que A ya empezó, rompiendo la garantía básica de que cada transacción ve una
foto fija y consistente de los datos. Ese es el problema de fondo que resuelve esta capa.

Cuando varias transacciones operan en paralelo, cada una debe ver una versión consistente
de los datos. Para eso (MVCC), el motor necesita saber, para cada versión de una fila,
qué transacción la creó, qué transacción la borró o la reemplazó, y dónde está la versión
anterior para lecturas del pasado. Aquí es donde los motores divergen más profundamente:
no es solo que cambie el tamaño, cambia el mecanismo completo.

| Motor        | Dónde vive el metadato MVCC             | Overhead                                                          | Dónde van las versiones viejas          |
| ------------ | --------------------------------------- | ----------------------------------------------------------------- | --------------------------------------- |
| PostgreSQL   | `t_xmin`/`t_xmax`/`t_ctid` en cada fila | 23 B mínimo, en cada fila                                         | En el heap mismo, como tuples separadas |
| MySQL InnoDB | `DB_TRX_ID`/`DB_ROLL_PTR` en cada fila  | 13 B, en cada fila                                                | Undo log (área separada)                |
| Oracle       | ITL en el header del bloque             | 1 B por fila ("lock byte") + espacio fijo en el header del bloque | Rollback segments (undo tablespace)     |
| SQL Server   | Depende del nivel de aislamiento        | Ninguno en la tabla si no hay RCSI/SI                             | tempdb, si RCSI/SI está activo          |

PostgreSQL escribe una tupla nueva completa en cada UPDATE y conecta la vieja con la nueva
vía `t_ctid`. VACUUM libera las viejas después. InnoDB hace lo opuesto: modifica la fila
en su lugar y guarda la versión anterior en el undo log, así que el heap siempre tiene la
versión más reciente y para leer una versión vieja hay que seguir el puntero. Oracle no
guarda nada de esto por fila: la ITL en el header del bloque es una lista de las
transacciones que tocaron ese bloque, y cada fila solo tiene un byte que apunta a qué
entrada de la ITL la modificó, por eso el overhead por fila es mínimo pero el bloque paga
un costo fijo. SQL Server sin RCSI/SI no versiona en absoluto, usa locks. Con RCSI/SI
activo, las versiones se guardan en tempdb, fuera de la tabla.

**Capa 5 — Estructura interna de la fila: serialización de columnas**

El motor ya localizó la fila (Capa 3) y verificó su visibilidad (Capa 4). Ahora tiene, en
algún lugar de la página, una secuencia continua de bytes que representa esa fila
completa, algo como `01 00 00 00 E8 03 00 00 01 00 00 00`. Una fila lógica con columnas
nombradas (`id`, `precio`, `cantidad`) se guarda en disco exactamente así, una tira de
bytes sin separadores, sin etiquetas, y sin ningún indicio visual de dónde termina una
columna y empieza la siguiente. Ese es el problema central de esta capa.

Esto no es una limitación, es una decisión deliberada de eficiencia. El nombre `precio` y
el tipo `INT4` son exactamente los mismos para las 10 millones de filas de la tabla, así
que repetirlos en cada una sería un desperdicio enorme de espacio, 10 millones de copias
idénticas de la misma información. El motor los guarda una sola vez, en el catálogo
(Capa 6), y deja que cada fila sea solo los bytes puros del valor.

A esto se le llama serialización, empaquetar una estructura con nombre y forma en una
secuencia lineal de bytes, y el motor necesita poder revertir ese proceso para extraer el
valor de una sola columna sin tener que interpretar la fila entera. Pero saber que el
catálogo dice "precio es un INT4" no basta, falta resolver una pregunta puramente
aritmética. ¿En qué byte exacto empieza? Si todas las columnas midieran lo mismo, sería
una simple multiplicación. Pero en cuanto aparece una sola columna de longitud variable,
un VARCHAR o un TEXT, esa aritmética se rompe, porque el tamaño real de esa columna solo
se conoce leyendo sus propios bytes, no el catálogo. Cada motor resuelve esta ruptura con
su propio mecanismo. Todo motor relacional resuelve el mismo problema en tres partes.

**¿Cuáles columnas son NULL?** NULL no es un valor de cero bytes, es ausencia de valor.
El motor debe distinguir "columna 3 es NULL" de "columna 3 es string vacío" sin gastar
bytes de más.

| Motor                  | Mecanismo                                                                                                                           |
| ---------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| PostgreSQL             | Null bitmap después del `HeapTupleHeader`, 1 bit por columna nullable                                                               |
| MySQL InnoDB (COMPACT) | Null bitmap en la sección de headers del record, junto al VLF                                                                       |
| Oracle                 | Trailing NULL compression: los NULL al final de la fila se omiten sin bytes ni marcador; en el medio usan un byte marcador (`0xFF`) |
| SQLite                 | `type_code` en el record header; `type_code = 0` significa NULL, sin bitmap separado                                                |

**¿Dónde empiezan los bytes de la columna N?** Para columnas de longitud fija (INT,
BIGINT, DATE) el offset se calcula sumando las longitudes de las columnas anteriores,
aritmética pura en O(1). El problema real son las columnas de longitud variable
(VARCHAR, TEXT, BLOB, JSON), donde el offset de la columna N depende de cuántos bytes
ocuparon las columnas 1..N-1.

| Motor                  | Mecanismo                                                                 | Costo                                                               |
| ---------------------- | ------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| PostgreSQL             | Prefijo de longitud (1 o 4 B) antes de cada valor variable                | O(N): recorrer los prefijos anteriores y sumar                      |
| MySQL InnoDB (COMPACT) | VLF (Variable-Length Field list) al inicio del record, en orden inverso   | O(N), mejor localidad de caché al estar agrupado al inicio          |
| Oracle                 | Prefijo de longitud de 1 B (&lt;251 B) o 3 B (mayor) antes de cada valor  | O(N): recorrer los prefijos anteriores                              |
| SQLite                 | Record header con un `type+length` varint por columna, agrupado al inicio | El motor lee el header completo y salta directo al offset necesario |

**¿Cómo se decodifican los bytes del valor?** Una vez ubicados los bytes, el tipo del
catálogo dice cómo interpretarlos, y la codificación cambia entre motores. Para un entero
con valor 25:

| Motor           | Codificación                                  |
| --------------- | --------------------------------------------- |
| PostgreSQL INT4 | `19 00 00 00` (little-endian, 4 bytes)        |
| InnoDB INT      | `80 00 00 19` (big-endian + XOR `0x80000000`) |
| Oracle NUMBER   | BCD-like, longitud variable                   |
| SQLite INTEGER  | Big-endian, 1–8 bytes según la magnitud       |

El XOR de InnoDB transforma el rango con signo a sin signo manteniendo el orden numérico,
lo que permite comparar enteros con `memcmp()` directamente, algo crítico para navegar el
B+Tree sin tener que deserializar el valor primero.

Sin esta capa, saber que "precio es la columna 2" no basta si la columna 1 es un VARCHAR
de longitud variable: el motor no puede calcular dónde empiezan los bytes de la columna 2
sin saber cuántos ocupó la 1.

**Capa 6 — El catálogo o diccionario de datos: interpretar bytes como columnas**

La Capa 5 resolvió dónde empiezan y terminan los bytes de una columna. Esta capa resuelve
una pregunta distinta, qué significan esos bytes. Ni el tamaño ni la posición de un valor
dicen si es un INT4, un DATE, o los primeros 4 bytes de un TEXT que por casualidad ocupan
el mismo espacio. Esa información no vive en la fila, vive en un catálogo o diccionario de
datos aparte, una tabla que el motor consulta cada vez que necesita traducir bytes crudos
a columnas con nombre y tipo.

Para PostgreSQL en concreto, esa tabla es `pg_attribute`, y guarda, por cada columna de
cada tabla, su nombre (`attname`), su tipo (`atttypid`, un OID que apunta a `pg_type`), su
longitud en bytes (`attlen`, o `-1` si es variable), su posición ordinal (`attnum`), y si
acepta NULL (`attnotnull`). Sin esa fila, el motor no tiene forma de saber que la columna 2
de `ventas` se llama `precio`, es un entero de 4 bytes, y vive en el offset 4.

Aquí hay una paradoja que vale la pena notar. `pg_attribute` es, ella misma, una tabla
heap normal, con sus propias filas, páginas y tuples codificadas exactamente como
cualquier tabla de usuario (le aplican las Capas 1 a 5 igual que a cualquier otra). Pero
interpretar sus filas requiere conocer su propia estructura de columnas, y esa estructura
es precisamente lo que un catálogo provee. PostgreSQL rompe ese círculo con un proceso de
bootstrap. Las primeras filas de `pg_attribute`, `pg_class` y `pg_type` se generan a
partir de definiciones fijas en el código fuente del motor, no en disco, durante la
compilación, dando un punto de partida que no depende de leerse a sí mismo.

Esto arranca justo donde termina la Capa 5, en el offset donde empiezan los datos del
usuario (en PG: `t_hoff`; en InnoDB: después de `DB_ROLL_PTR`). Desde ahí, el motor
interpreta los bytes consultando su catálogo.

| Motor        | Catálogo                                                                         | Dónde vive                                      |
| ------------ | -------------------------------------------------------------------------------- | ----------------------------------------------- |
| PostgreSQL   | `pg_class` + `pg_attribute`                                                      | Tablas heap normales, como cualquier otra tabla |
| MySQL InnoDB | Data dictionary (8.0+: tablas InnoDB internas; antes: archivos `.frm` por tabla) | Interno al motor                                |
| Oracle       | `DBA_COLUMNS` / `ALL_COLUMNS`                                                    | System tablespace                               |
| SQLite       | `sqlite_master`                                                                  | Primera página del mismo archivo `.db`          |

Por ejemplo, para leer `precio` en PostgreSQL, `pg_attribute` dice que la columna 2 es
INT4 de 4 bytes en el offset 4. El motor lee los bytes `[offset+4..offset+7]`, obtiene
`19 00 00 00`, y lo decodifica como little-endian, 25. SQLite es el caso distinto en
naturaleza: su catálogo es el propio árbol B+Tree de la primera página del archivo, que
contiene el SQL literal de cada `CREATE TABLE` y que el motor parsea para saber tipos y
columnas.

Lo que es idéntico en todos los motores es esto. Si el catálogo se corrompe o se pierde,
los bytes de datos siguen intactos en disco, pero el motor no puede interpretarlos. La
"tabla" desaparece conceptualmente aunque sus bytes existan físicamente.

**Capa 7 — Los índices: estructuras que aceleran la búsqueda**

Sin un índice, encontrar la fila con `id = 2` significa repetir las Capas 1 a 4 para cada
página del heap, cargarla a RAM, leer su header, consultar su directorio y verificar la
visibilidad de cada fila, hasta encontrar la que buscas o hasta llegar al final del
archivo si no existe. Con un millón de filas eso son miles de páginas leídas para
encontrar una sola. Un índice invierte el problema. En vez de recorrer páginas de datos,
recorre una estructura mucho más pequeña y ordenada, un árbol, que existe con el único
propósito de decir, en unos pocos saltos, dónde está exactamente la fila que buscas.

El archivo del índice también es solo bytes, con su propia convención de formato (B+Tree
en vez de heap). Las hojas del árbol contienen entradas que combinan el valor de la clave
indexada con algo que le dice al motor cómo llegar a los bytes de la fila completa. Aquí
está la diferencia más importante entre modelos de almacenamiento, qué guarda la hoja
como "puntero", y si ese puntero exige una segunda travesía del árbol o llega directo a
los bytes.

| Motor                               | Qué guarda la hoja                          | Tamaño del puntero                                                                               | Saltos hasta los bytes                   |
| ----------------------------------- | ------------------------------------------- | ------------------------------------------------------------------------------------------------ | ---------------------------------------- |
| PostgreSQL (heap)                   | `(valor, ctid)`, dirección física           | ctid: 6 B (página 4B + slot 2B)                                                                  | 1: índice → bytes                        |
| MySQL InnoDB, índice clustered (PK) | `(valor_PK, fila completa)`                 | — (la fila vive en la hoja)                                                                      | 0: la hoja ES la fila                    |
| MySQL InnoDB, índice secundario     | `(valor_col, valor_PK)`, valor lógico       | —                                                                                                | 2: índice secundario → clustered → bytes |
| SQL Server sin clustered index      | `(valor_col, RID)`, dirección física        | RID: 8 B (file_id 2B + page_id 4B + slot 2B)                                                     | 1: índice → bytes                        |
| SQL Server con clustered index      | `(valor_col, clustering_key)`, valor lógico | —                                                                                                | 2: igual que InnoDB                      |
| Oracle (heap)                       | `(valor_col, ROWID)`, dirección física      | ROWID: 10 B (object# 32 bits + file#/block# 32 bits compartidos + row# 16 bits, a nivel de bits) | 1: índice → bytes                        |
| SQLite, tabla con INTEGER PK        | `(rowid, datos)`                            | — (igual que InnoDB clustered)                                                                   | 0: la hoja ES la fila                    |
| SQLite, índice secundario           | `(valor_col, rowid)`                        | —                                                                                                | 2: segunda travesía                      |

El patrón que se repite: cuando el índice apunta a una dirección física (ctid, RID,
ROWID), un solo salto lleva del índice a los bytes de la fila. Cuando apunta a un valor
lógico (la PK o el rowid, como en los índices clustered o secundarios de InnoDB, SQLite o
SQL Server con clustered index), el motor necesita una segunda travesía del árbol para
resolver ese valor a bytes físicos, duplicando el costo O(log n).

**Estas capas fuera del mundo relacional: key-value, documentales y de grafos**

Las 7 capas anteriores son la arquitectura de un motor relacional. Esto es lo que cambia
(o desaparece por completo) cuando el motor no lo es, capa por capa:

| Capa                          | En motores no relacionales                                                                                                                                                                                                                                                                                                                                                                                        |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1 — Página                    | RocksDB/Cassandra (LSM): bloques de tamaño fijo, 4 KB por defecto, configurable hasta 32 KB. Neo4j: registros de tamaño fijo por tipo (uno para nodos, otro para relaciones). Redis: excepción, no pagina a disco de la misma forma, solo al serializar RDB/AOF.                                                                                                                                                  |
| 2 — Header                    | RocksDB: trailer con checksum y tipo de compresión en vez de header. Neo4j: no necesita header, la posición del registro N se calcula como N × tamaño_registro.                                                                                                                                                                                                                                                   |
| 3 — Directorio de filas       | RocksDB: "restart points" cada 16 entradas por defecto, mismo espíritu que el Page Directory de InnoDB. Neo4j: no hace falta directorio, aritmética directa por tamaño fijo.                                                                                                                                                                                                                                      |
| 4 — MVCC                      | Cassandra/DynamoDB: timestamp por escritura + last-write-wins, no MVCC real. Redis: un solo hilo evita el problema de raíz. MongoDB (WiredTiger): MVCC real, cercano en espíritu al de PostgreSQL (Bloque 2, Sección 2.4).                                                                                                                                                                                        |
| 5 — Serialización de columnas | MongoDB (BSON) y DynamoDB son autodescriptivos: cada valor lleva su propio tipo pegado (type tag en BSON, descriptor `S`/`N`/`M`/`L` en DynamoDB), así que decodificar un campo no depende de sumar longitudes previas. Redis: cada value es un blob con encoding propio según su tipo (string, hash, list, set, zset).                                                                                           |
| 6 — Catálogo                  | MongoDB y DynamoDB no necesitan catálogo: el dato ya declara su propio tipo (Capa 5), así que el "esquema" es una convención de la aplicación, no una regla que el motor imponga.                                                                                                                                                                                                                                 |
| 7 — Índices                   | En LSM (RocksDB, Cassandra) el índice y los datos son la misma estructura, la distinción índice vs. heap no aplica. Neo4j usa **index-free adjacency**: cada nodo apunta directo a su primera relación y cada relación a la siguiente del mismo nodo, así que recorrer el grafo no pasa por ningún índice, y el costo depende solo de cuántas relaciones tiene ese nodo, no del tamaño total de la base de datos. |

**Resumen: qué es universal entre motores relacionales, y qué cambia de uno a otro**

| Capa                          | Concepto universal (en motores relacionales)        | Motor donde más diverge                                                                        |
| ----------------------------- | --------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| 1 — Página                    | Unidad fija de I/O, siempre se carga completa       | El tamaño (ver tabla en Capa 1)                                                                |
| 2 — Header de página          | Metadatos de navegación al inicio de cada página    | Oracle guarda ahí la ITL; InnoDB agrega el linked list de hojas                                |
| 3 — Directorio de filas       | Offset de cada fila dentro de la página             | InnoDB no tiene una entrada por fila, usa lista enlazada + directorio disperso                 |
| 4 — MVCC                      | Qué transacción creó/borró cada versión de una fila | Oracle lo guarda en el bloque, no por fila; SQL Server solo versiona con RCSI/SI               |
| 5 — Serialización de columnas | Empaquetar N columnas sin etiquetas entre ellas     | Null encoding, prefijos de longitud y endianness cambian por motor                             |
| 6 — Catálogo                  | Traduce bytes a columnas con nombre y tipo          | SQLite guarda su catálogo como el SQL literal del `CREATE TABLE`, no como metadata separada    |
| 7 — Índice                    | Evita el sequential scan, de O(n) a O(log n)        | Qué guarda la hoja, dirección física o valor lógico, decide si hace falta una segunda travesía |

Regla para leer todo Bloque 1, Bloque 2 y Bloque 3: cuando aparece un concepto nuevo,
pregúntate siempre si es el CONCEPTO (universal) o la IMPLEMENTACIÓN (específica de un
motor), y en qué CAPA de la cadena bytes → dato vive ese mecanismo. Responder esas dos
preguntas convierte detalles sueltos en arquitectura coherente.

**Las implicaciones prácticas de entender este modelo**

Con las 7 capas claras, esto es lo que se deriva directamente.

**La página es la unidad mínima de I/O, sin excepción.** Cambiar 1 byte en cualquier fila
significa cargar la página completa a RAM, modificar el byte, marcar la página como
"dirty", y escribir la página completa a disco en el próximo checkpoint. Esto es igual en
PostgreSQL, InnoDB, SQL Server y Oracle. La única diferencia real es cuánto mide esa página.

**Los archivos de un motor son ilegibles para otro.** Un `.ibd` de InnoDB y un heap file
de PostgreSQL son ambos "bytes", pero las convenciones de las Capas 1 a 7 son distintas en
cada uno: el header vive en un offset diferente, el directorio de filas usa un tamaño de
entrada diferente, el null encoding es diferente, la codificación de enteros es diferente.
Ningún motor puede leer archivos del otro.

**El DELETE no siempre significa lo mismo a nivel de bytes.** PostgreSQL (`t_xmax`),
InnoDB (`deleted_flag`) y SQL Server (bit de "ghost record") comparten el mismo patrón:
marcan la fila como borrada y la purgan físicamente después, en segundo plano. Oracle es
la excepción real: libera el espacio del bloque de inmediato y, si alguien necesita la
versión anterior para un read consistente o un rollback, la reconstruye desde el undo
segment, no desde bytes marcados en el bloque.

**Mover una fila cuesta distinto según qué guarda el índice.** Si un UPDATE agranda una
columna variable y la fila ya no cabe en su lugar (Capa 5), el motor la reubica
físicamente. En PostgreSQL, SQL Server (heap) y Oracle, donde los índices guardan una
dirección física (ctid, RID, ROWID), reubicar la fila obliga a actualizar esa dirección en
todos los índices que la referencian. En InnoDB y SQLite, donde los índices secundarios
guardan un valor lógico (la PK o el rowid), reubicarla dentro del árbol clustered no toca
los índices secundarios en absoluto.

**El catálogo es la llave que abre los datos.** Si se corrompe (`pg_attribute`,
`sqlite_master`, un `.frm` viejo de MySQL), los bytes de datos siguen en disco pero el
motor no sabe cómo interpretarlos. La tabla desaparece conceptualmente aunque sus bytes
existan físicamente. Por eso un `ALTER TABLE ADD COLUMN` con un valor por defecto
constante en PostgreSQL moderno no reescribe filas viejas: el valor se guarda una sola vez
en el catálogo (`attmissingval`) y cada fila vieja lo recibe al vuelo cuando se lee.

**El buffer pool es la capa obligatoria entre bytes y operaciones.** Ningún motor opera
directamente sobre bytes en disco: siempre carga la página a RAM primero, y el camino es
siempre disco → buffer pool → motor → buffer pool → disco. El nombre cambia (buffer pool
en MySQL, shared buffers en PostgreSQL, buffer cache en Oracle, page cache en SQL Server)
pero el mecanismo es idéntico.

**El orden real de ejecución, y cómo cambia según el motor y el plan de acceso:**

```
SQL: SELECT precio FROM ventas WHERE id = 2

1. Catálogo (Capa 6) → confirma que "ventas" existe y tiene la columna precio

────────────────────────────────────────────────────────────────────────────
CASO A — índice separado del heap: PostgreSQL, Oracle, SQL Server sin clustered
────────────────────────────────────────────────────────────────────────────
2. Índice (Capa 7)        → navega el árbol, encuentra id = 2
3. Buffer pool (Capa 1)   → carga la página del heap
4. Header (Capa 2)        → ubica dónde empiezan las filas
5. Directorio (Capa 3)    → resuelve el offset exacto de la fila

────────────────────────────────────────────────────────────────────────────
CASO B — índice clustered, busca por PK: InnoDB, SQL Server con clustered
                                         index, SQLite con INTEGER PK
────────────────────────────────────────────────────────────────────────────
2. Índice (Capa 7)        → navega el árbol clustered
                             (bajar por el árbol ya aplica las Capas 1, 2 y 3
                              en cada nivel; la hoja final ES la fila completa,
                              sin heap fetch aparte)

────────────────────────────────────────────────────────────────────────────
CASO C — índice secundario sobre un motor clustered (los mismos del Caso B)
────────────────────────────────────────────────────────────────────────────
2. Índice secundario (Capa 7)  → resuelve id = 2 → devuelve el valor de PK
2. Índice clustered (Capa 7)   → segunda travesía completa, ahora con la PK
                                  (cada travesía repite las Capas 1, 2 y 3
                                   en cada nivel del árbol que recorre)

────────────────────────────────────────────────────────────────────────────
CASO D — sin índice usable, en cualquier motor
────────────────────────────────────────────────────────────────────────────
Sequential scan: repetir las Capas 1, 2 y 3 en cada página del heap hasta
                 encontrar id = 2

────────────────────────────────────────────────────────────────────────────
Los 4 casos convergen aquí:
────────────────────────────────────────────────────────────────────────────
6. MVCC (Capa 4)          → verifica que esta versión de la fila es visible
7. Serialización (Capa 5) → calcula dónde empiezan los bytes de "precio"
8. Catálogo (Capa 6)      → decodifica esos bytes como INT4

Resultado: precio = 25
```

El resto de esta sección describe el modelo de PostgreSQL en detalle.

---

### Sección 1.2: El heap file: la tabla como colección desordenada de páginas {#1-heap-file}

Sección 1.1 mostró las 7 capas de interpretación de forma universal, con PostgreSQL como
primer ejemplo concreto. Esta sección y las que siguen, de la 1.3 a la 1.11, profundizan
en la implementación real de PostgreSQL capa por capa, empezando por el archivo que
contiene las páginas de una tabla, el heap file, y el vocabulario que le corresponde.

Antes de cualquier diagrama, dos distinciones de vocabulario que se usan en todo este bloque:

**Tabla (en SQL) vs. heap file (en disco)**
Cuando escribes `CREATE TABLE ventas (...)`, creas un concepto lógico — la tabla. El motor
la almacena físicamente en uno o más archivos usando el formato **heap file** (en inglés;
también llamado simplemente "heap"). "Heap file" es el formato de almacenamiento, no un
sinónimo de tabla — la relación exacta es:

```
Tabla de usuario normal   → 1 heap file en disco
                            (si la tabla supera 1 GB, se divide en segmentos:
                             24601, 24601.1, 24601.2, ... — cada uno de hasta 1 GB)

Tabla TOAST               → también 1 heap file, pero no es una tabla de usuario;
                            PostgreSQL la crea automáticamente para columnas grandes,
                            el usuario nunca la ve directamente en SQL

Índice B+tree             → 1 archivo propio, pero NO en formato heap —
                            usa formato de árbol (páginas de nodos y hojas ordenadas)

Vista (VIEW)              → ningún archivo de datos — es solo SQL guardado en el catálogo
```

La palabra "heap" no es el heap de memoria dinámica — en bases de datos significa
"colección sin orden garantizado": las tuples se insertan donde haya espacio libre,
sin ningún criterio de orden.

```
Concepto lógico (lo que escribes en SQL):   tabla  "ventas"
Implementación física (lo que vive en disco): /var/lib/postgresql/data/base/16384/24601
```

**Fila / row (en SQL) vs. tuple / tupla (en el motor)**
Cuando insertas con `INSERT INTO ventas VALUES (...)`, lógicamente estás creando una **fila**
(en inglés: **row**). En disco esa fila se almacena como una **tuple** (en inglés: **tuple**;
en español también se dice **tupla**). Son el mismo dato visto desde dos niveles distintos:

- **Fila / row**: el concepto SQL — los valores que tú escribiste y que SELECT te devuelve.
- **Tuple / tupla**: la representación física — esos mismos valores más un header de metadata
  que el motor agrega (IDs de transacción para MVCC, puntero a la versión siguiente, etc.).
  El usuario nunca ve ese header en SQL; el motor lo usa internamente.

En este documento se usa "tuple" cuando se habla del objeto físico en disco y "fila" cuando
se habla del concepto lógico desde SQL. En la práctica los términos se intercambian porque
cada fila lógica corresponde a exactamente una tuple física (o a veces varias versiones de
la misma fila si hay UPDATEs sin VACUUM aún).

**Offset** — posición de un dato dentro de un bloque, contada en bytes desde el inicio.
Offset 0 = primer byte del bloque. Offset 4 = el byte que está 4 posiciones más adelante,
justo después de los primeros 4 bytes. Se usa en todo este bloque para navegar dentro de
páginas y dentro de tuples: saber dónde empieza un campo, dónde empieza una tuple, etc.
No es un número mágico — siempre se puede calcular: si id ocupa 4 bytes desde el offset 0,
entonces precio empieza en el offset 4 (0 + 4 bytes de id = 4).

```
/var/lib/postgresql/data/base/16384/24601      ← heap file (datos de la tabla ventas)
/var/lib/postgresql/data/base/16384/24601_fsm  ← Free Space Map de ventas
/var/lib/postgresql/data/base/16384/24601_vm   ← Visibility Map de ventas
/var/lib/postgresql/data/base/16384/24610      ← índice B+tree sobre ventas.id (formato árbol, no heap)
/var/lib/postgresql/data/base/16384/24615      ← heap file de la tabla TOAST de ventas (si hay cols grandes)
```

Los tres números del path son OIDs (identificadores internos del motor):

- `16384` = OID de la base de datos
- `24601` = OID de la tabla `ventas` (asignado por PostgreSQL al crear la tabla)

El heap file es el archivo principal. Los dos compañeros sirven de navegación:

- **`_fsm` (Free Space Map)**: mapa que indica qué páginas tienen espacio libre. Cuando
  haces un INSERT, el motor consulta este mapa para encontrar una página con hueco sin
  tener que escanear el heap file entero.
- **`_vm` (Visibility Map)**: mapa que indica qué páginas tienen solo tuples visibles para
  todas las transacciones activas. VACUUM lo usa para saltarse páginas que ya están limpias.

El heap file está dividido en **páginas** de 8 KB. Cada página contiene varias tuples:

```
Heap file /data/base/16384/24601:
┌─────────────────────────────────────────────────────────────────────┐
│  Página 0 (bytes 0–8191)                                            │
│  ┌────────────┬──────────────────┬───────────┬─────────────────┐   │
│  │page header │ item pointers    │ libre     │ tuples          │   │
│  │ 24 bytes   │[slot1][slot2]... │           │[id=1,p=1000,c=1]│   │
│  └────────────┴──────────────────┴───────────┴─────────────────┘   │
├─────────────────────────────────────────────────────────────────────┤
│  Página 1 (bytes 8192–16383)   [más tuples]                         │
├─────────────────────────────────────────────────────────────────────┤
│  ...                                                                │
└─────────────────────────────────────────────────────────────────────┘
```

No hay orden entre páginas ni entre tuples en cuanto a **valores**: la fila con id=100
puede estar en la página 0 y la fila con id=1 en la página 7. Sin embargo, el motor sí
sabe dónde está cada tuple: guarda su dirección física exacta. Cómo funciona esa
dirección se explica en los apartados siguientes.

**¿Las páginas de 8 KB son físicas o una abstracción de código?**
Son físicas en el sentido de que los bytes en disco SÍ están organizados en bloques de
exactamente 8.192 bytes contiguos — si lees el archivo con un editor hex, el bloque 0
ocupa los bytes 0–8191, el bloque 1 ocupa 8192–16383, etc. Sin embargo, el sistema de
archivos y el disco no saben nada de "páginas de 8 KB"; eso lo hace cumplir el código de
PostgreSQL: siempre lee y escribe exactamente 8.192 bytes a la vez. Es una convención que
PostgreSQL impone sobre un archivo de bytes plano — por eso se puede cambiar en compilación
(`--with-blocksize`), aunque 8 KB es el default universal.

Si insertas una sola fila pequeña, esa página de 8 KB se carga entera en el buffer pool
y el espacio sobrante queda libre para inserts futuros — no se pierde. El espacio libre
lo delimitan dos punteros del page header (`pd_lower` y `pd_upper`); cuando se tocan, la
página está llena. La mecánica completa con diagramas está en la sección siguiente.

Cuando eliminas una fila, su tuple se marca muerta pero el espacio no se libera de
inmediato — VACUUM lo reclama después. Para saber qué páginas tienen hueco sin escanear
todo el heap, el motor consulta el FSM (`_fsm`, explicado arriba).

**¿Es como un array? ¿Cuánto cuesta pasar de una página a otra o de una tuple a otra?**
Sí, en dos niveles:

- El **heap file es un array de páginas**: para leer la página N el motor calcula
  `offset = N × 8192` en O(1) y hace una sola llamada de I/O de 8 KB. No hay punteros
  que seguir ni estructura de árbol que recorrer — acceso directo por posición.

- El **item pointer array dentro de una página es un array de slots**: slot[2] está en
  una posición fija del header, accesible en O(1). Dentro de la página todo es aritmética
  de punteros en RAM.

El costo real depende de si la página ya está en el buffer pool (RAM) o no:

| situación                                      | costo                                         |
| ---------------------------------------------- | --------------------------------------------- |
| tuple dentro de la misma página, página en RAM | nanosegundos — aritmética de punteros         |
| página distinta, ya en buffer pool (RAM)       | nanosegundos — cálculo de offset + acceso RAM |
| página distinta, **no** en buffer pool         | milisegundos — I/O de disco para cargar 8 KB  |

El salto entre "en RAM" y "en disco" es de 4–5 órdenes de magnitud (~100 ns vs ~1–10 ms).
Por eso el buffer pool existe: mantener en RAM las páginas calientes es la optimización más
importante del motor. Pasar de una tuple a otra dentro de la misma página es prácticamente
gratuito; forzar una carga de página nueva desde disco es lo que se quiere evitar.

A esto se le llama la **estructura de dos niveles** del row-store: el **heap file** es el
contenedor en disco y la **página** es la unidad mínima de I/O — el bloque que el motor
siempre carga completo en RAM, nunca byte a byte. Tres mecanismos complementan este
modelo de páginas: **fill factor** (reservar espacio libre para UPDATEs futuros),
**HOT updates** (evitar actualizar índices cuando la tuple nueva cabe en la misma página),
y **TOAST** (mover valores grandes a un heap auxiliar). Estos se cubren en detalle en
`guia_transacciones_db.md`. Bloque 3 contrasta el row-store con el modelo columnar.

---

### Sección 1.3: Anatomía de una página — tamaños y estructura por motor {#1-anatomia-pagina}

Con el heap file definido, el siguiente nivel de zoom muestra qué hay físicamente
dentro de cada uno de esos bloques de 8 KB.

```
┌─────────────────────────────────────────────────────────────────┐
│  PAGE HEADER (24 bytes)                                         │
│  • pd_lsn: LSN de la última modificación (para WAL recovery)    │
│  • pd_checksum: checksum para detectar corrupción               │
│  • pd_flags: flags de estado (all-visible, has-free-lines, etc) │
│  • pd_lower: offset al final del área de item pointers          │
│  • pd_upper: offset al inicio del área de tuples                │
│  • pd_special: offset al inicio del área especial               │
├─────────────────────────────────────────────────────────────────┤
│  ITEM POINTERS — también llamados "slots" o "line pointers"     │
│  Array de entradas de 4 bytes, una por tuple en la página.      │
│  • slot[1]: offset=8100, len=72, flags=normal                   │
│  • slot[2]: offset=8028, len=72, flags=normal                   │
│  • slot[3]: offset=7956, len=72, flags=dead (fila borrada;      │
│             conserva offset/len originales hasta que VACUUM     │
│             compacta la página y lo marca UNUSED)               │
│  Los offsets apuntan cerca del final de la página (≈8192)       │
│  porque las tuples crecen desde el fondo hacia arriba.          │
│  • ...                                                          │
├─────────────────────────────────────────────────────────────────┤
│  ESPACIO LIBRE (área entre pd_lower y pd_upper)                 │
│  (puede ser 0 si la página está llena)                          │
├─────────────────────────────────────────────────────────────────┤
│  TUPLE 2 — fila 2 en disco (segunda insertada, encima de la 1)  │
│  TUPLE 1 — fila 1 en disco (primera insertada, más al fondo)    │
│  ÁREA ESPECIAL (usada solo en páginas de índice, no en heap)    │
└─────────────────────────────────────────────────────────────────┘
```

Este diagrama usa una vista **vertical** de la página (de arriba a abajo). Los item
pointers crecen hacia abajo desde el header; las tuples crecen hacia arriba desde el
fondo; el espacio libre queda en el medio. La sección siguiente muestra la misma página
en vista **horizontal** — `[header][slots→][libre][←tuples]` — que es la que usan los
diagramas de `pd_lower`/`pd_upper`. Ambas representan lo mismo desde ángulos distintos.

> El campo `pd_lsn` vincula cada página con el WAL: el motor lo usa para saber si la página
> ya fue respaldada en el log antes de un crash. Ver `01_internals_del_motor.md §3` para la
> explicación completa del WAL y por qué garantiza durabilidad.

**¿Qué son los item pointers / slots?**

Los slots son una **capa de indirección** entre los índices externos y las tuples físicas.
Cada slot almacena el offset en bytes donde empieza su tuple _dentro de esta página_. Para
localizar una tuple, el motor necesita dos coordenadas:

```
ctid = (número de página, número de slot)
```

El número de página dice qué bloque de 8 KB cargar; el número de slot dice qué entrada del
array consultar; y esa entrada contiene el offset exacto en bytes donde vive la tuple.

El objetivo **no** es simplemente "saber la posición" — es mantener esa posición estable.
Los índices B+Tree apuntan a un `ctid` (página + slot), no a un byte offset. Eso permite
que VACUUM mueva tuples físicamente dentro de la página para consolidar espacio sin
romper ningún índice: solo actualiza el `lp_off` dentro del slot, y el ctid `(página, slot)`
sigue siendo válido. Si los índices apuntaran directamente al byte offset, cada VACUUM
invalidaría todos los índices de la tabla.

En cuanto a actualizaciones del array:

- **INSERT** → se agrega un slot nuevo (pd_lower avanza 4 bytes).
- **DELETE** → el slot permanece `NORMAL`; lo que cambia es `t_xmax` en el header de la
  tuple (marca la transacción que la eliminó). El slot solo cambia a `DEAD` cuando VACUUM
  confirma que la tuple ya no es visible para ninguna transacción activa.
- **UPDATE** → en PostgreSQL es DELETE + INSERT (MVCC), así que aplica lo mismo.
- **VACUUM** → limpia los slots DEAD, puede marcarlos UNUSED para reutilizarlos en el
  siguiente INSERT. El número de slot nunca cambia; lo que cambia es el `lp_off` interno.

**Tamaños de página por motor**

```
╔══════════════╦══════════════════╦══════════════════════╦══════════════════════════════════════╗
║ Motor        ║ Tamaño default   ║ Rango configurable   ║ Nota                                 ║
╠══════════════╬══════════════════╬══════════════════════╬══════════════════════════════════════╣
║ PostgreSQL   ║ 8 KB             ║ 1–32 KB (compile)    ║ Heap pages + index pages misma talla ║
║ MySQL InnoDB ║ 16 KB            ║ 4–64 KB (tablespace) ║ Leaf pages = datos ordenados por PK  ║
║ SQL Server   ║ 8 KB             ║ Fijo (no config.)    ║ Extent = 8 páginas = 64 KB           ║
║ Oracle       ║ 8 KB (BLOCKSIZE) ║ 2–32 KB (tablespace) ║ Múltiples block sizes por tablespace ║
║ SQLite       ║ 4 KB             ║ 512 B–64 KB          ║ Una sola B+tree por archivo          ║
║ DynamoDB     ║ — (opaco)        ║ —                    ║ Sin página visible al usuario        ║
║ MongoDB      ║ 4 KB (WiredTiger)║ Configurable         ║ Internal cache pages, no expuesto    ║
╚══════════════╩══════════════════╩══════════════════════╩══════════════════════════════════════╝
```

La elección del tamaño de página es un tradeoff: páginas **grandes** reducen el número
de I/Os para range scans pero desperdician caché en queries de punto. Páginas **pequeñas**
se ajustan mejor a OLTP pero requieren más I/Os para scans. El valor de 8 KB de PostgreSQL
y SQL Server es un compromiso histórico que funciona bien para la mayoría de workloads OLTP.

---

### Sección 1.4: La estructura de datos del item pointer array {#1-item-pointer}

Cada entrada del array es un `uint32` (4 bytes) con tres campos empaquetados en bits:

```
Item pointer — 32 bits totales:
  bits 14..0   (15 bits): lp_off   — offset en bytes de la tuple desde el inicio de la página
  bits 16..15  ( 2 bits): lp_flags — estado de la tuple
  bits 31..17  (15 bits): lp_len   — longitud de la tuple en bytes

Ejemplo: slot[1] = offset=8100, len=36, flags=NORMAL
  → la tuple empieza en el byte 8100 de esta página y mide 36 bytes
  → acceso: leer page[8100..8135]
```

`lp_off` no es lo mismo que el offset de una columna dentro de la tuple: `lp_off` indica
dónde empieza la **tuple** dentro de la **página**. El offset de columna (calculado de
`pg_attribute.attlen`) indica dónde empieza una **columna** dentro del bloque de datos
de la tuple. Son dos bytes en niveles distintos — primero llegas a la tuple con `lp_off`,
luego navegas dentro de ella con el offset de columna.

**Los cuatro valores de `lp_flags`:**

| flag       | qué significa                                                                                                                                                      |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `UNUSED`   | slot vacío, nunca usado o ya liberado por VACUUM                                                                                                                   |
| `NORMAL`   | tuple viva y legible                                                                                                                                               |
| `DEAD`     | tuple muerta confirmada; marcado por heap pruning (en lecturas normales) o por VACUUM — en ambos casos los índices aún apuntan aquí y el espacio no está reclamado |
| `REDIRECT` | el slot apunta a otro slot (caso especial de HOT updates)                                                                                                          |

El número de slot (1, 2, 3...) es el segundo componente del ctid: `ctid = (página, slot)`.
El número de slot es estable mientras la tuple está viva o muerta — un ctid como (3, 7)
seguirá apuntando al slot 7 de la página 3 aunque la tuple esté muerta. Lo que cambia
cuando VACUUM compacta es el `lp_off` dentro del slot, no el número del slot en sí.
Sin embargo, una vez que VACUUM marca un slot como UNUSED ese número queda disponible
para un INSERT futuro, que puede asignárselo a una fila completamente distinta. Por eso
el ctid no es un identificador estable a largo plazo.

El array de slots crece desde `pd_lower` hacia la derecha; las tuples se insertan desde
`pd_upper` hacia la izquierda. La página está llena cuando los dos punteros se tocan:

```
Página vacía:
 byte:  0        24                                               8192
        ┌─────────┬───────────────────────────────────────────────┐
        │ header  │                    libre                      │
        └─────────┴───────────────────────────────────────────────┘
                  ↑                                               ↑
              pd_lower=24                                   pd_upper=8192
              libre = 8192 − 24 = 8168 bytes

Tras insertar t1 (36 B) y t2 (36 B):
 byte:  0        24   28   32                        8120 8156 8192
        ┌─────────┬────┬────┬────────────────────────┬────┬────┐
        │ header  │ s1 │ s2 │         libre          │ t2 │ t1 │
        └─────────┴────┴────┴────────────────────────┴────┴────┘
                            ↑                        ↑
                        pd_lower=32             pd_upper=8120
                        libre = 8120 − 32 = 8088 bytes

Página llena:
        ┌─────────┬─────────────┬────────────────────────────────┐
        │ header  │    slots    │            tuples              │
        └─────────┴─────────────┴────────────────────────────────┘
                               ↑↑
                     pd_lower == pd_upper  →  sin espacio libre
```

**Por qué existe esta capa de indirección**

Los índices externos (B+Tree, etc.) apuntan a una tuple con su `ctid`: `(página, slot)`.
El número de slot es fijo en el array — slot 3 siempre está en el mismo lugar del header.
Lo que puede cambiar es el `lp_off` dentro de ese slot, pero los índices nunca lo leen
directamente: solo dicen "dame el slot N" y el motor calcula el offset desde ahí.

```
Índice B+Tree              Página
┌──────────────────┐       ┌─────────────────────────────────────┐
│ key=42 │ ctid    │──────→│ slot3: lp_off=8120, len=36, NORMAL  │
│        │ (7, 3) │       │              ↓                      │
└──────────────────┘       │ byte 8120: [ datos de la tuple ]   │
                           └─────────────────────────────────────┘
          (página 7, slot 3) — el índice no conoce el byte offset
```

Esto importa porque VACUUM mueve tuples físicamente dentro de la página para consolidar
el espacio libre. Supón que se eliminó t2 con DELETE. El proceso pasa por tres estados:

```
① Tras DELETE de t2  (slot sigue NORMAL; solo cambia t_xmax en la tuple):

 byte:  0        24   28   32   36               8084 8120 8156 8192
        ┌─────────┬────┬────┬────┬───────────────┬────┬────┬────┐
        │ header  │ s1 │ s2 │ s3 │     libre     │ t3 │ t2 │ t1 │
        │         │NRM │NRM │NRM │               │ ✓  │ ✗  │ ✓  │
        └─────────┴────┴────┴────┴───────────────┴────┴────┴────┘
                            ↑                    ↑
                        pd_lower=36          pd_upper=8084
        t2 tiene t_xmax≠0 — muerta lógicamente, pero slot NORMAL e índices intactos

② Tras heap pruning  (confirma que ninguna txn activa ve t2; ocurre en lecturas
   normales de la página — no requiere VACUUM):

 byte:  0        24   28   32   36               8084 8120 8156 8192
        ┌─────────┬────┬────┬────┬───────────────┬────┬────┬────┐
        │ header  │ s1 │ s2 │ s3 │     libre     │ t3 │ t2 │ t1 │
        │         │NRM │DEAD│NRM │               │ ✓  │ ✗  │ ✓  │
        └─────────┴────┴────┴────┴───────────────┴────┴────┴────┘
                            ↑                    ↑
                        pd_lower=36          pd_upper=8084
        s2 = DEAD: el motor sabe que t2 es irrecuperable, pero los índices
        todavía apuntan a (página, s2) — aún no se han limpiado

③ Después de VACUUM  (limpia índices → compacta página → s2 queda UNUSED):

 byte:  0        24   28   32   36                    8120 8156 8192
        ┌─────────┬────┬────┬────┬────────────────────┬────┬────┐
        │ header  │ s1 │ s2 │ s3 │       libre        │ t3 │ t1 │
        │         │NRM │UNSD│NRM │                    │    │    │
        └─────────┴────┴────┴────┴────────────────────┴────┴────┘
                            ↑                         ↑
                        pd_lower=36              pd_upper=8120
        libre = 8084 B  (ganó 36 B — el tamaño exacto de t2)
        s2 = UNUSED: slot disponible para el próximo INSERT
```

VACUUM actualizó `lp_off` en s3 (t3 se movió de byte 8084 → 8120). Los índices externos
no cambiaron: siguen apuntando a `(página, s1)` y `(página, s3)`.
Si apuntaran directamente al byte offset, cada VACUUM rompería todos los índices.

**Por qué todo es array:** heap file, item pointer array y column-store usan arrays de
elementos de tamaño fijo, lo que hace posible el acceso O(1) por posición en cada nivel:

```
heap file    → página N:  byte_offset = N × 8192           O(1)
página       → slot K:    byte_offset = 24 + (K−1) × 4     O(1)
tuple        → columna C: byte_offset = sum(attlen anteriores) O(1) solo si todas
                          las columnas anteriores son de ancho fijo (INT, BIGINT…)
                          Si alguna es variable (TEXT, JSONB…), attlen = −1 y el
                          motor debe recorrer columna por columna → O(N)
column-store → fila i:    byte_offset = i × tamaño_tipo    O(1)  (→ Bloque 3)
```

Lo que NO es un array es el índice B+tree — ese es un árbol balanceado que se
recorre en O(log n). Todos los demás niveles son arrays que soportan O(1) por posición,
con la excepción de las columnas variables dentro de una tuple.

---

### Sección 1.5: Anatomía de una tuple (fila / row) {#1-anatomia-tuple}

Una tuple es el contenido al que apunta cada slot del item pointer array: el `lp_off`
del slot indica el byte de la página donde empieza esta estructura. Cada fila que
insertas con `INSERT` se convierte en una tuple en disco. La diferencia entre
lo que tú ves en SQL y lo que vive en disco es el **tuple header**: el motor antepone 23 bytes
de metadata que SQL nunca te muestra pero que usa internamente para MVCC, para saber si la fila
está viva o muerta, y para encontrar versiones más nuevas de la misma fila tras un UPDATE.

```
┌──────────────────────────────────────────────────────────────────┐
│  TUPLE HEADER (23 bytes mínimo; crece si hay NULL bitmap)        │
│  • t_xmin:  ID de txn que INSERT esta fila (para MVCC)           │
│  • t_xmax:  ID de txn que DELETE/UPDATE esta fila (0 si activa)  │
│  • t_ctid:  (página, slot) de la versión más nueva de esta fila  │
│             Si la fila fue UPDATEd, la versión vieja tiene aquí  │
│             el ctid de la versión nueva — cadena de versiones     │
│  • t_infomask: flags de estado (null bits, vacuumed, etc.)       │
│  • t_hoff:  offset real al inicio de los datos (≥24 si hay NULLs)│
├──────────────────────────────────────────────────────────────────┤
│  NULL BITMAP (1 bit por columna nullable, si la fila tiene NULLs)│
│  Cada bit indica si ESA columna EN ESTA FILA es NULL (1) o no   │
│  (0). Es por fila, no global — dos filas distintas pueden tener  │
│  NULLs en columnas distintas. Si ningún valor es NULL, el bitmap │
│  no se incluye en el header (se ahorra espacio).                 │
├──────────────────────────────────────────────────────────────────┤
│  PADDING (alineación a 8 bytes)                                  │
├──────────────────────────────────────────────────────────────────┤
│  DATOS DE LAS COLUMNAS                                           │
│  • col1: INTEGER (4 bytes)                                       │
│  • col2: TEXT (longitud variable, con prefijo de longitud)       │
│  • col3: TIMESTAMP (8 bytes)                                     │
│  • ...                                                           │
└──────────────────────────────────────────────────────────────────┘
```

---

### Sección 1.6: El ctid: la dirección física de una fila {#1-ctid}

Antes de entrar en el detalle, una distinción que conviene tener clara porque aplica
en muchos contextos de bases de datos, no solo aquí:

```
Identificador único   → responde "¿cuál fila es esta?"
                        estable, lo define el modelo de negocio
                        ejemplo: id=42 siempre es el mismo pedido

Dirección física      → responde "¿dónde están los bytes de esta fila?"
                        efímera, la gestiona el motor internamente
                        ejemplo: ctid=(3,7) es donde vive HOY ese pedido
```

En otros contextos (punteros en C, direcciones de memoria) estos dos roles suelen
ser el mismo: la dirección _es_ el identificador. En bases de datos están
deliberadamente separados porque el motor necesita libertad para mover datos
físicamente — compactar páginas, reescribir el heap, reordenar filas — sin que eso
afecte a nada del mundo exterior. El identificador permanece estable; la dirección
es un detalle interno que puede cambiar en cualquier operación de mantenimiento.

Con eso claro: el `ctid` es la dirección física. La PRIMARY KEY es el identificador.

---

El término "ctid" aparece en dos contextos distintos que es fácil confundir. Son
conceptualmente relacionados pero tienen propósitos opuestos:

```
ctid como dirección externa  →  cómo el mundo exterior localiza una tuple
t_ctid dentro del header     →  cómo la tuple vieja apunta a su versión nueva
```

**El ctid como dirección externa**

`ctid` tiene formato `(página, slot)`. El primer número es el índice de la página dentro
del heap file (empezando en 0); el segundo es el número de slot en el item pointer array
de esa página (empezando en 1). No es un byte offset — el byte exacto lo calcula el motor
leyendo `lp_off` dentro del slot una vez que llega a él.

Este ctid **no está guardado dentro de la tuple**. El motor lo conoce implícitamente
por ubicación física: cuando carga la página N ya sabe que es la página N; cuando
recorre el slot K ya sabe que es el slot K. Ese par `(N, K)` es el ctid. Los índices
B+Tree guardan una copia de ese par como puntero al heap — son ellos quienes lo
almacenan externamente, no la tuple.

```sql
-- PostgreSQL expone el ctid como columna virtual de sistema
SELECT ctid, id, nombre FROM productos LIMIT 5;
--  ctid  │ id │ nombre
-- -------│----│---------
-- (0,1)  │  1 │ Laptop      ← página 0, slot 1
-- (0,2)  │  2 │ Mouse       ← página 0, slot 2
-- (0,3)  │  3 │ Teclado     ← página 0, slot 3
-- (1,1)  │  4 │ Monitor     ← página 1, slot 1 (nueva página)
-- (1,2)  │  5 │ Webcam      ← página 1, slot 2
```

```
Índice B+tree:  mapea valor de clave → ctid     ("la fila con id=42 está en (3,7)")
ctid:           ES la dirección física           (página 3, slot 7)
```

El índice B+tree es el MAPA. El ctid es el DESTINO. Sin índice puedes usar el ctid
directamente (`WHERE ctid = '(0,1)'`); sin ctid el índice no lleva a ningún lado.

**¿Cuándo cambia el ctid y cuándo no?**

El VACUUM regular — el que compacta páginas moviendo tuples dentro de la misma página —
**no cambia el ctid**. Ese es exactamente el propósito de la capa de indirección de los
slots: VACUUM solo actualiza `lp_off` dentro del slot, y el par `(página, slot)` que
conocen los índices sigue siendo válido.

Lo que sí cambia el ctid son tres operaciones específicas donde la tuple termina en una
ubicación física completamente distinta:

| operación                | por qué cambia el ctid                                                                                |
| ------------------------ | ----------------------------------------------------------------------------------------------------- |
| `VACUUM FULL`            | reescribe la tabla entera en un archivo nuevo; todas las tuples obtienen nuevas posiciones            |
| `CLUSTER`                | reordena físicamente la tabla según un índice; mismo efecto que VACUUM FULL                           |
| `UPDATE` fuera de página | si la tuple nueva no cabe en la misma página, va a otra; el nuevo ctid es `(otra_página, nuevo_slot)` |

Además, los slots marcados UNUSED pueden ser reutilizados por un INSERT futuro. Cuando
eso pasa, el ctid `(página, slot)` queda asociado a una fila completamente distinta —
no a la que lo tenía antes.

**Cómo se relacionan la PRIMARY KEY y el ctid**

La PRIMARY KEY no reemplaza al ctid ni convive con él de forma paralela e independiente.
Son dos niveles del mismo camino de búsqueda:

```
query: WHERE id = 42
         │
         ▼
  Índice B+Tree sobre id
  (nodo hoja): id=42 → ctid=(3,7)   ← el índice guarda el ctid
         │
         ▼
  Heap file, página 3
  slot 7: lp_off=8064, len=36
         │
         ▼
  bytes 8064–8099 de la página 3
  → tuple con id=42, nombre='Ana', salario=95000
```

El índice del PRIMARY KEY es un B+Tree donde cada nodo hoja almacena el par
`(valor_de_clave → ctid)`. El valor de la PK (`id=42`) es la llave de búsqueda;
el ctid es el puntero al heap. Sin ese puntero, el índice sabría que `id=42` existe
pero no sabría dónde ir a leer sus bytes.

La PRIMARY KEY como valor también vive dentro de la tuple (en el área de datos de
columnas, como cualquier otro campo). El índice guarda una copia de ese valor como
llave de búsqueda. Son dos copias del mismo dato en dos lugares distintos con roles
distintos: una dentro de la tuple para que `SELECT id` te la devuelva, otra en el
índice para que `WHERE id=42` la encuentre.

**¿Qué pasa con el B+Tree cuando el ctid cambia?**

Sí — si el ctid de una tuple cambia, el índice queda apuntando a una dirección
inválida y debe actualizarse. Cómo se maneja depende del caso:

| caso                            | qué pasa con el índice                                           |
| ------------------------------- | ---------------------------------------------------------------- |
| VACUUM regular (in-page)        | **nada** — ctid no cambia, slot absorbe el cambio interno        |
| UPDATE en la misma página (HOT) | **nada** — ctid no cambia gracias al mecanismo de slots          |
| UPDATE que va a otra página     | el motor actualiza la entrada del índice con el nuevo ctid       |
| VACUUM FULL / CLUSTER           | reconstruye todos los índices desde cero tras reescribir el heap |

El caso más común en producción es el UPDATE in-page (HOT), que gracias a la capa de
indirección de slots evita tocar los índices — ese es uno de los beneficios más concretos
de ese diseño. El caso más costoso es VACUUM FULL/CLUSTER: reconstruir todos los índices
de una tabla grande bloquea escrituras y puede tardar minutos.

**Por qué la PRIMARY KEY no tiene los problemas del ctid**

La PRIMARY KEY es un valor que tú defines como parte del modelo de datos — no una
dirección física. El motor nunca la reasigna ni la recicla: `id=42` siempre identifica
la misma fila de negocio aunque esa fila cambie de página, de slot o de archivo.
El ctid en cambio es una dirección temporal: puede cambiar con VACUUM FULL, CLUSTER, o
un UPDATE que cruza de página, y el mismo `(página, slot)` puede quedar asociado a una
fila completamente distinta si el slot se recicla en un INSERT posterior.

Usar ctid como identificador externo (guardarlo en otra tabla, exponerlo a una API) es
un bug latente: la próxima operación de mantenimiento lo invalida silenciosamente.

**`t_ctid` dentro del tuple header — propósito distinto**

El campo `t_ctid` que aparece en el header de cada tuple (ver diagrama de anatomía más
arriba) no es para que los de afuera encuentren esta tuple. Es para encadenar versiones
tras un UPDATE.

Cuando haces `UPDATE`, PostgreSQL no modifica la tuple existente — crea una tuple nueva
con un ctid diferente y marca la vieja como muerta (`t_xmax ≠ 0`). Para que el motor
pueda navegar de la versión vieja a la nueva, guarda en `t_ctid` de la vieja el ctid de
la nueva:

```
tuple vieja  (ctid = (3,2), t_xmax = 501)
  └─ t_ctid → (3,5)   ← apunta a la versión nueva

tuple nueva  (ctid = (3,5), t_xmax = 0)
  └─ t_ctid → (3,5)   ← apunta a sí misma (es la versión actual)
```

Si la fila nunca fue actualizada, `t_ctid` apunta a la propia tuple. Si fue actualizada,
forma una cadena que el motor recorre para encontrar la versión visible según MVCC.

En resumen: `t_ctid` vive dentro de la tuple pero apunta hacia adelante en la cadena de
versiones — no es la dirección de esta tuple sino la de su sucesora. La dirección de
esta tuple la conoce quien llegó a ella (el índice, el seq scan), no la tuple misma.

VACUUM recoge las tuples muertas al final de la cadena y libera su espacio.

**El concepto de dirección física en otros motores**

El `ctid` es específico de PostgreSQL, pero cada motor tiene su equivalente interno:

| Motor        | Nombre              | Formato / semántica                                                          |
| ------------ | ------------------- | ---------------------------------------------------------------------------- |
| PostgreSQL   | `ctid`              | `(página, slot)` — expuesto como columna de sistema                          |
| MySQL InnoDB | PK / `_rowid`       | El clustered index ES la dirección; no hay segundo nivel de indirección      |
| SQL Server   | RID (heap) o CI key | `FileID:PageID:SlotID` en heap; clave del clustered index en tablas CI       |
| Oracle       | `ROWID`             | `OOOOOOFFFBBBBBBSSS` — objeto, archivo relativo, bloque, número de fila      |
| SQLite       | `rowid` implícito   | Entero 64-bit; el B+Tree lo usa como clave de clustering en tablas con rowid |

La diferencia fundamental: en PostgreSQL el `ctid` es una dirección de heap
independiente del índice — el índice apunta al heap _usando_ el ctid. En motores con
clustered index (InnoDB, SQL Server con CI, Oracle IOT, SQLite), **la dirección física
y el valor de la PK son la misma cosa**: el árbol B+Tree lleva directamente a los datos,
sin segundo salto al heap.

---

### Sección 1.7: Cómo el motor sabe qué bytes corresponden a qué columna: el catálogo {#1-catalogo}

Imagina que alguien te entrega esta tira de bytes:

```
01 00 00 00  E8 03 00 00  01 00 00 00
```

¿Qué significa? No puedes saberlo. Los bytes en disco no tienen etiquetas — podrían ser
tres enteros, texto, cualquier cosa. Pero si tienes esta tabla de referencia:

```
Posición 0, tamaño 4 bytes, tipo INT4  → esto es "id"
Posición 4, tamaño 4 bytes, tipo INT4  → esto es "precio"
Posición 8, tamaño 4 bytes, tipo INT4  → esto es "cantidad"
```

Ahora sí:

- Bytes 0–3: `01 00 00 00` → id = 1
- Bytes 4–7: `E8 03 00 00` → precio = 1000
- Bytes 8–11: `01 00 00 00` → cantidad = 1

Eso es exactamente lo que hace PostgreSQL. Esa "tabla de referencia" se llama
**`pg_attribute`** — una tabla interna donde viven los nombres, tipos y posiciones de
todas las columnas de todas las tablas. Sin ella, los bytes no significan nada.
A diferencia del ctid o del `lp_off`, `pg_attribute` no es una dirección: no te dice
dónde está ninguna tuple. Te dice cómo interpretar los bytes una vez que ya llegaste a
la tuple por ctid → slot → lp_off. Son herramientas para preguntas distintas:
ctid/slot/lp_off responden "¿dónde está?"; pg_attribute responde "¿qué significan sus bytes?".
La tabla relevante aquí guarda la definición de cada columna de cada tabla:

```sql
SELECT attnum, attname, atttypid::regtype AS tipo, attlen, attalign
FROM pg_attribute
WHERE attrelid = 'ventas'::regclass AND attnum > 0
ORDER BY attnum;

--  attnum │ attname  │  tipo   │ attlen │ attalign
-- ────────│──────────│─────────│────────│──────────
--       1 │ id       │ integer │      4 │ i  (alineación a 4 bytes)
--       2 │ precio   │ integer │      4 │ i
--       3 │ cantidad │ integer │      4 │ i
```

Con esa información el motor sabe exactamente cómo leer cada tuple:

- columna 1 (`id`): leer 4 bytes desde el offset 0
- columna 2 (`precio`): leer 4 bytes desde el offset 4 (0 + 4)
- columna 3 (`cantidad`): leer 4 bytes desde el offset 8 (4 + 4)

Las columnas se almacenan en el orden de `attnum` (el orden en que las declaraste en
`CREATE TABLE`), con padding entre ellas si el tipo siguiente requiere alineación mayor.

**El offset no está guardado — se calcula.** `pg_attribute` no almacena directamente "precio
empieza en el byte 4". Lo que almacena es el `attlen` de cada columna (cuántos bytes ocupa).
El motor calcula el offset en runtime sumando los `attlen` de todas las columnas anteriores:

```
attnum 1 → id,       attlen=4  → offset = 0
attnum 2 → precio,   attlen=4  → offset = 0 + 4 = 4
attnum 3 → cantidad, attlen=4  → offset = 4 + 4 = 8
```

Si un tipo requiere alineación mayor (por ejemplo `BIGINT` alineado a 8 bytes), el motor
inserta bytes de padding antes de esa columna para que caiga en la dirección correcta.

**Qué contiene cada fila de `pg_attribute`:**

| campo      | qué contiene                                                                                                                                                                                   |
| ---------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `attrelid` | OID de la tabla a la que pertenece esta columna                                                                                                                                                |
| `attnum`   | número de orden de la columna (1 = primera del `CREATE TABLE`) — no confundir con el número de slot: `attnum` es posición lógica de columna; el slot es posición física de tuple en una página |
| `attname`  | el nombre de la columna (`"precio"`, `"id"`, etc.)                                                                                                                                             |
| `atttypid` | referencia al tipo de dato (en `pg_type`)                                                                                                                                                      |
| `attlen`   | cuántos bytes ocupa ese tipo (`-1` = longitud variable)                                                                                                                                        |
| `attalign` | regla de alineación en memoria                                                                                                                                                                 |

**El esquema de una tabla vive en `pg_attribute`, no en el heap file.** Cuando haces
`CREATE TABLE ventas (...)`, PostgreSQL crea el heap file vacío y además inserta una fila
en `pg_attribute` por cada columna declarada. El heap file solo contiene bytes de datos;
los nombres, tipos y posiciones de esas columnas están en otro lugar: el catálogo.

**Bootstrap:** si todo se describe con `pg_attribute`, ¿qué describe a `pg_attribute`
misma? Su propio esquema está hardcodeado en el código fuente de PostgreSQL
(`src/include/catalog/pg_attribute.h`). Es el piso fijo del que arranca el catálogo —
no hay circularidad.

```
Lo que ves en SQL:           Lo que hay en disco:
─────────────────────        ──────────────────────────────────────────────
id       = 1                 bytes  0– 3: 01 00 00 00
precio   = 1000       ←      bytes  4– 7: E8 03 00 00   ← el "nombre" precio
cantidad = 1                 bytes  8–11: 01 00 00 00      solo existe en pg_attribute
                             (más los 24 bytes del header antes de todo esto)
```

Cuando haces `SELECT precio`, el motor consulta `pg_attribute` para saber en qué posición
de la tuple están los bytes que corresponden a `precio`, los lee y te los devuelve.

En **column-store** la situación es distinta: cada columna tiene su propio archivo en
disco (`precio.col`), así que no hace falta calcular posiciones — todos los precios están
juntos de forma continua. Esa diferencia es el origen de toda la discusión de Bloque 3.

---

### Sección 1.8: ¿Cómo se ven realmente los datos en disco? {#1-datos-en-disco}

Los datos son **bytes binarios puros** — no es texto, no es SQL, no es JSON. Si abres el archivo
del heap con un editor hexadecimal ves algo como esto:

```
00000000  00 00 00 00 D8 01 98 01  00 00 00 00 24 00 60 1F  │....Ø.˜.....$.`_│
00000010  00 20 04 20 00 00 00 00  E0 9F 4E 00 A0 9F 4E 00  │. . ....à.N. .N.│
...
```

Esos bytes son la página entera — header, item pointers, tuples (filas) — todo junto.

**Ejemplo concreto.** Dada esta tabla (heap file) y fila (tuple en disco):

```sql
CREATE TABLE empleados (id INT, nombre TEXT, salario INT);
INSERT INTO empleados VALUES (42, 'Ana', 95000);
```

El área de **datos de columnas** de esa tuple (después del header de 23 bytes) se ve así:

```
Offset  Bytes               Significado
──────  ──────────────────  ────────────────────────────────────────────────
  +0    2A 00 00 00         id = 42   (INT4, little-endian: 0x0000002A = 42)
  +4    09 41 6E 61         nombre = 'Ana'
        └─┤  └──────────── datos: 'A'=0x41  'n'=0x6E  'a'=0x61  (ASCII)
          └── prefijo de longitud (1 byte): 0x09 = 4 bytes totales (1 header + 3 datos)
  +8    18 73 01 00         salario = 95000  (INT4, little-endian: 0x00017318)
```

Puedes ver esto con la extensión `pageinspect` de PostgreSQL:

```sql
CREATE EXTENSION IF NOT EXISTS pageinspect;

SELECT t_data
FROM heap_page_items(get_raw_page('empleados', 0))
LIMIT 1;

-- Resultado (son los bytes del área de datos, en hex):
--            t_data
-- ────────────────────────────
--  \x2a00000009416e6118730100
--    └──id=42──┘└──'Ana'──┘└─salario=95000─┘
```

Cada tipo de dato tiene un tamaño y encoding fijo que el motor conoce por el catálogo del sistema
(`pg_attribute`):

| Tipo             | Tamaño en disco  | Encoding                               |
| ---------------- | ---------------- | -------------------------------------- |
| `INT` / `INT4`   | 4 bytes fijos    | little-endian                          |
| `BIGINT`         | 8 bytes fijos    | little-endian                          |
| `BOOLEAN`        | 1 byte           | `01` = true, `00` = false              |
| `TIMESTAMP`      | 8 bytes fijos    | microsegundos desde 2000-01-01         |
| `TEXT`/`VARCHAR` | variable         | prefijo de longitud + bytes del string |
| `NULL`           | 0 bytes en datos | marcado en el NULL bitmap del header   |

Sin el esquema de la tabla, esos bytes son ininterpretables — es el esquema el que le dice al motor
dónde termina cada campo y cómo leerlo.

---

### Sección 1.9: Por qué no hay orden "natural" {#1-sin-orden}

El heap no mantiene las filas ordenadas por ningún valor — las inserta donde haya espacio.
Cuando borras una fila, su slot pasa a DEAD; en el siguiente INSERT el motor puede
reutilizar ese slot (DEAD → UNUSED → reasignado), de modo que la nueva fila ocupa
físicamente el lugar de la borrada. El resultado: el orden de retorno de un `SELECT`
sin `ORDER BY` depende del orden físico en páginas, no del orden de inserción.

```sql
-- Insertas filas en este orden: 1, 2, 3
INSERT INTO t VALUES (1), (2), (3);

-- Borras la fila del medio
DELETE FROM t WHERE id = 2;

-- Insertas una nueva fila: ocupa el espacio liberado por id=2
INSERT INTO t VALUES (99);

-- El SELECT sin ORDER BY puede devolver: 1, 99, 3
-- (o cualquier otro orden dependiendo del planner y el estado del buffer pool)
SELECT * FROM t;  -- NUNCA asumir orden sin ORDER BY explícito
```

---

### Sección 1.10: Cómo se conectan todas las capas: estructuras de datos y costo de acceso {#1-capas}

Cada nivel del heap es una estructura de datos distinta. El motor las encadena para llegar
a los bytes de una columna específica:

```
NIVEL          ESTRUCTURA DE DATOS              ACCESO
─────────────  ───────────────────────────────  ───────────────────────────────────
Heap file      Array de bloques de tamaño fijo  Página N → byte_offset = N × 8192
               (como un array en memoria, pero   Buscar página N = O(1)
               en disco)

Página         Header fijo + array de slots      Slot K → byte_offset = 24 + (K-1)×4
               + espacio libre + tuples          Buscar slot K = O(1)

Item pointer   uint32 empaquetado:               Decodificar → (offset, length, flags)
(slot)         bits 14..0  = lp_off  (15 bits)  = O(1)
               bits 16..15 = lp_flags (2 bits)
               bits 31..17 = lp_len  (15 bits)

Tuple          Bytes secuenciales:               Columna fija → offset precomputado
               header + null bitmap + datos       desde pg_attribute = O(1)
                                                 Columna variable → escanear columnas
                                                 anteriores para saber dónde empieza
                                                 = O(posición de la columna)

pg_attribute   Tabla del catálogo                Dado el tipo y orden de la columna,
               (hash por OID en caché)           el motor sabe el offset y tamaño
                                                 = O(1) (siempre en RAM)
```

**El camino completo para leer `precio` de la fila con ctid = (2, 3):**

```
ctid = (página=2, slot=3)

Paso 1: Heap file → Página 2
  byte_offset = 2 × 8192 = 16384
  El motor hace seek(16384) y lee 8192 bytes           → O(1)

Paso 2: Página 2 → Slot 3
  slot_3 está en el byte 24 + (3-1)×4 = 32 de la página
  Lee el uint32 ahí: lp_off=8100, lp_len=36            → O(1)

Paso 3: Slot 3 → Tuple
  Lee bytes [8100 .. 8135] de la página (36 bytes)     → O(1)
  Los primeros 24 bytes son el header (t_hoff=24);
  los datos de columnas empiezan en el byte 8124 de la página.

Paso 4: Tuple → columna "precio"
  pg_attribute dice: precio es la 2ª columna, INT4 (4 bytes)
  id es INT4 (4 bytes) → ocupa los primeros 4 bytes de datos
  precio empieza en datos+4 = byte 8128 de la página
  Lee bytes [8128 .. 8131] de la página                → O(1)
  (O(1) porque id y precio son ambos de ancho fijo)

Total: O(1) — el acceso por ctid es el más rápido posible
```

**Costo de las operaciones comunes:**

```
Operación                            Estructura usada         Costo
───────────────────────────────────  ───────────────────────  ──────────────────
Acceso por ctid directo              Array heap → array slots  O(1)
Búsqueda con índice B+tree           B+tree + array heap       O(log n)
Scan completo (sin índice)           Array heap, página a pág. O(n_páginas)
INSERT sin índices                   FSM → append en página    O(1)
INSERT con k índices B+tree          FSM + actualizar índices  O(k · log n) †
DELETE                               Marcar t_xmax en tuple    O(1) *
UPDATE HOT (misma página)            Nueva tuple en misma pág. O(1)   ‡
UPDATE no-HOT (nueva página)         Nueva pág. + índices      O(k · log n) †
VACUUM (limpiar tuples muertas)      Scan de páginas sucias    O(pág. con muertas)

* DELETE marca la tuple como muerta pero no libera espacio — eso lo hace VACUUM.
† k = número de índices B+tree sobre la tabla; cada uno requiere un descenso O(log n).
‡ HOT evita actualizar índices: el slot antiguo queda como REDIRECT al nuevo, y los
  índices siguen apuntando al mismo (página, slot) de siempre.
```

**Por qué el scan sin índice es O(n_páginas) y no O(n_filas):**
El motor siempre opera en unidades de páginas, no de filas. Aunque solo necesite
1 columna, carga la página entera. El costo real del scan es proporcional al número
de páginas, que depende del tamaño de las filas y del fill factor.

---

### Sección 1.11: El camino completo: de SQL a bytes en disco {#1-camino-sql-disco}

```
INSERT INTO ventas VALUES (1, 1000, 1);

  1. CATÁLOGO (pg_class + pg_attribute)
     pg_class: una fila por tabla/índice/vista — guarda el OID y nombre de la relación
     pg_attribute: una fila por columna — guarda tipo, tamaño y orden (ya visto antes)
     → "ventas" tiene OID 24601
     → 3 columnas: id INT4 (4B), precio INT4 (4B), cantidad INT4 (4B)
     → tuple header struct: 23B
        t_hoff (donde empiezan los datos) = MAXALIGN(23) = 24B  ← 1B de padding interno
        datos: 3 columnas INT4 × 4B = 12B
        total: 24B + 12B = 36B

  2. FREE SPACE MAP (24601_fsm)
     → página 0 tiene 7.200 bytes libres → cabe la nueva tuple

  3. BUFFER POOL (RAM)
     → si la página 0 no está en RAM, la carga desde disco (8 KB completos)
     → la página ya está en RAM: no hay I/O adicional

  4. ESCRITURA EN LA PÁGINA (en RAM)
     → tuple header: t_xmin=txn_actual, t_xmax=0, t_ctid=(0, slot_nuevo)
     → datos de columnas en bytes:
        id=1     → 01 00 00 00
        precio=1000 → E8 03 00 00
        cantidad=1  → 01 00 00 00
     → item pointer array: nuevo slot apunta al offset de la tuple

  5. WAL (Write-Ahead Log)
     → antes de que esa página llegue a disco, el cambio se escribe en el WAL
     → si hay un crash antes del checkpoint, el WAL permite reconstruirlo

  6. CHECKPOINT (eventualmente)
     → el buffer pool escribe la página modificada ("dirty") al heap file en disco
     → los 8 KB completos se reescriben, aunque solo cambió 1 tuple
```

El camino complementario — cómo el motor **lee** una columna dado un `ctid` — está en
la sección anterior ("Cómo se conectan todas las capas", pasos 1–4).

---

## Bloque 2: Modelos de almacenamiento por motor: heap vs clustered index {#2-modelos-almacenamiento}

Bloque 1 describió a fondo el modelo heap, sus páginas, tuples, ctid y el catálogo del
motor. Este bloque compara ese modelo con las arquitecturas de MySQL InnoDB, SQL Server,
Oracle, SQLite y los motores NoSQL, y analiza cuándo cada diseño es la elección correcta.

---

### Sección 2.1: El modelo clustered index: MySQL InnoDB {#2-clustered-index}

---

#### Tres conceptos que el texto usa todo el tiempo — separarlos antes de empezar

En los diagramas de bases de datos se escribe "id" y se asume que el lector sabe qué
tipo de cosa es. Pero "id" puede significar tres cosas completamente distintas, y
confundirlas hace que los modelos de almacenamiento sean incomprensibles.

**1. El valor de clave primaria (PK value) — identificador lógico definido por el usuario**

Cuando escribes `CREATE TABLE ventas (id INT PRIMARY KEY, precio INT, cantidad INT)`,
el campo `id` es una columna normal que tú defines. El valor `id=2` es un
**identificador lógico**: le dice a la aplicación "esta fila es la venta número 2".
No tiene ninguna relación con la posición física de esa fila en disco — la fila
con `id=2` puede estar en el byte 131.072 del archivo o en el byte 4.294.967.296.
El valor lógico `id=2` no cambia a menos que el usuario ejecute explícitamente
`UPDATE ventas SET id = 99 WHERE id = 2`.

**2. La dirección física — coordenada donde viven los bytes en disco**

La **dirección física** es el número que el motor usa internamente para saber en qué
bytes exactos del archivo de disco está una fila. No es un concepto del modelo SQL;
es un mecanismo interno del motor de almacenamiento:

```
Motor         Nombre         Tamaño   Estructura interna
────────────  ─────────────  ───────  ───────────────────────────────────────────
PostgreSQL    ctid            6 B     (nº_página: 4B) + (nº_slot: 2B)
SQL Server    RID             8 B     (file_id: 2B) + (page_id: 4B) + (slot: 2B)
Oracle        ROWID          10 B     (object#: 32 bits) + (file#: 10 bits + block#: 22 bits, empaquetados en 4B) + (row#: 16 bits)
SQLite        rowid           8 B     entero de 64 bits — clave del B+tree interno
MySQL InnoDB  (no existe)     —       InnoDB NO expone dirección física pública
```

La dirección física sí puede cambiar sin que cambie el valor lógico de PK. En PostgreSQL,
cuando VACUUM mueve una tuple de la posición (página=7, slot=3) a (página=7, slot=1)
al compactar la página, el ctid cambia de `(7,3)` a `(7,1)` — pero el valor `id=2`
en los datos de la tuple no cambia. Son dos niveles distintos: el dato del usuario y
la coordenada interna del motor.

**3. La foreign key — referencia lógica entre tablas**

Una **foreign key** (FK) es cuando una columna de tabla B guarda el _valor lógico de
PK_ de una fila en tabla A. Por ejemplo:

```sql
CREATE TABLE pedidos (
    id         INT PRIMARY KEY,
    cliente_id INT REFERENCES clientes(id),  -- FK: guarda el valor lógico de PK de clientes
    monto      DECIMAL
);
```

El campo `cliente_id` guarda el valor `id=42` de la tabla `clientes`. No es ni una
dirección física ni un puntero de disco — es un dato que la aplicación pone ahí.
Para usar esa referencia, el motor hace una búsqueda en el índice de `clientes(id)`:

```
POSTGRESQL — resolución de FK  (cliente_id=42, referencia clientes.id que es PK):

  pedidos: [id=1] [cliente_id=42] [monto=500]
                        │  "dame la fila donde id=42"
                        ▼
    ① árbol índice de clientes(id):  clave 42 → ctid=(3,7)
    ② heap de clientes, pág 3, slot 7 → [id=42, nombre='Ana', email=...]  ✓
       2 accesos: árbol B+Tree + heap

MYSQL INNODB — misma FK:

  pedidos: [id=1] [cliente_id=42] [monto=500]
                        │  "dame la fila donde id=42"
                        ▼
    ① clustered index de clientes, navegar árbol con clave 42:
       hoja → [id=42, nombre='Ana', email=...]  ✓
       1 acceso: la hoja del B+Tree ya tiene todos los bytes

  EXCEPCIÓN — FK sobre columna no-PK (ej.: clientes.email UNIQUE):
    PostgreSQL: árbol de email → ctid → heap           (2 accesos)
    InnoDB:     árbol de email → val_PK → clustered    (2 árboles, más costoso)
```

La FK es un valor de dato del usuario. El costo de resolverla depende del motor
y de si la columna referenciada es la PK del target o un índice secundario.

```
Resumen de los tres niveles:

  ┌─────────────────────────────────────────────────────────────────────┐
  │ NIVEL LÓGICO (usuario)                                              │
  │   Valor de PK:   id=2  → "esta fila es la venta 2"                 │
  │   Foreign key:   cliente_id=42 → "esta fila referencia cliente 42" │
  │   Ambos son datos del usuario. No dicen nada de dónde están en disco│
  ├─────────────────────────────────────────────────────────────────────┤
  │ NIVEL FÍSICO (motor interno)                                        │
  │   ctid=(0,2)  → "los bytes de esta fila están en pág 0, slot 2"   │
  │   RID=(1,87,3) → "file 1, página 87, slot 3"                       │
  │   Son coordenadas internas. El usuario nunca las pone — las asigna  │
  │   el motor al hacer INSERT. Pueden cambiar sin que el usuario sepa. │
  └─────────────────────────────────────────────────────────────────────┘
```

Con este vocabulario claro, todo lo que sigue sobre InnoDB tiene sentido preciso.

---

#### De bytes crudos a estructura: el mecanismo de páginas

Esta es la pregunta correcta antes de ver cualquier diagrama: si un archivo en disco
es solo bytes, ¿qué convierte esos bytes en un "árbol" o en un "array"? La respuesta
es que la estructura **no está en los bytes** — está en la convención que usa el motor
para leerlos y escribirlos.

El mecanismo que usa todo motor de base de datos es la **página**: el archivo se divide
en bloques de tamaño fijo (8 KB en PostgreSQL, 16 KB en InnoDB) y cada bloque se
interpreta según un esquema fijo de campos. El esquema es lo que da significado a cada
byte.

```
Un archivo de disco es una secuencia plana y ciega de bytes:

  byte: 0    1    2    3    4    5    6    7    8    9    10   11  ...
        [3C] [00] [00] [01] [00] [00] [00] [02] [00] [00] [03] [E8] ...

  Esos bytes no tienen estructura por sí solos. Son solo números del 0 al 255.
  La estructura viene de cómo el motor decide interpretarlos.


─────────────────────────────────────────────────────────────────────────────
INTERPRETACIÓN A: array de enteros de 4 bytes
─────────────────────────────────────────────────────────────────────────────

  La convención: "cada elemento ocupa 4 bytes, el elemento N empieza en byte N×4"

  elemento[0] = bytes 0–3    elemento[1] = bytes 4–7    elemento[2] = bytes 8–11
  ┌────────────────────┐     ┌────────────────────┐     ┌────────────────────┐
  │ 3C 00 00 01        │     │ 00 00 00 02        │     │ 00 00 03 E8        │
  │ → valor 1.006.536   │     │ → valor 2           │     │ → valor 1000       │
  └────────────────────┘     └────────────────────┘     └────────────────────┘

  Para encontrar elemento[2]: byte_offset = 2 × 4 = 8. Sin punteros, sin árbol.
  La posición se calcula matemáticamente — eso define un array.


─────────────────────────────────────────────────────────────────────────────
INTERPRETACIÓN B: árbol B+Tree usando páginas con punteros entre páginas
─────────────────────────────────────────────────────────────────────────────

  La convención: "el archivo se divide en bloques de 12 bytes (página).
                  La página 0 es la raíz. Dentro de cada página, los primeros
                  2 bytes dicen el tipo (00=hoja, 01=interno) y el resto son campos."

  PÁGINA 0 (bytes 0–11):           PÁGINA 1 (bytes 12–23):
  ╔══════════════════════╗          ╔══════════════════════╗
  ║ tipo:    01 (interno)║          ║ tipo:    00 (hoja)   ║
  ║ clave:    3          ║          ║ id=1 │ precio=1000   ║
  ║ hijo_izq: 1 ─────────╫──────────╫──►                   ║
  ║ hijo_der: 2          ║          ╚══════════════════════╝
  ╚══════════════════════╝
         │ hijo_der: 2              PÁGINA 2 (bytes 24–35):
         └──────────────────────────╔══════════════════════╗
                                    ║ tipo:    00 (hoja)   ║
                                    ║ id=3 │ precio= 75    ║
                                    ║ id=4 │ precio=350    ║
                                    ╚══════════════════════╝

  "hijo_izq: 1" significa: ir a la página número 1 = byte 1×12 = byte 12 del archivo.
  "hijo_der: 2" significa: ir a la página número 2 = byte 2×12 = byte 24 del archivo.

  El "árbol" no es una estructura separada — es solo un número (número de página)
  guardado dentro de otra página. El motor calcula el byte offset así:
      byte_offset = número_de_página × tamaño_de_página
```

**La diferencia entre "nodo interno" y "hoja" es solo el campo `tipo`** al inicio de
la página. No son cosas distintas a nivel de archivo — son páginas del mismo archivo,
con un esquema de campos diferente:

```
  Página tipo INTERNO:   [tipo=01] [clave] [num_página_hijo_izq] [num_página_hijo_der]
                                              ↑ el "dato" es un número de página
                                                el motor lo usa para saber a qué
                                                página ir después en el árbol

  Página tipo HOJA:      [tipo=00] [clave] [dato_o_puntero]
                                              ↑ aquí termina la navegación.
                                                PostgreSQL pone un ctid (puntero al heap).
                                                InnoDB pone los datos completos de la fila.
```

Este mecanismo de páginas con números de página como punteros es universal: funciona
igual en PostgreSQL, InnoDB, SQL Server y SQLite. Lo que varía entre motores es qué
campos específicos van en cada tipo de página — que es exactamente la diferencia que
se explica a continuación.

---

#### ¿Por qué B+Tree y no un array ordenado?

Ambas estructuras permiten búsqueda binaria en O(log N). La diferencia crítica aparece
al insertar.

```
ARRAY ORDENADO — búsqueda O(log N)  ✓  |  inserción O(N)  ✗

  4.000.000 filas × 50 bytes, páginas de 8 KB → 25.000 páginas en disco:

  pág 0:     [id=1 ... id=160]
  pág 1:     [id=161 ... id=320]
  ...
  pág 25000: [id=3.999.841 ... id=4.000.000]

  INSERT id=500:  encaja entre id=499 e id=500, en la página 3.
  → Todo lo posterior debe desplazarse una posición.
  → Reescribir ~24.997 páginas = 200 MB de I/O para UN solo INSERT.
  → Con 1.000 inserts/segundo: impracticable.


B+TREE — búsqueda O(log N)  ✓  |  inserción O(log N)  ✓

  INSERT id=500:
    ① Navegar el árbol hasta la hoja correcta  (~3-4 I/Os de lectura)
    ② La hoja tiene espacio libre → escribir solo esa página  (~1 I/O)
       Si la hoja está llena → page split:
         - Crear página nueva, mover la mitad de los records
         - Actualizar el nodo padre con la nueva clave separadora
         - Si el padre también está lleno → split recursivo hacia arriba
         - Profundidad del árbol ≈ log₄₀₀(4.000.000) ≈ 3 niveles
         → máximo 3 splits, nunca afecta páginas fuera del camino al nodo.

  Ningún INSERT desplaza elementos de páginas que no están en el camino del árbol.


¿Para qué sirve entonces el array? — el heap file de PostgreSQL es exactamente eso:

  INSERT siempre va a la primera página con espacio libre, en cualquier orden.
  Sin mantener orden, sin desplazar nada → O(1) para cualquier INSERT.
  El precio: sin orden no hay búsqueda directa por valor → necesita el B+Tree encima.

  PostgreSQL: B+Tree (búsqueda eficiente) + heap (inserción eficiente) — dos estructuras.
  InnoDB:     mete los datos dentro del B+Tree → gana acceso directo por PK,
              pierde la inserción libre del heap (page splits con PKs no secuenciales).


¿Cuándo brilla cada estructura?

  Estructura       Búsqueda exacta   Rango          Inserción        Para qué
  ──────────────── ─────────────── ──────────────  ────────────────  ────────────────────
  Array heap        O(N) scan       O(N) scan       O(1) append       almacén de filas (PG)
  Array ordenado    O(log N)        O(log N)        O(N) desplaza     raramente útil en BD
  Hash table        O(1)            imposible*      O(1) avg          índices de igualdad
  B+Tree            O(log N)        O(log N)        O(log N)          índices generales
  Clustered B+Tree  O(log N)        secuencial ✓✓   O(log N)          InnoDB, SQL Server

  * hash table no puede hacer rangos porque las claves no están en ningún orden.
```

---

#### La diferencia fundamental: ¿dónde viven los bytes de una fila?

PostgreSQL usa **dos archivos** con páginas de esquemas distintos: el **heap file**
(páginas de tipo "fila", donde viven los datos de cada tupla) y el **archivo de índice**
(páginas de árbol B+, donde las hojas tienen `(clave → ctid)` como puntero al heap).
El heap existe con o sin índices — es el almacenamiento primario.

InnoDB toma una decisión diferente: **el heap no existe como archivo separado**. Hay
un solo archivo cuyo árbol B+ tiene páginas de tipo hoja que, en vez de guardar un
ctid hacia otro archivo, guardan directamente los campos completos de la fila. Las
hojas del árbol son el almacenamiento de datos — no hay segundo salto.

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
POSTGRESQL — DOS archivos en disco: el índice y el heap son estructuras distintas
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Tabla: id=1,2,3,4 insertados en este orden: 3, 1, 4, 2  (no orden de PK)

  ┌─────────────────────────────────────────────────────────────────────────┐
  │  ARCHIVO ÍNDICE  OID 24610                                              │
  │  B+Tree ordenado por id — las hojas contienen PUNTEROS, no datos        │
  │                                                                         │
  │                [RAÍZ — nodo interno]                                    │
  │                ┌──────────────────┐                                     │
  │                │ id < 3 → izq.    │                                     │
  │                │ id ≥ 3 → der.    │                                     │
  │                └────────┬─────────┘                                     │
  │                         │                                               │
  │              ┌──────────┴──────────┐                                    │
  │              ↓                     ↓                                    │
  │        [HOJA izq.]           [HOJA der.]                                │
  │        ╔═══════════════╗     ╔═══════════════╗                          │
  │        ║ id=1 │(0, 1) ║     ║ id=3 │(0, 0) ║                          │
  │        ║ id=2 │(0, 2) ║     ║ id=4 │(1, 0) ║                          │
  │        ╚═══════════════╝     ╚═══════════════╝                          │
  │              │                                                          │
  │     (clave PK | ctid)  ← la hoja guarda SOLO EL PUNTERO al heap        │
  │                           precio, cantidad, etc. NO están aquí          │
  └──────────────────┬──────────────────────────────────────────────────────┘
                     │  ctid = (página, slot)
                     │  el motor usa este puntero para saltar al heap
                     ↓
  ┌─────────────────────────────────────────────────────────────────────────┐
  │  ARCHIVO HEAP  OID 24601                                                │
  │  Las filas viven aquí, en ORDEN DE INSERCIÓN (no de PK)                 │
  │                                                                         │
  │  PÁGINA 0 (filas en orden de inserción, NO por PK):                    │
  │  ╔══════════════════════════════════╗                                   │
  │  ║ slot 0: id=3, precio= 75, cant=2 ║                                   │
  │  ║ slot 1: id=1, precio=1000,cant=5 ║                                   │
  │  ║ slot 2: id=2, precio= 25, cant=1 ║                                   │
  │  ╚══════════════════════════════════╝                                   │
  │  PÁGINA 1:                                                              │
  │  ╔══════════════════════════════════╗                                   │
  │  ║ slot 0: id=4, precio=350, cant=8 ║                                   │
  │  ╚══════════════════════════════════╝                                   │
  └─────────────────────────────────────────────────────────────────────────┘

  SELECT * FROM ventas WHERE id = 2:

    Paso ①  árbol índice → raíz: id=2<3 → hoja izq. → ctid = (0, 2)
    Paso ②  heap, página 0, slot 2 → id=2, precio=25, cant=1  ✓

    Dos archivos accedidos. El índice no tenía los datos, solo el puntero.


━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
MYSQL INNODB — UN solo archivo: el motor guarda los bytes de las filas en las
               páginas hoja del B+Tree (esto se llama "clustered index")
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  ¿Qué significa "clustered"?
  Un índice es clustered cuando los bytes de las filas están guardados dentro
  de sus propias páginas hoja, ordenados por PK. No existe heap file separado.
  "Clustered" = datos e índice de PK son la misma estructura física.
  El motor (InnoDB) siempre interpreta los bytes según el esquema de la tabla;
  ventas.ibd son bytes como cualquier otro archivo en disco.

  ┌─────────────────────────────────────────────────────────────────────────┐
  │  ARCHIVO ventas.ibd  (el motor InnoDB lo interpreta como B+Tree)        │
  │                                                                         │
  │                [RAÍZ — nodo interno]                                    │
  │                ┌──────────────────┐                                     │
  │                │ id < 3 → izq.    │                                     │
  │                │ id ≥ 3 → der.    │                                     │
  │                └────────┬─────────┘                                     │
  │                         │                                               │
  │              ┌──────────┴──────────┐                                    │
  │              ↓                     ↓                                    │
  │        [HOJA izq.]                      [HOJA der.]                     │
  │        ╔═════════════════════════════╗  ╔═════════════════════════════╗ │
  │        ║ id=1 │ precio=1000 │ cant=5 ║  ║ id=3 │ precio=  75 │ cant=2 ║ │
  │        ║ id=2 │ precio=  25 │ cant=1 ║  ║ id=4 │ precio= 350 │ cant=8 ║ │
  │        ╚═════════════════════════════╝  ╚═════════════════════════════╝ │
  │                                                                         │
  │   Los bytes reales en disco para id=2, precio=25, cant=1:               │
  │     precio → 4 bytes: 80 00 00 19   (encoding InnoDB big-endian+XOR)    │
  │     cant   → 4 bytes: 80 00 00 01                                       │
  │   El motor lee esos bytes en la hoja y los decodifica. Sin otro archivo. │
  └─────────────────────────────────────────────────────────────────────────┘

  ─────────────────────────────────────────────────────────────────────────
  "página" vs "hoja" — no son lo mismo
  ─────────────────────────────────────────────────────────────────────────

    Página = bloque físico de 16 KB en disco. El motor siempre lee y
             escribe páginas completas. Es el concepto de almacenamiento.

    Hoja   = nodo del B+Tree SIN hijos. Ahí viven las filas reales.
             Es el concepto de posición en el árbol.

    En InnoDB cada nodo del árbol —interno o hoja— ocupa exactamente
    una página. Así que "hoja" y "página hoja" dicen lo mismo; la
    diferencia es énfasis: uno habla del árbol, el otro del disco.

  ─────────────────────────────────────────────────────────────────────────
  Ejemplo completo: cómo crece el árbol desde cero
  ─────────────────────────────────────────────────────────────────────────

    Usamos máximo 4 filas por página para que el ejemplo sea corto.
    En InnoDB real caben ~100–500 filas por hoja según el tamaño de la fila.

    ── ESTADO 1: primeros cuatro inserts ──────────────────────────────────

    El árbol arranca con una sola página que hace de raíz Y de hoja a la vez.

        [página #1 — raíz y única hoja]
        ╔═══════════╗
        ║  id = 10  ║
        ║  id = 20  ║
        ║  id = 30  ║
        ║  id = 40  ║  ← llena (4 filas)
        ╚═══════════╝

    ── ESTADO 2: INSERT id=50 — primera división (split) ──────────────────

    La página #1 está llena. El motor no puede meter id=50 ahí.
    Lo que hace:

      1. Toma las 5 filas [10,20,30,40,50] y las divide en dos mitades.
      2. Crea página #2 con la mitad izquierda: [10, 20].
      3. Crea página #3 con la mitad derecha:   [30, 40, 50].
      4. Convierte página #1 (la raíz vieja) en nodo interno.
         En lugar de filas, ahora guarda dos cosas:
           - el número de página de cada hijo (#2 y #3)
           - un valor de corte que le dice cuándo ir a cada uno

    El valor de corte que se escribe en la raíz es el PRIMER id de la
    mitad derecha: 30. Eso significa:
      "si el id que buscas es < 30, lee la página #2;
       si es ≥ 30, lee la página #3."

    El 30 no es una fila. Es solo un número copiado de la primera fila
    de la página #3 en el momento del split. Vive en la raíz para poder
    decidir a qué hijo bajar.

        [página #1 — raíz INTERNA]       ← ya no guarda filas
        ┌────────────────────────────┐
        │  pág.#2  │  30  │  pág.#3  │
        └────────────────────────────┘
             ↓                  ↓
        id < 30            id ≥ 30

        [pág.#2 — hoja]        [pág.#3 — hoja]
        ╔═══════════╗           ╔═══════════╗
        ║  id = 10  ║           ║  id = 30  ║
        ║  id = 20  ║           ║  id = 40  ║
        ╚═══════════╝           ║  id = 50  ║
                                ╚═══════════╝

    Buscar id=35:
      raíz: 35 ≥ 30  →  leer pág.#3
      pág.#3: recorrer filas → id=35 no existe (o encontrarlo si existe)
    Dos lecturas de página. Nunca más.

    ── ESTADO 3: INSERT 60, 70 — segunda división ─────────────────────────

    id=60 entra en pág.#3 (ahora tiene 30,40,50,60 — llena).
    id=70 intenta entrar en pág.#3 y la encuentra llena → split de pág.#3.

      1. Las 5 filas [30,40,50,60,70] se dividen en dos mitades.
      2. pág.#3 se queda con la izquierda: [30, 40].
      3. Se crea pág.#4 con la derecha:    [50, 60, 70].
      4. El primer id de pág.#4 es 50.
         Ese 50 se escribe en la raíz (pág.#1) junto al puntero a pág.#4.

    La raíz NO se recrea. El motor abre pág.#1 y le agrega el nuevo
    valor (50) y el nuevo puntero (pág.#4). La misma página, modificada.

        [página #1 — raíz INTERNA, modificada en lugar]
        ┌──────────────────────────────────────────┐
        │  pág.#2  │  30  │  pág.#3  │  50  │  pág.#4  │
        └──────────────────────────────────────────┘
             ↓                  ↓                  ↓
           id<30          30 ≤ id < 50           id≥50

        [pág.#2]       [pág.#3]       [pág.#4]
        ╔═══════╗      ╔═══════╗      ╔═══════╗
        ║  10   ║      ║  30   ║      ║  50   ║
        ║  20   ║      ║  40   ║      ║  60   ║
        ╚═══════╝      ╚═══════╝      ║  70   ║
                                      ╚═══════╝

    Nota: el id=50 aparece en la raíz (como valor de corte) Y en pág.#4
    (como fila real). No es un duplicado — son dos roles distintos.
    La raíz usa el 50 solo para decidir a qué hijo ir. La fila real con
    sus datos (precio, cant...) vive únicamente en pág.#4.

    ── ESTADO 4: sigues insertando ids grandes ────────────────────────────

    Cada vez que una hoja se llena, se divide y su primer id nuevo se
    escribe en la raíz. La raíz acumula valores: [30 | 50 | 70 | 90 | ...].
    Mientras la raíz tenga espacio, solo se modifica en lugar.

    Digamos que la raíz también cabe máximo 4 punteros (3 valores de corte).
    Cuando está llena y una hoja vuelve a hacer split, la raíz también
    necesita dividirse:

      1. La raíz se parte en dos nodos internos: izq. y der.
      2. Se crea una RAÍZ NUEVA con un solo valor de corte y dos punteros
         (uno a cada nuevo interno). El árbol sube un nivel de altura.

        [RAÍZ NUEVA — pág.#K]
        ┌─────────────────────────────┐
        │  pág.#interno-izq  │  50  │  pág.#interno-der  │
        └─────────────────────────────┘
                    ↓                              ↓
        [interno izq. — ids < 50]     [interno der. — ids ≥ 50]
        ┌──────────────────────┐      ┌──────────────────────────────┐
        │ pág.#2 │ 30 │ pág.#3 │      │ pág.#4 │ 70 │ pág.#5 │ 90 │ ...│
        └──────────────────────┘      └──────────────────────────────┘
             ↓          ↓                  ↓          ↓          ↓
           hojas      hojas              hojas      hojas      hojas
         (10,20)   (30,40)           (50,60,70)  (80,90)    (100...)

    El lado derecho tiene muchas más hojas porque casi todos los ids son
    grandes. Pero TODAS las hojas están a la misma profundidad: 3 saltos
    desde la raíz. Eso es lo que garantiza el B+Tree: sin importar cuántos
    ids haya en un rango, cualquier búsqueda cuesta exactamente
    (altura del árbol) lecturas de página.

    En una tabla real de 10 millones de filas InnoDB tiene altura 3–4,
    o sea 3–4 lecturas de página para cualquier búsqueda por PK.

  SELECT * FROM ventas WHERE id = 2:

    Paso ①  raíz: id=2 < 3 → hoja izq.
            hoja izq.: buscar id=2 → leer bytes → precio=25, cant=1  ✓

    Un solo archivo. No hay segundo salto.
```

La diferencia entre ambos modelos se reduce a una sola pregunta: **¿qué hay en la
hoja del árbol B+?**

```
  PostgreSQL (hoja de índice):   ║ id=2 │ ctid=(0,2)              ║  ← puntero; obliga a un 2.º salto
  InnoDB (hoja clustered):       ║ id=2 │ precio=25 │ cant=1      ║  ← bytes completos; no hay 2.º salto
```

InnoDB no "ahorra" una estructura — fusiona el heap dentro del árbol. Eso tiene un
costo distinto: cuando buscas por un **índice secundario** (no por PK), InnoDB
necesita recorrer **dos árboles** completos en lugar de uno:

```
  Búsqueda  SELECT * WHERE precio = 25  (índice secundario sobre precio):

  POSTGRESQL:
    ① árbol de precio → ctid=(0,2)
    ② heap, pág 0, slot 2 → dato ✓
    Total: 1 árbol + 1 salto al heap

  MYSQL INNODB:
    ① árbol de precio → val_PK = 2         ← el secundario no tiene los datos
    ② árbol clustered con val_PK=2 → dato ✓ ← hay que recorrer un segundo árbol completo
    Total: 2 árboles completos
```

**¿Por qué PostgreSQL no usa clustered index si InnoDB tiene ventajas en PK lookups?**

Tres razones de diseño deliberado:

**1. MVCC en el heap.** PostgreSQL implementa MVCC guardando versiones viejas de una fila
como tuplas adicionales en el mismo heap. Al hacer `UPDATE`, la nueva versión va a
cualquier página con espacio libre — no importa el valor de PK. Con un clustered B+Tree,
mantener múltiples versiones de la misma clave ordenadas por PK y después limpiarlas
con VACUUM requeriría reorganizar el árbol constantemente. El heap hace esto trivial.

**2. Inserts sin penalización por PK aleatoria.** El heap no exige orden. Si tu PK es
UUID v4 (completamente aleatorio), PostgreSQL simplemente inserta en la primera página
con hueco libre. InnoDB con UUID v4 sufre un page split en casi cada INSERT.

**3. HOT (Heap-Only Tuple).** Si un UPDATE cambia una columna no indexada y la nueva
versión cabe en la misma página, PostgreSQL no actualiza ningún índice en absoluto. Con
clustered index, la fila está dentro del árbol y este tipo de optimización es más difícil.

```
Comparación por operación — misma tabla, mismo query:

  Operación                   PostgreSQL (heap + índice) MySQL InnoDB (clustered) Ventaja
  ─────────────────────────── ────────────────────────── ──────────────────────── ───────
  WHERE id = 42               2 I/Os (árbol + heap)      1 I/O (hoja del árbol)   InnoDB
  WHERE id BETWEEN 100..200   saltos al heap dispersos   scan secuencial de hojas InnoDB
  FK referencia a PK ajena    2 I/Os (árbol + heap)      1 I/O (hoja del árbol)   InnoDB
  Índice secundario sin cover 1 árbol + 1 heap           2 árboles completos      PG
  FK referencia a no-PK       1 árbol + 1 heap           2 árboles completos      PG
  INSERT con PK UUID v4       O(1): cualquier página     page split probable       PG
  INSERT con PK serial        O(1): cualquier página     O(1): append hoja final  empate
  UPDATE (fila se mueve)      actualiza todos N índices  índices secundarios ≠ toque InnoDB
  MVCC (versiones antiguas)   tuplas en heap, VACUUM     undo log separado         PG
  Fragmentación por deletes   bloat en heap (VACUUM)     bloat en árbol (optimize) empate
```

PostgreSQL CLUSTER existe pero no se mantiene automático: el heap se desordena con
cada nuevo INSERT y hay que re-ejecutar CLUSTER manualmente. InnoDB mantiene el orden
automáticamente porque el árbol es la única estructura de almacenamiento.

---

#### Anatomía de un record InnoDB a nivel de bytes

Así como la sección de PostgreSQL mostró el HeapTupleHeader byte por byte, aquí se
muestra exactamente cómo son los bytes de un record InnoDB en formato COMPACT (el
formato moderno, activo desde MySQL 5.0 y aún el default).

Tabla de ejemplo: `ventas (id INT NOT NULL, precio INT, cantidad INT)` con `id` como PK.
Fila insertada: `id=2, precio=25, cantidad=1`.

```
Record COMPACT en la página hoja del clustered index:

Posición    Campo                    Bytes  Valor en disco        Explicación
──────────  ───────────────────────  ─────  ────────────────────  ────────────────────────────────────
[prefijo]   VLF section              0 B    (vacío)               No hay columnas VARCHAR/TEXT en este
                                                                  ejemplo → no hay longitudes variables
                                                                  que declarar. Si hubiera una columna
                                                                  VARCHAR(200), aquí iría 1 o 2 bytes
                                                                  indicando la longitud real del valor.

[prefijo]   NULL bitmap              1 B    00000010b             1 bit por columna nullable, de derecha
                                                                  a izquierda. precio y cantidad son
                                                                  nullable (no declaradas NOT NULL):
                                                                  bit 0 = precio: 0 = no es NULL
                                                                  bit 1 = cantidad: 1 = IS NULL
                                                                  (ejemplo: cantidad es NULL)
                                                                  id NO aparece aquí (es NOT NULL por PK)

bytes 0–4   Record header            5 B    [ver desglose abajo]  5 bytes empaquetados en bits

bytes 5–10  DB_TRX_ID               6 B    00 00 00 00 00 42     ID de la transacción que insertó/
                                                                  modificó esta fila por última vez.
                                                                  MVCC: para saber si esta versión
                                                                  de la fila es visible a cada txn.
                                                                  (equivale funcionalmente al t_xmin
                                                                  de PostgreSQL, pero sin t_xmax)

bytes 11–17 DB_ROLL_PTR             7 B    80 00 00 00 01 A2 3C  Puntero al undo log. Apunta al
                                                                  registro de undo donde está la
                                                                  versión anterior de esta fila.
                                                                  MVCC: para reconstruir versiones
                                                                  antiguas. No existe en PostgreSQL
                                                                  porque PG guarda versiones viejas
                                                                  como tuples separadas en el heap.

bytes 18–21 id (PK, INT NOT NULL)   4 B    80 00 00 02           El valor lógico id=2.
                                                                  ¡OJO: big-endian + flip de bit alto!
                                                                  (ver explicación abajo)

bytes 22–25 precio (INT nullable)   4 B    80 00 00 19           El valor lógico precio=25.
                                                                  Mismo encoding: 80 00 00 19

[ausente]   cantidad (INT nullable) 0 B    (no presente)         cantidad es NULL en este ejemplo.
                                                                  El NULL bitmap ya lo indica.
                                                                  El campo no ocupa bytes en el record.

TOTAL para este record: 1 + 5 + 6 + 7 + 4 + 4 = 27 bytes
```

**Una tupla es un bloque contiguo de bytes**

Antes de ver cómo el motor lee cada columna, hay que tener claro cómo
están organizadas las filas dentro de una página.

Cada fila es un bloque contiguo de bytes — todos los datos de esa fila
van pegados uno tras otro. Y dentro de ese bloque, las columnas siempre
aparecen en el mismo orden en que las declaraste en `CREATE TABLE`.

El problema es que una página contiene decenas o cientos de filas, y el
motor necesita saber dónde empieza cada una. PostgreSQL e InnoDB lo
resuelven de forma distinta:

```
Una página con 3 filas:

  POSTGRESQL — ItemId array al inicio de la página
  ┌──────────────────────────────────────────────────────────────┐
  │ [slot 1→byte 200] [slot 2→byte 160] [slot 3→byte 120]  ...  │ ← directorio
  │                      (espacio libre)                        │
  │ [header][id=3][precio=75][cant=2]        ← fila en byte 160 │
  │ [header][id=2][precio=25][cant=1]        ← fila en byte 200 │
  │ [header][id=1][precio=99][cant=5]        ← fila en byte 120 │
  └──────────────────────────────────────────────────────────────┘
  Cada slot apunta al byte exacto donde empieza esa fila.
  Acceso directo: lees el slot, saltas a ese byte, lees la fila.

  INNODB — lista enlazada entre filas
  ┌──────────────────────────────────────────────────────────────┐
  │ [Infimum]──→──[fila 1]──→──[fila 2]──→──[fila 3]──→──[Supremum]
  │                                                             │
  │ fila 1: [prefijo][header → apunta a fila 2][id=1][precio=99][cant=5]
  │ fila 2: [prefijo][header → apunta a fila 3][id=2][precio=25][cant=1]
  │ fila 3: [prefijo][header → apunta a Supremum][id=3][precio=75][cant=2]
  └──────────────────────────────────────────────────────────────┘
  Cada fila tiene en su header un puntero a la siguiente.
  Para llegar a una fila específica: Page Directory (búsqueda binaria)
  + recorrido corto de la lista hasta encontrarla.
```

Una vez que el motor tiene el puntero al inicio de una fila, lee sus
bytes de izquierda a derecha: primero el header con sus metadatos, luego
los bytes de las columnas en orden de `CREATE TABLE`. Siempre igual,
para todas las filas de esa tabla.

---

**Cómo el motor sabe qué bytes pertenecen a qué columna**

El motor no tiene guardado en disco "precio empieza en el byte 22". Lo
deduce a partir del schema. El razonamiento tiene tres niveles según
lo que haya en la tabla.

**Nivel 1 — todas las columnas son de tamaño fijo, ninguna es NULL**

El schema dice `id INT, precio INT, cantidad INT`. El motor sabe que
cada INT ocupa exactamente 4 bytes. Los datos son bytes pegados en
orden de columna:

```
bytes en disco:  [00 00 00 02] [00 00 00 19] [00 00 00 01]
                 └── id=2 ──┘  └─precio=25─┘  └─cant=1──┘
```

No hay magia. El motor acumula tamaños: id termina en el byte 4,
precio empieza en el 4 y termina en el 8, cantidad empieza en el 8.

**Nivel 2 — alguna columna es NULL**

Si `cantidad` es NULL no ocupa bytes. Los datos quedan así:

```
bytes en disco:  [00 00 00 02] [00 00 00 19]
```

Ahora el motor no puede saber si el segundo bloque de 4 bytes es
`precio` o `cantidad`. La solución es el **NULL bitmap**: una lista de
bits guardada junto al record donde cada bit indica si esa columna es
NULL o no.

```
NULL bitmap: 001  →  columna 3 (cantidad) es NULL
```

Con ese bitmap el motor sabe: primer bloque = id, segundo = precio,
cantidad no tiene bytes en este record.

**Nivel 3 — hay columnas de tamaño variable (VARCHAR, TEXT)**

`nombre VARCHAR(100)` puede ser 3 bytes ('Ana') o 50 bytes ('Carlos
Alberto García'). El motor no sabe cuántos bytes ocupa hasta saberlo
de algún lado — y si no lo sabe, no puede calcular dónde empieza la
columna siguiente.

Aquí PostgreSQL e InnoDB toman caminos distintos:

```
POSTGRESQL — la longitud va pegada justo antes de cada valor variable

  bytes:  [4B id] [03] [A n a] [4B precio]
                   ↑
             "nombre mide 3 bytes"

  Para llegar a precio: el motor lee el 03, salta 3 bytes, y ahí está.
  El tamaño viaja con el valor.


INNODB — todas las longitudes van agrupadas AL INICIO del record

  bytes:  [03] [NULL bitmap] [header] [4B id] [A n a] [4B precio]
           ↑
     "la columna variable mide 3 bytes"

  El motor lee el 03 al principio. Ya sabe que cuando llegue a
  nombre ocupa 3 bytes, sin necesidad de inspeccionar el valor.
  Los datos vienen después sin ningún prefijo inline.
```

En InnoDB esa zona inicial se llama **VLF section** (variable-length
field lengths). Solo aparece si hay columnas variables; si la tabla
tiene solo INTs, BIGINTs o fechas esa zona está vacía.

```
Ejemplo completo — tabla con VARCHAR y NULL

  Schema: id INT NOT NULL, nombre VARCHAR(100) NOT NULL, precio INT
  Fila:   id=5, nombre='Ana', precio=NULL

  Prefijo del record InnoDB:
    VLF section:  03          → nombre ocupa 3 bytes
    NULL bitmap:  10          → precio (columna 3) es NULL

  Datos que siguen:
    [columnas ocultas: 5B header + 6B DB_TRX_ID + 7B DB_ROLL_PTR]
    [80 00 00 05]   id=5      (4 bytes, INT fijo)
    [41 6E 61]      nombre    (3 bytes, longitud leída del VLF)
    [ausente]       precio    (NULL bitmap lo marcó NULL → 0 bytes)
```

---

#### Cómo el motor navega de SQL a bytes: PostgreSQL vs InnoDB

PostgreSQL e InnoDB resolvieron el mismo problema con dos filosofías distintas.
Esa diferencia se ve desde la estructura de archivos hasta el layout de cada fila.

```
  ┌─────────────────────────────────────────────────────────────────────┐
  │  POSTGRESQL — heap separado del índice                              │
  │                                                                     │
  │  base/16384/                                                        │
  │  ├── 24601        ← heap file: las filas, sin orden de PK           │
  │  └── 24610        ← index file: B+Tree sobre id                     │
  │                                                                     │
  │  El índice guarda una DIRECCIÓN FÍSICA (ctid) que apunta al heap.   │
  │  Índice leaf:  id=2  →  ctid = (página 0, slot 2)                   │
  │  Heap pág. 0:  slot 2 del ItemId array → offset exacto de la fila  │
  └─────────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────────────┐
  │  INNODB — el B+Tree clustered ES la tabla                           │
  │                                                                     │
  │  ventas.ibd    ← un solo archivo; el árbol contiene las filas       │
  │                                                                     │
  │  No hay heap separado. No hay dirección física en los índices.      │
  │  Árbol leaf:   id=2  →  [id=2 | precio=25 | cant=1]  ← fila aquí   │
  └─────────────────────────────────────────────────────────────────────┘
```

Esa decisión —dirección física vs valor lógico de PK— determina el
comportamiento de todo lo demás: cómo se actualiza una fila, qué pasa
con los índices secundarios, cómo funciona el MVCC.

**PostgreSQL — camino completo de `SELECT * FROM ventas WHERE id = 2`**

```
  Paso 1 — catálogo
  ┌──────────────────────────────────────────────────────┐
  │  pg_class:     ventas → OID 24601 (heap), 24610 (idx)│
  │  pg_attribute: id INT4, precio INT4, cantidad INT4   │
  └──────────────────────────────────────────────────────┘
  El motor sabe el layout de la fila antes de leer un solo byte de datos.

  Paso 2 — navegar el B+Tree del índice (archivo 24610)
  ┌─────────────────────────────────────────────┐
  │  nodo raíz → nodos internos → nodo hoja     │
  │  hoja:  id=2  →  ctid = (0, 2)              │
  └─────────────────────────────────────────────┘
  ctid es una dirección física: página 0 del heap, slot número 2.
  Este es el único dato que el índice necesita guardar.

  Paso 3 — saltar al heap con el ctid
  El motor carga la página 0 del archivo 24601 en el buffer pool (8 KB).

  Paso 4 — ItemId array: slot 2 → offset exacto de la fila
  ┌────────────────────────────────────────────────────────────────┐
  │  [page header] [slot 0] [slot 1] [slot 2] ... [filas crecen ↑]│
  │                                   ↓                           │
  │                           offset de la fila en esta página    │
  └────────────────────────────────────────────────────────────────┘
  Un solo slot da el offset exacto. Acceso O(1), sin búsqueda.

  Paso 5 — leer la fila: HeapTupleHeader + datos
  ┌─────────────────────────────────────────────────────────────────┐
  │  HeapTupleHeader (23 bytes):                                    │
  │    xmin      → ID de la transacción que insertó esta fila       │
  │    xmax      → ID de la txn que la borró (0 = sigue viva)       │
  │    ctid      → apunta a la versión más nueva (HOT chain)        │
  │    t_infomask → flags: XMIN_COMMITTED, HAS_NULL, etc.           │
  │  ─────────────────────────────────────────────────────────────  │
  │  Datos (little-endian, layout fijo):                            │
  │    id=2:       02 00 00 00                                      │
  │    precio=25:  19 00 00 00                                      │
  │    cant=1:     01 00 00 00                                      │
  └─────────────────────────────────────────────────────────────────┘
  xmin y xmax en el header son los mecanismos de visibilidad MVCC.
  Cada fila sabe qué transacción la creó y cuál la eliminó.
```

**InnoDB — camino completo de `SELECT * FROM ventas WHERE id = 2`**

```
  Paso 1 — diccionario de datos interno de InnoDB
  InnoDB no usa pg_attribute. Tiene sus propias tablas internas que
  describen el layout de cada tabla. El motor las consulta primero.

  Paso 2 — navegar el clustered index (archivo ventas.ibd)
  ┌──────────────────────────────────────────────────────┐
  │  nodo raíz → nodos internos → nodo hoja              │
  │  hoja: contiene la fila completa con id=2            │
  └──────────────────────────────────────────────────────┘
  No hay segundo archivo. No hay ctid. El árbol lleva directo a la fila.

  Las claves se comparan como bytes sin signo. Para que los enteros
  signed se ordenen correctamente en comparación binaria, InnoDB los
  codifica con XOR 0x80000000 (invierte el bit de signo):

    id= 0  →  00 00 00 00  XOR  80 00 00 00  =  80 00 00 00
    id= 2  →  00 00 00 02  XOR  80 00 00 00  =  80 00 00 02
    id=-1  →  FF FF FF FF  XOR  80 00 00 00  =  7F FF FF FF

  Orden como bytes: 7F... < 80 00 00 00 < 80 00 00 02  →  -1 < 0 < 2  ✓
  Sin el XOR, id=-1 (FF...) aparecería después de id=2 (00...).

  Paso 3 — Page Directory: ubicar el record dentro de la página hoja
  (explicado en la sección anterior: búsqueda binaria sobre grupos
  de ~4-8 records, luego recorrido lineal de la lista enlazada)

  Paso 4 — leer el record: layout interno de InnoDB
  ┌─────────────────────────────────────────────────────────────────┐
  │  NULL bitmap   → un bit por columna nullable; si el bit está    │
  │                  prendido, esa columna no ocupa bytes en el     │
  │                  record. Ejemplo: cant=NULL → su bit = 1,       │
  │                  no hay bytes de cant en el record.             │
  │  Record header → 5 bytes con flags internos y next_record       │
  │                  (el offset al siguiente record en la lista      │
  │                  enlazada de la página)                         │
  │  DB_TRX_ID     → 6 bytes: ID de la txn que escribió este record │
  │  DB_ROLL_PTR   → 7 bytes: puntero al undo log con la versión    │
  │                  anterior de esta fila                          │
  │  ─────────────────────────────────────────────────────────────  │
  │  Columnas (big-endian con XOR):                                 │
  │    id=2:       80 00 00 02  →  XOR  →  2                        │
  │    precio=25:  80 00 00 19  →  XOR  →  25                       │
  │    [cant=NULL: no hay bytes — el NULL bitmap lo indica]         │
  └─────────────────────────────────────────────────────────────────┘
  DB_TRX_ID y DB_ROLL_PTR son columnas ocultas que InnoDB agrega
  automáticamente a cada fila. Son el equivalente de xmin/xmax, pero
  con una diferencia importante: en lugar de guardar xmax (el ID de
  quien borró), InnoDB guarda un puntero al undo log. Las versiones
  anteriores de la fila viven ahí, no en el heap como en PostgreSQL.
```

**Consecuencia del diseño: qué pasa en un UPDATE**

```
  UPDATE ventas SET precio = 999 WHERE id = 2;

  POSTGRESQL:
    La fila actualizada se escribe en cualquier página con espacio libre.
    Esa nueva fila tiene un ctid distinto, ej. (3, 7).
    El índice sobre id todavía apunta a ctid=(0,2) → desactualizado.
    → el motor debe actualizar TODOS los índices secundarios afectados
      (excepto HOT update, que funciona solo si la nueva versión cabe
      en la misma página y no hay índices que cubran la columna cambiada)

  INNODB:
    La versión anterior de la fila va al undo log (DB_ROLL_PTR apunta ahí).
    La fila en el árbol se actualiza en su posición lógica (mismo PK).
    Los índices secundarios guardan el valor de PK, no una dirección física.
    El PK (id=2) no cambió → los índices secundarios NO necesitan tocarse.

  Esa es la razón de fondo del diseño de InnoDB: usar PK lógica en lugar
  de dirección física hace que los updates sean mucho menos costosos para
  los índices secundarios.
```

**Resumen comparativo**

|                      | PostgreSQL                             | InnoDB                                          |
| -------------------- | -------------------------------------- | ----------------------------------------------- |
| Tabla =              | heap file separado                     | clustered B+Tree                                |
| Índice leaf →        | ctid (dirección física)                | fila completa (PK clustered) / PK (secundarios) |
| Dentro de página     | ItemId: offset exacto, O(1)            | Page Directory: grupos + linked list            |
| MVCC por fila        | xmin, xmax en el header                | DB_TRX_ID + DB_ROLL_PTR ocultos                 |
| Versiones anteriores | en el heap, mismo archivo              | en el undo log separado                         |
| UPDATE → índices     | todos los afectados deben actualizarse | secundarios intactos si PK no cambia            |
| Overhead por fila    | 23B header + 4B ItemId                 | 13B ocultos (TRX+ROLLPTR) + NULL bitmap         |

---

#### El Page Directory de InnoDB: cómo encuentra un record dentro de la página

Una vez que el B+Tree llevó al motor a la página correcta, aún necesita
encontrar la fila dentro de esa página. PostgreSQL e InnoDB lo resuelven
de forma distinta, y el tradeoff es velocidad vs espacio.

```
  PostgreSQL — ItemId array
  ─────────────────────────
  Cada fila tiene un slot con su offset exacto dentro de la página.
  Buscar la fila K = leer slot[K] = saltar directo a esa dirección.
  Costo: O(1). Sin recorrido.

  ┌──────────────────────────────────────────┐
  │  slot[0]=offset_fila1                    │  4 bytes por fila
  │  slot[1]=offset_fila2                    │
  │  slot[2]=offset_fila3                    │
  │  ...                                     │
  └──────────────────────────────────────────┘
       ↓ cada slot apunta directo a su fila

  InnoDB — Page Directory
  ────────────────────────
  Las filas están enlazadas en orden de clave (lista enlazada).
  El Page Directory no tiene un slot por fila — tiene uno por cada
  grupo de 4–8 filas, apuntando a la ÚLTIMA fila de ese grupo.

  Para buscar una fila:
    ① Búsqueda binaria sobre los slots → ubica el grupo correcto
    ② Recorrido lineal dentro del grupo (máximo 8 filas) → encuentra la fila

  ┌──────────────────────────────────────────────┐
  │  slot → última fila del grupo D (filas 17-20)│  2 bytes por grupo
  │  slot → última fila del grupo C (filas 13-16)│
  │  slot → última fila del grupo B (filas  9-12)│
  │  slot → última fila del grupo A (filas  5- 8)│
  │  ...                                         │
  └──────────────────────────────────────────────┘
       ↓ cada slot apunta al último de un grupo de ~4-8 filas

  Costo: O(log grupos) + O(tamaño grupo) ≈ unas 14 comparaciones
         para una página con 400 filas. En la práctica: sub-microsegundo.

  Tradeoff de espacio en una página con 300 filas:
    PostgreSQL ItemId:      300 × 4 bytes = 1 200 bytes de overhead
    InnoDB Page Directory:   ~50 × 2 bytes =   100 bytes de overhead
```

El rendimiento de búsqueda es casi idéntico (ambos en RAM son
sub-microsegundo). La diferencia real es el overhead de espacio:
InnoDB usa ~8× menos bytes para el directorio.

---

#### Por qué InnoDB usa el valor de PK en los índices secundarios y no una dirección física

Ahora que los conceptos están claros, la razón del diseño de InnoDB es precisa.

El problema de partida es el ctid. En PostgreSQL, cuando un UPDATE no puede hacer HOT
(la nueva versión de la fila no cabe en la misma página), el motor escribe la nueva
tuple en cualquier otra página que tenga espacio libre. Esa nueva tuple tiene un ctid
diferente. Todos los índices secundarios que apuntan al ctid viejo quedan desactualizados:

```
PostgreSQL — UPDATE que rompe el ctid:

  Estado inicial:
    heap pág 0: [slot 1 → id=1, precio=1000, cantidad=5]
    índice sobre precio: (precio=1000) → ctid=(0,1)  ← apunta a dirección física
    índice sobre cantidad: (cantidad=5) → ctid=(0,1) ← apunta a dirección física

  UPDATE ventas SET descripcion = 'texto largo...' WHERE id = 1;
  → la nueva tuple con descripcion no cabe en pág 0 (no hay HOT posible)
  → PostgreSQL escribe nueva tuple en pág 5, slot 2
  → nueva dirección física: ctid=(5,2)

  PROBLEMA: los índices secundarios todavía tienen ctid=(0,1) — incorrecto.
  PostgreSQL DEBE actualizar todos los índices secundarios:
    índice sobre precio:    borrar (precio=1000, ctid_viejo=(0,1))
                            insertar (precio=1000, ctid_nuevo=(5,2))
    índice sobre cantidad:  borrar (cantidad=5, ctid_viejo=(0,1))
                            insertar (cantidad=5, ctid_nuevo=(5,2))
  Con 5 índices secundarios: 10 operaciones de árbol adicionales por UPDATE.
  Cuanto más índices, más costoso cada UPDATE que mueve la tuple.

InnoDB — por qué no tiene este problema:

  En InnoDB, los índices secundarios no guardan direcciones físicas.
  Guardan el VALOR LÓGICO de la clave primaria.

  Estado inicial:
    clustered index hoja: [val PK=1, precio=1000, cant=5]  ← los bytes están aquí
    índice sobre precio:  (precio=1000) → val_PK=1  ← valor lógico, no dirección física
    índice sobre cantidad: (cant=5) → val_PK=1       ← valor lógico, no dirección física

  UPDATE ventas SET descripcion = 'texto largo...' WHERE id = 1;
  → InnoDB actualiza el clustered index (posiblemente con page split si no cabe)
  → el valor lógico de PK sigue siendo 1 — no cambió
  → los índices secundarios apuntan a val_PK=1 — que SIGUE SIENDO VÁLIDO

  → índice sobre precio:    (precio=1000, val_PK=1) → SIN CAMBIOS ✓
  → índice sobre cantidad:  (cantidad=5,  val_PK=1) → SIN CAMBIOS ✓

  La fila se movió físicamente dentro del clustered index, pero los índices
  secundarios no saben ni necesitan saber dónde está físicamente — solo saben
  el valor de PK, y el clustered index siempre puede encontrar la fila por ese valor.

  ¿Cuándo SÍ se actualizan los índices secundarios en InnoDB?
    Solo cuando la columna indexada en el secundario cambia:
    UPDATE ventas SET precio = 200 WHERE id = 1;
    → índice sobre precio: borrar (precio_viejo=1000, val_PK=1)
                           insertar (precio_nuevo=200, val_PK=1)
    El puntero sigue usando val_PK=1 (no una dirección física que cambió).
```

La elegancia del diseño: al usar el valor lógico de PK como el "puntero" de los índices
secundarios, InnoDB crea un nivel de indirección lógico que aísla a los índices
secundarios de los movimientos físicos de las filas. El tradeoff es la doble travesía.

---

#### Índices secundarios en InnoDB: el doble camino y su costo real en bytes

Un **índice secundario** es cualquier índice construido sobre una columna que no es la
clave primaria. Con nuestra tabla `ventas(id, precio, cantidad)`:

- El **clustered index** (o índice primario) está sobre `id`. Es el árbol que contiene
  la fila completa — ya visto en detalle.
- Si ejecutamos `CREATE INDEX idx_precio ON ventas (precio)`, InnoDB construye un
  **segundo árbol B+ separado** — el índice secundario sobre `precio`. Este árbol no
  contiene las filas; solo existe para responder rápido a preguntas como
  "¿qué filas tienen precio=25?".

La pregunta clave es: **¿qué guarda una hoja de ese segundo árbol?**

```
ESTRUCTURA DE LOS DOS ÁRBOLES EN INNODB
tabla: ventas(id PK, precio, cantidad)
Datos: (id=1, precio=10, cantidad=5), (id=2, precio=25, cantidad=NULL), (id=3, precio=25, cantidad=8)

  Árbol 1 — Clustered index (sobre id)        Árbol 2 — Índice secundario (sobre precio)
  ════════════════════════════════════         ════════════════════════════════════════════
  Nodo raíz                                    Nodo raíz
  ┌─────────────────────────────┐              ┌──────────────────────────────┐
  │  separador: id=2            │              │  separador: precio=25        │
  └────────────┬────────────────┘              └───────────┬──────────────────┘
               │                                           │
  ┌────────────▼────────────────────────┐     ┌───────────▼───────────────────────────────┐
  │ Hoja del clustered index            │     │ Hojas del índice secundario                │
  │                                     │     │                                            │
  │ [id=1][precio=10][cantidad=5  ]     │     │ [precio=10][val_PK=1]                      │
  │ [id=2][precio=25][cantidad=NULL]    │     │ [precio=25][val_PK=2]                      │
  │ [id=3][precio=25][cantidad=8  ]     │     │ [precio=25][val_PK=3]                      │
  │                                     │     │                                            │
  │ ← fila completa aquí               │     │ ← solo precio + PK lógica, nada más        │
  └─────────────────────────────────────┘     └────────────────────────────────────────────┘
```

La hoja del árbol secundario guarda `(precio, val_PK)` — el valor indexado más el valor
lógico de la PK, pero **no guarda el resto de la fila**. `cantidad` no está aquí.

Esto crea el problema del doble camino. Considera esta consulta:

```sql
SELECT precio, cantidad FROM ventas WHERE precio = 25;
```

El motor encuentra `precio=25` en el índice secundario y obtiene `val_PK=2` y `val_PK=3`.
Pero `cantidad` no está en ese árbol. Para obtenerla, la única opción es usar ese valor
de PK para buscar la fila completa — lo que significa recorrer el clustered index desde
su raíz, de nuevo, una vez por cada fila encontrada.

**Por qué PostgreSQL no tiene este problema**

En PostgreSQL no existe el clustered index: la tabla es un heap desordenado y todos los
índices (incluyendo el del PK) son estructuras separadas. La diferencia decisiva está en
qué guarda la hoja de cada índice:

```
POSTGRESQL — índice secundario sobre precio       INNODB — índice secundario sobre precio
Hoja:  [precio=25] [ctid=(0,2)]                   Hoja:  [precio=25] [val_PK=2]
                        │                                                  │
                        │ dirección física:                                │ clave lógica:
                        │ página 0, slot 2                                 │ "busca id=2"
                        ▼                                                  ▼
               Saltar directamente             Recorrer el clustered index
               a esa página del heap          de raíz a hoja otra vez
               O(1) — un solo I/O             O(log n) — 3–4 I/Os más
```

El `ctid` es una dirección física concreta. No hay nada que "resolver" — el motor carga
esa página, lee ese slot, y tiene todos los campos de la fila de una sola vez. El
`val_PK` de InnoDB es una dirección lógica que todavía tiene que convertirse en una
ubicación física navegando el árbol.

**El camino en bytes**

```
SELECT precio, cantidad FROM ventas WHERE precio = 25;

══════════════════════════════════════════════════════════════════════════════════
POSTGRESQL — una travesía de árbol + un salto físico al heap
══════════════════════════════════════════════════════════════════════════════════

  ① Navegar el árbol del índice secundario sobre precio:
     Nodos internos → hoja:
     hoja: [precio=25] [ctid=(0,2)]   ← valor indexado + dirección física

  ② ctid=(0,2) es una dirección física — saltar directamente:
     página 0 del heap  →  slot 2  →  ItemId  →  lp_off=8084
     byte 8084: HeapTupleHeader (23 B), luego datos:
       id=2       @ offset 24: 02 00 00 00 → 2
       precio=25  @ offset 28: 19 00 00 00 → 25
       cantidad=1 @ offset 32: 01 00 00 00 → 1
     Todos los campos disponibles. Una sola lectura de página.

  Costo total: traversal árbol secundario (3–4 I/Os) + 1 I/O heap = 4–5 I/Os

══════════════════════════════════════════════════════════════════════════════════
MYSQL INNODB — dos travesías de árbol completas
══════════════════════════════════════════════════════════════════════════════════

  ① Navegar el árbol del índice secundario sobre precio:
     Nodos internos → hoja del índice secundario:
     hoja: [precio=25 → 80 00 00 19] [val_PK=2 → 80 00 00 02]
     Aquí termina lo que sabe este árbol. cantidad no existe en estas hojas.

  ② val_PK=2 no es una dirección física — hay que ir al otro árbol:
     Navegar el clustered index desde su raíz con clave 80 00 00 02:
     Raíz → nodos internos → hoja del clustered:
     hoja: [NULL bitmap=00000010b][rec_header][DB_TRX_ID][DB_ROLL_PTR]
           [id → 80 00 00 02][precio → 80 00 00 19][cantidad → ausente por NULL]
     Decodificar: id=2, precio=25, cantidad=NULL.

  Costo total: traversal árbol secundario (3–4 I/Os) + traversal clustered (3–4 I/Os)
               = 6–8 I/Os — el doble que PostgreSQL para el mismo resultado.
```

**Por qué el costo se multiplica con muchas filas**

El costo por fila adicional no es equivalente en ambos motores. En PostgreSQL, cada fila
extra cuesta 1 I/O de página heap — y si varias filas caen en la misma página, ese I/O
se comparte entre todas ellas. En InnoDB, cada fila extra puede costar O(log n) I/Os
adicionales porque el clustered index se recorre independientemente por cada PK:

```
Para una consulta que devuelve k = 10.000 filas:

  PostgreSQL:  3–4 I/Os (árbol secundario)  +  ≤10.000 I/Os (heap fetches)
               ≈ 10.004 I/Os
               Mitigado por Bitmap Index Scan: agrupa ctids por página antes de leer,
               reduciendo I/Os cuando muchas filas comparten páginas del heap.

  InnoDB:      3–4 I/Os (árbol secundario)  +  10.000 × 3–4 I/Os (clustered)
               ≈ 40.004 I/Os
               Mitigado solo si el clustered index completo cabe en el buffer pool.

  La diferencia crece linealmente con k.
  En tablas grandes (árbol de 4 niveles, millones de filas), cada columna ausente
  del índice secundario cuesta 4 I/Os adicionales por cada fila devuelta.
```

**La solución: el covering index en InnoDB**

La segunda travesía existe porque el índice secundario no tiene suficientes columnas.
La solución directa es incluir en el índice todas las columnas que la consulta necesita.
Ese índice se llama **covering index** (o índice cubriente): un índice secundario cuyas
hojas contienen toda la información necesaria para responder la consulta, sin tocar
el clustered index.

La diferencia está en lo que guarda cada hoja:

```
ÍNDICE SECUNDARIO REGULAR — CREATE INDEX idx_precio ON ventas (precio)

  Hoja del índice:
  ┌──────────────────────────────────────────────────────┐
  │  precio = 25  →  80 00 00 19                         │
  │  val_PK = 2   →  80 00 00 02                         │
  └──────────────────────────────────────────────────────┘
  cantidad no está aquí.
  Para obtenerla: 2ª travesía completa al clustered index. Inevitable.

────────────────────────────────────────────────────────────────────────────────

COVERING INDEX — CREATE INDEX idx_precio_cov ON ventas (precio, cantidad)

  Hoja del índice:
  ┌──────────────────────────────────────────────────────┐
  │  precio   = 25  →  80 00 00 19                       │
  │  cantidad =  1  →  80 00 00 01                       │
  │  val_PK   =  2  →  80 00 00 02   ← implícito siempre │
  └──────────────────────────────────────────────────────┘
  precio ✓   cantidad ✓
  La consulta se resuelve aquí. El clustered index no se toca.
```

Al agregar `cantidad` al índice, la hoja pasa de tener `(precio, val_PK)` a tener
`(precio, cantidad, val_PK)`. InnoDB siempre incluye el val_PK implícitamente en
toda hoja de índice secundario, por lo que no hay overhead extra en eso. El único
costo es el espacio en disco adicional del árbol y el mantenimiento en cada escritura.

```sql
-- Sin covering index: InnoDB necesita 2 travesías
SELECT precio, cantidad FROM ventas WHERE precio = 25;
-- EXPLAIN → Extra: NULL  (hace lookup al clustered)

-- Con covering index: InnoDB resuelve en 1 sola travesía
CREATE INDEX idx_precio_cov ON ventas (precio, cantidad);
SELECT precio, cantidad FROM ventas WHERE precio = 25;
-- EXPLAIN → Extra: "Using index"  (solo lee el índice secundario)
```

**Por qué los covering indexes valen más en InnoDB que en PostgreSQL**

En PostgreSQL también existen los covering indexes (llamados index-only scans), y también
evitan leer el heap. Pero la alternativa en caso de no tenerlos es diferente:

```
Sin covering index — costo de obtener una columna extra por fila:

  PostgreSQL:  1 I/O de página heap
               (el ctid apunta directo; si ya está en buffer pool, costo ~0)

  InnoDB:      3–4 I/Os de traversal del clustered index
               (recorre el árbol de raíz a hoja cada vez; en tabla de 1M filas
                con árbol de 4 niveles → 4 I/Os por fila, sin excepción)
```

En ambos motores el covering index elimina esa segunda lectura. La diferencia es que
en InnoDB lo que se elimina es una travesía de árbol O(log n) por fila, mientras que en
PostgreSQL lo que se elimina es un salto de página O(1). El ahorro absoluto por fila es
mucho mayor en InnoDB, y se multiplica con cada fila que devuelve la consulta.

---

#### El page split: el costo en bytes de los INSERTs desordenados

Para entender el problema hay que tener claro un hecho sobre cómo funciona cada motor
cuando recibe un INSERT.

En PostgreSQL el heap no tiene orden. Cuando llega un INSERT, el motor busca cualquier
página con espacio libre — no le importa el valor de PK. La fila con `id=1` y la fila
con `id=999999` pueden terminar en la misma página. El FSM (Free Space Map) registra
qué páginas tienen hueco, y encontrar una cuesta O(1).

En InnoDB el clustered index es la tabla, y sus páginas hoja guardan las filas
físicamente ordenadas por valor de PK. Cuando llega un INSERT, el motor tiene que
colocar la nueva fila en la página que le corresponde según ese orden — no tiene la
opción de "poner la fila donde haya espacio". Si esa página está llena, no hay
alternativa: hay que dividirla.

**El fill factor: por qué las páginas no se llenan al 100%**

InnoDB no llena sus páginas hoja al 100%. Por defecto las llena al 93.75% (15/16) y
reserva el 6.25% restante (1/16). Ese espacio reservado sirve de colchón: si llegan
inserts cuyos valores caen dentro del rango de esa página, pueden acomodarse sin
necesitar un split inmediato. Este parámetro se llama **fill factor** y se controla
con `innodb_fill_factor`.

**Caso 1: INSERT con PK secuencial (auto_increment) — el caso ideal**

Supongamos una tabla con `id BIGINT AUTO_INCREMENT`. Los IDs se asignan en orden
creciente: 1, 2, 3, 4... Cada nuevo INSERT tiene el ID más grande que ha existido
hasta ahora.

```
Estado del árbol con 600 filas ya insertadas:

  ┌─────────────────────────┐  ┌─────────────────────────┐  ┌─────────────────────────┐
  │ hoja A                  │  │ hoja B                  │  │ hoja C        ← última  │
  │ id: 1 → 200             │  │ id: 201 → 400           │  │ id: 401 → 600           │
  │ [■■■■■■■■■■■■■■■ 93%]   │  │ [■■■■■■■■■■■■■■■ 93%]   │  │ [■■■■■■■■■■■■■ 93%  □] │
  └─────────────────────────┘  └─────────────────────────┘  └──────────────┬──────────┘
                                                                            │ 6% libre
                                                             INSERT id=601 ─┘
                                                             siempre va aquí
```

El nuevo `id=601` es mayor que todos los existentes, así que pertenece a la última hoja
del árbol. InnoDB mantiene en memoria un puntero a esa última hoja. El record se escribe
directamente ahí, dentro del espacio reservado.

Resultado: 0 page splits. Cuando la hoja C se llena del todo, InnoDB agrega una nueva
hoja D al final — sin mover ni reorganizar nada de lo que ya existe.

**Caso 2: INSERT con PK UUID v4 — el caso problemático**

UUID v4 genera 128 bits aleatorios. Sus primeros bytes — los que determinan la posición
en el árbol — no siguen ningún orden: cada UUID nuevo puede caer en cualquier punto del
árbol, con igual probabilidad.

El problema se puede ver así: si el árbol tiene 1000 páginas hoja y cada una está al
93.75% de ocupación, solo el 6.25% de su espacio está libre. Un UUID aleatorio tiene el
mismo 6.25% de probabilidad de caer en una página que aún tiene hueco. El otro 93.75%
de las veces la página está llena, y hay que hacer un page split.

Veamos el split concreto. Los valores que aparecen a continuación son los primeros bytes
del UUID en hexadecimal — son los bytes que InnoDB usa para ordenar los records dentro
de la página y dentro del árbol:

```
ANTES del INSERT — página hoja con records cuyos primeros bytes van de 5b... a 6b...:

  ╔══════════════════════════════════════════════════════════════════════════════╗
  ║  Infimum                                                                     ║
  ║    → [5b12…] → [5c3a…] → [5e8f…] → [5f01…] → [6003…] → [613c…] → [6b90…] ║
  ║  → Supremum                                                                  ║
  ║                                                                              ║
  ║  Ocupación: 93.75%  │  Espacio libre: 6.25%  │  Capacidad: 1 record más     ║
  ╚══════════════════════════════════════════════════════════════════════════════╝

  Llega INSERT con UUID cuyos primeros bytes son 5d44…
  5d44 es mayor que 5c3a y menor que 5e8f → debe ir entre esos dos records.
  La página solo tiene espacio para 1 record más, y ese espacio no está en el
  medio — está al final. No se puede insertar en el medio sin hacer espacio.
  → InnoDB ejecuta PAGE SPLIT.
```

El split ocurre en cinco pasos:

```
  ① Asignar una nueva página hoja vacía en disco            [1 I/O escritura]

  ② Mover la mitad de los records al final de la página     [2–3 I/Os]
     (los records de 5e8f… en adelante van a la nueva página)

  ③ Actualizar el nodo padre del árbol: agregar una         [1 I/O escritura]
     entrada que apunte a la nueva página
     Si el nodo padre también está lleno → split recursivo hacia arriba

  ④ Actualizar el linked list de hojas:                     [I/Os adicionales]
       página_original.next  = nueva_página
       nueva_página.prev     = página_original
       nueva_página.next     = lo_que_era_next_original

  ⑤ Insertar el record 5d44… en la página original
     (ahora hay espacio porque se movió la mitad)

DESPUÉS del INSERT — dos páginas, cada una al ~50% de ocupación:

  ╔════════════════════════════════════╗  ╔══════════════════════════════════════╗
  ║ [5b12…][5c3a…][5d44…][5e8f…]      ║  ║ [5f01…][6003…][613c…][6b90…]        ║
  ║ ← 50% llena                        ║  ║ ← 50% llena                          ║
  ╚════════════════════════════════════╝  ╚══════════════════════════════════════╝
```

Las mismas 8 filas ahora ocupan 2 páginas en lugar de 1. La densidad bajó de 93.75%
a ~50%. A medida que esto se repite, el árbol necesita casi el doble de páginas para
los mismos datos, lo que significa más I/Os en lecturas y más presión sobre el buffer
pool.

A escala, con UUID v4 como PK:

- ~94% de los INSERTs caen en páginas llenas → causan split
- A 10,000 inserts/segundo → ~9,400 page splits/segundo
- La densidad media del árbol converge al 50%, no al 93.75% con el que fue diseñado

**UUID v7: cómo se resuelve el problema a nivel de bytes**

La causa raíz del problema con UUID v4 es concreta: los primeros bytes del UUID son
aleatorios, y son exactamente esos primeros bytes los que determinan en qué posición
del árbol cae cada record.

La solución, entonces, también es concreta: necesitamos un identificador cuyos primeros
bytes sean siempre crecientes — así cada nuevo INSERT siempre cae al final del árbol,
igual que `AUTO_INCREMENT`.

UUID v7 hace exactamente eso. Pone un timestamp de 48 bits (milisegundos desde Unix
epoch) en sus primeros 6 bytes. InnoDB almacena el UUID como `BINARY(16)` y lo ordena
comparando sus bytes de izquierda a derecha, sin ninguna transformación adicional.
Como el timestamp en big-endian ya garantiza que un valor mayor produce bytes mayores,
el orden lexicográfico byte a byte es el mismo que el orden cronológico:

```
  UUID v4 — primeros bytes completamente aleatorios:

    INSERT a las 10:00:00  →  7f 3a 2b 1c …  ← posición arbitraria en el árbol
    INSERT a las 10:00:01  →  3c 8d 1a 2b …  ← posición diferente, también arbitraria
    INSERT a las 10:00:02  →  a1 f0 44 9e …  ← posición diferente, también arbitraria

    Los tres records caen en tres hojas distintas. Las tres páginas probablemente
    estaban llenas → tres page splits.

  ─────────────────────────────────────────────────────────────────────────────

  UUID v7 — primeros 6 bytes = timestamp en milisegundos:

    INSERT a las 10:00:000  →  01 96 3a 2b 1c 4d  7? ?? 8? ?? …
                                └─────────────────┘
                                timestamp = ms desde epoch  (creciente)

    INSERT a las 10:00:001  →  01 96 3a 2b 1c 4e  7? ?? 8? ?? …  ← 1 ms después
    INSERT a las 10:00:002  →  01 96 3a 2b 1c 4f  7? ?? 8? ?? …  ← 1 ms después

    Los tres records tienen prefijos casi idénticos → los tres caen en la misma
    hoja o en hojas adyacentes al final del árbol → esa hoja tiene espacio libre
    → 0 page splits.
```

El resultado es comportamiento casi idéntico a `AUTO_INCREMENT`: todos los inserts
recientes van a la misma zona del árbol, sin reorganizaciones.

---

#### Tabla de costos Big-O: PostgreSQL (heap) vs MySQL InnoDB (clustered)

```
╔══════════════════════════════════════╦════════════════════════════════╦══════════════════════════════════════╗
║ Operación                            ║ PostgreSQL (heap)               ║ MySQL InnoDB (clustered)             ║
╠══════════════════════════════════════╬════════════════════════════════╬══════════════════════════════════════╣
║ INSERT (val PK secuencial)           ║ O(1) amortizado                ║ O(log n), append a última hoja       ║
║                                      ║ FSM busca cualquier pág libre  ║ sin page splits — caso óptimo        ║
╠══════════════════════════════════════╬════════════════════════════════╬══════════════════════════════════════╣
║ INSERT (val PK aleatorio, UUID v4)   ║ O(1) amortizado                ║ O(log n) + page split frecuente      ║
║                                      ║ el orden de PK no importa      ║ ~5× más I/Os de escritura por split  ║
╠══════════════════════════════════════╬════════════════════════════════╬══════════════════════════════════════╣
║ SELECT por valor de PK exacto        ║ O(log n) árbol del índice PK   ║ O(log n) árbol clustered             ║
║                                      ║ → ctid → O(1) heap fetch       ║ datos ya en hoja — sin fetch extra   ║
║                                      ║ ~4 I/Os árbol + 1 heap = 5     ║ ~4 I/Os árbol (algo menos)           ║
╠══════════════════════════════════════╬════════════════════════════════╬══════════════════════════════════════╣
║ SELECT por índice secundario         ║ O(log n) árbol secundario      ║ O(log n) árbol secundario            ║
║ (sin covering index)                 ║ → ctid → O(1) heap fetch       ║ → val PK → O(log n) clustered        ║
║                                      ║ ~5 I/Os totales                ║ ~8 I/Os totales                      ║
╠══════════════════════════════════════╬════════════════════════════════╬══════════════════════════════════════╣
║ SELECT por índice secundario         ║ O(log n) árbol secundario      ║ O(log n) árbol secundario            ║
║ (con covering index)                 ║ index-only scan — sin heap     ║ index-only scan — sin clustered      ║
║                                      ║ ~4 I/Os                        ║ ~4 I/Os — empate                     ║
╠══════════════════════════════════════╬════════════════════════════════╬══════════════════════════════════════╣
║ Range scan por val PK                ║ O(log n) inicio                ║ O(log n) inicio                      ║
║ (WHERE id BETWEEN x AND y)           ║ + k heap fetches               ║ + k lecturas secuenciales de hojas   ║
║                                      ║ (dispersos si heap desordenado)║ (hojas adyacentes en orden de PK)    ║
║                                      ║ PG mitiga con Bitmap Index Scan║ InnoDB gana en práctica 2–5×         ║
╠══════════════════════════════════════╬════════════════════════════════╬══════════════════════════════════════╣
║ Full table scan                      ║ O(n) páginas heap              ║ O(n) páginas hoja del árbol          ║
║                                      ║ secuencial — óptimo            ║ secuencial — empate                  ║
╠══════════════════════════════════════╬════════════════════════════════╬══════════════════════════════════════╣
║ UPDATE (no cambia val PK)            ║ MVCC: nueva tuple en el heap   ║ In-place si cabe en la misma pág     ║
║                                      ║ actualizar índices afectados   ║ actualizar índices afectados         ║
║                                      ║ ctid cambia si no HOT          ║ val PK no cambia → índices secundarios║
║                                      ║                                ║ NO necesitan actualización de puntero ║
╠══════════════════════════════════════╬════════════════════════════════╬══════════════════════════════════════╣
║ DELETE lógico                        ║ Marca t_xmax en la tuple       ║ delete mark en el record header      ║
║                                      ║ (soft delete en los bytes)     ║ (5 bytes; el bit se lee en info_bits)║
║                                      ║ VACUUM limpia después          ║ Purge thread limpia después          ║
╚══════════════════════════════════════╩════════════════════════════════╩══════════════════════════════════════╝
```

**Por qué el range scan por PK favorece a InnoDB en la práctica**

La diferencia no es de complejidad asintótica — ambos motores son O(log n) para
encontrar el punto de inicio. La diferencia está en cómo se leen las filas restantes.

WHERE id BETWEEN 1 AND 1000

POSTGRESQL — el índice PK devuelve ctids que apuntan a páginas heap dispersas:

    Hoja del índice (B+Tree sobre id)     Heap (desordenado — filas donde cayeron)
    ──────────────────────────────        ─────────────────────────────────────────
    id=1  → ctid=(7, 2)  ────────────▶   página 7   [id=1, precio=10, …]  (I/O #1)
    id=2  → ctid=(0, 4)  ────────────▶   página 0   [id=2, precio=25, …]  (I/O #2)
    id=3  → ctid=(3, 1)  ────────────▶   página 3   [id=3, precio=50, …]  (I/O #3)
    id=4  → ctid=(7, 6)                  página 7    ← ya en buffer pool, sin I/O
    id=5  → ctid=(12,2)  ────────────▶   página 12  [id=5, precio=30, …]  (I/O #4)
    …

    Cada fila se insertó en un momento distinto → quedó en una página del heap
    sin relación con las demás. Para k=1000 filas puede haber hasta 1000 páginas
    distintas → hasta 1000 I/Os de lectura.

    El Bitmap Index Scan alivia esto: agrupa todos los ctids por número de página
    antes de empezar a leer, y luego lee cada página una sola vez. Si las 1000
    filas están repartidas en 200 páginas distintas → 200 I/Os en lugar de 1000.
    Pero si cada fila vive en una página diferente, el bitmap no ayuda.

INNODB — hojas del clustered index enlazadas en orden de PK:

    ┌─────────────────────┐    ┌─────────────────────┐    ┌─────────────────────┐
    │ hoja A              │───▶│ hoja B              │───▶│ hoja C              │
    │ id: 1 – 200         │    │ id: 201 – 400       │    │ id: 401 – 600       │
    │ [filas completas]   │    │ [filas completas]   │    │ [filas completas]   │
    └─────────────────────┘    └─────────────────────┘    └─────────────────────┘
       (I/O #1)                    (I/O #2)                   (I/O #3)

    InnoDB navega hasta la primera hoja que contiene id=1 y luego sigue el linked
    list de hojas (campo page_next del FIL Header) hasta que el id supera 1000.
    Las filas completas ya están en las hojas del árbol — no hay heap que consultar.

    Para k=1000 filas con 200 records por hoja → 5 páginas → 5 I/Os.
    Misma consulta, mismos datos: 5 I/Os vs hasta 1000 en PostgreSQL.

### Sección 2.2: SQL Server: heap y clustered index en el mismo motor {#2-sqlserver}

SQL Server es el motor enterprise que más explícitamente expone la elección entre los
dos modelos al DBA. Oracle también permite elegir — sus IOT (Index-Organized Tables)
son el equivalente del clustered index — pero en Oracle el heap es el default y las IOT
son una opción especial. En SQL Server, cualquier tabla puede vivir como heap o como
clustered index, y esa decisión puede cambiarse en cualquier momento con
`CREATE CLUSTERED INDEX` o `DROP INDEX`.

---

#### El heap de SQL Server y el RID

Cuando una tabla en SQL Server no tiene clustered index, es un heap: filas en páginas de
8 KB (igual que PostgreSQL) sin orden garantizado. Los índices no-clustered apuntan a
las filas con un **RID** (Row Identifier) — `file_id` (2B) + `page_id` (4B) + `slot` (2B). El campo `file_id` es necesario porque SQL Server puede distribuir páginas entre múltiples archivos de datos (`.mdf`, `.ndf`) en el mismo filegroup.

---

#### Cómo los índices non-clustered apuntan en cada caso

SQL Server tiene una regla precisa que depende del estado de la tabla:

```
CASO A: Tabla es HEAP (sin clustered index)

Índice non-clustered en (precio):
┌────────────────────────────────────────────────┐
│ hoja: precio=25, RID=(file=1, page=87, slot=3) │
└────────────────────────────────────────────────┘

SELECT * FROM ventas WHERE precio = 25;
→ Índice non-clustered: O(log n) → obtiene RID
→ Saltar directamente a file=1, pág=87, slot=3 en el heap: O(1)
→ Costo total: O(log n) + O(1) ← igual que PostgreSQL

─────────────────────────────────────────────────────────────────────────

CASO B: Tabla tiene CLUSTERED INDEX (clave = id)

Índice non-clustered en (precio):
┌───────────────────────────────────────────────────────────────────┐
│ hoja: precio=25, clustering_key=2 (valor de PK lógico, no RID) │
└───────────────────────────────────────────────────────────────────┘

SELECT * FROM ventas WHERE precio = 25;
→ Índice non-clustered: O(log n) → obtiene PK=2
→ Navegar el clustered index con PK=2: O(log n)
→ Costo total: O(log n) + O(log n) ← igual que InnoDB

IMPLICACIÓN IMPORTANTE: si cambias una tabla de heap a clustered index,
SQL Server reconstruye todos sus índices non-clustered automáticamente
(convierte los RID en claves de clustering). Es una operación bloqueante
y pesada en tablas grandes — planificarla con una ventana de mantenimiento.

```

La regla de SQL Server: cuando existe un clustered index, todos los índices non-clustered
se comportan igual que los índices secundarios de InnoDB. El modelo cambia completamente
según si la tabla tiene clustered index o no.

---

#### Fill factor y reserva de espacio para UPDATEs

```
╔══════════════╦══════════════════════╦══════════════════════════════════════════════╗
║ Motor        ║ Fill factor default  ║ Estrategia para UPDATEs                    ║
╠══════════════╬══════════════════════╬══════════════════════════════════════════════╣
║ PostgreSQL   ║ 100 %                ║ HOT updates en la misma página;            ║
║              ║                      ║ VACUUM recupera espacio de tuples muertas  ║
╠══════════════╬══════════════════════╬══════════════════════════════════════════════╣
║ MySQL InnoDB ║ 93.75 % (15/16)      ║ Reserva 1/16 por página; page split cuando ║
║              ║                      ║ se agota ese espacio                       ║
╠══════════════╬══════════════════════╬══════════════════════════════════════════════╣
║ SQL Server   ║ 0 (= 100 % en hojas) ║ Default: páginas llenas al crear el índice;║
║              ║ configurable por DBA ║ muchos equipos lo bajan a 70–80 % en OLTP  ║
╠══════════════╬══════════════════════╬══════════════════════════════════════════════╣
║ Oracle       ║ 90 % (PCTFREE=10)    ║ Reserva 10 % para expansión de filas       ║
║              ║                      ║ con columnas VARCHAR al actualizar         ║
╚══════════════╩══════════════════════╩══════════════════════════════════════════════╝

```

**¿Por qué PostgreSQL usa 100% y los demás reservan espacio?**

PostgreSQL tiene HOT (Heap-Only Tuple): si un UPDATE no cambia columnas indexadas y
la nueva tuple cabe en la misma página que la vieja, PostgreSQL puede escribir la
versión nueva ahí mismo sin actualizar ningún índice. El espacio libre dentro de la
página es el que hace posible ese HOT. Si ya no hay espacio, VACUUM recupera el
espacio de tuples muertas para reutilizarlo. La estrategia es "llenar ahora, recuperar
después" en lugar de "reservar siempre". HOT updates se cubre en detalle en
`guia_transacciones_db.md`.

SQL Server usa 0 como default porque al crear un índice nuevo las páginas se llenan
eficientemente (sin splits inmediatos). El fill factor solo afecta el estado inicial
del índice — no se mantiene automáticamente con el tiempo. Bajarlo reduce splits en
tablas con inserts aleatorios a costa de más páginas (más I/Os en reads).

---

#### Cuándo usar heap vs clustered en SQL Server

```
╔══════════════════════════════════════════════╦════════════════════════════════════╗
║ Usar HEAP cuando:                            ║ Usar CLUSTERED INDEX cuando:       ║
╠══════════════════════════════════════════════╬════════════════════════════════════╣
║ • Carga masiva / staging                     ║ • Queries frecuentes con ORDER BY  ║
║ INSERT sin overhead de árbol B+              ║ o WHERE sobre la clave             ║
╠══════════════════════════════════════════════╬════════════════════════════════════╣
║ • Tabla solo se lee con full scans           ║ • Range scans frecuentes           ║
║ (nunca por clave específica)                 ║ (reportes por fecha, ID, rango)    ║
╠══════════════════════════════════════════════╬════════════════════════════════════╣
║ • Append-only inmutable                      ║ • OLTP intensiva en lectura        ║
║ (logs, auditoría sin acceso por PK)          ║ con PK secuencial                  ║
╠══════════════════════════════════════════════╬════════════════════════════════════╣
║ • Tablas temporales o de trabajo             ║ • Joins frecuentes usando          ║
║ de corta vida                                ║ la clave de clustering             ║
╚══════════════════════════════════════════════╩════════════════════════════════════╝

```

**Regla práctica:** toda tabla OLTP con acceso por clave debe tener un clustered index
(normalmente en PK). Solo se crean como heap las tablas de staging, ETL o temporales —
y muchas veces se convierten a clustered index cuando pasan a producción permanente.

---

### Sección 2.3: Oracle y SQLite: el mismo espectro, implementaciones propias {#2-oracle-sqlite}

---

#### Oracle: heap por defecto, IOT como opción explícita

Oracle es el motor enterprise más parecido a PostgreSQL en cuanto a almacenamiento:
heap por defecto, sin clustered index obligatorio, cada fila puede ir a cualquier bloque
con espacio libre. Pero Oracle tiene su propia terminología y algunas diferencias de
implementación notables.

**El ROWID de Oracle**

Oracle identifica físicamente cada fila con un ROWID — el equivalente al ctid de
PostgreSQL, pero más grande y con más información:

```
Oracle ROWID — 10 bytes internos (80 bits), mostrado como 18 caracteres en base-64:

Bytes internos (empaquetado a nivel de BITS, no de bytes completos —
file# y block# comparten los mismos 4 bytes):
┌────────────────────┬───────────────────────────────────┬──────────────────┐
│ Data Object#        │ Relative File# (10 bits) +        │ Row# dentro      │
│ 32 bits = 4 bytes   │ Block# dentro del file (22 bits)  │ del bloque       │
│                     │ = 4 bytes compartidos entre ambos │ 16 bits = 2 bytes│
└────────────────────┴───────────────────────────────────┴──────────────────┘
Total: 4B (objeto) + 4B (archivo + bloque empaquetados) + 2B (fila) = 10 bytes

Representación externa (lo que ves en SQL) — 18 caracteres en base-64,
más generosa en bits por campo que el empaquetado interno, para que cada
campo quede alineado a caracteres completos:
AAAFvS AAE AAAACK AAA
────── ─── ────── ───
obj#   file# block# row#

Ejemplo real: AAAFvSAAEAAAACKAAA → object 24466, file 1, block 651, row 0

Comparación de punteros físicos entre todos los motores:
╔══════════════╦══════════╦═══════════════════════════════════════════════╗
║ Motor ║ Tamaño ║ Campos ║
╠══════════════╬══════════╬═══════════════════════════════════════════════╣
║ PostgreSQL ║ 6B ║ page_number(4) + slot(2) ║
║ SQL Server ║ 8B ║ file_id(2) + page_id(4) + slot(2) ║
║ Oracle ║ 10B ║ object#(32 bits) + file#(10 bits) + block#(22 bits) + row#(16 bits), empaquetado a nivel de bits ║
║ MySQL InnoDB ║ N/A ║ no expone dirección física; usa valor de PK ║
╚══════════════╩══════════╩═══════════════════════════════════════════════╝

Oracle incluye el Data Object# porque un mismo tablespace puede contener
múltiples objetos (tablas, índices) y el ROWID debe identificar cuál.
El block# es relativo al file# específico (no absoluto dentro del tablespace).

```

**El bloque Oracle y PCTFREE**

Este diagrama junta, en un solo lugar, lo que en PostgreSQL vive separado entre la Capa 2
(header) y la Capa 3 (directorio). El BLOCK HEADER de Oracle ya incluye tanto la ITL como
el Table Directory y el Row Directory, todo dentro del mismo overhead inicial del bloque.

Oracle llama "bloque" a lo que otros motores llaman "página". El tamaño por defecto
es 8 KB, configurable entre 2 KB y 32 KB:

```
Bloque Oracle (8.192 bytes por defecto):

╔══════════════════════════════════════════════════════════════════╗
║ BLOCK HEADER 84–107 bytes (overhead total, variable)             ║
║ • Cache layer (SCN, read/write mode, etc.)                       ║
║ • Transaction layer (TL) con entradas ITL:                       ║
║ ITL = Interested Transaction List                                ║
║ cada ITL entry: xid(8B) + uba(8B) + flag(1B) + lock(1B) + scn    ║
║ (Oracle guarda info de transacciones en el header del bloque,    ║
║ a diferencia de PostgreSQL que la guarda en cada tuple header)   ║
╠══════════════════════════════════════════════════════════════════╣
║ TABLE DIRECTORY variable                                         ║
║ • Qué tablas tienen filas en este bloque                         ║
║ • En bloques de segmentos de datos: normalmente 1 entrada        ║
╠══════════════════════════════════════════════════════════════════╣
║ ROW DIRECTORY 2B × n_filas                                       ║
║ • Array de offsets de cada fila en el bloque                     ║
║ • Equivalente al ItemId array de PostgreSQL                      ║
║ • 2 bytes por fila (offset dentro del bloque)                    ║
╠══════════════════════════════════════════════════════════════════╣
║                                                                  ║
║ PCTFREE — espacio reservado configurable %                       ║
║ (default PCTFREE=10 → reserva el 10% para expansión de filas)    ║
║ Un bloque deja de aceptar INSERTs cuando llega al límite         ║
║ de PCTFREE. El espacio reservado es para UPDATEs que crecen.     ║
║                                                                  ║
╠══════════════════════════════════════════════════════════════════╣
║ ROW DATA (filas, de abajo hacia arriba) variable                 ║
║                                                                  ║
║ Cada Row Piece (puede estar encadenada entre bloques):           ║
║ • Row Header: 3B (lock byte, col count, flags)                   ║
║ • Column data: tipo + longitud + valor para cada columna         ║
║                                                                  ║
╚══════════════════════════════════════════════════════════════════╝

PCTFREE vs fill factor de PostgreSQL:
Son el mismo concepto con nombres diferentes y orientaciones opuestas:
PostgreSQL fill_factor=80 → llenar al 80%, dejar 20% libre
Oracle PCTFREE=10 → dejar libre el 10%, llenar hasta el 90%
Son equivalentes (fill_factor + PCTFREE = 100%)

Diferencia importante: Oracle tiene PCTUSED además de PCTFREE, y no es redundante.
Sin PCTUSED, un bloque que baja de 91% a 89% de ocupación (apenas por debajo del
límite de PCTFREE) volvería a la free list de inmediato, aceptaría un INSERT pequeño,
y volvería a superar el límite casi al instante, entrando y saliendo de la free list
todo el tiempo por cambios mínimos de espacio. Exigir que el bloque baje hasta el 40%
antes de reincorporarse evita ese vaivén: cuando un bloque vuelve a la free list, tiene
que tener espacio de sobra (60%) para que valga la pena el costo de administrarlo de nuevo.
PostgreSQL no necesita esto porque el FSM (Free Space Map) rastrea el espacio libre de
cada página con granularidad fina, sin depender de un umbral de entrada y salida.

```

**Index-Organized Tables (IOT): el clustered index de Oracle**

Oracle permite declarar una tabla con `ORGANIZATION INDEX` para almacenarla como
un clustered B+tree, idéntico al modelo de InnoDB:

```sql
-- Tabla heap (default de Oracle — igual que PostgreSQL):
CREATE TABLE ventas (
    id     NUMBER      PRIMARY KEY,
    precio NUMBER,
    cant   NUMBER
);
-- Las filas van a bloques con espacio libre, sin orden de PK garantizado.

-- Index-Organized Table (IOT — igual que InnoDB clustered):
CREATE TABLE ventas (
    id     NUMBER      PRIMARY KEY,
    precio NUMBER,
    cant   NUMBER
) ORGANIZATION INDEX;
-- Las filas se almacenan en el B+tree del PK.
-- No existe "heap" separado. Los mismos tradeoffs que InnoDB:
--   ✓ Range scans por PK muy eficientes
--   ✗ Índices secundarios hacen doble travesía
--   ✗ INSERTs con PKs aleatorias causan page splits

-- IOT con "overflow segment" para columnas muy largas:
CREATE TABLE ventas (
    id     NUMBER      PRIMARY KEY,
    precio NUMBER,
    cant   NUMBER,
    desc   CLOB        -- columna muy larga
) ORGANIZATION INDEX
  OVERFLOW TABLESPACE users_data;
-- Las columnas que no caben en la hoja del árbol se almacenan en un
-- heap separado (el "overflow segment"). El B+tree guarda un puntero.
-- Esto mitiga el problema de páginas llenas por filas muy largas.
```

---

#### SQLite: todo es B+tree, sin excepción

SQLite es el caso más radical: no existe el concepto de heap. Toda tabla, en todo caso,
se almacena como un B+tree. No hay configuración para elegir otro modelo.

```
Una base de datos SQLite = UN SOLO ARCHIVO .db:

╔═════════════════════════════════════════════════════════════════════╗
║                       ARCHIVO ÚNICO (.db)                          ║
║                                                                     ║
║  Página 1: Database Header (100 bytes al inicio del archivo)        ║
║  • Tamaño de página (2B), versión de formato (4B), page count (4B)  ║
║  • Encoding: UTF-8 / UTF-16 (4B)                                   ║
║  • Otros metadatos del database                                     ║
║                                                                     ║
║  ┌─────────────────────────────────────────────────────────┐       ║
║  │  B+tree de sqlite_master (el catálogo del sistema)      │       ║
║  │  Contiene: type, name, tbl_name, rootpage, sql          │       ║
║  │  Equivale a pg_class + pg_attribute de PostgreSQL       │       ║
║  │  Vive en páginas del mismo archivo, como cualquier tabla │       ║
║  └─────────────────────────────────────────────────────────┘       ║
║                                                                     ║
║  ┌─────────────────────────────────────────────────────────┐       ║
║  │  B+tree de la tabla "ventas"                            │       ║
║  │  • Páginas internas: (rowid, página_hijo)               │       ║
║  │  • Páginas hoja: (rowid, datos de la fila)              │       ║
║  │  • Hojas enlazadas para range scans                     │       ║
║  └─────────────────────────────────────────────────────────┘       ║
║                                                                     ║
║  ┌─────────────────────────────────────────────────────────┐       ║
║  │  B+tree del índice "ventas_precio_idx"                  │       ║
║  │  • Páginas hoja: (precio, rowid)                        │       ║
║  │  • Igual que índice secundario de InnoDB                │       ║
║  └─────────────────────────────────────────────────────────┘       ║
║                                                                     ║
║  Free pages: rastreadas en una lista enlazada ("freelist")          ║
║  (no hay FSM como PostgreSQL — SQLite usa una freelist simple)      ║
╚═════════════════════════════════════════════════════════════════════╝

Tamaño de página en SQLite:
  Default: 4.096 bytes (4 KB)
  Configurable: 512 B a 65.536 B (potencia de 2 obligatoria)
  Comparación: PostgreSQL 8 KB, InnoDB 16 KB, SQLite 4 KB (default)

  Con 4 KB: más niveles de árbol necesarios para la misma cantidad de datos.
  Para 1M filas de 100B:
    SQLite (4KB):      3–4 niveles (fan-out menor por página más pequeña)
    PostgreSQL (8KB):  3 niveles
    InnoDB (16KB):     2–3 niveles

  Cuándo cambiar el tamaño de página en SQLite (debe hacerse al crear el DB):
    PRAGMA page_size = 8192;  -- antes de cualquier CREATE TABLE
    Páginas más grandes: menos niveles, mejor para reads secuenciales
    Páginas más pequeñas: mejor para bases de datos con filas pequeñas y muchos updates
```

**Las tres variantes de tabla en SQLite**

```
VARIANTE 1: INTEGER PRIMARY KEY (la más común y eficiente)

  CREATE TABLE ventas (id INTEGER PRIMARY KEY, precio INT, cantidad INT);

  → "id" ES el rowid interno de SQLite. No ocupa espacio adicional.
  → El B+tree usa el rowid como clave de clustering.
  → Las hojas del árbol: [rowid=1, precio=1000, cant=5]
                          [rowid=2, precio=25,   cant=1]
                          [rowid=3, precio=75,   cant=2]
  → INSERT siempre al final (rowid es auto-increment): O(log n), casi sin splits
  → SELECT por id: O(log n) — datos en hoja, sin segundo lookup
  → Sin overhead adicional: la PK no duplica espacio

──────────────────────────────────────────────────────────────────────────

VARIANTE 2: PK que no es INTEGER pura (cualquier otro tipo o nombre)

  CREATE TABLE ventas (uuid TEXT PRIMARY KEY, precio INT, cantidad INT);

  → SQLite crea un rowid interno invisible (auto-generado)
  → El árbol principal usa el rowid interno como clave
  → SQLite crea automáticamente un índice ÚNICO sobre "uuid"
  → SELECT por uuid: O(log n) índice único → obtiene rowid
                   + O(log n) árbol principal por rowid
  → DOBLE TRAVESÍA — igual que InnoDB con índice secundario
  → INSERT con uuid TEXT aleatorio: sin page splits en el árbol principal
    (el rowid interno es secuencial), pero el índice de uuid sí se fragmenta

──────────────────────────────────────────────────────────────────────────

VARIANTE 3: WITHOUT ROWID (para PKs compuestas o texto como PK "real")

  CREATE TABLE permisos (
      usuario TEXT,
      recurso TEXT,
      nivel   INT,
      PRIMARY KEY (usuario, recurso)
  ) WITHOUT ROWID;

  → No hay rowid interno. El árbol usa (usuario, recurso) como clave clustering.
  → Las hojas: [(usuario='alice', recurso='db'), nivel=3]
               [(usuario='alice', recurso='web'), nivel=1]
               [(usuario='bob', recurso='db'), nivel=2]
  → SELECT WHERE usuario='alice' AND recurso='db': O(log n) — datos en hoja
  → SELECT WHERE usuario='alice': O(log n) inicio + scan de hojas — eficiente
    porque el árbol está ordenado por (usuario, recurso)
  → Sin doble travesía para la PK compuesta
  → Ideal para tablas de relación M:N donde el acceso siempre es por PK completa

  ¿Cuándo NO usar WITHOUT ROWID?
  → Tablas con filas grandes (el árbol se llena más lento, más niveles)
  → Tablas con muchos UPDATEs de la PK (requiere delete+insert en el árbol)
  → Si necesitas rowid para alguna feature específica de SQLite

Comparación de las tres variantes:
  ╔═══════════════════════════╦════════════════╦═══════════════╦════════════════╗
  ║ Característica            ║ INTEGER PK     ║ TEXT/UUID PK  ║ WITHOUT ROWID  ║
  ╠═══════════════════════════╬════════════════╬═══════════════╬════════════════╣
  ║ INSERT (PK secuencial)    ║ O(log n)       ║ O(log n)×2    ║ O(log n)       ║
  ║ SELECT por PK             ║ O(log n)       ║ O(log n)×2    ║ O(log n)       ║
  ║ SELECT por idx secundario ║ O(log n)×2     ║ O(log n)×3    ║ O(log n)×2     ║
  ║ Overhead de almacenamiento║ Mínimo (PK=row)║ Alto (2 árbs) ║ Bajo (sin row) ║
  ║ Range scan por PK         ║ Secuencial     ║ Secuencial×2  ║ Secuencial     ║
  ╚═══════════════════════════╩════════════════╩═══════════════╩════════════════╝
```

---

### Sección 2.4: DynamoDB y MongoDB: sin heap tradicional {#2-nosql}

---

Los motores relacionales vistos hasta aquí (PostgreSQL, MySQL, SQL Server, Oracle, SQLite)
comparten la misma premisa fundamental: los datos viven en páginas organizadas en un
**heap** o en un **clustered B+Tree**. Los motores NoSQL reemplazan esa premisa.

**DynamoDB (Amazon)**

DynamoDB no expone páginas, heap ni B+Tree al usuario. Internamente usa una arquitectura
basada en **LSM Trees** (Log-Structured Merge Trees) particionados por clave.
El modelo visible al usuario es:

```
Partition key  →  determina en qué nodo viven los datos (sharding automático)
Sort key       →  ordena los datos dentro de cada partición (como clustered index local)
```

No hay heap: no existe la distinción "dato en árbol vs dato en heap". El `partition_key +
sort_key` es el equivalente funcional al clustered index de InnoDB, pero el motor lo
distribuye automáticamente entre nodos sin intervención del DBA. Los índices secundarios
(GSI — Global Secondary Index) son proyecciones replicadas automáticamente, no punteros
a un heap central.

**MongoDB (WiredTiger)**

MongoDB usa el motor WiredTiger, que mantiene un **B+Tree por colección** con el campo
`_id` como clave de clustering. WiredTiger usa un modelo de versionado inspirado en LSM
para escrituras: las modificaciones van primero a un write-ahead log y luego se consolidan
en el árbol (similar al MVCC de PostgreSQL pero sin heap separado).

```
_id (ObjectId por defecto)   →  clave de clustering del B+Tree
Secondary indexes             →  apuntan al _id (no a un ctid de heap)
```

El `_id` cumple el mismo rol que la PK en InnoDB: es el puntero que los índices
secundarios usan para localizar el documento. El ObjectId por defecto es monotónicamente
creciente en el tiempo (incluye timestamp), por lo que los inserts secuenciales no
generan page splits — a diferencia de un UUID v4 aleatorio (ver Bloque 20).

**Comparación con los modelos relacionales**

```
╔═══════════════╦═════════════════════════════════╦══════════════════════════════════════╗
║ Motor         ║ Modelo físico                   ║ Equivalente a…                       ║
╠═══════════════╬═════════════════════════════════╬══════════════════════════════════════╣
║ DynamoDB      ║ LSM Tree particionado           ║ Clustered index sin B+Tree visible   ║
║               ║ (opaco, gestionado por AWS)     ║ + sharding automático                ║
╠═══════════════╬═════════════════════════════════╬══════════════════════════════════════╣
║ MongoDB       ║ B+Tree de documentos BSON       ║ Heap con _id como clustered PK       ║
║ (WiredTiger)  ║ + WAL con escrituras LSM-style  ║ (similar a InnoDB con _id como PK)   ║
╚═══════════════╩═════════════════════════════════╩══════════════════════════════════════╝
```

La abstracción de "página de tamaño fijo" y la elección entre heap y clustered index son
detalles de implementación que estos motores ocultan deliberadamente. No existe una decisión
equivalente a "¿uso UUID o BIGSERIAL como PK?" a nivel de almacenamiento físico — el motor
gestiona particionamiento, compactación y escrituras de forma automática.

> El detalle completo de partición, GSI, sharding y diseño de acceso en DynamoDB y MongoDB
> queda fuera del alcance de este archivo, centrado en motores relacionales.

---

### Sección 2.5: Cuándo la arquitectura de almacenamiento determina la decisión {#2-decision}

Con el entendimiento profundo de cómo cada motor almacena los datos físicamente, estos
son los casos concretos donde la elección tiene impacto directo en producción:

---

#### Caso 1: UUID como PK en MySQL InnoDB — el antipatrón más común

```
Escenario: tabla de pedidos en e-commerce con UUID v4 como PK.
  UUID v4: 7f3a2b1c-4d5e-4f7a-8b9c-0d1e2f3a4b5c (completamente aleatorio)

¿Qué pasa en cada motor con 1.000 inserts/segundo?

POSTGRESQL:
  → El UUID va al heap. El Free Space Map encuentra un hueco en cualquier página.
  → El árbol B+tree del índice UUID sí se fragmenta (inserts aleatorios).
  → El heap en sí no tiene problema — no hay árbol que mantener ordenado.
  → El throughput de INSERT está limitado por WAL + fsync, no por page splits.

MYSQL INNODB con UUID v4:
  → Cada UUID aleatorio debe ir a una posición aleatoria en el árbol clustered.
  → Esa posición está en una página que ya tiene otros UUIDs → probablemente llena.
  → Page split: InnoDB crea página nueva, mueve la mitad de los records, actualiza
    el nodo padre. En el peor caso, el nodo padre también se parte (cascada).
  → Con 1.000 inserts/segundo: potencialmente 1.000 page splits/segundo.
  → El buffer pool se llena de páginas de árbol diferentes (acceso aleatorio).
    Si el árbol no cabe en RAM: cada insert requiere leer la página correcta de disco
    antes de escribir → el throughput de INSERT colapsa.
  → Fragmentación severa: páginas al 50% → el árbol usa 2× el espacio en disco.

Degradación real observada en producción:
  auto_increment INT como PK:   ~50.000–100.000 inserts/segundo (SSD)
  UUID v4 como PK en InnoDB:    ~5.000–20.000 inserts/segundo (5–10× peor)
  UUID v7 como PK en InnoDB:    ~40.000–80.000 inserts/segundo (casi igual al int)

MYSQL INNODB con UUID v7:
  → Los primeros 48 bits son timestamp en ms → los UUIDs del mismo ms son adyacentes
  → Inserts casi siempre van a la última página hoja del árbol → casi sin splits
  → Comportamiento similar a auto_increment INT para el motor de almacenamiento

POSTGRESQL con UUID v4 o v7:
  → No importa el tipo de UUID: el heap no tiene orden
  → El índice B+tree sobre UUID sí se fragmenta (igual en v4 y v7)
  → Pero la tabla heap no sufre page splits
  → UUID v7 ayuda principalmente al rendimiento de range scans por UUID
    (que son raros — normalmente se busca por UUID exacto, no por rango)
```

---

#### Caso 2: Range scan por la clave de clustering — InnoDB y SQL Server ganan

```
Escenario: tabla pedidos con PK = id INT auto-increment.
  Query de reporte: todos los pedidos del Q1 2024.

SELECT * FROM pedidos WHERE fecha BETWEEN '2024-01-01' AND '2024-03-31';
-- Existe un índice en (fecha). La tabla tiene 50M de filas.
-- El query devuelve ~4M filas (el 8% de la tabla).

POSTGRESQL (heap desordenado):
  → Usa Bitmap Index Scan: escanea el índice (fecha), acumula todos los ctids
  → Los 4M ctids se agrupan por número de página del heap
  → PostgreSQL lee las páginas del heap en orden de número de página
  → PERO: las filas del Q1 2024 se insertaron entre otras (el heap es desordenado)
    → Las páginas del heap que tienen filas del Q1 están intercaladas con páginas
       de otras fechas → el planner puede decidir que un sequential scan es mejor
  → Bitmap Heap Scan: ~800K páginas de 8KB si las filas están dispersas en todo el heap
  → Sequential Scan: ~350K páginas (las 50M filas × 36 bytes / 8192 bytes/pág)
  → El planner elige el menor: probablemente sequential scan o bitmap scan

MYSQL INNODB con id como PK (fecha es índice secundario):
  → Índice secundario en fecha: O(log n) → lista de ids para el Q1 2024
  → 4M ids → 4M travesías del clustered index = O(4M × log n) → MUY LENTO
  → InnoDB usa Multi-Range Read (MRR): ordena los 4M ids, los busca en orden
    → convierte accesos aleatorios al clustered en casi-secuenciales
  → Aún así: el clustered index tiene los datos ordenados por id, no por fecha
    → los pedidos del Q1 están dispersos por el árbol (ids no son correlacionados con fechas necesariamente)

MYSQL INNODB con fecha como PK (o como 1ª columna del clustered):
  → O(log n) para encontrar 2024-01-01 en el árbol clustered
  → Las páginas hoja están FÍSICAMENTE ORDENADAS por fecha
  → Los 90 días de pedidos están en páginas CONTIGUAS del árbol
  → Sequential scan de páginas hoja: el SO hace prefetch automático
  → I/Os: ~(4M filas × 36 bytes) / 16384 bytes/pág ≈ 8.800 páginas contiguas
  → ÓPTIMO para este workload

CONCLUSIÓN PARA ESTE CASO:
  Si el workload es range scans por fecha y los datos se insertan en orden temporal:
  → InnoDB con fecha como clustered key = modelo ideal
  → PostgreSQL necesita CLUSTER TABLE (bloqueante, se desactualiza con el tiempo)
    o índice BRIN (muy compacto pero con falsos positivos en pruebas, menos preciso)
```

---

#### Caso 3: Muchos índices secundarios con SELECT de muchas columnas

```
Escenario: tabla productos con 6 índices y queries OLTP que usan distintos filtros.

CREATE TABLE productos (
    id          INT PRIMARY KEY,
    nombre      VARCHAR(200),
    descripcion TEXT,          -- ~500 chars promedio
    precio      DECIMAL(10,2),
    stock       INT,
    categoria   INT,
    marca       INT,
    fecha_alta  DATE
);
-- 6 índices: precio, stock, categoria, marca, fecha_alta, (categoria, precio)
-- 1M de filas

Query típica: SELECT id, nombre, descripcion, precio, stock, marca
              FROM productos WHERE categoria = 5 AND precio < 100;

POSTGRESQL:
  1. Índice (categoria, precio): O(log n) → lista de ctids que cumplen la condición
  2. Para cada ctid: O(1) heap fetch — la página heap YA TIENE todas las columnas
  3. Si devuelve k=5.000 filas: ~5.000 heap fetches
  4. Bitmap Scan optimiza: agrupa ctids por página → quizás 500 páginas únicas
  5. Las páginas heap en el buffer pool: si el buffer pool es suficientemente grande,
     muchas de estas 500 páginas ya están en RAM → I/Os de disco reducidos
  Costo: O(log n) + k × O(1) con posible optimización de Bitmap Scan

MYSQL INNODB:
  1. Índice (categoria, precio): O(log n) → lista de PKs que cumplen la condición
  2. Para cada PK: O(log n) travesía del clustered index para obtener datos completos
     (descripcion y otras columnas no están en el índice secundario)
  3. Si devuelve k=5.000 filas: 5.000 × O(log n) travesías del clustered
  4. Con MRR: ordena los 5.000 PKs y los busca en orden → mejora la localidad
  5. Pero el overhead base sigue siendo O(k log n) vs O(k) de PostgreSQL

  ¿Qué haría un DBA de InnoDB?
  → Crear un covering index: CREATE INDEX idx_cov ON productos (categoria, precio, nombre, stock, marca);
  → "descripcion" es TEXT largo → NO ponerla en el índice (duplicaría 500 chars × 1M filas)
  → Si el query NECESITA descripcion: no hay escapatoria — doble travesía inevitable en InnoDB
  → Si el query NO necesita descripcion: covering index lo resuelve en O(log n)

  Para este escenario (descripcion necesaria):
  PostgreSQL tiene ventaja estructural: O(k × 1) vs InnoDB O(k × log n)
  La diferencia crece con k (número de filas devueltas) y con la profundidad del árbol.
```

---

#### Caso 4: Write-heavy con inserts masivos y workload de análisis posterior

```
Escenario: pipeline de eventos — 500.000 inserts/segundo, luego queries de análisis
  en lote (no en tiempo real). ID de evento es UUID aleatorio.

POSTGRESQL:
  INSERTs:
  → UUID va al heap: O(1) amortizado, el FSM encuentra hueco en RAM
  → WAL es el bottleneck real: 500K × 36B = 18 MB/s solo en datos
  → Los índices del UUID sí se fragmentan, pero si no hay reads por UUID: no importa
  → Se puede usar UNLOGGED TABLE para eliminar WAL overhead en staging:
    CREATE UNLOGGED TABLE eventos_staging (...);  -- sin durabilidad, máximo throughput

  Análisis posterior:
  → Full table scan de todo el heap: O(n) secuencial
  → Si el análisis es por timestamp: índice BRIN sobre timestamp (muy compacto)
    BRIN índice: 1 entrada por rango de páginas (default 128 páginas)
    Para 50M filas: un BRIN de solo ~3.200 entradas (vs B+tree de 50M entradas)
    Funciona bien si los datos se insertaron en orden temporal (lo cual es común en eventos)

MYSQL INNODB con UUID como PK:
  INSERTs:
  → UUID aleatorio: page splits constantes (ya explicado en Caso 1)
  → A 500K inserts/segundo: el motor colapsa bajo la presión de los page splits
  → NO recomendado para este workload con UUID

MYSQL INNODB con auto_increment como PK:
  INSERTs:
  → Append al final del árbol: O(log n) eficiente, casi sin splits
  → Throughput comparable a PostgreSQL para inserts secuenciales
  → El UUID del evento puede ser columna separada con índice secundario

  Análisis posterior:
  → Full table scan de las hojas del árbol: O(n) secuencial — igual que PostgreSQL
  → Si el análisis es por timestamp: índice secundario en timestamp + MRR
    El árbol tiene los eventos en orden de id (auto-increment ≈ orden temporal)
    → Los eventos del mismo día están cerca en el árbol → MRR eficiente

CONCLUSIÓN: Para este workload, ambos motores son comparables si InnoDB usa
auto_increment. PostgreSQL tiene ligera ventaja en inserts completamente aleatorios.
```

---

#### Tabla resumen definitiva: cuándo cada modelo gana

```
╔════════════════════════════════════════╦══════════════════════════╦════════════════════════════════╗
║ Workload dominante                     ║ Modelo óptimo            ║ Motor(es) recomendados         ║
╠════════════════════════════════════════╬══════════════════════════╬════════════════════════════════╣
║ Range scans frecuentes por PK          ║ Clustered index          ║ InnoDB (fecha/id como PK)      ║
║ (reportes por fecha, ID secuencial)    ║ → datos co-localizados   ║ SQL Server (clustered idx)     ║
║                                        ║   en páginas adyacentes  ║ Oracle IOT                     ║
╠════════════════════════════════════════╬══════════════════════════╬════════════════════════════════╣
║ Inserts masivos con PK aleatoria       ║ Heap                     ║ PostgreSQL                     ║
║ (UUID v4, hashes, claves distribuidas) ║ → sin page splits        ║ SQL Server (sin clustered)     ║
║                                        ║   en el almacenamiento   ║ Oracle (heap default)          ║
╠════════════════════════════════════════╬══════════════════════════╬════════════════════════════════╣
║ Muchos índices + SELECT con columnas   ║ Heap                     ║ PostgreSQL                     ║
║ no cubiertas (texto largo, blobs, etc) ║ → heap fetch O(1)        ║ (heap fetch cuesta O(1))       ║
║                                        ║   vs O(log n) en InnoDB  ║                                ║
╠════════════════════════════════════════╬══════════════════════════╬════════════════════════════════╣
║ OLTP con PK secuencial y reads por PK  ║ Clustered index          ║ InnoDB (auto_increment)        ║
║ (la query más común: SELECT por PK)    ║ → dato en hoja, sin      ║ SQLite (INTEGER PK)            ║
║                                        ║   segundo lookup         ║ SQL Server (clustered)         ║
╠════════════════════════════════════════╬══════════════════════════╬════════════════════════════════╣
║ Covering index factible                ║ Clustered index          ║ InnoDB (el beneficio es        ║
║ (todas las cols del query en el índice)║ → 2ª travesía evitada    ║ mayor que en PostgreSQL)       ║
║                                        ║   es O(log n) en InnoDB  ║ SQL Server (covering idx)      ║
╠════════════════════════════════════════╬══════════════════════════╬════════════════════════════════╣
║ Base de datos embebida, acceso por PK  ║ B+tree siempre           ║ SQLite (INTEGER PK)            ║
║ (móvil, desktop, cache local)          ║ → SQLite no da opción    ║ SQLite (WITHOUT ROWID para PK  ║
║                                        ║   — siempre clustered    ║ compuesta)                     ║
╠════════════════════════════════════════╬══════════════════════════╬════════════════════════════════╣
║ Carga mixta (OLTP + reporting)         ║ Depende del balance      ║ PostgreSQL (más flexible:      ║
║ muchos patrones de acceso distintos    ║ → heap es más versátil   ║ partial indexes, BRIN, etc.)   ║
║                                        ║   para acceso variado    ║                                ║
╠════════════════════════════════════════╬══════════════════════════╬════════════════════════════════╣
║ Análisis columnar (OLAP puro)          ║ Column store             ║ Ver Bloque 3 de esta guía       ║
║ (agregaciones sobre muchas filas,      ║ → ni heap ni clustered   ║ (Redshift, BigQuery, etc.)     ║
║ pocas columnas del SELECT)             ║   son el modelo correcto ║                                ║
╚════════════════════════════════════════╩══════════════════════════╩════════════════════════════════╝


El principio que une todo:

  Clustered index = pagas en el momento de escribir (mantener el orden del árbol,
  gestionar page splits) para obtener localidad de datos en el momento de leer.
  El bet es: "el patrón de lectura es predecible y se alinea con el orden de clustering".

  Heap = pagas en el momento de leer (heap fetches potencialmente aleatorios) para
  obtener escrituras sin restricciones de orden. El bet es: "el patrón de acceso es
  variado e impredecible, o los writes son tan frecuentes que el orden del árbol sería
  demasiado costoso de mantener".

  La elección incorrecta en producción típicamente no se nota en desarrollo
  (pocos datos, todo en RAM) pero emerge bajo carga real cuando el árbol deja
  de caber en el buffer pool y los page splits / heap fetches aleatorios empiezan
  a saturar el subsistema de I/O.
```

---

### Sección 2.6: Síntesis visual: arquitectura de almacenamiento por motor {#2-sintesis}

El diagrama siguiente consolida todo lo visto en Bloque 1 y Bloque 2: los conceptos de almacenamiento son
universales, pero cada motor los implementa de forma distinta. Sirve como mapa de
referencia después de leer la sección en detalle, y como punto de entrada rápida para
recordar dónde vive cada concepto en cada motor.

```
╔══════════════════════════════════════════════════════════════════════════════════╗
║  CAPA UNIVERSAL — lo que ve todo usuario de cualquier motor                     ║
║                                                                                  ║
║  SQL:  SELECT nombre FROM empleados WHERE id = 42;                               ║
║        INSERT INTO pedidos VALUES (7, 200, 3);                                   ║
║        UPDATE productos SET stock = stock - 1 WHERE id = 99;                    ║
╚═══════════════════════════════════════╦════════════════════════════════════════════╝
                                        │
                                        ▼
╔══════════════════════════════════════════════════════════════════════════════════╗
║  BUFFER POOL — universal                                                         ║
║  El motor trabaja con PÁGINAS en RAM, nunca con bytes sueltos de disco          ║
║  Una página = unidad mínima de I/O                                               ║
║                                                                                  ║
║  PostgreSQL  MySQL InnoDB  SQL Server   Oracle    SQLite   DynamoDB   MongoDB   ║
║    8 KB         16 KB         8 KB       8 KB      4 KB    (opaco)     4 KB     ║
╚════════╦══════════════════════════════════════════════════════════════╦══════════╝
         │                                                              │
         │  ¿cómo están organizadas las páginas en disco?              │
         │                                                              │
   ┌─────┴─────────────────┐                      ┌────────────────────┴───────┐
   │   MODELO A: HEAP       │                      │  MODELO B: CLUSTERED INDEX │
   │   dato y árbol son     │                      │  datos = hojas del árbol   │
   │   estructuras DISTINTAS│                      │  árbol = tabla + índice PK │
   │                        │                      │                            │
   │  heap file (disco)     │                      │    ┌─[raíz]─┐             │
   │  ┌───────┬───────┬──┐  │                      │   [nodo]  [nodo]           │
   │  │ pág 0 │ pág 1 │..│  │                      │  [hoja] [hoja] [hoja]      │
   │  │[3,7,1]│[8,2,5]│..│  │                      │   1,Ana  51,Bo  101,C      │
   │  └───────┴───────┴──┘  │                      │   2,Eva  52,El  102,D      │
   │    ↑ datos en           │                      │         ↑ datos EN las      │
   │    cualquier orden      │                      │         hojas del árbol     │
   │                        │                      │                            │
   │  Índice B+Tree externo │                      │  Índice secundario         │
   │  id=42 → ctid=(3, 7)   │                      │  nombre='Ana' → PK=1       │
   │  ctid → heap page 3    │                      │  PK=1 → hoja del B+Tree    │
   │  slot 7 → bytes de fila│                      │                            │
   │                        │                      │  SELECT por PK: 1 travesía │
   │  2 saltos: B+Tree +    │                      │  SELECT por idx sec: 2 tra.│
   │  heap fetch (puede      │                      │  INSERT PK rand.: splits++ │
   │  ser I/O aleatorio)    │                      │  INSERT PK sec.: O(log n)  │
   └────────────┬───────────┘                      └──────────────┬─────────────┘
                │                                                  │
   PostgreSQL   │                              MySQL InnoDB        │
   SQL Server   │                              SQL Server + CI     │
    (sin CI)    │                              Oracle IOT          │
   Oracle heap  │                              SQLite (siempre)    │
                │                                                  │
                └─────────────────────┬────────────────────────────┘
                                      │
                                      ▼
              ┌──────────────────────────────────────────────────┐
              │  MODELO C: NoSQL / DISTRIBUIDO                   │
              │  (sin heap ni B+Tree visible al usuario)         │
              │                                                  │
              │  DynamoDB:                                       │
              │  ┌─────────────────────────────────────────┐    │
              │  │ [part A: sk 1–100] [part B: sk 101–200] │    │
              │  └─────────────────────────────────────────┘    │
              │  LSM Tree por partición — escrituras rápidas    │
              │  partition_key → nodo físico (sharding auto)    │
              │  sort_key → orden dentro del nodo               │
              │                                                  │
              │  MongoDB (WiredTiger):                           │
              │  B+Tree por colección, _id = clustering key     │
              │  escrituras vía WAL (compacta a B+Tree)         │
              │  idx secundario → _id → hoja del B+Tree         │
              └──────────────────────────────────────────────────┘
```

**Referencia rápida, el mismo concepto en cada motor**

| Concepto                    | PostgreSQL       | InnoDB               | SQL Server     | Oracle            | SQLite              | DynamoDB             | MongoDB (WiredTiger)         |
| --------------------------- | ---------------- | -------------------- | -------------- | ----------------- | ------------------- | -------------------- | ---------------------------- |
| Modelo base                 | heap file        | clustered            | heap o CI      | heap o IOT        | B+Tree              | LSM (opaco)          | B+Tree (`_id`)               |
| Página default              | 8 KB             | 16 KB                | 8 KB           | 8 KB (config.)    | 4 KB                | opaco                | 4 KB                         |
| Orden de los datos          | ninguno          | por PK               | ninguno o CI   | ninguno/IOT       | por rowid/PK        | partition + sort key | por valor de `_id`           |
| Dirección física de fila    | ctid (pág, slot) | PK value             | RID o CI key   | ROWID             | rowid (int64)       | opaco                | valor de `_id`               |
| Índice secundario → ?       | ctid             | PK value             | RID o CI key   | ROWID             | rowid               | campo del GSI        | valor de `_id`               |
| INSERT con PK aleatoria     | O(1), heap       | O(log n) + splits    | O(log n) si CI | O(1), heap        | O(log n)            | O(log n), LSM        | splits si `_id` es aleatorio |
| Range scan por PK           | seq scan         | O(log n), hojas      | O(log n) si CI | O(log n), IOT     | O(log n), hojas     | scan por sort key    | O(log n), B+Tree             |
| Versiones viejas (MVCC)     | en el heap       | undo log             | version store  | undo segments     | WAL + snapshot      | opaco                | WAL (opaco)                  |
| Limpiar versiones viejas    | VACUUM           | purge thread         | ghost cleanup  | retención de UNDO | checkpoint          | automático           | automático                   |
| Reserva para UPDATEs        | fill factor      | 15/16 de página      | fillfactor     | PCTFREE 10%       | no configurable     | no aplica            | no aplica                    |
| Overflow de valores grandes | TOAST            | BLOB fuera de página | row overflow   | row chaining      | páginas de overflow | ítem ≤ 400 KB        | documento BSON ≤ 16 MB       |

---

## Bloque 3: Row-Based vs Column-Based: OLTP vs OLAP {#3-row-vs-column}

Todo lo que vimos en Bloque 1 y Bloque 2 — páginas de 8 KB, tuples con header MVCC, distintas
arquitecturas de almacenamiento por motor — describe la implementación del **row-store**:
el diseño donde una fila es la unidad física en disco. La pregunta que surge naturalmente
es: ¿ese diseño era obligatorio? ¿Qué pasa si la unidad física es la columna en vez de la fila?

### Sección 3.1: Nota sobre los diagramas de esta sección {#3-nota-diagramas}

Verás notación como `[id │ precio │ cantidad]` o `[01 00 00 00 │ E8 03 00 00]`. El símbolo `│`
es solo un **separador visual** para que puedas distinguir dónde termina un campo y empieza el
otro. **No es un byte real** en el archivo — los datos en disco no tienen separadores entre
columnas. El motor sabe los límites de cada campo por el esquema de la tabla (igual que vimos
en Bloque 1 con el `t_hoff` y los tamaños fijos por tipo).

---

### Sección 3.2: El modelo de Bloque 1 y Bloque 2 tiene nombre: row-store {#3-row-store}

El modelo que Bloque 1 y Bloque 2 describen — heap file, páginas de 8 KB, tuples con header MVCC,
distintas arquitecturas por motor — es lo que la industria llama **row-store** (almacenamiento por
fila): la fila completa es la unidad física en disco.

Dos términos que dan nombre a esta sección y que se usan en todo el sector:

- **OLTP** (Online Transaction Processing): muchas operaciones pequeñas sobre filas individuales
  — crear un pedido, actualizar un saldo, buscar el perfil de un usuario. Pocas filas por
  operación, muchas operaciones concurrentes.

- **OLAP** (Online Analytical Processing): pocas queries pero cada una escanea millones de
  filas para calcular agregaciones — ventas totales por mes, tasa de conversión por región,
  percentil de latencia. Muchas filas por query, pocas columnas necesarias.

La pregunta de esta sección: ¿qué pasa si la unidad física no es la fila sino la columna?

---

### Sección 3.3: La idea central: agrupar por fila vs. agrupar por columna {#3-idea-central}

Antes de los bytes exactos, entiende el principio con la misma tabla de 4 filas:

```
           id    precio  cantidad
         ┌────┬────────┬──────────┐
  fila 0 │  1 │  1000  │    1     │ ══════ ROW-STORE: estos 12 bytes van juntos
  fila 1 │  2 │    25  │    3     │ ══════ en disco. La fila entera es la unidad
  fila 2 │  3 │    75  │    2     │ ══════ física. Leer cualquier columna obliga
  fila 3 │  4 │   350  │    1     │ ══════ a cargar toda la fila.
         └────┴────────┴──────────┘
            ║       ║        ║
         id.col  precio.col  cantidad.col
            ║       ║        ║
  COLUMN-STORE: cada columna vive en su propio archivo.
  Los 4 precios (1000, 25, 75, 350) están todos juntos en precio.col.
  Para leer solo precios, id.col y cantidad.col no se tocan en absoluto.
```

Row-store agrupa **horizontalmente**: los campos de una fila son la unidad.
Column-store agrupa **verticalmente**: los valores de una columna son la unidad.

---

### Sección 3.4: Ambos modelos usan archivos divididos en bloques — lo que cambia es el contenido {#3-archivos-bloques}

En ambos modelos la estructura en disco es la misma: **archivos divididos en bloques**.
El motor nunca lee bytes sueltos — siempre carga un bloque completo a RAM. Lo que
cambia entre row-store y column-store no es esa estructura, sino cuántos archivos
existen y qué contiene cada bloque:

```
ROW-STORE
  Archivo: heap file — 1 por tabla   (/data/base/16384/ventas)
  Bloque:  página — 8 KB, contiene ~170 filas con TODAS sus columnas

  heap file
  ├── página 0 (bytes 0–8191):       [fila0: id+precio+cantidad][fila1: id+precio+cantidad]...
  ├── página 1 (bytes 8192–16383):   [fila171: id+precio+cantidad]...
  └── ...

  Para leer un dato: el motor abre el heap file, carga la página que
  contiene esa fila y lee los bytes de la columna que necesita dentro
  de esa página. Aunque solo necesite 'precio', carga la página entera.


COLUMN-STORE
  Archivo: archivo de columna — 1 por columna   (precio.col, id.col, cantidad.col...)
  Bloque:  bloque de columna — ~256 KB, contiene ~65 000 valores de UNA sola columna

  precio.col          id.col          cantidad.col
  ├── bloque 0        ├── bloque 0    ├── bloque 0
  │   filas 0–65535   │   filas 0–65535   filas 0–65535
  ├── bloque 1        ├── bloque 1    ├── bloque 1
  └── ...             └── ...         └── ...

  Para leer 'precio': el motor abre precio.col y carga sus bloques.
  id.col y cantidad.col no existen para esa query.
```

"Página" y "bloque de columna" son el mismo concepto — el bloque que el motor carga a RAM —
aplicado a modelos distintos. Lo único diferente es su contenido:

```
              Archivo              Bloque de I/O (lo que carga a RAM)
Row-store:    heap file (1/tabla)  página: filas completas (todas las columnas)
Column-store: por columna (k/tab)  bloque de columna: valores de 1 sola columna
```

Eso explica directamente por qué la tabla comparativa más adelante dice
"O(1) páginas" para row-store y "O(k) archivos de columna" para column-store en
operaciones de escritura:

```
UPDATE una fila en row-store:
  El motor abre el heap file, localiza la página que contiene esa fila,
  modifica los bytes en RAM y marca la página como dirty.
  → 1 heap file tocado, 1 página afectada

UPDATE una fila en column-store:
  El motor abre CADA archivo de columna por separado, localiza el
  bloque de columna que contiene esa posición de fila, y lo modifica.
  → precio.col:    abrir → localizar bloque de columna → modificar
  → id.col:        abrir → localizar bloque de columna → modificar
  → cantidad.col:  abrir → localizar bloque de columna → modificar
  → k archivos de columna tocados, k bloques de columna afectados
```

El trabajo por bloque es el mismo en los dos casos — la diferencia es que
en column-store hay que repetirlo k veces en k archivos de columna distintos.

---

### Sección 3.5: Qué es `precio.col`: el bloque de columna, equivalente de la página {#3-bloque-columna}

`precio.col` no es un archivo plano de bytes sin estructura. Está dividido en **bloques de
columna** (llamados "granule" en ClickHouse, "row group" en Parquet, "stripe" en ORC). Cada
bloque contiene los valores de UNA sola columna para un rango de filas:

```
precio.col — organizado en bloques de columna:

  ┌──────────────────────────────────────────────────────────────────┐
  │  BLOQUE 0  (filas 0 – 65,535)                                    │
  │  Header: tipo=INT4, filas=65536, compresión=LZ4                  │
  ├──────────────────────────────────────────────────────────────────┤
  │  E8 03 00 00 │ 19 00 00 00 │ 4B 00 00 00 │ 5E 01 00 00 │ ...    │
  │  precio=1000   precio=25    precio=75     precio=350             │
  │  (256 KB sin comprimir → ~50 KB comprimido con LZ4)             │
  └──────────────────────────────────────────────────────────────────┘
  ┌──────────────────────────────────────────────────────────────────┐
  │  BLOQUE 1  (filas 65,536 – 131,071)  ...                        │
  └──────────────────────────────────────────────────────────────────┘
```

El motor de column-store tiene su propio caché equivalente al buffer pool de row-store, pero en
lugar de cargar páginas (8 KB de filas completas) carga **bloques de columna** de `precio.col`.
Cuando ejecuta `SELECT SUM(precio)`, ese caché nunca toca `id.col` ni `cantidad.col`.

---

### Sección 3.6: Cómo se ven los mismos datos en disco: los bytes reales {#3-bytes-reales}

Vamos a usar esta tabla con 4 filas y 3 columnas INT para que los bytes sean fáciles de leer:

```
CREATE TABLE ventas (id INT, precio INT, cantidad INT);

  id=1, precio=1000, cantidad=1
  id=2, precio=25,   cantidad=3
  id=3, precio=75,   cantidad=2
  id=4, precio=350,  cantidad=1
```

---

#### En row-store (PostgreSQL, MySQL): un heap file, datos entrelazados

```
Offset  Bytes en disco          Qué contienen
──────  ──────────────────────  ──────────────────────────────────────────────
        ┌─ TUPLE 0 (fila id=1, 12 bytes) ──────────────────────────────────┐
0       │  01 00 00 00           id = 1         (INT4, little-endian)       │
4       │  E8 03 00 00           precio = 1000  (0x3E8 = 1000)              │
8       │  01 00 00 00           cantidad = 1                               │
        └──────────────────────────────────────────────────────────────────┘
        ┌─ TUPLE 1 (fila id=2, empieza justo después, en el byte 12) ──────┐
12      │  02 00 00 00           id = 2                                     │
16      │  19 00 00 00           precio = 25    (0x19 = 25)                 │
20      │  03 00 00 00           cantidad = 3                               │
        └──────────────────────────────────────────────────────────────────┘
        ┌─ TUPLE 2 (fila id=3) ─────────────────────────────────────────────┐
24      │  03 00 00 00           id = 3                                     │
28      │  4B 00 00 00           precio = 75    (0x4B = 75)                 │
32      │  02 00 00 00           cantidad = 2                               │
        └──────────────────────────────────────────────────────────────────┘
36      ... TUPLE 3 (fila id=4): 04 00 00 00 │ 5E 01 00 00 │ 01 00 00 00
```

Los bytes de `precio` están en los offsets **4, 16, 28, 40** — diseminados cada 12 bytes,
separados por los bytes de `id` y `cantidad` que no necesitas si solo quieres sumar precios.

---

#### En column-store (ClickHouse, DuckDB, Redshift): tres archivos, uno por columna

```
── Archivo id.col ───────────────────────────────────────────────────────────
Offset  Bytes               Qué contienen
──────  ──────────────────  ─────────────────────────────────────────────────
0       01 00 00 00         id de fila 0 = 1
4       02 00 00 00         id de fila 1 = 2
8       03 00 00 00         id de fila 2 = 3
12      04 00 00 00         id de fila 3 = 4

── Archivo precio.col ───────────────────────────────────────────────────────
Offset  Bytes               Qué contienen
──────  ──────────────────  ─────────────────────────────────────────────────
0       E8 03 00 00         precio de fila 0 = 1000
4       19 00 00 00         precio de fila 1 = 25
8       4B 00 00 00         precio de fila 2 = 75
12      5E 01 00 00         precio de fila 3 = 350   (0x15E = 350)

── Archivo cantidad.col ─────────────────────────────────────────────────────
Offset  Bytes               Qué contienen
──────  ──────────────────  ─────────────────────────────────────────────────
0       01 00 00 00         cantidad de fila 0 = 1
4       03 00 00 00         cantidad de fila 1 = 3
8       02 00 00 00         cantidad de fila 2 = 2
12      01 00 00 00         cantidad de fila 3 = 1
```

Los bytes de `precio` están en los offsets **0, 4, 8, 12** de `precio.col` — todos juntos,
sin ningún otro dato entre ellos. Para leer todos los precios el motor lee ese archivo de
principio a fin: cero desperdicio.

**Esta es la diferencia fundamental: el mismo dato, reorganizado físicamente de forma distinta.**

Para tipos de ancho fijo (INT, FLOAT8), acceder al valor de la fila `i` dentro de un bloque
es O(1) — exactamente como indexar un array:

```
precio[i] = leer 4 bytes en el offset (i × 4) dentro del bloque   → O(1)
```

Para TEXT y VARCHAR (ancho variable), el bloque de columna tiene un array de offsets — el
mismo concepto que el item pointer array de la página heap, pero solo para esa columna:

```
producto.offsets: [0, 6, 11, 18, ...]   ← dónde empieza cada string en el bloque de datos
producto.data:    [Laptop│Mouse│Teclado│Monitor│...]

producto[1] → offsets[1]=6, offsets[2]=11 → leer bytes 6..10 → "Mouse"   O(1)
```

---

### Sección 3.7: Cómo accede el motor a esos bytes en cada caso {#3-acceso-bytes}

---

#### Operación 1: `SELECT SUM(precio) FROM ventas` — la fortaleza del column-store

**En row-store** el motor no tiene forma de saltar directamente a los bytes de `precio`.
Las páginas contienen filas completas. El proceso es:

```
1. Cargar página 0 en el buffer pool (8 KB completos desde disco)

2. Iterar por cada slot del item pointer array:
   (slot = entrada del array que guarda el offset y longitud de cada tuple dentro de la página)
   slot[0] → offset=7972, len=12 → leer los 12 bytes de TUPLE 0
             bytes leídos: 01 00 00 00 │ E8 03 00 00 │ 01 00 00 00
             bytes usados: solo E8 03 00 00 (precio=1000) — los otros 8 se descartan

   slot[1] → offset=7960, len=12 → leer los 12 bytes de TUPLE 1
             bytes leídos: 02 00 00 00 │ 19 00 00 00 │ 03 00 00 00
             bytes usados: solo 19 00 00 00 (precio=25)

   slot[2] → ... (mismo proceso)
   slot[3] → ...

3. Suma acumulada: 1000 + 25 + 75 + 350 = 1450

Bytes leídos:  4 filas × 12 bytes = 48 bytes
Bytes usados:  4 filas ×  4 bytes = 16 bytes
Eficiencia:    33%  ← 67% del I/O fue trabajo inútil
```

Con 1 millón de filas y una tabla de 20 columnas INT (80 bytes por fila):

- Row-store lee: 80 MB — y usa: 4 MB (5% de utilización)

**En column-store** el motor trabaja sobre bloques de `precio.col` — sin tocar los otros archivos:

```
1. Localizar los bloques de columna de precio.col que cubren el rango de filas pedido
2. Cargar esos bloques en el caché del motor (equivalente al buffer pool, pero solo
   para precio.col — id.col y cantidad.col no se cargan en ningún momento)
3. Procesar cada bloque secuencialmente:
   offset 0:  E8 03 00 00 → precio=1000 → acumular
   offset 4:  19 00 00 00 → precio=25   → acumular
   offset 8:  4B 00 00 00 → precio=75   → acumular
   offset 12: 5E 01 00 00 → precio=350  → acumular
4. Resultado: 1450

Bytes cargados del disco: solo los bloques de precio.col
Bytes cargados en row-store equivalente: los 8 KB de la página, con todas las columnas
```

Con 1 millón de filas y la misma tabla de 20 columnas: column-store lee 4 MB (solo precios).
La diferencia es **20× menos I/O** — y eso se convierte directamente en 20× más velocidad para
la query.

---

#### Operación 2: `SELECT * FROM ventas WHERE id = 2` — la fortaleza del row-store

**En row-store** el B+tree sobre `id` devuelve el ctid, y el motor va directo a esa fila:

```
1. B+tree: id=2 → ctid = (página 0, slot 1)              O(log n)
2. Cargar página 0 si no está en buffer pool              O(1) páginas
3. Item pointer array: slot[1] → offset=7960, len=12      O(1)
4. Leer los 12 bytes en ese offset:
   02 00 00 00 │ 19 00 00 00 │ 03 00 00 00
   id=2          precio=25     cantidad=3
   → fila completa reconstruida en una sola lectura

Seeks a disco: 1 (o 0 si la página ya estaba en buffer pool)
Bytes leídos:  12 bytes → todos útiles
```

**En column-store** el mismo dato está distribuido en 3 archivos. El motor puede hacer
esto porque los archivos de columna están **alineados por posición**: la posición 1 en
`id.col`, la posición 1 en `precio.col` y la posición 1 en `cantidad.col` siempre
corresponden a la misma fila lógica. Es un contrato que el motor mantiene al insertar:
cada columna recibe el valor en la misma posición ordinal que las demás.

```
1. Índice: id=2 está en la posición lógica 1 (segunda fila insertada)  O(log n)
2. Abrir id.col       → seek al offset 1×4=4 → leer 4 bytes → id=2
3. Abrir precio.col   → seek al offset 1×4=4 → leer 4 bytes → precio=25
4. Abrir cantidad.col → seek al offset 1×4=4 → leer 4 bytes → cantidad=3

Seeks a disco: 3 (uno por columna, misma posición en cada archivo)
Bytes leídos:  12 bytes → todos útiles, pero con 3× el costo de posicionamiento
```

Para una tabla con 50 columnas: column-store necesita 50 seeks para reconstruir una sola fila.
Cada seek en SSD cuesta ~0.1 ms → 50 seeks = 5 ms solo en posicionamiento, sin contar el
tiempo de lectura. En row-store: 1 seek = 0.1 ms total.

---

#### Operación 3: `INSERT` y `UPDATE` — por qué el column-store sufre en escrituras

**En row-store** insertar una fila nueva es agregar una tuple a una página con espacio libre:

```
INSERT INTO ventas VALUES (5, 499, 2);

  → motor busca una página con espacio suficiente
  → escribe los 12 bytes al final de esa página
  → actualiza el slot del item pointer array
  → marca la página como dirty

Páginas modificadas: 1
Archivos tocados:    1 (el heap file)
```

**En column-store** la misma inserción toca todos los archivos de columna:

```
INSERT INTO ventas VALUES (5, 499, 2);

  → agregar id=5      al final de id.col       (1 write)
  → agregar precio=499 al final de precio.col  (1 write)
  → agregar cantidad=2 al final de cantidad.col (1 write)

Archivos tocados: 3 (uno por columna) — con 50 columnas serían 50 writes
```

Por esto los motores columnares suelen ser **append-only** y procesan las escrituras en
lotes (batches): acumulan miles de filas nuevas en memoria y las vuelcan todas juntas.
Los sistemas de analítica (Redshift, ClickHouse) están diseñados para ingestar datos en
bulk, no para INSERTs o UPDATEs individuales de una sola fila.

---

### Sección 3.8: Por qué la compresión es entre 5× y 20× mejor en column-store {#3-compresion}

La clave es que en un bloque columnar todos los valores son del mismo tipo y contexto semántico.
Los algoritmos de compresión explotan repetición y similitud:

```
Columna 'pais' con 1M de filas (baja cardinalidad — pocos valores distintos):
  Datos raw: [MX,MX,MX,MX,US,MX,MX,US,MX,MX,MX,US,...]
  → Dictionary encoding: {MX→0, US→1} + bitarray [0,0,0,0,1,0,0,1,0,0,0,1,...]
  → En lugar de guardar strings, guarda bits — 1M strings de 2 bytes → 125 KB de bits

Columna 'precio' con variaciones pequeñas:
  Datos raw: [999, 999, 1000, 998, 999, 1001, 999, 998, ...]
  → Delta encoding: guardar [999, 0, +1, -2, +1, +2, -2, -1, ...]
  → Los deltas son pequeños → caben en 1 byte en vez de 4 → compresión 4×

En row-store, la misma columna 'pais' está intercalada con otros tipos:
  [8B int][8B timestamp][20B texto][... 'MX' ...][4B int][8B int][8B timestamp][... 'MX' ...]
  → El compresor nunca ve dos valores consecutivos de 'pais' — los patrones están rotos
```

Además los CPUs modernos tienen instrucciones SIMD (AVX2, AVX-512) que procesan múltiples
valores del mismo tipo en paralelo. Con column-store:

```
Sumar 4 precios INT4 con AVX2:
  carga:   [1000][25][75][350]  ← 4 valores en un solo registro de 128 bits
  operación: SIMD ADD           ← suma los 4 en un solo ciclo de CPU
  resultado: 1450

Con row-store los valores no son contiguos → no se puede aplicar SIMD directamente
```

---

### Sección 3.9: Por qué los motores tomaron decisiones de diseño distintas {#3-decisiones-diseno}

Esto no fue una elección arbitraria — cada motor fue construido para un problema diferente:

**Row-store (PostgreSQL 1986, MySQL 1995, SQL Server 1989):**

- Estos motores se diseñaron cuando el caso de uso dominante era OLTP: aplicaciones con
  muchos usuarios haciendo operaciones individuales (pedidos, pagos, sesiones).
- El modelo de MVCC con `xmin`/`xmax` por tuple (que vimos en Bloque 1) encaja perfectamente con
  row-store: cada fila tiene su propia versión, sus propios locks, su propio ciclo de vida.
- Una transacción que actualiza 1 fila toca 1 página — mínimo impacto en el resto del sistema.
- Construir MVCC columnar requeriría coordinar versiones de la misma fila lógica entre 50
  archivos distintos — complejísimo y más lento para el caso OLTP.

**Column-store (Vertica 2005, Redshift 2012, ClickHouse 2016, DuckDB 2019):**

- Surgieron cuando las empresas empezaron a tener terabytes de datos de eventos, logs, ventas
  históricas, y necesitaban hacer queries analíticas (SUM, COUNT, AVG, GROUP BY) sobre ellos.
- En analytics los datos casi nunca se actualizan — se insertan en bulk y se leen muchas veces.
  Sin UPDATEs concurrentes, el MVCC por fila no es necesario: puedes simplificar mucho.
- La compresión no es solo un ahorro de espacio — es una reducción directa del I/O, que es el
  cuello de botella principal en queries OLAP sobre terabytes.
- Los procesadores modernos con SIMD fueron diseñados para operar sobre arrays de datos del
  mismo tipo (el caso de uso de la simulación científica, ML, señales digitales) — column-store
  los aprovecha directamente.

**Por qué PostgreSQL no puede "simplemente" añadir column-store:**

- Todo el código de VACUUM, WAL, MVCC, locks, y recuperación de crashes está escrito asumiendo
  que una fila es un bloque contiguo de bytes en una página heap.
- Cambiar ese supuesto requeriría reescribir el motor desde cero.
- La extensión `Citus columnar` existe pero es un hack significativo — no es column-store nativo.

---

### Sección 3.10: Tabla comparativa de costos {#3-tabla-costos}

```
Operación                  Row-store              Column-store
─────────────────────────  ─────────────────────  ────────────────────────────────
Point lookup (PK)          O(log n) páginas       O(log n) + O(k) seeks
                                                  k = columnas seleccionadas
Full column scan           O(n × row_bytes)       O(n × col_bytes)  ← mucho menor
  (SUM, COUNT, AVG)          lee todas las cols     lee solo 1 col
Full row scan (SELECT *)   O(n × row_bytes)       O(n × k × col_bytes)
UPDATE 1 fila              O(1) páginas           O(k) archivos
INSERT 1 fila              O(1) páginas           O(k) archivos (por eso van en lotes)
Compresión                 moderada (2-3×)        alta (5-20× típico)
Vectorización SIMD         difícil (tipos mixtos) natural (un tipo por bloque)
Transacciones OLTP         excelente (MVCC/fila)  complicado (MVCC/columna es caro)
─────────────────────────────────────────────────────────────────────────────────
```

> **Nota — transacciones en column-store:** "complicado" no significa imposible. Los motores
> columnares sí soportan ACID, pero con estrategias distintas al xmin/xmax por tuple de
> PostgreSQL: ClickHouse usa append-only + bitmap de eliminación; Redshift y SQL Server
> mantienen un delta store en row-store para escrituras recientes que luego compactan al
> formato columnar; Snowflake y DuckDB usan snapshot isolation por segmento en lugar de
> por tuple. La tendencia actual son los sistemas HTAP (Hybrid Transactional/Analytical
> Processing) — TiDB, AlloyDB, Oracle In-Memory — que mantienen ambos formatos en paralelo.

---

### Sección 3.11: Qué motores usan cada modelo y por qué {#3-motores-por-modelo}

| Modelo       | Motores                                                            | Por qué                                                                   |
| ------------ | ------------------------------------------------------------------ | ------------------------------------------------------------------------- |
| Row-store    | PostgreSQL, MySQL, SQL Server, Oracle                              | OLTP: muchas txns individuales, MVCC por fila, writes O(1)                |
| Column-store | Redshift, ClickHouse, DuckDB, BigQuery, Snowflake, Parquet         | OLAP: scans masivos, compresión alta, sin UPDATEs frecuentes              |
| Híbrido      | SQL Server COLUMNSTORE INDEX, Oracle In-Memory, PostgreSQL + Citus | Misma tabla, el optimizador elige en tiempo de query según costo estimado |

---

### Sección 3.12: La regla práctica — y el porqué real {#3-regla-practica}

```
¿La operación necesita FILAS COMPLETAS o pocas filas por PK?
  → Row-store
  Por qué físico: todos los campos de la fila están en bytes contiguos en 1 página.
                  1 seek + leer N bytes = fila completa. INSERT/UPDATE toca 1 página.
  Cuándo pasa:   apps OLTP, APIs que sirven datos de usuarios, sistemas transaccionales.

¿La operación escanea MILLONES DE FILAS y necesita pocas columnas?
  → Column-store
  Por qué físico: los bytes de esa columna son contiguos en su propio archivo.
                  Solo se lee el archivo de las columnas necesarias — el resto no existe.
  Cuándo pasa:   dashboards, reportes, ML features, auditorías históricas, data warehouses.

¿Ambos patrones en el mismo sistema?
  → Dos bases de datos: OLTP row-store + pipeline ETL + OLAP column-store.
  → Esto es exactamente lo que hace toda empresa grande: PostgreSQL/MySQL para la app,
    Redshift/BigQuery/Snowflake para el analytics warehouse.
```

---

### Sección 3.13: Resumen visual: de SQL a disco, de páginas a columnas {#3-resumen-visual}

```
 ┌──────────────────────────────────────────────────────────────────────────┐
 │  LO QUE VE EL USUARIO EN SQL                                             │
 │                                                                          │
 │  tabla:   ventas                                                         │
 │  columnas: id INT    │  precio INT  │  cantidad INT                      │
 │  filas:   (1, 1000, 1) │ (2, 25, 3) │ (3, 75, 2) │ (4, 350, 1)         │
 └──────────────────────────────────────────────────────────────────────────┘

 Cómo se ve en disco lo que SQL llama tabla, columna y fila:
   TABLA   → un archivo binario en disco: /data/base/16384/24601
             no hay ningún texto "ventas" dentro del archivo — solo páginas de bytes
   FILA    → una tuple dentro de una página: header (24B) + bytes de columnas
             no hay separadores entre columnas, ni etiquetas, ni saltos de línea
   COLUMNA → en row-store NO tiene lugar propio en disco; sus bytes viven
             intercalados con los de las otras columnas dentro de cada tuple
             en column-store SÍ tiene su propio archivo: precio.col, id.col...

                    │
                    │  el mismo dato puede guardarse de dos formas distintas
                    │  según el motor de base de datos
           ┌────────┴─────────┐
           ▼                  ▼
   ROW-STORE                  COLUMN-STORE
   PostgreSQL, SQL Server,    ClickHouse, DuckDB, Redshift
   Oracle (heap file)         (Sección 3.3, arriba)
   MySQL InnoDB (B+Tree,
   también row-oriented
   pero sin heap file)
   (Bloque 1 y Bloque 2)


 ROW-STORE — agrupa por FILA — todos los campos de una fila van juntos
 ─────────────────────────────────────────────────────────────────────────
 heap file: /data/base/16384/24601   (1 archivo por tabla)
 │
 ├── Página 0 (8 192 bytes) ← unidad mínima de lectura/escritura
 │   ┌──────────────────────────────────────────────────────────────────┐
 │   │  item pointers: [slot1→offset][slot2→offset][slot3→offset]...   │
 │   │  ── espacio libre ──────────────────────────────────────────── │
 │   │  tuple 2: [t_xmin|t_xmax|t_ctid|t_hoff][03000000|4B000000|02000000]│
 │   │  tuple 1: [t_xmin|t_xmax|t_ctid|t_hoff][02000000|19000000|03000000]│
 │   │  tuple 0: [t_xmin|t_xmax|t_ctid|t_hoff][01000000|E8030000|01000000]│
 │   │           └────── header 24 bytes ─────┘└────── datos 12 bytes ────┘│
 │   └──────────────────────────────────────────────────────────────────┘
 │
 │   El motor interpreta los datos con pg_attribute (catálogo):
 │     col 1 "id"       → INT4, offset 0, leer 4 bytes → 01000000 = 1
 │     col 2 "precio"   → INT4, offset 4, leer 4 bytes → E8030000 = 1000
 │     col 3 "cantidad" → INT4, offset 8, leer 4 bytes → 01000000 = 1
 │     sin pg_attribute esos bytes no tienen ningún significado
 │     ↑ los 3 campos de cada fila están juntos en bytes contiguos
 │     ↑ leer 'precio' obliga a cargar también id y cantidad
 │     ↑ el orden físico de las tuples NO está garantizado: un DELETE
 │       seguido de un INSERT puede ocupar el hueco con datos distintos
 │       → NUNCA asumir orden sin ORDER BY explícito en SQL
 │
 ├── Página 1 (8 192 bytes) ... más tuples
 └── ...

 fill factor: si < 100%, la página reserva espacio. HOT update
   es posible solo si se cumplen DOS condiciones:
   1. la nueva tuple cabe en la misma página (espacio reservado por fill factor)
   2. la columna modificada NO está indexada (si estuviera, el índice
      tendría el valor viejo y habría que actualizarlo de todas formas)
   cuando ambas se cumplen → slot no se mueve → índices no se actualizan

 TOAST: si el tamaño total de la tuple supera ~2 KB (no una columna
   individual — es el tamaño de la fila completa) → la columna grande se
   mueve a un heap file separado (24615) → en la tuple del heap principal
   queda un puntero de 18 bytes apuntando a los chunks en la tabla TOAST


 COLUMN-STORE — agrupa por COLUMNA — todos los valores de una col van juntos
 ─────────────────────────────────────────────────────────────────────────
 1 archivo por columna   (cada uno dividido en bloques de columna ~256 KB)
 │
 ├── id.col:
 │   [01000000][02000000][03000000][04000000]...
 │    id fila0   id fila1   id fila2   id fila3
 │
 ├── precio.col:
 │   [E8030000][19000000][4B000000][5E010000]...
 │    1000       25         75         350
 │   ↑ para SUM(precio): solo se lee este archivo, los demás no se tocan
 │
 └── cantidad.col:
     [01000000][03000000][02000000][01000000]...

 Los "bloques de columna" son el equivalente de las páginas en row-store:
 misma idea (unidad interna de I/O), distinto contenido (1 col × M filas)

 Compresión (Sección 3.8): al tener todos los valores del mismo tipo juntos, comprimen mejor:
   pais.col:   [MX,MX,MX,US,MX,MX,...] → run-length: [(MX,3),(US,1),(MX,2)...]
               columnas de baja cardinalidad → 5-20× compresión típica
   precio.col: [999,999,1000,998,999,...] → delta encoding: [999,0,+1,-2,+1,...]
               diferencias pequeñas caben en menos bits → 3-4× compresión
   En row-store los tipos mezclados dentro de cada tuple rompen los patrones
   → menos compresión efectiva → más I/O → queries OLAP más lentas


 ÍNDICES — apuntan al heap file, no almacenan los datos de la tabla
 ─────────────────────────────────────────────────────────────────────────
 Índice B+tree sobre ventas.id  (archivo 24610, formato árbol — NO heap)

   ctid = (número de página, número de slot)
          └── en qué página    └── qué entrada del item pointer array

   [árbol B+tree]          [heap file]
   id=1 → ctid=(0,1) ───► página 0, slot 1 → tuple completa
   id=2 → ctid=(0,2) ───► página 0, slot 2 → tuple completa
   id=3 → ctid=(0,3) ───► página 0, slot 3 → tuple completa

 Sin índice: hay que leer todas las páginas del heap file (scan completo)
 Con índice: B+tree → ctid → página exacta → tuple exacta

 Estructura interna del B+tree y tipos de índice → Bloque 5 a Bloque 8


 TIPOS DE ACCESO AL HEAP — cómo el motor elige cómo leer los datos
 ─────────────────────────────────────────────────────────────────────────

 Sequential Scan:  leer todas las páginas del heap en orden físico
                   → O(n_páginas), sin importar cuántas filas se necesitan

 Index Scan:       B+tree → ctid → cargar solo la página que tiene la fila
                   → O(log n) para encontrar el ctid + O(1) para la página

 Index-Only Scan:  si el índice tiene todas las cols del SELECT → evita
                   visitar el heap en la mayoría de los casos. Excepción:
                   si la Visibility Map no marca la página como all-visible,
                   el motor visita el heap para verificar visibilidad de la tuple
                   → O(log n) en el caso ideal, el más eficiente para queries selectivas

 Bitmap Scan:      combinar varios índices → construir mapa de páginas →
                   leer solo las páginas necesarias en orden físico
                   → útil cuando múltiples índices aplican al WHERE

 Detalle de cada tipo de scan → Bloque 9 a Bloque 12 (pendientes)


 COSTOS DE LAS OPERACIONES PRINCIPALES
 ─────────────────────────────────────────────────────────────────────────
 ROW-STORE
 Operación                  Costo            Qué estructuras toca
 ───────────────────────── ───────────────  ──────────────────────────────
 SELECT (Index Scan)        O(log n)         B+tree → página → tuple
 SELECT (Sequential Scan)   O(n_páginas)     todas las páginas del heap
 SELECT (Index-Only Scan)   O(log n)         solo B+tree
 INSERT                     O(1) sin índices    FSM → página con espacio → tuple
                            O(k·log n) con k índices → cada índice B+tree se actualiza
 UPDATE HOT                 O(1)                misma página, cadena t_ctid
 UPDATE no-HOT              O(k·log n)           nueva página + actualizar k índices
 DELETE                     O(1)             marcar t_xmax en la tuple †
 VACUUM (tras DELETEs)      O(pág. sucias)   libera espacio, actualiza FSM y VM
 ───────────────────────── ───────────────  ──────────────────────────────
 † DELETE no libera espacio físicamente — solo marca la tuple como muerta.
   VACUUM es el proceso que recorre esas páginas, elimina las tuples muertas
   y actualiza el FSM (espacio disponible) y el VM (páginas limpias).
   Sin VACUUM, el heap file crece indefinidamente aunque se borren datos.

 COLUMN-STORE (Bloque 3)
 Operación                  Costo            Diferencia clave vs. row-store
 ───────────────────────── ───────────────  ──────────────────────────────
 SUM / AVG sobre 1 columna  O(n × 1 col)     lee solo el archivo de esa col
                                             row-store leería n × k columnas
 SELECT * (todas las cols)  O(n × k cols)    k archivos distintos — más lento
                                             que row-store para filas completas
 INSERT / UPDATE / DELETE   O(k archivos)    toca k archivos (1 por columna)
                                             row-store toca solo 1 heap file
 ─────────────────────────────────────────────────────────────────────────
```

> **Lo que este bloque no cubre (referencia a otros archivos):**
>
> - Cómo el motor carga páginas en RAM y las escribe (buffer pool, WAL, checkpoints)
>   → `01_internals_del_motor.md §3`
> - MVCC: cómo t_xmin/t_xmax determinan la visibilidad en una transacción
>   → `guia_transacciones_db.md §6`

## Bloque 4: Qué es un índice y por qué existe {#4-indice}

> **Contexto:** Bloque 1 presentó los índices como la Capa 7, la última de las 7 capas de
> interpretación, mostrando qué guarda cada motor como "puntero" hacia la fila. Este bloque
> retoma ese punto y profundiza en el problema que resuelve un índice, su estructura interna,
> y cuándo vale la pena usarlo.

---

### Sección 4.1: El problema que resuelve {#4-problema}

En Bloque 1 vimos que el heap file es un array de páginas con tuples en ningún orden garantizado.
Si quieres la fila con `id = 42`, el motor no sabe en qué página está — tiene que leer
todas las páginas del archivo de principio a fin. Con 1 millón de filas a 36 bytes cada
una, eso es leer ~5 500 páginas de 8 KB. Eso es un **sequential scan**: O(n_páginas).

El índice resuelve exactamente eso: es una estructura de datos separada que guarda los
valores de una columna en orden, apuntando al ctid de cada tuple correspondiente en el
heap. En lugar de leer todo el heap, el motor consulta el índice para encontrar
directamente la página y el slot exactos.

---

### Sección 4.2: Qué es físicamente un índice {#4-que-es}

Un índice es **un archivo separado en disco** — distinto del heap file, con su propio OID.
El archivo tiene el mismo formato de páginas de 8 KB que el heap file, pero el contenido
de cada página es diferente: en lugar de tuples con datos de usuario, tiene entradas
`(valor_de_clave, ctid)` ordenadas por el valor de clave.

```
Heap file 24601 (desordenado):        Índice 24610 (ordenado por id):
  página 0, slot 0: id=3, precio=75     hoja: id=1 → ctid=(0,1)
  página 0, slot 1: id=1, precio=1000   hoja: id=2 → ctid=(0,2)
  página 0, slot 2: id=2, precio=25     hoja: id=3 → ctid=(0,0)
  página 1, slot 0: id=4, precio=350    hoja: id=4 → ctid=(1,0)
```

Cada entrada del índice ocupa unos 16 bytes (clave + ctid + overhead de la página).
El índice no duplica la fila completa — solo guarda el valor indexado y la dirección
física (ctid) de la tuple en el heap.

Una nota sobre las páginas de índice: usan el mismo `PageHeaderData` de 24 bytes que
las páginas del heap. La diferencia es el **ÁREA ESPECIAL** al fondo de la página, que
en las páginas heap no se usa pero en las páginas de índice contiene metadatos del árbol
(punteros a páginas hermanas, nivel del nodo, flags). Por eso en Bloque 1 decíamos
"ÁREA ESPECIAL — usada solo en páginas de índice, no en heap".

---

### Sección 4.3: Por qué es rápido: la estructura de árbol {#4-por-que-rapido}

Un índice no es simplemente una lista ordenada — si lo fuera, insertar un valor nuevo
requeriría desplazar todos los elementos posteriores: O(n). La estructura real es un
**árbol balanceado** que mantiene el orden y permite búsqueda, inserción y eliminación
en O(log n).

```
Para encontrar id=42 en una tabla de 1 000 000 filas:

  Sequential scan (sin índice):
    Leer ~5 556 páginas de 8 KB hasta encontrar id=42
    Costo: O(n_páginas) ≈ miles de lecturas de disco

  Índice B+tree (con índice):
    Nivel 0 (raíz):     1 página — decide si ir izquierda o derecha
    Nivel 1 (interno):  1 página — afina la dirección
    Nivel 2 (interno):  1 página — afina más
    Nivel 3 (hoja):     1 página — encuentra id=42 → ctid=(87,3)
    Heap:               1 página — carga la página 87 y lee el slot 3
    Total: 5 lecturas de disco para cualquier fila de 1M
    Costo: O(log n) ≈ 3–4 niveles para millones de filas
```

La profundidad de un B+tree crece muy lentamente: con páginas de 8 KB y claves de 4
bytes, cada página interna puede contener ~400 punteros (fan-out alto). Eso significa
que con solo 3 niveles se puede indexar 400³ ≈ 64 millones de filas.

---

### Sección 4.4: Por qué ocupa espacio adicional {#4-espacio}

El índice es una copia parcial de los datos (solo la columna indexada + ctid), más la
estructura del árbol, más el overhead de página. Para una tabla con 1M de filas:

```
Tabla ventas (id INT, precio INT, cantidad INT):
  Heap file:  1M × 36 bytes ≈ 36 MB
  Índice en id (INT4, 4 bytes):
    1M entradas × ~16 bytes (clave + ctid + overhead) ≈ 16 MB adicionales
    (≈ 45% del tamaño del heap en este ejemplo)
```

Cuantos más índices tenga una tabla, más espacio en disco. Pero el costo real no es solo
el espacio — es el **costo de escritura**: cada `INSERT`, `UPDATE` o `DELETE` en la tabla
debe actualizar también todos sus índices. Una tabla con 5 índices multiplica por 6 el
trabajo de escritura (heap + 5 índices).

---

### Sección 4.5: Por qué no usar otras estructuras {#4-otras-estructuras}

```
Estructura      Búsqueda exacta  Rango (id > 10)  INSERT    Motivo de descarte
──────────────  ───────────────  ───────────────  ────────  ─────────────────────
Array ordenado  O(log n)         O(log n) + scan  O(n)      INSERT desplaza todo
Hash table      O(1)             ✗ imposible      O(1)      sin soporte de rango
BST             O(log n) medio   O(log n) + scan  O(log n)  degrada a O(n), 1 nodo
                                                            por página = mal en disco
B+tree          O(log n)         O(log n) + scan  O(log n)  ideal: fan-out alto,
                                                            hojas enlazadas para rango
```

El hash table resuelve búsquedas exactas en O(1) pero es completamente inútil para
`WHERE id > 1000` o `ORDER BY id` — el motor necesita recorrer todo. PostgreSQL sí tiene
índices hash pero su uso es minoritario precisamente por esto.

El BST tiene el mismo O(log n) teórico que el B+tree pero falla en disco: cada nodo es
un puntero a una posición diferente del archivo, lo que significa un seek de disco por
comparación. 20 comparaciones = 20 seeks = 20 × 0.1 ms (SSD) = 2 ms. Un B+tree con
fan-out de 400 hace el mismo trabajo en 3–4 seeks. Bloque 5 explica esto en detalle.

---

### Sección 4.6: Cuándo el índice NO ayuda {#4-no-ayuda}

Un índice acelera lecturas selectivas. Si una query devuelve la mayoría de las filas, el
motor puede decidir que un sequential scan es más eficiente que seguir miles de ctids
individuales (cada uno en una página distinta):

```
SELECT * FROM ventas WHERE id > 5;    -- devuelve 999 995 de 1 000 000 filas
  → el índice apunta a 999 995 ctids distintos, posiblemente en páginas distintas
  → 999 995 seeks aleatorios >> leer las 5 556 páginas en orden secuencial
  → el planner elige sequential scan aunque existe el índice
```

La regla práctica: un índice vale la pena cuando filtra a menos del ~5–15% de las filas.
Por encima de ese porcentaje, el sequential scan suele ganar por el patrón de I/O
secuencial vs. aleatorio.

---

## Bloque 5: Por qué los árboles binarios no funcionan en disco {#5-arboles-binarios}

---

### Sección 5.1: El Binary Search Tree (BST): excelente en memoria, horrible en disco {#5-bst}

Un BST tiene búsqueda en O(log₂ n) en el caso promedio. Para 1 millón de filas, eso es
~20 comparaciones. En memoria, 20 comparaciones es instantáneo. En disco, es un desastre.

```
Problema: cada nodo del BST es un puntero a una posición en disco.
Cada comparación = un acceso a disco aleatorio.
Tiempo de acceso a disco HDD: ~10ms (10,000 microsegundos)
Tiempo de acceso a disco SSD: ~0.1ms (100 microsegundos)

Para 1 millón de filas con BST:
  20 accesos × 10ms = 200ms  (HDD)
  20 accesos × 0.1ms = 2ms   (SSD)

Comparar con leer desde memoria:
  20 comparaciones × 0.0001ms = 0.002ms

El problema no es la complejidad algoritmica (O(log n) es buena).
El problema es que cada "paso" del algoritmo es un acceso a disco.
La solución: minimizar los accesos a disco, no las comparaciones.
```

---

### Sección 5.2: La solución: aumentar el "ancho" del árbol {#5-ancho-arbol}

Si en lugar de un árbol binario (2 hijos por nodo) usas un árbol de 100 hijos por nodo:

```
BST (factor de ramificación = 2):
  1,000,000 filas → altura del árbol = log₂(1,000,000) ≈ 20 niveles
  Búsqueda = 20 accesos a disco

Árbol de orden 100 (factor de ramificación ≈ 100):
  1,000,000 filas → altura = log₁₀₀(1,000,000) ≈ 3 niveles
  Búsqueda = 3 accesos a disco

La comparación clave:
  BST: 20 accesos a disco × 10ms = 200ms
  B+Tree: 3-4 accesos a disco × 10ms = 30-40ms
  (y en la práctica, los niveles superiores del árbol siempre están en cache)
```

Un nodo de 8KB puede contener ~100 claves enteras (cada clave tiene 4 bytes + 8 bytes de puntero).
Eso le da al árbol un factor de ramificación de ~100, reduciendo drásticamente la altura.

---

## Bloque 6: El B-Tree original (Bayer & McCreight, 1972) {#6-btree}

---

### Sección 6.1: Definición formal {#6-definicion}

Un B-Tree de orden `m` tiene las siguientes propiedades:

```
1. Cada nodo tiene máximo m hijos
2. Cada nodo tiene mínimo ⌈m/2⌉ hijos (excepto la raíz)
3. La raíz tiene mínimo 2 hijos (si no es hoja)
4. Todas las hojas están al mismo nivel (árbol balanceado)
5. Un nodo con k hijos contiene k-1 claves
6. Las claves en cada nodo están ordenadas
7. CRÍTICO: los datos (o punteros a datos) viven en TODOS los nodos (internos y hojas)
```

---

### Sección 6.2: Estructura visual de un B-Tree de orden 5 {#6-estructura-visual}

```
                    [30 | 70]
                   /    |    \
          [10|20]    [40|50|60]    [80|90]
          / | \       / | | \       / \
        [5] [15] [25] [35][45][55][65] [75][85][95]

Cada nodo interno contiene:
  - Claves de separación
  - Punteros a hijos
  - TAMBIÉN punteros a los datos (records) para cada clave ← problema

Buscar la clave 45:
  1. Raíz: 45 > 30, 45 < 70 → ir al hijo central
  2. Nodo [40|50|60]: 45 > 40, 45 < 50 → acceder al dato de 45 en este nodo
  3. O si 45 no está aquí, ir al hijo entre 40 y 50
```

---

### Sección 6.3: El problema del B-Tree puro para bases de datos {#6-problema-btree-puro}

```
Para buscar id=45 → acceder al nodo interno donde vive id=45 → leer los datos ahí mismo
Para buscar id=42 → bajar hasta el nodo hoja donde vive id=42 → leer los datos ahí

Problema 1: los datos están dispersos en todos los niveles del árbol.
  → Un rango scan (WHERE id BETWEEN 40 AND 80) requiere saltar entre nodos en diferentes
    niveles y posiciones del árbol. No hay orden secuencial de datos.

Problema 2: tamaño de los nodos.
  → Si guardas los datos (que pueden ser filas completas de 200 bytes) en nodos internos,
    el factor de ramificación cae dramáticamente.
  → Factor de ramificación = tamaño_nodo / (tamaño_clave + tamaño_dato + tamaño_puntero)
  → Con datos de 200 bytes: 8192 / (4 + 200 + 8) ≈ 38 hijos por nodo
  → Con solo claves: 8192 / (4 + 8) ≈ 682 hijos por nodo
  → Mayor factor de ramificación = árbol más bajo = menos I/O

Por eso las bases de datos usan el B+Tree, no el B-Tree original.
```

---

## Bloque 7: El B+Tree: la estructura real que usan los motores {#7-bplustree}

---

El B+Tree resuelve el problema del B-Tree original separando completamente navegación de datos. Los nodos internos solo guardan claves de navegación, y los datos viven exclusivamente en las hojas, enlazadas entre sí para que un range scan las recorra en secuencia sin volver a bajar por el árbol. Lo que sigue es cómo se ve eso en bytes reales, dentro de un motor SQL concreto.

### Sección 7.1: Por qué la linked list de hojas es crítica {#7-linked-list}

```sql
-- Range scan: WHERE id BETWEEN 35 AND 75
-- Con B-Tree: saltar entre nodos en distintos niveles → I/O aleatorio
-- Con B+Tree:
--   1. Bajar al nodo hoja donde está id=35 (3-4 I/Os)
--   2. Escanear la linked list hacia la derecha: 35→50→65→75
--   3. Parar cuando se supera el límite superior
-- → I/O secuencial a partir del primer nodo hoja → extremadamente eficiente

-- ORDER BY aprovecha la misma linked list:
-- el resultado ya viene ordenado del índice → no hace falta Sort node en el plan
```

---

### Sección 7.2: El factor de ramificación real en PostgreSQL {#7-factor-ramificacion}

Un nodo de índice en PostgreSQL es de 8KB. Cada entrada en un nodo interno contiene:

- La clave: 4 bytes (INTEGER) u 8 bytes (BIGINT) o variable (TEXT)
- El puntero al hijo: 6 bytes (BlockNumber + OffsetNumber)
- Overhead del nodo: ~24 bytes de header

```
Factor de ramificación con INTEGER key:
  (8192 - 24) / (4 + 6) ≈ 817 hijos por nodo interno

Tabla de altura del árbol:
  Altura 1 (solo la raíz): hasta 817 claves
  Altura 2:                hasta 817² ≈ 667,000 claves
  Altura 3:                hasta 817³ ≈ 545 millones de claves
  Altura 4:                hasta 817⁴ ≈ 445 mil millones de claves

En la práctica: una tabla con 10 millones de filas tiene un índice B+Tree de altura 3-4.
Las búsquedas son casi siempre ≤ 4 accesos a disco.
```

---

### Sección 7.3: Algoritmo de eliminación: merges de nodo {#7-eliminacion-merge}

```
Si un nodo queda con menos de ⌈m/2⌉ - 1 claves tras un DELETE:

Opción 1 — Robar del hermano (si tiene claves extra):
  rotar una clave del hermano a través del padre

Opción 2 — Merge con el hermano (si el hermano también está al mínimo):
  combinar los dos nodos en uno → eliminar la clave de separación del padre
  → el padre puede también necesitar merge → propagación hacia arriba

En la práctica, muchos motores (incluyendo PostgreSQL) no implementan merge agresivo
y dejan páginas parcialmente vacías. Vacuum/REINDEX las limpia periódicamente.
```

---

## Bloque 8: B+Tree en disco: PostgreSQL vs MySQL InnoDB vs SQL Server {#8-bplus-motores}

---

### Sección 8.1: PostgreSQL: heap + índice separado (heap-based table) {#8-postgresql}

```
Estructura en disco:
  tabla_heap.dat     ← filas en heap (sin orden)
  idx_pk.dat         ← árbol B+Tree del índice de PK
  idx_email.dat      ← árbol B+Tree del índice de email
  idx_fecha.dat      ← árbol B+Tree del índice de fecha

Las hojas de CUALQUIER índice contienen:
  (clave_del_índice, ctid)
  Donde ctid = (página, offset) en el heap

Para buscar por email='ana@example.com':
  1. Recorrer idx_email.dat hasta la hoja → obtener ctid=(3, 7)
  2. Leer heap: página 3, tuple 7 → devolver la fila completa
  Total: 2 lecturas (una en el índice, una en el heap)

Ventaja:
  Los UPDATEs al heap no afectan los índices (solo el ctid cambia si hay HOT update)
  Todos los índices son "iguales" — ninguno es especial
  VACUUM puede reclamar espacio del heap sin reconstruir índices

Desventaja:
  Siempre hay una lectura adicional al heap ("heap fetch")
  → a veces más lento que un clustered index si accedes por la clave del índice
```

---

### Sección 8.2: MySQL InnoDB: clustered index (tabla ES el índice primario) {#8-mysql}

```
Estructura en disco:
  tabla.ibd          ← UN SOLO ARCHIVO que contiene todo

  Árbol B+Tree del índice primario (PK):
    Nodos internos: solo claves (PK values)
    Hojas:          (pk_value, FILA_COMPLETA)   ← la fila vive aquí

  Árboles B+Tree de índices secundarios:
    Hojas:          (clave_secundaria, pk_value)  ← solo la PK, no la fila

Para buscar por email='ana@example.com' (índice secundario):
  1. Recorrer idx_email → encontrar pk_value=42
  2. Recorrer el clustered index (PK) → encontrar la fila para pk=42
  Total: 2 lecturas de árbol (no 1 árbol + 1 heap como en PostgreSQL)

Para buscar por PK directamente:
  1. Recorrer el clustered index → la hoja TIENE la fila completa
  Total: 1 lectura (más eficiente que PostgreSQL en este caso)

Consecuencias críticas:
  1. La PK DEBE ser pequeña (los índices secundarios la incluyen en todas sus hojas)
     PK BIGINT (8 bytes) vs PK UUID (16 bytes): UUID dobla el tamaño de cada secundario
  2. La PK DEBE ser secuencial (inserciones aleatorias fragmentan el clustered index)
     → UUID v4 como PK en MySQL: performance mucho peor que BIGSERIAL
  3. UPDATE a una columna no-PK es in-place (no mueve la fila como en PostgreSQL MVCC)
```

---

### Sección 8.3: SQL Server: clustered index por defecto, similar a MySQL {#8-sqlserver}

```
SQL Server también usa clustered index, pero con diferencias:
  - Si defines PRIMARY KEY sin CLUSTERED, es UNIQUE NONCLUSTERED
  - Puedes elegir cuál índice es CLUSTERED (no tiene que ser la PK)
  - Heap tables existen si no hay clustered index (similar a PostgreSQL)

CREATE TABLE productos (
    id INT PRIMARY KEY,                    -- CLUSTERED por defecto
    nombre NVARCHAR(100),
    precio DECIMAL(10,2)
);

-- Índice no-clustered (similar a índice secundario en MySQL):
CREATE NONCLUSTERED INDEX idx_nombre ON productos(nombre);
-- Las hojas de este índice contienen: (nombre, id_clustered)
```

---

### Sección 8.4: Tabla comparativa {#8-tabla-comparativa}

| Aspecto               | PostgreSQL               | MySQL InnoDB               | SQL Server             |
| --------------------- | ------------------------ | -------------------------- | ---------------------- |
| Modelo de tabla       | Heap + índices separados | Clustered index            | Clustered index        |
| Hojas del PK index    | (clave, ctid)            | (clave, fila completa)     | (clave, fila completa) |
| Hojas de secundario   | (clave, ctid)            | (clave, pk_value)          | (clave, pk_value)      |
| Buscar por PK         | 1 árbol + heap fetch     | 1 árbol (fila en hoja)     | 1 árbol (fila en hoja) |
| Buscar por secundario | 1 árbol + heap fetch     | 2 árboles                  | 2 árboles              |
| UPDATE row            | nueva tuple en heap      | in-place en clustered      | in-place en clustered  |
| Impacto UUID como PK  | Moderado (fragmentación) | Alto (doble fragmentación) | Alto                   |

---
