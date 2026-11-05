# Cómo funciona el motor por dentro

**Bloque 1 — Capas del sistema**

- [Sección 1.1 — Conceptos base](#1-conceptos-base)
- [Sección 1.2 — La arquitectura universal, capa por capa](#1-arquitectura-universal)
- [Sección 1.3 — La misma arquitectura, vista en distintos tipos de motor](#1-arquitectura-por-motor)

**Bloque 2 — Procesos, threads y modelo de concurrencia**

- [Sección 2.1 — Por qué hay que instalar una base de datos y dónde corre](#2-instalar-y-donde-corre)
- [Sección 2.2 — Fundamentos del OS: RAM, memoria virtual y aislamiento](#2-fundamentos-os)
- [Sección 2.3 — Process y thread: diferencias reales en todas las dimensiones](#2-process-vs-thread)
- [Sección 2.4 — Relación padre-hijo entre procesos: cómo un proceso "crea" otro](#2-padre-hijo)
- [Sección 2.5 — Paralelismo real y concurrencia](#2-paralelismo)
- [Sección 2.6 — Modelo de concurrencia por motor](#2-modelo-por-motor)
- [Sección 2.7 — Conexión, proceso/thread y transacción: cómo se relacionan](#2-conexion-proceso-transaccion)

**Bloque 3 — Parser, planner y executor: cómo se decide y ejecuta una query**

- [Sección 3.1 — Cómo lo resuelve cada motor](#3-por-motor)

**Bloque 4 — Memoria, páginas, caché de páginas y write-ahead logging (WAL)**

- [Sección 4.1 — Tres términos distintos que incluyen la palabra "página"](#4-tres-terminos-pagina)
- [Sección 4.2 — OS page: por qué el MMU trabaja en bloques de 4 KB](#4-os-page)
- [Sección 4.3 — DB page: el problema del disco y por qué el motor define su propio bloque](#4-db-page)
- [Sección 4.4 — Dónde vive la memoria compartida: proceso vs. thread](#4-memoria-compartida)
- [Sección 4.5 — Buffer pool: la caché de páginas del motor](#4-buffer-pool)
- [Sección 4.6 — WAL: Write-Ahead Log](#4-wal-detalle)

**Bloque 5 — fsync, durabilidad y rendimiento**

- [Sección 5.1 — El problema: "escribir" no es lo mismo que "guardar en disco"](#5-problema)
- [Sección 5.2 — fsync(): vaciar todas las capas hasta el medio físico](#5-fsync-detalle)
- [Sección 5.3 — La distinción crítica: archivos de datos vs WAL](#5-datos-vs-wal)
- [Sección 5.4 — Dos formas muy distintas de "relajar" la durabilidad](#5-relajar-durabilidad)
- [Sección 5.5 — Group commit: batching de fsync para alto throughput](#5-group-commit)
- [Sección 5.6 — Latencias de hardware y TPS resultantes](#5-latencias-hardware)
- [Sección 5.7 — Configuración por motor](#5-configuracion-por-motor)

**Bloque 6 — Estructuras de almacenamiento: cómo encontrar una fila sin leer todo el archivo**

- [Sección 6.1 — B-tree (o B+tree)](#6-btree)
- [Sección 6.2 — LSM-tree (Log-Structured Merge-tree)](#6-lsm-tree)
- [Sección 6.3 — Cómo lo resuelve cada motor](#6-por-motor)

**Bloque 7 — Locks: la otra mitad del control de concurrencia**

- [Sección 7.1 — Cómo lo resuelve cada motor](#7-por-motor)

**Bloque 8 — Control de concurrencia multiversión (MVCC): versionado y visibilidad de datos**

- [Sección 8.1 — El problema universal](#8-problema-universal)
- [Sección 8.2 — Cómo lo resuelve cada motor](#8-por-motor)
- [Sección 8.3 — El problema real: cuándo un snapshot no es lo que quieres](#8-problema-real)
- [Sección 8.4 — Por qué esto solo se nota con al menos dos transacciones a la vez](#8-dos-transacciones)
- [Sección 8.5 — Qué es automático y qué depende de ti](#8-automatico-vs-manual)
- [Sección 8.6 — Por qué tener MVCC no significa que las transacciones salgan gratis: el caso de MongoDB](#8-mongodb)
- [Sección 8.7 — Cómo lo hace PostgreSQL por dentro](#8-postgresql)

**Bloque 9 — Recuperación de espacio de versiones obsoletas y límites de contadores de transacción**

- [Sección 9.1 — El problema universal](#9-problema-universal)
- [Sección 9.2 — Cómo lo resuelve cada motor](#9-por-motor)
- [Sección 9.3 — Cómo lo hace PostgreSQL por dentro](#9-postgresql)

**Bloque 10 — Replicación: cuando la durabilidad de un solo nodo no es suficiente**

- [Sección 10.1 — Cómo lo resuelve cada motor](#10-por-motor)

---

**Alcance de este bloque**

**Cómo leer este bloque:** cada sección abre con el problema arquitectónico universal, es decir
lo que cualquier motor con garantías de durabilidad y concurrencia tiene que resolver sí o sí,
sin importar su nombre ni su implementación. Después viene una comparación real entre motores
(PostgreSQL, MySQL InnoDB, SQL Server, Oracle, SQLite, y donde aplique MongoDB/WiredTiger,
Cassandra, Neo4j y ClickHouse) mostrando cómo cada uno lo nombra y lo resuelve. Todo el mecanismo
interno de PostgreSQL (diagramas completos, xmin/xmax, WAL, vacuum, y demás detalle mecánico) vive
en un archivo aparte, [`postgresql_caso_estudio.md`](postgresql_caso_estudio.md), enlazado desde
cada sección donde aplica. Es opcional: se puede leer este bloque entero sin abrirlo, y entender
el concepto y la comparación entre motores igual de bien.

**Qué es universal** (aplica a todos los motores que garantizan durabilidad y concurrencia):
convertir una petición en un plan y ejecutarlo · páginas como unidad de I/O · estructuras de
almacenamiento que evitan escanear todo el archivo (B-tree, LSM-tree u otra) · caché de páginas
en RAM · algún log de escritura adelantada o equivalente · el trade-off de fsync entre durabilidad
y latencia · el control de concurrencia (locks y/o multiversión) · la necesidad de recuperar
espacio de versiones obsoletas · anomalías de concurrencia · extender la durabilidad de un nodo a
varios mediante replicación.

**Qué es específico de una implementación concreta** (no universal, aunque se explique a fondo
como caso de estudio): xmin/xmax/ctid como campos de fila y VACUUM son la forma en que
PostgreSQL resuelve el problema universal de versionado y limpieza; MySQL lo resuelve con undo
log y un purge thread, Oracle con undo tablespace, y así con cada motor. Lo mismo aplica al
modelo de concurrencia: proceso-por-conexión es la elección de PostgreSQL, no la única forma
válida (MySQL usa threads, Redis un solo hilo con event loop).

Para el resto de motores no relacionales, con más profundidad de la que cabe aquí, ver
`guia_panorama_bases_de_datos.md` §2–§4.

## Bloque 1: Capas del sistema

Una sola petición HTTP que hace un `UPDATE` no va directamente "a la base de datos". Atraviesa una pila de componentes que la mayoría de los desarrolladores nunca ve: drivers, pools de conexiones, parsers, planificadores, ejecutores, gestores de transacciones, motores de concurrencia, y múltiples capas de almacenamiento en RAM antes de tocar el disco físico.

### Sección 1.1: Conceptos base {#1-conceptos-base}

Si no estás familiarizado con sistemas de bajo nivel, estos cuatro conceptos te permiten entender todo lo que viene después, en esta sección y en el resto del bloque.

**RAM vs. disco**

La RAM (Random Access Memory) es la memoria de trabajo del ordenador. Acceder a un dato en RAM tarda nanosegundos. Es ultrarrápida, pero **volátil**: si el proceso se apaga o hay un corte de luz, todo lo que estaba en RAM desaparece para siempre. El disco (HDD, SSD, NVMe) es **persistente**: los datos sobreviven aunque no haya electricidad, pero es entre 1,000 y 100,000 veces más lento. Una base de datos usa RAM para operar a máxima velocidad y disco para garantizar que los datos sobrevivan caídas. El reto de diseño central es: ¿cuándo es imprescindible escribir a disco y cuándo basta trabajar en RAM?

**Proceso e hilo (thread)**

Un proceso es una instancia de un programa en ejecución con su propio espacio de memoria, aislado del resto. Si el proceso A falla, el proceso B no se ve afectado. Un **hilo** (thread) vive dentro del mismo proceso y comparte memoria con otros hilos —más ligero de crear, pero un bug en un hilo puede afectar a todos los del mismo proceso. En PostgreSQL, cada conexión genera un **proceso** separado en el servidor. En MySQL, genera un **hilo** dentro del proceso `mysqld`.

**Socket**

Un socket es el canal de comunicación bidireccional entre dos programas. Puede ser a través de la red (TCP socket, entre máquinas distintas) o dentro de la misma máquina (Unix domain socket, que evita la pila de red y es más rápido). En cualquier motor cliente-servidor (PostgreSQL, MySQL, MongoDB, Cassandra, Neo4j, ClickHouse), el driver de tu backend mantiene un socket abierto y autenticado con el servidor durante toda la sesión, y a través de ese canal viajan tus queries en un sentido y los resultados en el otro. Esto no aplica a los motores que corren embebidos dentro de tu propio proceso, sin servidor de por medio, como SQLite o DuckDB: ahí no hay socket que abrir, porque no hay otro programa con el que hablar, la librería opera directamente sobre los archivos en disco.

**Kernel del sistema operativo y OS page cache**

El kernel es la capa central del sistema operativo: el software que gestiona el hardware directamente. Toda operación de disco pasa por él obligatoriamente. Cuando la base de datos escribe algo, el kernel puede almacenarlo en su propio buffer de RAM (el **OS page cache**) y responder "OK" antes de que los bytes lleguen físicamente al disco. Si hay un corte de luz justo después de ese "OK", los datos se pierden. Para evitarlo, la base de datos usa `fsync()`, que le dice al kernel: "no me devuelvas OK hasta que los bytes estén en el hardware físico, no en tu caché".

### Sección 1.2: La arquitectura universal, capa por capa {#1-arquitectura-universal}

Esta es la ruta que cualquier motor de base de datos, sea relacional, documento, clave-valor, wide-column, grafo, columnar o vectorial, tiene que resolver de alguna forma cuando llega una escritura: **algo recibe la conexión** (un proceso, un thread o un event loop, según el motor) → **algo convierte la petición en un plan de ejecución** (parser + planner en un motor con lenguaje de consulta; un dispatcher directo de comando en un motor clave-valor, que no necesita planificar nada) → **algo ejecuta ese plan paso a paso** (el executor) → **algo decide si esta operación puede chocar con otra que corre al mismo tiempo** (control de concurrencia: locks, MVCC, un solo hilo que serializa todo, o consenso distribuido, según el motor) → **algo dejó constancia del cambio antes de aplicarlo**, para poder recuperarlo si el proceso muere a la mitad (WAL, redo log, commit log, journal o AOF, según el motor) → **algo mantiene en RAM las páginas o estructuras que se leen y escriben con frecuencia**, para no pagar el costo de disco en cada operación (buffer pool, caché de páginas, o directamente todo el dataset si el motor vive en RAM). Ningún motor se salta estas responsabilidades; lo que cambia de un tipo de motor a otro es **quién hace cada cosa, cómo se llama, y qué tan cara le sale** cada una según su diseño.

### Sección 1.3: La misma arquitectura, vista en distintos tipos de motor {#1-arquitectura-por-motor}

| Capa                                                          | Qué resuelve                                                                 | Relacional (PostgreSQL/MySQL)                            | Documento (MongoDB)                                                                             | Clave-valor (Redis)                                                                                  | Wide-column (Cassandra)                                                                    | Grafo (Neo4j)                                                                                     | Columnar (ClickHouse)                                                                                           |
| ------------------------------------------------------------- | ---------------------------------------------------------------------------- | -------------------------------------------------------- | ----------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- |
| **Conexión y concurrencia** (Bloque 2)                        | Qué unidad del SO atiende cada conexión o request                            | Un proceso (PostgreSQL) o un thread (MySQL) por conexión | Un thread por conexión, gestionado por un pool interno                                          | Un solo hilo con event loop: nunca hay dos comandos ejecutando código a la vez                       | Un pool de threads por nodo, coordinado sin proceso central                                | Un pool de threads sobre la JVM, con Bolt como protocolo de conexión                              | Un pool de threads por consulta, con ejecución vectorizada dentro de cada uno                                   |
| **Planificación de queries** (Bloque 3)                       | Cómo se convierte una petición declarativa en pasos ejecutables              | Parser + optimizador basado en costos + executor         | Planner con selección empírica de plan (varios planes candidatos corren en paralelo brevemente) | No aplica: dispatch directo del comando, no hay nada que planificar                                  | Planner mínimo, restringido por la partition key (CQL exige incluirla o `ALLOW FILTERING`) | Planner basado en costos sobre el patrón de búsqueda (Cypher)                                     | Optimizador orientado a _predicate pushdown_ y _partition pruning_, no a joins                                  |
| **Caché en RAM** (Bloque 4)                                   | Qué se mantiene en memoria para no pagar el costo de disco en cada operación | Buffer pool                                              | Caché interna de WiredTiger sobre páginas                                                       | Es la estructura primaria: RAM es donde vive todo el dataset, no una caché de otra cosa              | Memtable (buffer de escritura reciente en RAM antes de volcarse a SSTable)                 | Caché de páginas del store, orientada a mantener el grafo caliente accesible                      | Marca de bloques recientes en RAM antes de que el sistema operativo los sirva desde su propia caché de archivos |
| **Durabilidad** (Bloque 4 y Bloque 5)                         | Cómo sobrevive un cambio confirmado a un crash                               | WAL / redo log                                           | Journal de WiredTiger                                                                           | AOF (opcional) o RDB                                                                                 | Commit log                                                                                 | Log de transacciones propio, con checkpoints                                                      | Los datos ya escritos a una part son inmediatamente durables; no hay WAL separado del dato                      |
| **Estructuras de almacenamiento** (Bloque 6)                  | Cómo encuentra una fila sin escanear todo el archivo                         | B-tree (heap + índice, o clustered index)                | B-tree (WiredTiger)                                                                             | Hash table en RAM; skip list para sorted sets                                                        | LSM-tree (memtable + SSTable)                                                              | Adyacencia indexada (_index-free adjacency_): punteros directos, no árbol de búsqueda             | Parts ordenados con índice disperso (familia MergeTree)                                                         |
| **Control de concurrencia** (Bloque 7 y Bloque 8)             | Qué le pasa a una operación cuando compite con otra                          | Locks y/o MVCC                                           | MVCC por documento (WiredTiger)                                                                 | No aplica en el sentido clásico: el single-thread serializa todo por diseño, sin locks que gestionar | Sin coordinación entre nodos: cada réplica resuelve conflictos con last-write-wins         | No usa MVCC: locking puro a nivel de nodo/relación                                                | No aplica en el sentido clásico: no hay fila que versionar, cada INSERT crea una part inmutable                 |
| **Recuperación de espacio de versiones obsoletas** (Bloque 9) | Qué hace el motor con datos viejos que ya nadie puede ver                    | `vacuum` / autovacuum                                    | Checkpoint de WiredTiger libera versiones obsoletas                                             | No aplica: no hay versiones que limpiar, solo el dato vigente en RAM                                 | _Compaction_ de SSTables                                                                   | No aplica igual: al no usar MVCC, el mantenimiento es de fragmentación del store, no de versiones | _Merge_ de parts en background                                                                                  |
| **Replicación** (Bloque 10)                                   | Cómo extiende un motor su durabilidad más allá de un solo nodo               | Log shipping primary-réplica (WAL/redo/binlog)           | Replica sets vía oplog, con elección automática de primary                                      | Propagación de comandos asíncrona; tunable con `WAIT`                                                | Peer-to-peer sin primary, _consistency level_ tunable por query                            | Consenso Raft entre _core servers_, más _read replicas_ asíncronas                                | Asíncrona, coordinada por un servicio externo (ZooKeeper/Keeper)                                                |

Falta el vectorial a propósito de esta tabla, no por descuido: a diferencia de los otros seis, el modelo vectorial casi nunca tiene una arquitectura interna propia y distinta. Normalmente vive encima de uno de los seis anteriores (pgvector corre dentro de PostgreSQL y hereda cada fila de esta tabla tal cual; Milvus/Weaviate/Qdrant ensamblan piezas ya conocidas, como almacenamiento tipo documento o clave-valor, y le agregan un índice HNSW/IVF encima) o vive completamente gestionado y opaco (Pinecone). No hay una fila honesta que escribir para "vectorial" sin heredarla de otro tipo.

Ver esa misma ruta implementada por completo en un motor concreto, con diagrama, glosario y el recorrido paso a paso de un `UPDATE` desde el driver hasta el disco, en [`postgresql_caso_estudio.md` §1](postgresql_caso_estudio.md#1-arquitectura).

---

## Bloque 2: Procesos, threads y modelo de concurrencia {#2-concurrencia}

**El problema universal:** todo motor de base de datos, sin excepción, tiene que decidir qué unidad de ejecución del sistema operativo le asigna a cada conexión entrante, y esa decisión no es de detalle, determina cuánta memoria cuesta cada conexión extra, qué tan aislada está una conexión de un fallo en otra, y por qué casi todos los motores recomiendan un pool de conexiones en vez de conectar directo. Esta sección responde: si diez usuarios hacen una query al mismo tiempo, ¿qué ocurre exactamente en el servidor? Para responderlo hay que entender tres cosas en orden: **dónde y cómo vive el motor de base de datos en la computadora**, **qué son los procesos y threads del OS**, y **cómo cada motor asigna esas unidades a las conexiones entrantes**, algo que cada tipo de motor, relacional o no, resuelve de forma distinta, como se ve más abajo.

---

### Sección 2.1: Por qué hay que instalar una base de datos y dónde corre {#2-instalar-y-donde-corre}

Una base de datos no es un archivo ni una librería — es un **programa servidor**. Como cualquier programa, cuando lo ejecutas se convierte en uno o más procesos que el OS carga en RAM y el CPU ejecuta. Lo que lo distingue de una calculadora o un editor de texto es que:

- **No termina solo**: sigue corriendo en background indefinidamente, esperando clientes
- **Escucha en un puerto de red**: acepta conexiones TCP (PostgreSQL: 5432, MySQL: 3306)
- **Gestiona archivos propios en disco**: tablas, índices y logs que solo él sabe interpretar

"Instalar" la base de datos significa copiar los binarios del programa al disco y configurar el directorio donde vivirán los archivos de datos. "Levantar" la base de datos significa ejecutar ese binario: el OS lo carga en RAM y se queda corriendo como proceso hasta que lo detengas.

```
En un servidor con PostgreSQL activo:

  DISCO
  ├── /usr/bin/postgres          ← el binario del programa
  └── /var/lib/postgresql/data/
        ├── base/                ← archivos de las tablas
        ├── pg_wal/              ← Write-Ahead Log
        └── postgresql.conf      ← configuración

  RAM
  ├── postmaster (proceso)       ← el "motor" corriendo en memoria
  ├── backend process × N        ← uno por cada conexión activa
  └── buffer pool                ← caché de páginas leídas del disco

  CPU
  └── ejecuta las instrucciones de esos procesos
      turno por turno (o en paralelo si hay varios cores)
```

La **RAM física** la administra el OS, no el programa. Cuando el motor arranca y pide memoria (por ejemplo, para el buffer pool), hace una llamada al sistema (`malloc`, `mmap`) y el kernel le asigna páginas físicas de RAM y actualiza las estructuras internas. El motor nunca toca la RAM física directamente: solo trabaja con direcciones virtuales. El OS hace la traducción.

---

### Sección 2.2: Fundamentos del OS: RAM, memoria virtual y aislamiento {#2-fundamentos-os}

#### La RAM es un arreglo de bytes que el OS reparte entre procesos

La memoria RAM es, físicamente, un arreglo lineal de bytes numerados desde 0. En teoría cualquier programa podría leer cualquier dirección, lo cual sería catastrófico (el navegador podría leer la contraseña que el gestor tiene en su RAM). El OS y el hardware lo impiden.

El OS le da a cada proceso su propio **espacio de direcciones virtuales**: una ilusión de que tiene toda la RAM para sí solo. Lo crucial es que las mismas direcciones virtuales en dos procesos distintos apuntan a **regiones físicas completamente distintas**:

```
Proceso A (Chrome)               Proceso B (VS Code)
Espacio de mem. virtual          Espacio de mem. virtual
┌─────────────────────────┐      ┌─────────────────────────┐
│ 0x0000...  código       │      │ 0x0000...  código       │
│ 0x8000...  heap         │      │ 0x8000...  heap         │
│ 0xFFFF...  stack        │      │ 0xFFFF...  stack        │
└────────────┬────────────┘      └────────────┬────────────┘
             │ traducción virtual→física        │ traducción distinta
             ▼                                 ▼
┌────────────────────────────────────────────────────────────┐
│                   RAM FÍSICA (única, compartida por el OS)  │
│  [código A] [heap A] [código B] [heap B] [stack A] ...     │
└────────────────────────────────────────────────────────────┘

El proceso B no puede acceder a la memoria de A:
su tabla de páginas no tiene traducción a esas direcciones físicas.
```

Las direcciones del diagrama (`0x0000`, `0x8000`, `0xFFFF`) son ilustrativas, no literales. En un sistema real el layout es más complejo (la dirección 0 normalmente ni siquiera está mapeada, justo para detectar punteros nulos, y las posiciones exactas varían con mecanismos como ASLR). Lo que sí es literal es la idea central: la misma dirección virtual en dos procesos distintos traduce a bytes físicos completamente distintos.

#### MMU y page tables: el hardware que hace el aislamiento

La traducción virtual → física la hace el **MMU (Memory Management Unit)**, un chip integrado en el CPU. No es el OS ejecutando código en cada acceso, eso sería demasiado lento. Es hardware puro, y en el caso común corre en nanosegundos gracias a una caché dedicada dentro del propio CPU llamada **TLB (Translation Lookaside Buffer)**, que guarda las traducciones más recientes para no tener que recorrer la page table completa en cada acceso.

El OS crea y mantiene una **page table** (tabla de páginas) por cada proceso. La page table es un mapa: "dirección virtual X de este proceso → dirección física Y en la RAM". El MMU consulta esa tabla (o el TLB, si la traducción ya está cacheada ahí) en cada acceso a memoria, no en cada instrucción, muchas instrucciones (sumar dos registros, comparar valores) nunca tocan memoria y no necesitan traducción. Cuando un proceso intenta acceder a una dirección sin traducción válida, el MMU dispara una excepción (_page fault_). La mayoría de estas excepciones son rutinarias y el OS las resuelve sin matar nada, por ejemplo, cargar una página que aún no estaba en RAM. Solo cuando la dirección es genuinamente inválida (no le corresponde a ese proceso bajo ninguna circunstancia) el OS termina el proceso con `SIGSEGV` (segmentation fault en Linux) o "access violation" (Windows).

Este mecanismo es la razón por la que un bug en el proceso A no puede corromper la memoria del proceso B: la page table de A no tiene entradas que apunten a las páginas físicas de B.

#### Stack y heap: la memoria dentro de un proceso

Dentro del espacio de memoria de un proceso hay dos regiones donde viven los datos de ejecución:

```
Espacio de memoria de un proceso:

  ┌───────────────────────────────────────────────────────────┐
  │  STACK (pila)                                             │
  │  ┌──────────────────────────────────┐                     │
  │  │  frame: función procesar_query() │ ← variables locales │
  │  │    resultado = None              │   parámetros        │
  │  ├──────────────────────────────────┤                     │
  │  │  frame: función handle_request() │                     │
  │  └──────────────────────────────────┘                     │
  │                                                           │
  │  • Cada thread tiene su PROPIO stack (privado, ~8 MB).    │
  │  • Crece y decrece automáticamente con las llamadas.      │
  │  • Al salir de la función, su frame desaparece.           │
  ├───────────────────────────────────────────────────────────┤
  │  HEAP (montículo)                                         │
  │  ┌──────────┐  ┌──────────┐  ┌──────────┐               │
  │  │ objeto A │  │ libre    │  │ objeto C │  ...           │
  │  └──────────┘  └──────────┘  └──────────┘               │
  │                                                           │
  │  • COMPARTIDO por todos los threads del proceso.          │
  │  • Un thread que escribe en el heap → todos los demás     │
  │    threads lo ven de inmediato (permite comunicación,     │
  │    pero también race conditions).                         │
  ├───────────────────────────────────────────────────────────┤
  │  código del programa (solo lectura)                       │
  │  variables globales / estáticas                           │
  └───────────────────────────────────────────────────────────┘
```

---

### Sección 2.3: Process y thread: diferencias reales en todas las dimensiones {#2-process-vs-thread}

Ambos son unidades que el scheduler del OS puede poner a correr en un CPU core. Eso es lo que tienen en común. Lo que los separa es **qué tan independientes son como entidades** y **qué recursos comparten o duplican**:

| Dimensión                   | Proceso                                                                                        | Thread                                                                                                                              |
| --------------------------- | ---------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| **Espacio de memoria**      | Propio y exclusivo. Cada proceso tiene su page table y sus direcciones virtuales son privadas. | Compartido con todos los threads del mismo proceso. No hay aislamiento de memoria entre threads del mismo proceso.                  |
| **Creación**                | `fork()`: el OS duplica la estructura entera del proceso. Costo: ~1-10 ms, varios MB.          | `pthread_create()`: solo se crea un nuevo stack y un registro de ejecución. Costo: ~microsegundos, ~8 MB.                           |
| **Entidad en el OS**        | Entidad de primera clase. Tiene PID propio visible en `ps aux`, Task Manager, etc.             | Vive dentro de un proceso. No puede existir sin un proceso que le dé memoria. En Linux: implementado con `clone()` (light process). |
| **Recursos propios**        | Page table propia. File descriptors propios. Signal handlers propios. Heap propio.             | Comparte page table, file descriptors y heap del proceso padre. Solo tiene stack propio.                                            |
| **Comunicación con pares**  | Difícil: requiere IPC explícito (pipes, sockets, shared memory, semáforos).                    | Trivial: leer/escribir una variable del heap es suficiente.                                                                         |
| **Aislamiento ante fallos** | Total: un crash (segfault) en un proceso no puede tocar la RAM de otro proceso.                | Parcial: un crash que corrompe el heap shared afecta a todos los threads del mismo proceso.                                         |
| **Costo en RAM**            | Alto: duplica page table, file descriptors, señales. Decenas de MB por proceso.                | Bajo: solo necesita un stack propio (~8 MB). Comparte todo lo demás.                                                                |

**Un thread no puede existir sin un proceso** porque necesita un espacio de memoria donde vivir: una page table que mapee sus direcciones virtuales a RAM física, un heap donde alocar objetos, file descriptors para E/S. El proceso provee todo eso. El thread solo aporta su propio stack y su propio contador de instrucciones (en qué instrucción del código está). Sin proceso, ninguna dirección de memoria es válida.

---

### Sección 2.4: Relación padre-hijo entre procesos: cómo un proceso "crea" otro {#2-padre-hijo}

Un punto que genera mucha confusión: cuando se dice que postmaster "tiene" procesos, no significa que los procesos vivan _dentro_ de postmaster como los threads viven dentro de un proceso. Lo que ocurre es una **relación padre-hijo por spawning** (creación):

```
postmaster llama a fork()
  │
  │  El OS crea un proceso HIJO completamente nuevo:
  │  • Le asigna un PID propio
  │  • Le crea una page table nueva (copia de la del padre al nacer,
  │    pero con traducciones a páginas físicas distintas → copy-on-write)
  │  • A partir de ese instante, padre e hijo son INDEPENDIENTES
  │  • El hijo NO puede leer la memoria del padre
  │
  └──→ backend process (PID hijo)
         Trabaja de forma independiente.
         Si muere, muere solo — el padre recibe una señal del OS
         pero su propio estado no se ve afectado.
```

La relación padre-hijo significa:

- El padre sabe qué hijos creó (los registra con sus PIDs)
- Si un hijo muere, el OS notifica al padre con la señal `SIGCHLD`
- El padre puede monitorearlos y reiniciarlos si fallan

Lo que **no** significa:

- El padre no puede leer la memoria del hijo directamente
- El hijo no "vive dentro" del padre en ningún sentido de memoria
- Es una jerarquía de supervisión, no de contención

**Analogía:** es como un gerente que contrató empleados. El gerente sabe quiénes son, los supervisa y puede reemplazarlos si uno falta. Pero cada empleado trabaja en su propio espacio y el gerente no "contiene" a los empleados físicamente.

Contrasta esto con los threads en MySQL, donde sí existe contención real:

```
mysqld (proceso único)
  │
  │  pthread_create() → no crea un proceso nuevo
  │  Solo crea una nueva "corriente de ejecución"
  │  dentro del MISMO espacio de memoria
  │
  ├── thread A  ─┐
  ├── thread B  ─┤  los tres comparten el mismo heap de mysqld
  └── thread C  ─┘  si uno corrompe una estructura, todos sufren
```

---

### Sección 2.5: Paralelismo real y concurrencia {#2-paralelismo}

Ambos procesos y threads son unidades que el scheduler del OS asigna a CPU cores. El límite del trabajo simultáneo real son los cores físicos:

```
Un solo core — CONCURRENCIA (no paralelismo real):

  Tiempo:  ────────────────────────────────────────────→
  Core 0:  [Proc.A]→[Proc.B]→[Proc.A]→[Proc.C]→[Proc.A]
                   ↑          ↑          ↑
              context switch (guarda/restaura estado del proceso)

  Solo UN proceso ejecuta instrucciones en cada instante.
  El OS alterna cada ~1-5 ms. Parece simultáneo pero es entrelazado.

Cuatro cores — PARALELISMO REAL:

  Tiempo:  ────────────────────────────────────────────→
  Core 0:  [─────────── Proceso A ───────────────────]
  Core 1:  [─────────── Proceso B ───────────────────]
  Core 2:  [─────────── Proceso C ───────────────────]
  Core 3:  [─────────── Proceso D ───────────────────]

  Cuatro procesos ejecutan instrucciones LITERALMENTE al mismo tiempo.
```

Con 8 cores y 200 conexiones en PostgreSQL, solo 8 backend processes ejecutan instrucciones de CPU en cualquier instante. Los otros 192 están en alguno de estos estados:

- **Bloqueados en I/O**: esperando que el disco lea una página → el OS los saca del core y pone otro proceso a trabajar
- **Esperando un lock**: pausados hasta que otra transacción libere un recurso
- **En la cola del scheduler**: listos para ejecutar, esperando su turno de CPU

**¿Por qué tener 200 procesos si solo hay 8 cores?** Porque la mayoría del tiempo un proceso de DB no necesita CPU: está esperando I/O. El OS aprovecha esas esperas para dar el core a quien sí tiene trabajo de cómputo en ese momento. Los cores casi nunca están ociosos.

**Context switch:** cuando el OS alterna entre procesos, guarda el estado completo del que sale (registros del CPU, stack pointer, program counter) y restaura el del que entra. Este guardado/restauración cuesta microsegundos. Con cientos de conexiones y muchos context switches por segundo el costo se acumula — una razón más para no abrir miles de conexiones directas a la base de datos.

---

### Sección 2.6: Modelo de concurrencia por motor {#2-modelo-por-motor}

| Motor          | Modelo por conexión/operación                                                                                               | Proceso raíz                 | Dónde le cuesta más                                                                                                                                                                                                                       |
| -------------- | --------------------------------------------------------------------------------------------------------------------------- | ---------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **PostgreSQL** | 1 proceso OS por conexión                                                                                                   | `postmaster`                 | Muchas conexiones directas (sin pool externo) son caras: cada una es un proceso completo, con su propio overhead de memoria y de `fork()`.                                                                                                |
| **MySQL**      | 1 thread por conexión                                                                                                       | `mysqld` (proceso único)     | Menos overhead por conexión que PostgreSQL, pero un bug que corrompe memoria compartida puede afectar a todos los threads del mismo proceso.                                                                                              |
| **SQL Server** | Pool de threads gestionado por SQLOS                                                                                        | `sqlservr.exe`               | El propio SQLOS decide el scheduling entre queries; cargas mixtas mal configuradas pueden generar contención de threads internos, no solo de conexiones.                                                                                  |
| **Oracle**     | 1 proceso por conexión, o _shared server_ (N conexiones → 1 proceso)                                                        | N/A                          | El modo dedicado hereda el mismo costo por conexión que PostgreSQL; _shared server_ lo evita a cambio de más complejidad de configuración.                                                                                                |
| **SQLite**     | Sin servidor propio                                                                                                         | El proceso del cliente mismo | No hay "conexión" que atender: el costo de concurrencia se paga como locks de archivo entre procesos del mismo host, no como conexiones de red.                                                                                           |
| **MongoDB**    | Pool de threads por nodo (uno o varios por conexión, gestionados internamente), sin proceso central que coordine el clúster | `mongod`                     | La concurrencia dentro de un nodo es barata; lo caro es coordinar entre nodos cuando una operación cruza shards (ver la nota de transacciones multi-shard en Bloque 8).                                                                   |
| **Redis**      | Un solo hilo con event loop; todos los comandos se ejecutan uno por uno, nunca en paralelo                                  | `redis-server`               | Cualquier comando que tarde (un `KEYS *` sobre millones de claves, o un `fsync` bloqueante) congela a todos los demás clientes, porque no hay otro hilo que los atienda mientras tanto.                                                   |
| **Cassandra**  | Pool de threads por nodo, sin proceso central: cada nodo del anillo es independiente                                        | JVM (proceso de Cassandra)   | La concurrencia dentro de un nodo es barata; lo caro es cualquier operación que exija tocar varios nodos a la vez, porque no hay coordinador que la reparta (ver la comparación de distribución en `guia_panorama_bases_de_datos.md` §4). |
| **Neo4j**      | Pool de threads sobre la JVM; cada transacción toma los locks de nodo/relación que necesita                                 | JVM (proceso de Neo4j)       | La escritura concurrente sobre el mismo nodo o relación se serializa por el lock manager; en un grafo muy conectado (muchas transacciones tocando los mismos nodos "hub"), esa contención puede volverse el cuello de botella, no la CPU. |
| **ClickHouse** | Pool de threads por consulta, con ejecución vectorizada: una sola query puede usar varios threads en paralelo internamente  | Proceso `clickhouse-server`  | El costo no está en atender muchas conexiones (eso es barato), sino en que demasiadas queries concurrentes compitan por los mismos threads de ejecución vectorizada, degradando la latencia de todas a la vez.                            |

#### PostgreSQL: un proceso OS por conexión

Cuando una aplicación se conecta, el proceso principal **postmaster** llama a `fork()` y crea un proceso hijo nuevo (**backend process**) dedicado exclusivamente a esa conexión, incluyendo también procesos internos permanentes como el autovacuum launcher, el WAL writer y el checkpointer. La ventaja es aislamiento total ante fallos: un crash de un backend process solo mata esa conexión. El costo es que `fork()` toma tiempo y memoria, razón por la que PostgreSQL necesita connection pooling externo (PgBouncer) en aplicaciones que abren y cierran conexiones constantemente. El árbol completo de procesos, con PIDs y responsabilidades de cada uno, está en [`postgresql_caso_estudio.md` §1](postgresql_caso_estudio.md#1-arquitectura).

#### MySQL InnoDB: un thread por conexión

MySQL tiene un único proceso del OS (`mysqld`). Cada conexión genera un **thread** dentro de ese proceso. Todos los threads comparten el mismo espacio de memoria de mysqld.

```
mysqld (PID 2000, único proceso de MySQL)
  │
  ├── thread  ← conexión del cliente A
  ├── thread  ← conexión del cliente B
  ├── thread  ← conexión del cliente C
  └── thread  ← tareas internas (purge, flush, etc.)

  Todos comparten el mismo heap de mysqld.
```

**Ventaja:** crear un thread cuesta microsegundos (vs milisegundos de `fork()`). MySQL puede manejar miles de conexiones sin necesitar un pooler externo.

**Inconveniente:** un bug en un thread que corrompa el heap shared puede afectar a toda la instancia. En código maduro esto rara vez ocurre, pero el riesgo teórico existe.

#### SQL Server: pool de threads con scheduler propio

SQL Server implementa su propio scheduler de threads (llamado SQLOS), sin delegar esa tarea al OS. Un pool de threads atiende las conexiones. SQLOS controla exactamente cuándo y cómo se ejecutan los threads, optimizando el uso de cada core en servidores con muchos CPUs.

---

### Sección 2.7: Conexión, proceso/thread y transacción: cómo se relacionan {#2-conexion-proceso-transaccion}

Aquí conviene aclarar la notación que aparece en otras partes de la guía. Cuando se escribe **"proceso/thread"** con barra, es una abreviatura para "el mecanismo que atiende la conexión, sea proceso (PG) o thread (MySQL)". No significa que sean lo mismo — son distintos en casi todo, pero comparten la regla que sigue.

**La cadena completa:**

```
1 conexión del cliente
    └──→ 1 proceso OS (PostgreSQL)
         ó 1 thread dentro de mysqld (MySQL)
              └──→ ejecuta queries SECUENCIALMENTE
                       └──→ solo puede tener 1 transacción activa a la vez
```

**Por qué solo una transacción por conexión:** una transacción es el estado `BEGIN … COMMIT/ROLLBACK` de esa sesión. Ese estado vive en el proceso/thread que atiende la conexión, y como ese proceso/thread ejecuta instrucciones de forma secuencial, solo puede estar "dentro de un BEGIN" o "fuera de él" en cada momento. No hay dos estados simultáneos.

```
backend process (PID 1001):

  estado = IDLE          ← sin BEGIN activo

  cliente envía BEGIN
  estado = IN_PROGRESS   ← transacción abierta, XID 7832

  cliente envía UPDATE ...
  [ejecuta la query, modifica páginas en el buffer pool]
  [anota en el WAL: XID 7832 modificó estas páginas]

  cliente envía COMMIT
  [marca el WAL como committed, libera locks]
  estado = IDLE           ← vuelta al inicio
```

Para tener dos transacciones en paralelo desde la misma aplicación se necesitan **dos conexiones** → dos procesos (PG) o dos threads (MySQL) → dos variables de estado independientes.

**Intercalado entre conexiones distintas:**

```
Tiempo →   T1 (conn 1 / proc 1001)    T2 (conn 2 / proc 1002)
────────── ────────────────────────   ────────────────────────
t1         BEGIN
t2                                    BEGIN
t3         UPDATE cuenta id=1
t4                                    SELECT productos   ← no choca, avanza
t5         UPDATE cuenta id=2
t6                                    COMMIT
t7         COMMIT
```

T1 y T2 avanzan en paralelo porque tocan datos distintos. Si necesitaran la misma fila, el Lock Manager decide quién espera. Cada columna del diagrama es un proceso OS diferente (en PostgreSQL) o un thread diferente (en MySQL).

**Condiciones de carrera y por qué importa la concurrencia:**

El intercalado de operaciones entre transacciones puede generar resultados incorrectos incluso con un solo core:

```
T1 lee saldo=100, T2 lee saldo=100
T1 suma 50 → escribe 150
T2 suma 30 → escribe 130   ← pisó el cambio de T1

Resultado real: 130. Resultado correcto: 180.
→ Lost update (actualización perdida).
```

Esto ocurre tanto en concurrencia (entrelazado en un core) como en paralelismo real (varios cores). Los **locks** y los **niveles de aislamiento** que se ven en secciones posteriores protegen contra esto.

El recorrido completo (cliente → red → postmaster → fork() → memoria compartida → CPU scheduling → disco), con PIDs, XIDs y el reparto exacto de responsabilidades entre el OS y el motor, está trazado paso a paso como diagrama integral en [`postgresql_caso_estudio.md` §1](postgresql_caso_estudio.md#1-arquitectura).

---

## Bloque 3: Parser, planner y executor: cómo se decide y ejecuta una query {#3-planner}

**El problema universal:** una petición no llega al motor como una acción, llega como texto o como una estructura declarativa (SQL, un filtro de MongoDB, un patrón de Cypher). Alguien tiene que convertir eso en una secuencia concreta de pasos ejecutables, y esa conversión no es mecánica: para la misma petición pueden existir varias formas válidas de ejecutarla (con o sin índice, en qué orden combinar tablas, cuántos threads usar), y elegir mal puede significar la diferencia entre milisegundos y minutos. Esto solo es un problema real para motores con un lenguaje de consulta declarativo (relacional, documento, grafo, columnar); un motor clave-valor puro no lo tiene, porque el comando ya especifica exactamente qué hacer, no hay nada que planificar.

Tres piezas resuelven esto, en orden:

1. **Parser:** convierte el texto (o la estructura declarativa) en un árbol de sintaxis (AST), una representación en memoria de qué pidió el cliente, sin decidir todavía cómo obtenerlo.
2. **Planner / optimizer:** toma ese árbol y genera uno o más planes de ejecución candidatos, cada uno una estrategia concreta (qué índice usar, en qué orden combinar los datos, qué algoritmo de join), y elige el más barato según una estimación de costo.
3. **Executor:** recorre el plan elegido, nodo por nodo, y lo convierte en llamadas reales a las capas de almacenamiento (locks, MVCC, buffer pool).

```
Texto/estructura declarativa
        │
        ▼
   PARSER  →  AST (qué pidió el cliente)
        │
        ▼
   PLANNER  →  varios planes candidatos → estima el costo de cada uno → elige el más barato
        │
        ▼
   EXECUTOR  →  ejecuta el plan elegido, nodo por nodo, contra el almacenamiento real
```

El costo de estimar mal no es teórico: un plan que asume 10 filas cuando en realidad hay 10 millones puede elegir un algoritmo que en la práctica es miles de veces más lento que la alternativa. Por eso la mayoría de los motores con planner mantienen **estadísticas** sobre sus tablas (distribución de valores, cantidad de filas, histogramas), y esas estadísticas se vuelven obsoletas con el tiempo si nadie las actualiza.

### Sección 3.1: Cómo lo resuelve cada motor {#3-por-motor}

| Motor            | Cómo decide el plan                                                                                                                                                                                                                                                                                  | Modelo de ejecución                                                                                          | Dónde le cuesta más                                                                                                                                                                                                                                                                                   |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **PostgreSQL**   | Optimizador basado en costos (CBO): usa estadísticas de `pg_statistic` para estimar filas y elegir entre nested loop, hash join o merge join. Con muchas tablas en el join, la búsqueda del orden óptimo es combinatoria y el planner cambia a un algoritmo genético por encima de `geqo_threshold`. | Volcano/iterador: cada nodo del plan pide la siguiente fila al nodo de abajo (modelo _pull_), fila por fila. | Estadísticas obsoletas (sin `ANALYZE` reciente) llevan a estimaciones de filas muy equivocadas, y de ahí a elegir un algoritmo de join pésimo para el volumen real de datos.                                                                                                                          |
| **MySQL InnoDB** | Optimizador basado en costos, históricamente más simple que el de PostgreSQL u Oracle: búsqueda greedy del orden de join en vez de explorar todas las combinaciones. Soporta hash join desde 8.0.18, antes solo nested loop.                                                                         | Iterador, similar a PostgreSQL.                                                                              | Con muchas tablas, la búsqueda greedy puede quedarse en un orden de join subóptimo; sin índice adecuado, cae a _full table scan_ sin aviso explícito salvo revisar `EXPLAIN`.                                                                                                                         |
| **SQL Server**   | Optimizador basado en costos con **plan cache**: la primera ejecución compila y guarda el plan; ejecuciones futuras con los mismos parámetros lo reutilizan.                                                                                                                                         | Modelo de operadores en árbol, con soporte nativo para planes paralelos entre cores.                         | _Parameter sniffing_: el plan se optimiza para los valores de parámetro de la primera ejecución, y si la distribución de datos varía mucho según el parámetro, ese mismo plan cacheado puede ser pésimo para llamadas posteriores.                                                                    |
| **Oracle**       | CBO con estadísticas extendidas (histogramas) y **SQL Plan Baselines** para fijar planes ya validados y evitar que un cambio de estadísticas empeore un plan que ya funcionaba bien.                                                                                                                 | Iterador, con soporte para planes paralelos.                                                                 | _Hard parse_ (optimizar desde cero) es costoso; sin bind variables, cada query con literales distintos fuerza un hard parse nuevo, lo que en cargas OLTP de alto volumen puede saturar la CPU dedicada a planning.                                                                                    |
| **SQLite**       | Planner mucho más simple: sin `ANALYZE`, usa heurísticas fijas sobre los índices disponibles; con `ANALYZE`, puebla `sqlite_stat1` y hace una estimación de costo básica. No hay algoritmo genético ni búsqueda exhaustiva de orden de join.                                                         | Iterador simple, acorde a un motor embebido de un solo archivo.                                              | Sin estadísticas (`ANALYZE` nunca ejecutado), el planner puede elegir un índice equivocado en tablas con distribución de datos muy asimétrica.                                                                                                                                                        |
| **MongoDB**      | No hay optimizador de costos clásico: para una query nueva, el planner genera varios planes candidatos (uno por índice viable) y los **corre en paralelo durante un período de prueba corto**, el que termina primero gana y su plan se cachea para queries con la misma forma.                      | Ejecución por etapas sobre el pipeline de agregación, o acceso directo por índice para `find()` simple.      | El _plan cache_ puede quedar obsoleto si la distribución de datos cambia; pipelines de agregación complejos (`$lookup`, múltiples `$group`) sin índice que respalde la primera etapa fuerzan un escaneo completo de la colección.                                                                     |
| **Cassandra**    | No hay optimizador en el sentido relacional: CQL exige que toda query incluya la partition key, o el motor obliga a especificar `ALLOW FILTERING` explícitamente para advertir que va a escanear entre particiones. No hay joins que planificar porque CQL no los soporta.                           | Ejecución directa contra el/los nodos responsables de la partition key.                                      | El costo no es una mala decisión del planner en tiempo de ejecución, es una decisión de diseño de datos: una query que no calza con la partition key obliga a `ALLOW FILTERING`, que en la práctica es un escaneo distribuido entre nodos.                                                            |
| **Neo4j**        | Planner basado en costos para Cypher: estima cardinalidad usando estadísticas del grafo (cuántos nodos por etiqueta, selectividad de propiedades indexadas) y decide desde qué nodo "anclar" el patrón de búsqueda y en qué orden expandir las relaciones.                                           | Iterador sobre el patrón, expandiendo relación por relación desde el nodo ancla.                             | Un patrón multi-salto sin ningún nodo indexado como punto de partida obliga a anclar en un _label scan_ completo, y el costo crece con cada salto adicional sin índice que lo acote.                                                                                                                  |
| **ClickHouse**   | Optimizador orientado a OLAP columnar: su prioridad es _predicate pushdown_ (filtrar antes de leer) y _partition pruning_ (descartar partes completas de disco que no pueden cumplir el filtro), además de decidir qué columnas leer (nunca lee columnas que la query no pide).                      | Ejecución vectorizada: procesa bloques de miles de valores por columna a la vez, no fila por fila.           | Los joins son históricamente el punto débil: el motor construye una tabla hash del lado derecho en memoria, así que joins entre tablas muy grandes, o mal ordenados (lado grande a la derecha), pueden agotar memoria o degradar el rendimiento mucho más que un filtro o una agregación equivalente. |

El detalle interno de cómo PostgreSQL genera y elige un plan, con un `EXPLAIN ANALYZE` leído paso a paso, está en [`postgresql_caso_estudio.md` §8](postgresql_caso_estudio.md#8-planner-interno).

---

## Bloque 4: Memoria, páginas, caché de páginas y write-ahead logging (WAL) {#4-wal}

**El problema universal detrás de toda esta sección:** cualquier motor que garantice durabilidad tiene que resolver dos cosas que están en tensión entre sí. Primero, necesita mantener en RAM las páginas que se leen y escriben con frecuencia, porque el disco es entre mil y cien mil veces más lento. Segundo, necesita que un cambio confirmado sobreviva un corte de luz o un crash sin pagar el costo de una escritura aleatoria a disco en cada commit. La solución casi universal a la segunda parte es algún tipo de log de escritura adelantada (el WAL de PostgreSQL, el redo log de MySQL/Oracle, el journal de MongoDB, el commit log de Cassandra, el AOF de Redis): un log secuencial, barato de escribir, que registra el cambio antes de que ese cambio llegue a las estructuras de datos reales. Esta sección responde cuatro preguntas en orden, comparando cómo lo resuelve cada motor: **qué son las páginas de base de datos y por qué existen**, **dónde vive la memoria compartida entre conexiones**, **qué es el buffer pool y cómo funciona**, y **para qué sirve el WAL y cómo garantiza que los datos sobrevivan a un crash**. El mecanismo exacto de PostgreSQL para cada una está enlazado a `postgresql_caso_estudio.md` donde aplica.

---

### Sección 4.1: Tres términos distintos que incluyen la palabra "página" {#4-tres-terminos-pagina}

Antes de cualquier otra cosa hay que desambiguar, porque el término "página" aparece en tres contextos completamente distintos en esta guía y mezclarlos genera confusión:

| Término                   | Qué es                                                                                                                           | Resuelve                                                                                                             |
| ------------------------- | -------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| **Page table** (Bloque 2) | Tabla de traducción del OS, una por proceso. No es un bloque de memoria, es una estructura de datos (un mapa).                   | Mapear direcciones virtuales a direcciones físicas de RAM; es la estructura que el MMU consulta en cada instrucción. |
| **OS page**               | Bloque de 4 KB de RAM física. La RAM está dividida en estos bloques fijos; el OS asigna RAM en múltiplos de 4 KB.                | La unidad con la que el MMU hace la traducción y con la que el OS gestiona y asigna la memoria física.               |
| **DB page**               | Bloque de 8-16 KB en disco, gestionado por el motor, que contiene filas de una tabla. Completamente independiente de la OS page. | La unidad con la que el motor lee y escribe datos del disco.                                                         |

La **page table** y las **OS pages** están relacionadas entre sí: la page table mapea bloques virtuales de 4 KB (OS pages virtuales) a bloques físicos de 4 KB (OS pages físicas). Las **DB pages** son una decisión de diseño separada del motor, sin relación directa con el sistema de memoria virtual.

---

### Sección 4.2: OS page: por qué el MMU trabaja en bloques de 4 KB {#4-os-page}

En Bloque 2 vimos que el MMU traduce cada dirección virtual a física consultando la page table. Pero ¿por qué traduce en bloques de 4 KB y no byte a byte?

**El problema de escala:** si la page table tuviera una entrada por cada byte del espacio de direcciones virtuales de 128 TB (lo que puede usar un proceso de 64 bits), la sola page table ocuparía 128 TB de RAM. Imposible. Si en cambio tiene una entrada por cada bloque de 4 KB, necesita 128 TB / 4 KB = 32 mil millones de entradas. Los CPUs modernos resuelven esto con page tables de varios niveles (solo se crean entradas para las regiones de memoria que el proceso realmente usa), pero el tamaño base de 4 KB ya reduce el problema por un factor de 4.096.

**La consecuencia práctica:** el OS asigna RAM a los procesos en múltiplos de 4 KB. Si un proceso pide 1 byte con `malloc()`, el OS le asigna 4 KB mínimo internamente. Cuando el OS hace swap (mueve RAM al disco porque está llena), mueve exactamente una OS page de 4 KB a la vez.

---

### Sección 4.3: DB page: el problema del disco y por qué el motor define su propio bloque {#4-db-page}

Este es un concepto completamente separado de las OS pages. Para entender por qué el motor define su propio tamaño de bloque (8 KB, 16 KB), hay que entender cómo funciona el acceso al disco.

#### El disco no se lee byte a byte — el seek time domina

En la RAM, leer el byte en la dirección `0x1000` o en `0x9000` cuesta exactamente lo mismo: nanosegundos, sin importar la distancia entre ellos.

En el disco, el costo tiene dos componentes muy distintos:

```
Costo de leer del disco:

  Tiempo total = seek time + transfer time

  seek time    = tiempo en POSICIONARSE en el lugar correcto
                 HDD: 5-10 ms (mover el cabezal físico — mecánico)
                 SSD: ~0.1 ms  (electrónico, sin partes móviles)

  transfer time = tiempo en TRANSFERIR los bytes una vez posicionado
                 HDD: ~100 MB/s  → leer 8KB cuesta ~0.08 ms
                 SSD: ~500 MB/s  → leer 8KB cuesta ~0.016 ms

  En un HDD: seek time (5-10 ms) es 60-100× mayor que el transfer (0.08 ms)
  → el seek DOMINA totalmente

  Consecuencia:
    Leer 1 byte  del disco ≈ 5-10 ms   (seek domina)
    Leer 8 KB    del disco ≈ 5-10 ms   (seek + 0.08 ms transfer)
    Leer 64 KB   del disco ≈ 5-10 ms   (seek + 0.64 ms transfer)
    → Leer 1 byte o 8 KB cuesta prácticamente lo mismo en tiempo.
```

**La conclusión directa:** si ya pagaste el costo de posicionarte en el disco (el seek), tiene sentido traer un bloque grande de datos — el tiempo marginal de transferir más bytes es casi cero comparado con el seek. No tiene sentido pedir al disco que te dé 1 byte si puedes pedir 8 KB al mismo costo.

#### La solución: el motor organiza el disco en páginas fijas

El motor divide sus archivos de datos en bloques de tamaño fijo llamados **DB pages**. Siempre lee y escribe páginas completas — nunca filas individuales, aunque solo necesite cambiar 1 byte de una fila.

```
Archivo de tabla en disco (PostgreSQL, páginas de 8 KB):

  ┌──────────┬──────────┬──────────┬──────────┬──────────┐
  │ Página 0 │ Página 1 │ Página 2 │ Página 3 │ Página 4 │
  │  8 KB    │  8 KB    │  8 KB    │  8 KB    │  8 KB    │
  │ fila 1   │ fila 8   │ fila 15  │ fila 22  │ fila 29  │
  │ fila 2   │ fila 9   │ fila 16  │ fila 23  │  ...     │
  │ fila 3   │ fila 10  │ fila 17  │ fila 24  │          │
  │ fila 4   │ fila 11  │ fila 18  │ fila 25  │          │
  │ fila 5   │ fila 12  │ fila 19  │ fila 26  │          │
  │ fila 6   │ fila 13  │ fila 20  │ fila 27  │          │
  │ fila 7   │ fila 14  │ fila 21  │ fila 28  │          │
  └──────────┴──────────┴──────────┴──────────┴──────────┘
       ↑
  Para leer fila 3: el motor lee toda la Página 0 (8 KB) completa.
  No existe la operación "leer solo los bytes de fila 3".
  Para modificar 1 byte de fila 3: el motor carga la Página 0, la
  modifica en RAM, y eventualmente la escribe de vuelta completa al disco.
```

**¿Por qué 8 KB y no los mismos 4 KB que usa el OS?**

El OS quiere páginas pequeñas (4 KB) para gestionar la RAM con granularidad fina; si asignara en bloques de 8 KB y un proceso pide 4.001 bytes, desperdiciaría 3.999 bytes. El motor, en cambio, quiere páginas más grandes porque cada página contiene filas: con 8 KB caben el doble de filas que con 4 KB, y leer 8 KB del disco cuesta prácticamente lo mismo en tiempo que leer 4 KB (el seek domina).

Este tamaño de página fijo es, en sí mismo, una decisión de motores con almacenamiento tipo B-tree/heap sobre disco (relacional y documento). No todos los tipos de motor lo necesitan de la misma forma:

| Motor                      | Tamaño de DB page                                                                                                                                                        |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **PostgreSQL**             | 8 KB, fijo, configurable solo al compilar                                                                                                                                |
| **MySQL InnoDB**           | 16 KB por defecto, configurable: 4K, 8K, 16K, 32K, 64K                                                                                                                   |
| **SQL Server**             | 8 KB, fijo                                                                                                                                                               |
| **Oracle**                 | 8 KB por defecto, configurable: 2K, 4K, 8K, 16K, 32K                                                                                                                     |
| **MongoDB (WiredTiger)**   | Sin tamaño fijo único: usa páginas B-tree de tamaño variable, con un límite configurable (`internal_page_max`/`leaf_page_max`, 4 KB/32 KB por defecto)                   |
| **Cassandra / ClickHouse** | No aplica el mismo concepto: no organizan el disco en páginas de tamaño fijo, sino en archivos inmutables completos (SSTables o parts) escritos de una vez, ver Bloque 6 |

La estructura interna exacta de una DB page (encabezado, mapa de slots, filas) está desglosada byte a byte en [`postgresql_caso_estudio.md` §2](postgresql_caso_estudio.md#2-memoria-paginas).

---

### Sección 4.4: Dónde vive la memoria compartida: proceso vs. thread {#4-memoria-compartida}

En motores con modelo de concurrencia por proceso (PostgreSQL), el buffer pool, la lock table y el WAL buffer tienen que vivir en una región de RAM visible por todos los procesos backend, algo que ningún proceso tiene por defecto (cada uno tiene su propia page table, aislada por el MMU). El OS resuelve esto con **shared memory** (`mmap`/`shmget`): mapea la misma región física de RAM en el espacio de direcciones virtuales de cada proceso, de modo que todos ven los mismos bytes físicos sin necesitar coordinación explícita.

En motores con modelo de concurrencia por thread (MySQL), este problema no existe: los threads de `mysqld` ya comparten el mismo heap del proceso por diseño, así que el buffer pool es simplemente una región de ese heap, sin mecanismo especial del OS de por medio.

```
mysqld (proceso único)
  └── heap de mysqld
        ├── InnoDB buffer pool   ← todos los threads acceden directamente
        ├── lock tables          ← mismo heap
        └── redo log buffer      ← mismo heap
```

El mecanismo completo de memoria compartida en PostgreSQL (`mmap`, `MAP_SHARED`, el diagrama de direcciones virtuales por proceso, y cuánta RAM reserva `shared_buffers`) está en [`postgresql_caso_estudio.md` §2](postgresql_caso_estudio.md#2-memoria-paginas).

---

### Sección 4.5: Buffer pool: la caché de páginas del motor {#4-buffer-pool}

Ahora que sabemos dónde vive (shared memory en PG, heap de mysqld en MySQL), entendemos qué hace y por qué existe.

#### Por qué existe: el disco es miles de veces más lento que la RAM

| Medio          | Latencia aproximada                    | Comparado con RAM          |
| -------------- | -------------------------------------- | -------------------------- |
| **RAM (DRAM)** | ~100 nanosegundos (0.0001 ms)          | referencia base            |
| **SSD NVMe**   | ~0.1 ms                                | ~1.000× más lento          |
| **SSD SATA**   | ~0.1 ms (similar en lecturas pequeñas) | ~1.000× más lento          |
| **HDD**        | ~5-10 ms                               | ~50.000-100.000× más lento |

Una query que lee 1.000 páginas tarda ~0.1 ms si todas están en RAM (buffer pool), contra ~5.000 ms (5 segundos) si todas están en HDD. El buffer pool existe precisamente para mantener en RAM las páginas que se usan con frecuencia, evitando ir al disco en cada operación. Esto es universal para cualquier motor con almacenamiento en disco, no solo relacional: es la misma razón por la que MongoDB (WiredTiger) mantiene su propia caché de páginas, y por la que Redis directamente elige vivir 100% en RAM en vez de tratarla como caché de algo más lento.

#### Cómo funciona un buffer pool en general

Es una región de RAM dividida en slots del tamaño de una página de disco. El motor mantiene un directorio `(tabla, página) → slot` para saber qué hay cacheado. Un **buffer hit** (la página ya está en un slot) resuelve la lectura en RAM, en nanosegundos. Un **buffer miss** obliga a leerla del disco y copiarla a un slot, bloqueando al proceso o thread que la pidió mientras el OS hace la I/O.

Cuando una escritura modifica una página ya cacheada, el motor no la manda a disco de inmediato: la marca como **dirty** y sigue trabajando en RAM, confiando en que el WAL ya garantizó la durabilidad de ese cambio. Las páginas dirty se escriben a disco de forma diferida, en batch, durante el checkpoint, no en cada COMMIT, porque el disco es lento y hacerlo en cada commit haría las transacciones lentas.

Cuando el buffer pool se llena y hay que traer una página nueva, el motor tiene que decidir qué expulsar. La familia de algoritmos más común (clock-sweep en PostgreSQL, variantes de LRU en MySQL InnoDB y en la caché de WiredTiger) prioriza mantener las páginas de acceso más reciente y expulsar primero las que llevan más tiempo sin tocarse. Expulsar una página limpia es casi gratis porque el disco ya tiene la versión vigente; expulsar una dirty exige escribirla primero, lo que hace que un buffer pool pequeño cueste latencia real por más misses y más evictions forzadas.

El mismo problema de eviction existe, en espíritu, en cualquier motor que use RAM como caché limitada de algo más grande: Redis, que trata toda su RAM como el dataset (no como caché de un disco autoritativo), enfrenta la misma decisión cuando llega a `maxmemory` y tiene que elegir qué expulsar, configurable con políticas como `allkeys-lru` o `allkeys-lfu`. La diferencia importante es que en PostgreSQL o MySQL expulsar una página limpia no pierde datos, porque el disco siempre tiene la versión vigente; en Redis sin persistencia, expulsar una clave (o que el proceso muera) sí puede perder ese dato para siempre, porque no hay "disco de respaldo" detrás de esa RAM salvo que se configure explícitamente (ver AOF/RDB en la comparación de WAL por motor).

El directorio de slots, el algoritmo clock-sweep paso a paso y un ejemplo numérico completo de una página dirty están en [`postgresql_caso_estudio.md` §3](postgresql_caso_estudio.md#3-buffer-pool).

#### Por qué el motor no usa el page cache del OS

El OS tiene su propio caché de archivos en RAM (el "page cache" del kernel): cuando un programa lee un archivo, el OS guarda los datos en RAM por si se vuelven a necesitar. El motor podría simplemente confiar en eso.

Pero no lo hace, porque necesita **control directo sobre el orden de escritura**:

- El WAL debe llegar al disco _antes_ que las páginas de datos (la regla WAL). Si el motor dejara al OS decidir cuándo hacer flush, el OS podría escribir una página de datos antes que su entrada WAL — violando la garantía de durabilidad.
- El motor conoce mejor qué páginas son "calientes" (frecuentemente accedidas) que el OS, que no entiende de transacciones ni de MVCC.

Por eso la mayoría de los motores abren sus archivos de datos con `O_DIRECT` (Linux) para hacer bypass del OS page cache y tener control total sobre las escrituras.

---

### Sección 4.6: WAL: Write-Ahead Log {#4-wal-detalle}

#### El problema que resuelve

El buffer pool está en RAM. Si el servidor pierde energía, todo lo que está en RAM (incluyendo todas las páginas dirty) se pierde. Los datos que el cliente ya recibió como confirmados (COMMIT) podrían desaparecer.

La solución obvia sería escribir cada página al disco en cada COMMIT. Pero eso es extremadamente lento: una transacción que modifica 100 páginas tendría que esperar 100 operaciones de escritura aleatoria al disco.

Hay otro problema: si el crash ocurre en medio de escribir una página de 8 KB al disco (el OS escribió los primeros 4 KB pero no los últimos), la página queda en un estado inválido parcialmente escrito.

#### La solución: un log secuencial que registra el cambio primero

El WAL es un archivo especial donde el motor escribe **una descripción de cada cambio** antes de modificar las páginas de datos. Es un archivo de escritura estrictamente secuencial — siempre se añade al final, nunca en posiciones aleatorias.

```
Por qué la escritura secuencial es mucho más rápida:

  Escritura aleatoria (páginas de datos):
    Cada página puede estar en cualquier parte del archivo
    HDD: cada escritura requiere un seek → 5-10 ms cada una
    100 páginas modificadas = ~500-1000 ms solo en seeks

  Escritura secuencial (WAL):
    Siempre al final del archivo, el cabezal ya está posicionado
    100 entradas WAL = ~1-5 ms en total

  En SSD la diferencia es menor, pero el WAL sigue siendo más
  eficiente porque genera una sola operación de flush grande
  en lugar de muchas escrituras pequeñas dispersas.
```

#### El WAL buffer: la etapa en RAM

El motor no escribe directamente al archivo WAL en disco con cada operación, eso sería demasiado lento. Primero acumula las entradas en el **WAL buffer**, una región en RAM. Solo cuando la transacción hace COMMIT, el WAL buffer se vuelca (`fsync`) al archivo en disco: la operación completa un UPDATE modifica primero la página en el buffer pool (marcándola dirty) y escribe la entrada correspondiente en el WAL buffer, y solo al llegar el COMMIT ese WAL buffer se fuerza a disco antes de responder OK al cliente. Las páginas dirty del buffer pool llegarán al disco más tarde, en el siguiente checkpoint.

**La invariante del WAL:** el motor nunca escribe una página de datos al disco sin antes haber escrito (y confirmado con fsync) la entrada WAL correspondiente. Esta regla es lo que le da el nombre al mecanismo, _Write-Ahead_, el log va primero. Por eso, si hay un crash después del COMMIT pero antes de que las páginas dirty lleguen al disco, el WAL en disco ya tiene el cambio confirmado, y el motor puede reaplicarlo sobre las páginas al reiniciar. El WAL describe _qué cambios_ hacer; las páginas de datos son el resultado de aplicar esos cambios, así que si están desactualizadas, el WAL sirve para recalcularlas.

El flujo completo con números de transacción concretos está en [`postgresql_caso_estudio.md` §4](postgresql_caso_estudio.md#4-wal-interno).

#### LSN: Log Sequence Number

Cualquier log secuencial necesita una forma de numerar sus entradas para saber cuáles ya se aplicaron y cuáles no; el nombre concreto de ese número varía por motor, pero el concepto es el mismo. PostgreSQL, MySQL InnoDB y SQL Server lo llaman igual, **LSN (Log Sequence Number)**; Oracle lo llama **SCN (System Change Number)**, con el mismo rol. Cada entrada del log tiene un número único que indica su posición, y cada página de datos almacena el LSN del último cambio que se le aplicó, lo que le permite al motor saber, durante el recovery, si una página ya tiene aplicado un cambio del log o si todavía hay que reaplicárselo. El motor lleva registro de tres LSN clave en todo momento: el último escrito al buffer en RAM, el último ya confirmado en disco, y el del último checkpoint (hasta ahí, páginas de datos y log están sincronizados). El ejemplo completo con un WAL de varias transacciones intercaladas está en [`postgresql_caso_estudio.md` §4](postgresql_caso_estudio.md#4-wal-interno).

#### Checkpoint: sincronizar el log con las páginas de datos

Sin checkpoints, el log de escritura adelantada crecería indefinidamente y el recovery tras un crash requeriría reproducir millones de entradas desde el principio. Cualquier motor con WAL necesita, periódicamente, volcar a disco las páginas dirty acumuladas hasta cierto punto del log y dejar constancia de hasta dónde llegó, para poder descartar el log anterior a ese punto. MySQL InnoDB hace lo mismo con su checkpoint difuso (_fuzzy checkpointing_) sobre el redo log, MongoDB/WiredTiger con su checkpoint periódico, y SQLite en modo WAL al consolidar el archivo `-wal` en el archivo principal. El costo es el mismo en todos: escribir muchas páginas dirty de golpe genera un pico de I/O que puede afectar la latencia de las queries normales mientras dura, por lo que la mayoría de los motores distribuyen esas escrituras a lo largo del intervalo entre checkpoints en vez de concentrarlas al final. El mecanismo interno de PostgreSQL, con LSN concretos y el efecto de `checkpoint_completion_target`, está en [`postgresql_caso_estudio.md` §4](postgresql_caso_estudio.md#4-wal-interno).

#### Recovery después de un crash

Al reiniciar, el motor no sabe qué páginas dirty llegaron al disco antes del crash. La estrategia general (localizar el último checkpoint, rehacer lo confirmado con **REDO**, deshacer lo que quedó a medias con **UNDO**) es el algoritmo que, con variaciones, siguen la mayoría de los motores basados en WAL, incluyendo PostgreSQL, MySQL InnoDB y SQL Server; se conoce en la literatura como el algoritmo ARIES. Gracias a los checkpoints periódicos, el recovery solo necesita reproducir el log desde el último checkpoint, no desde el principio de los tiempos, por lo que tarda segundos o pocos minutos incluso tras un crash, no horas. El ejemplo paso a paso, con transacciones confirmadas y a medias conviviendo en el mismo log, está en [`postgresql_caso_estudio.md` §4](postgresql_caso_estudio.md#4-wal-interno).

#### WAL por motor: decisiones de diseño

Todos los motores necesitan un log de durabilidad, pero sus prioridades produjeron diseños distintos:

| Motor                    | Mecanismo y archivos                                                           | Cómo funciona, y por qué así                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | Costo o particularidad clave                                                                                                                                                                                                                  |
| ------------------------ | ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **PostgreSQL**           | WAL (Write-Ahead Log): un único log secuencial dividido en segmentos de 16 MB. | Los segmentos son de tamaño fijo, no un archivo que crece indefinidamente, para facilitar el archivado (cada segmento es una unidad manejable), la limpieza (reciclar segmentos viejos sin truncar un archivo enorme) y la replicación (enviar segmentos completos a las réplicas). El nivel `wal_level` controla cuánta información se registra: `minimal` (solo crash recovery local, sin réplicas), `replica` (agrega streaming replication, el default en producción), `logical` (reconstruye cambios fila por fila, necesario para CDC/Debezium, con más volumen de WAL). | Archivando segmentos (`archive_command`) se habilita Point-in-Time Recovery: restaurar a cualquier momento pasado, no solo al último backup.                                                                                                  |
| **MySQL InnoDB**         | Tres archivos separados: redo log, undo log y binary log.                      | Cada uno tiene propósito y ciclo de vida distinto, separarlos permite optimizar cada uno por su cuenta. El redo log (equivalente al WAL de PostgreSQL) es circular y de tamaño fijo, y su espacio se reutiliza cuando los cambios ya llegaron a las páginas de datos. El undo log guarda versiones anteriores para MVCC y ROLLBACK, con un purge thread que lo limpia. El binary log es independiente del storage engine y sirve para replicación, auditoría y PITR.                                                                                                           | MySQL escribe el mismo cambio dos veces (redo + binlog), coordinados con un mini-2PC interno; y si los writes superan la velocidad del checkpoint, InnoDB se atasca esperando espacio libre en el redo log circular.                          |
| **SQL Server**           | Transaction Log (`.ldf`), archivo único.                                       | Crece automáticamente y sirve para crash recovery y PITR; el espacio no se reutiliza hasta hacer un _log backup_ (en modelo de recuperación FULL).                                                                                                                                                                                                                                                                                                                                                                                                                             | Sin log backups periódicos, el `.ldf` llena el disco, una trampa operativa común.                                                                                                                                                             |
| **Oracle**               | Redo log en grupos multiplexados + undo tablespace separado.                   | Oracle escribe a múltiples archivos simultáneamente (mirroring propio, sin depender del RAID del hardware). Los _online redo logs_ son donde escribe en tiempo real; los _archived redo logs_ son segmentos ya llenos, archivados para PITR y Data Guard.                                                                                                                                                                                                                                                                                                                      | El undo tablespace tiene ciclo de vida y gestión independientes del redo log.                                                                                                                                                                 |
| **SQLite**               | _Journal mode_ (original) o _WAL mode_ (desde 3.7.0, recomendado).             | En journal mode, antes de modificar copia la página original al journal y restaura desde ahí ante un crash, pero un writer excluye a todos los readers durante el commit. En WAL mode, los writes van al archivo `-wal` sin tocar el archivo principal, los readers ven el último snapshot confirmado ahí, y un writer convive con múltiples readers sin bloquearse; un checkpoint periódico consolida el `-wal` en el archivo principal.                                                                                                                                      | El modo WAL agrega dos archivos más (`-wal` y `-shm`), lo que complica el modelo "todo en un solo archivo" que hace simple a SQLite.                                                                                                          |
| **MongoDB (WiredTiger)** | Journal + checkpoint periódico.                                                | Cada operación se escribe primero al journal (mismo principio write-ahead con otro nombre), sincronizado a disco cada 100 ms por defecto o de inmediato si la escritura pide `writeConcern {j: true}`. El checkpoint (cada 60s o antes) toma una fotografía consistente de todas las colecciones; solo el journal posterior a ese punto hace falta reproducir tras un crash.                                                                                                                                                                                                   | Sin `j:true`, un crash entre dos sincronizaciones puede perder escrituras que el cliente ya recibió como confirmadas.                                                                                                                         |
| **Cassandra**            | Commit log + memtable + SSTable.                                               | Cada escritura se anexa primero al commit log (mismo rol que el WAL) antes de aplicarse a la memtable en RAM; si el nodo cae antes de volcar la memtable a un SSTable, el commit log se reproduce al reiniciar.                                                                                                                                                                                                                                                                                                                                                                | A diferencia de PostgreSQL, no necesita coordinar el commit log con "páginas de datos", porque su LSM-tree siempre anexa y nunca modifica in-place, así que el problema de "¿llegó el log antes que la página?" no se presenta igual.         |
| **Redis**                | RDB (snapshot binario) y/o AOF (log de comandos), ambos opcionales.            | RDB toma snapshots completos y periódicos; todo lo escrito después del último snapshot se pierde si el proceso muere. AOF registra cada comando de escritura y es el equivalente más cercano a un WAL, con `appendfsync` controlando la sincronización (`always`, `everysec` el default recomendado, `no`).                                                                                                                                                                                                                                                                    | La durabilidad es una opción explícita que hay que activar, no algo automático: Redis prioriza la latencia sobre la garantía de no perder nada.                                                                                               |
| **Neo4j**                | Log de transacciones propio.                                                   | Cada transacción escribe primero a un log secuencial (mismo principio write-ahead) antes de aplicar el cambio al store del grafo (nodos, relaciones y propiedades en sus propios archivos). Checkpoints periódicos consolidan ese log contra el store principal.                                                                                                                                                                                                                                                                                                               | El mecanismo en sí no es distinto de cualquier otro log secuencial; lo que cambia es qué protege: punteros entre registros (nodo → relación → nodo), así que un log corrupto arriesga la navegabilidad de todo el grafo, no solo un registro. |
| **ClickHouse**           | Ninguno separado del dato.                                                     | Cada INSERT crea una _part_ nueva e inmutable directamente en disco; la part ya es, en sí misma, la unidad durable desde que se escribe completa, no hace falta un log previo.                                                                                                                                                                                                                                                                                                                                                                                                 | Una escritura a medias que se interrumpe simplemente no deja una part visible (no hay dato corrupto que limpiar), pero muchos INSERTs pequeños generan muchas parts que hay que fusionar después (_compaction_/_merge_) constantemente.       |

---

## Bloque 5: fsync, durabilidad y rendimiento {#5-fsync}

En Bloque 4 vimos que el COMMIT hace `fsync` del WAL buffer al archivo WAL en disco antes de responder OK al cliente. Esta sección responde las preguntas que quedan: **qué es fsync exactamente**, **por qué un simple `write()` no garantiza nada**, y **cómo el motor balancea durabilidad y rendimiento sin sacrificar throughput**.

---

### Sección 5.1: El problema: "escribir" no es lo mismo que "guardar en disco" {#5-problema}

Cuando un programa llama a `write()` para enviar datos al disco, el OS no los escribe en el medio físico inmediatamente. Los coloca en una capa intermedia en RAM —el **page cache del kernel**— y responde OK de inmediato. Los datos llegarán al disco "cuando el OS tenga tiempo".

Pero hay más: incluso cuando el OS hace el flush al dispositivo, el controlador del disco (HDD, SSD, tarjeta RAID) tiene su propio caché DRAM interno. El controlador puede responder "OK, escribí" cuando los datos solo están en su DRAM interna, sin haber llegado al medio físico todavía.

```
El stack completo de escritura:

  Motor DB
    │  write()  "escribe estos bytes"
    ▼
┌──────────────────────────────────────────────┐
│  Page cache del kernel (RAM del OS)          │  ← RIESGO 1
│  El OS acumula escrituras y las envía al     │    crash del OS o del servidor
│  disco cuando le conviene.                   │    → todo lo no flusheado se pierde
└──────────────────────────────────────────────┘
    │  flush periódico o bajo presión de RAM
    ▼
┌──────────────────────────────────────────────┐
│  Caché write-back del controlador            │  ← RIESGO 2
│  (DRAM interna del disco, SSD o RAID card)   │    corte de luz sin batería
│  El controlador responde OK antes de         │    → los datos en su DRAM se pierden
│  escribir al medio físico                    │
└──────────────────────────────────────────────┘
    │  escritura física real
    ▼
┌──────────────────────────────────────────────┐
│  Medio físico (HDD, SSD NAND, NVMe)          │  ← seguro aquí
└──────────────────────────────────────────────┘

Un write() normal solo llega a la primera capa.
Si el servidor pierde energía mientras los datos están ahí:
  → datos perdidos, o peor: parcialmente escritos → inconsistencia
```

### Sección 5.2: fsync(): vaciar todas las capas hasta el medio físico {#5-fsync-detalle}

`fsync()` es la syscall que le dice al OS: **"vacía este archivo completamente hasta el medio físico, y no respondas hasta que esté ahí"**.

```
write() solo:
  Motor → kernel page cache → OK inmediato
  Crash → caché perdido → WAL incompleto → posible corrupción

fsync():
  Motor → kernel page cache → flush forzado → controlador → flush → físico → OK
  Crash → datos en disco → recovery funciona
```

El OS no responde `fsync()` hasta que el controlador confirma que los datos llegaron al medio físico (o a un caché con respaldo de batería que sobrevive cortes de luz).

---

### Sección 5.3: La distinción crítica: archivos de datos vs WAL {#5-datos-vs-wal}

El motor tiene dos tipos de archivos y los trata de forma diferente, esto conecta directamente con Bloque 4:

**Archivos de datos (tablas, índices):**
El motor los abre con `O_DIRECT` (Linux), que instruye al OS a **bypassear el page cache del kernel** completamente. Los datos van directo del buffer pool del motor al controlador. El motor gestiona su propio caché (el buffer pool del Bloque 4) y el OS no interfiere en cuándo ni cómo se escribe.

**Archivos WAL:**
El WAL en PostgreSQL (por defecto) sí pasa por el page cache del kernel. El motor escribe en ese caché y llama a `fsync()` al hacer COMMIT para forzar el flush. Esta es la razón concreta por la que el COMMIT espera: espera a que `fsync()` confirme que el WAL llegó al disco.

```
Archivos de datos:
  Motor → [O_DIRECT, bypass del page cache] → controlador → medio físico
  El OS no tiene copias en su caché. El buffer pool del motor ES la caché.

Archivos WAL:
  Motor → page cache del kernel → fsync() en cada COMMIT → medio físico
  El COMMIT espera aquí antes de responder OK al cliente.
```

---

### Sección 5.4: Dos formas muy distintas de "relajar" la durabilidad {#5-relajar-durabilidad}

No todos los knobs que aceleran el COMMIT arriesgan lo mismo, y confundirlos es un error operativo real. Hay una diferencia categórica entre **desactivar el fsync por completo** (el OS nunca es forzado a escribir nada a disco, ni el log ni los datos; si el servidor crashea, el archivo de log puede quedar truncado o inconsistente con las páginas de datos, y el motor puede terminar en un **estado irrecuperable**, no es pérdida de datos, es corrupción, y puede que ni siquiera vuelva a arrancar) y **responder OK al cliente antes de esperar la confirmación del disco** (la transacción se confirma de inmediato y el log llega a disco poco después; si el servidor crashea en esa ventana, esas transacciones puntuales se pierden, pero el log que sí alcanzó a llegar siempre es consistente, así que el motor arranca limpio). La primera arriesga la integridad de toda la base; la segunda arriesga, a lo sumo, los últimos commits. PostgreSQL expone esta distinción exactamente con `fsync` y `synchronous_commit`, dos parámetros que se confunden con frecuencia porque ambos "aceleran los commits"; el detalle de cada uno, con su tabla de riesgo, está en [`postgresql_caso_estudio.md` §5](postgresql_caso_estudio.md#5-durabilidad).

---

### Sección 5.5: Group commit: batching de fsync para alto throughput {#5-group-commit}

Cada fsync tiene un costo fijo (0.01–10 ms según el hardware). Si el motor hiciera un fsync por cada COMMIT, el throughput máximo sería `1 / latencia_fsync` TPS — con un HDD serían apenas ~100–250 TPS.

**Group commit** resuelve esto: si varias transacciones están esperando hacer COMMIT simultáneamente, el motor las agrupa en un solo fsync.

```
Sin group commit (un fsync por txn, HDD con 10ms de latencia):

  T1 COMMIT → fsync 10ms → OK
  T2 COMMIT →               fsync 10ms → OK     3 commits en 30ms
  T3 COMMIT →                              fsync 10ms → OK

Con group commit:

  T1 COMMIT ─┐
  T2 COMMIT ─┼──→ un solo fsync 10ms → OK a las 3    3 commits en 10ms
  T3 COMMIT ─┘

  El motor detecta que T2 y T3 llegaron mientras T1 esperaba el fsync
  y las incluye en el mismo batch.
  A mayor concurrencia, mayor el beneficio del batching.
```

Group commit es la razón por la que una base con muchas conexiones concurrentes puede superar ampliamente los TPS que la latencia de fsync sugeriría en teoría. Está activo por defecto en PostgreSQL y MySQL sin configuración adicional.

---

### Sección 5.6: Latencias de hardware y TPS resultantes {#5-latencias-hardware}

```
Hardware                 Latencia fsync     TPS aproximados (sin group commit)
───────────────────────  ─────────────────  ──────────────────────────────────
HDD 7200 RPM             4–10 ms            100–250 TPS
SATA SSD                 0.1–0.5 ms         2.000–10.000 TPS
NVMe PCIe 3.0            0.02–0.1 ms        10.000–50.000 TPS
NVMe PCIe 4.0            0.01–0.05 ms       20.000–100.000 TPS
BBWC (caché con batería)  < 0.01 ms         > 100.000 TPS

Optimizaciones para producción de alto throughput:
  • WAL en NVMe dedicado, separado físicamente de los archivos de datos
  • Controlador RAID con BBWC (Battery-Backed Write Cache): responde fsync()
    en < 0.01 ms porque su DRAM tiene batería — durabilidad real con latencia de RAM
  • Group commit activo por defecto (sin configuración extra en PG o MySQL)
```

---

### Sección 5.7: Configuración por motor {#5-configuracion-por-motor}

| Motor            | Parámetro(s) principal(es)                                         | Qué controla cada valor                                                                                                                                                                                                                                                                    | Riesgo si se relaja                                                                                                                                                              |
| ---------------- | ------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **PostgreSQL**   | `fsync` (on/off); `synchronous_commit` (on/off/remote_apply)       | `fsync=off` desactiva todos los fsync del motor. `synchronous_commit=on` (default) espera el fsync del WAL antes de responder al cliente; `off` responde antes, dejando el WAL en el page cache del OS; `remote_apply` espera además que la réplica confirme.                              | `synchronous_commit=off`: pérdida posible, sin corrupción. `fsync=off`: corrupción real, nunca en producción.                                                                    |
| **MySQL InnoDB** | `innodb_flush_log_at_trx_commit` (0/1/2)                           | `1` (default) hace fsync del redo log en cada COMMIT. `2` escribe al page cache del OS en cada COMMIT y hace fsync cada ~1s. `0` ni siquiera llega al page cache en cada COMMIT, solo al buffer interno de InnoDB.                                                                         | `2`: pierde hasta ~1s de transacciones si el OS crashea, sin corrupción. `0`: pierde ~1s y arriesga inconsistencia si cae el proceso `mysqld`.                                   |
| **SQL Server**   | `DELAYED_DURABILITY` (DISABLED/ALLOWED/FORCED)                     | Por defecto escribe durablemente en cada COMMIT, sin equivalente a `fsync=off`. `ALLOWED`/`FORCED` retrasan el flush del log de transacciones.                                                                                                                                             | Equivalente a `synchronous_commit=off`: pérdida posible, sin corrupción.                                                                                                         |
| **Oracle**       | `COMMIT WRITE` (IMMEDIATE WAIT / BATCH NOWAIT)                     | `IMMEDIATE WAIT` (default) hace fsync síncrono en cada commit. `BATCH NOWAIT` agrupa varios commits en un solo fsync (group commit asíncrono).                                                                                                                                             | `BATCH NOWAIT`: pérdida posible, sin corrupción.                                                                                                                                 |
| **SQLite**       | `PRAGMA synchronous` (FULL/NORMAL/OFF)                             | `FULL` (default en modo journal) hace fsync antes y después de escribir el journal. `NORMAL` (default y recomendado en modo WAL) hace fsync con menos frecuencia. `OFF` no hace ningún fsync.                                                                                              | `NORMAL`: ventana mínima de pérdida, solo ante un crash del propio OS. `OFF`: riesgo real de corrupción del archivo, no solo de pérdida.                                         |
| **MongoDB**      | `writeConcern` (`j`, `w`)                                          | `j: true` espera la confirmación del journal antes de responder, el equivalente a `synchronous_commit=on`. `j: false` (default histórico) responde antes de que el journal se sincronice. `w: "majority"` es un eje independiente: además espera que la mayoría de las réplicas confirmen. | `j: false`: ventana de pérdida de hasta ~100 ms.                                                                                                                                 |
| **Cassandra**    | `commitlog_sync` (periodic/batch)                                  | `periodic` (default) hace fsync del commit log cada `commitlog_sync_period_in_ms` (10s por defecto). `batch` hace fsync antes de confirmar cada escritura, o grupo de escrituras dentro de una ventana configurable.                                                                       | `periodic`: ventana de pérdida real si el nodo cae entre dos flushes. `batch`: mucho más durable, mucho más lento.                                                               |
| **Redis**        | `appendfsync` (always/everysec/no)                                 | `always` hace fsync en cada escritura. `everysec` (default recomendado) hace fsync una vez por segundo. `no` deja que el OS decida cuándo.                                                                                                                                                 | `everysec`: pérdida acotada a ~1s ante un crash, el mismo trade-off que `innodb_flush_log_at_trx_commit=2` en MySQL. `no`: mismo riesgo que `fsync=off` en cualquier otro motor. |
| **Neo4j**        | Configuración de fsync del log de transacciones (`dbms.tx_log...`) | Por defecto, cada commit fuerza el log a disco antes de confirmar, igual que `synchronous_commit=on` en PostgreSQL. Relajar esa garantía es posible pero inusual.                                                                                                                          | Un log de transacciones perdido no solo pierde datos, arriesga la integridad de los punteros entre nodos.                                                                        |
| **ClickHouse**   | `fsync_after_insert`, `fsync_directories`                          | No hay un parámetro de fsync por commit equivalente, porque no hay WAL separado del dato (ver "WAL por motor" en Bloque 4). Estos parámetros fuerzan que una _part_ llegue a disco físico antes de reportar éxito.                                                                         | El mismo trade-off latencia-vs-durabilidad de cualquier otro motor, aplicado a la unidad "part" en vez de a la unidad "log".                                                     |

---

## Bloque 6: Estructuras de almacenamiento: cómo encontrar una fila sin leer todo el archivo {#6-estructuras}

**El problema universal:** ya sabes que el motor organiza el disco en páginas de tamaño fijo (Bloque 4) y que cachea esas páginas en RAM (buffer pool). Pero eso no resuelve el problema real: dado un valor a buscar, ¿cómo sabe el motor en qué página exacta está, sin leer el archivo completo página por página? Sin una estructura que lo indique, encontrar una fila entre un millón exige, en el peor caso, leer el millón de páginas (_sequential scan_ o _full table scan_). Cualquier motor que quiera responder consultas puntuales rápido necesita una estructura de datos que reduzca esa búsqueda de "leer todo" a "leer un puñado de páginas", y las dos familias dominantes para resolverlo son el **B-tree** y el **LSM-tree**, con decisiones de diseño opuestas sobre qué sacrifican.

### Sección 6.1: B-tree (o B+tree) {#6-btree}

Un árbol balanceado donde cada nodo es una página, ordenado por la clave de búsqueda. Encontrar una fila es descender desde la raíz hasta la hoja correcta, típicamente 3-4 niveles incluso con millones de filas, así que localizar cualquier fila cuesta 3-4 lecturas de página en el peor caso, no un millón. El costo lo paga la escritura: insertar o actualizar una fila puede requerir reordenar páginas del árbol para mantenerlo balanceado (_page splits_), una operación de escritura aleatoria.

```
B-tree, buscar la clave 47:

              [ 50 ]                    ← raíz
             /       \
        [20, 40]   [60, 80]             ← nivel intermedio
        /  |   \    /  |   \
     ... [30,45] ...  ...  ...          ← hojas (aquí vive la fila)
             ↑
        3 páginas leídas para encontrar la fila con clave 47,
        sin importar si la tabla tiene mil filas o mil millones.
```

### Sección 6.2: LSM-tree (Log-Structured Merge-tree) {#6-lsm-tree}

En vez de mantener una sola estructura ordenada que se actualiza in-place, el motor acumula escrituras en una estructura en RAM (_memtable_), y cuando se llena, la vuelca a disco como un archivo ordenado e inmutable (_SSTable_). Escribir es barato porque siempre es secuencial, nunca hay que reordenar nada existente. El costo lo paga la lectura: una fila puede estar en la memtable o en cualquiera de varios SSTables acumulados, así que leer puede exigir consultar varias estructuras y fusionar el resultado, un proceso en background (_compaction_) las va fusionando para mantener el número de SSTables a revisar bajo control.

```
LSM-tree, escritura:

  Memtable (RAM, ordenada)
       │ se llena
       ▼
  SSTable nuevo (disco, inmutable, ordenado)  ← escritura 100% secuencial

  Con el tiempo se acumulan varios SSTables:
  [SSTable 1] [SSTable 2] [SSTable 3] ...
       │
       ▼ compaction (background)
  Los fusiona en SSTables más grandes, descartando versiones obsoletas.

  Leer una fila puede requerir revisar la memtable Y varios SSTables
  hasta encontrar la versión más reciente.
```

La elección entre uno y otro no es una preferencia estética, es un trade-off explícito entre el costo de escribir y el costo de leer, y por eso motores pensados para cargas distintas eligen estructuras distintas.

### Sección 6.3: Cómo lo resuelve cada motor {#6-por-motor}

| Motor                    | Estructura principal                                                                                                                                                                                                                                                                                          | Cómo encuentra una fila                                                                                                                                                                                                               | Dónde le cuesta más                                                                                                                                                                                                                                      |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **PostgreSQL**           | Heap (filas sin orden particular) + índices B-tree separados que apuntan al heap por `ctid`. También ofrece GiST, GIN (para JSONB/arrays) y BRIN (para datos ordenados físicamente, como series de tiempo) para casos que un B-tree no cubre bien.                                                            | El índice B-tree lleva del valor buscado a la ubicación física en el heap; sin índice, es sequential scan del heap completo.                                                                                                          | Un `UPDATE` frecuente en una columna indexada genera muchas versiones (MVCC, Bloque 8) que también hay que indexar y luego limpiar (vacuum, Bloque 9); el índice crece más rápido que los datos reales.                                                  |
| **MySQL InnoDB**         | _Clustered index_: la tabla misma **es** un B+tree ordenado por la primary key. Los índices secundarios son B-trees separados que, en sus hojas, no guardan un puntero físico sino el valor de la primary key.                                                                                                | Un índice secundario primero encuentra la primary key, y luego hace un segundo descenso por el clustered index para llegar a la fila completa (_doble lookup_).                                                                       | Elegir una primary key con valores no secuenciales (como un UUID aleatorio) fuerza _page splits_ constantes porque cada INSERT cae en una posición aleatoria del árbol, en vez de simplemente añadirse al final.                                         |
| **SQL Server**           | Igual que InnoDB: _clustered index_ (la tabla ordenada por su clave) + índices _nonclustered_ que apuntan a la clave del clustered index. Una tabla sin clustered index queda como heap.                                                                                                                      | Igual mecanismo de doble lookup que InnoDB para índices nonclustered.                                                                                                                                                                 | La misma trampa que InnoDB con claves no secuenciales, más fragmentación de índice con el tiempo, que se corrige con `REBUILD`/`REORGANIZE`.                                                                                                             |
| **Oracle**               | Heap-organized tables (por defecto, como PostgreSQL) o _Index-Organized Tables_ (IOT, la tabla vive directamente dentro del B-tree de su primary key, como el clustered index de MySQL).                                                                                                                      | Depende del tipo de tabla elegido al crearla; con IOT, encontrar por PK no requiere el segundo salto al heap.                                                                                                                         | Tablas heap con muchos índices secundarios pagan un costo de mantenimiento por cada INSERT/UPDATE (cada índice es una estructura independiente que actualizar).                                                                                          |
| **SQLite**               | B-tree, igual familia que los anteriores; cada tabla y cada índice es su propio árbol dentro del mismo archivo `.sqlite`.                                                                                                                                                                                     | Descenso estándar por el B-tree correspondiente.                                                                                                                                                                                      | Sin índice explícito, cualquier `WHERE` fuerza un recorrido completo de la tabla, más notorio en SQLite porque suele correr sin la capa de caché de un servidor dedicado detrás.                                                                         |
| **MongoDB (WiredTiger)** | B-tree por defecto para colecciones e índices (WiredTiger también soporta un modo LSM, pero B-tree es el default y el más usado).                                                                                                                                                                             | El índice B-tree lleva directo al documento; sin índice, _collection scan_ completo.                                                                                                                                                  | Índices sobre campos con muchos valores repetidos (baja selectividad) o sobre arrays grandes (_multikey indexes_) crecen más de lo esperado y ofrecen poca ventaja real sobre un scan.                                                                   |
| **Redis**                | Ninguna estructura de disco equivalente: las claves viven en una hash table en RAM (acceso O(1) directo). Estructuras como _sorted sets_ usan una _skip list_ en RAM, una alternativa al B-tree con la misma idea de mantener orden con búsqueda rápida, pero pensada para memoria, no para páginas de disco. | Acceso directo por hash de la clave; en sorted sets, descenso por la skip list.                                                                                                                                                       | Sets o hashes con millones de elementos dentro de una sola clave: la operación sigue siendo rápida en promedio, pero cualquier operación que deba tocar todos los elementos (como `SMEMBERS` completo) es lineal y bloquea el único hilo mientras corre. |
| **Cassandra**            | LSM-tree: memtable en RAM + SSTables inmutables en disco, exactamente el mecanismo genérico descrito arriba.                                                                                                                                                                                                  | Dentro de una partición, un índice disperso (_partition index_) más un _bloom filter_ por SSTable evitan leer SSTables que con certeza no contienen la clave buscada.                                                                 | Muchos SSTables sin compactar (`compaction` atrasada) obligan a consultar cada uno para una sola lectura; lecturas que no calzan con la partition key no tienen estructura que las acelere en absoluto.                                                  |
| **Neo4j**                | Ninguna de las dos: usa **adyacencia indexada** (_index-free adjacency_). Cada nodo guarda punteros directos a sus relaciones, y cada relación apunta directo a los nodos que conecta, así que recorrer el grafo es seguir punteros, no hacer lookups en un índice.                                           | Para arrancar un recorrido sí hace falta un índice tradicional (B-tree) sobre una propiedad; una vez ubicado el nodo de partida, expandir relaciones es navegación directa por punteros, con costo prácticamente constante por salto. | Sin ningún nodo indexado como punto de partida, no hay forma de "entrar" al grafo salvo escaneando todos los nodos de una etiqueta; el índice solo importa para el primer paso, no para la navegación.                                                   |
| **ClickHouse**           | Familia MergeTree: cada tabla se parte en _parts_ inmutables, ordenados físicamente por la clave de orden (`ORDER BY`) declarada en la tabla. El "índice primario" no es un B-tree, es disperso: guarda la clave cada N filas (8192 por defecto), no de cada fila.                                            | Con la clave de orden, salta directo al rango aproximado de filas dentro de cada part usando ese índice disperso, y de ahí lee secuencialmente ese bloque, mucho más barato que un B-tree tradicional para escaneos de rango grandes. | Filtrar por una columna que no es la clave de orden no tiene ningún índice que ayude, cae a leer esa columna completa en cada part (aunque sigue siendo más barato que leer todas las columnas gracias al formato columnar).                             |

El detalle interno del B-tree de PostgreSQL, con la estructura exacta de una página de índice y un recorrido de búsqueda paso a paso, está en [`postgresql_caso_estudio.md` §9](postgresql_caso_estudio.md#9-btree-interno).

---

## Bloque 7: Locks: la otra mitad del control de concurrencia {#7-locks}

**El problema universal:** cuando dos operaciones quieren tocar el mismo dato al mismo tiempo, algo tiene que decidir el orden, o el resultado puede quedar corrompido (dos escrituras que se pisan) o inconsistente (una lectura a mitad de una escritura). Hay dos familias de solución a este problema, y ya conoces una: la multiversión (MVCC, Bloque 8), donde el motor evita el conflicto manteniendo varias versiones del dato y dejando que cada lector vea la suya. La otra familia, más antigua y todavía la base de todo lo demás incluso en motores con MVCC, es el **locking**: antes de tocar un dato, una operación pide permiso explícito, y si alguien más ya lo tiene, espera.

Un **lock** es, en su forma más simple, una marca que el motor guarda en memoria: "la fila X está tomada por la transacción Y, con este tipo de permiso". Hay dos tipos universales:

- **Lock compartido (S, shared):** varios lectores pueden tenerlo a la vez sobre el mismo dato. Es "puedo leer, pero nadie puede escribir mientras yo lo tenga".
- **Lock exclusivo (X, exclusive):** solo una transacción puede tenerlo. Es "voy a escribir, nadie más puede leer ni escribir mientras dure".

```
Compatibilidad universal entre locks sobre el mismo dato:

              Otro pide S       Otro pide X
  Yo tengo S     compatible        espera
  Yo tengo X       espera          espera
```

Incluso en motores con MVCC (donde las lecturas normales no usan locks en absoluto, ver Bloque 8), las escrituras siguen tomando un lock exclusivo por debajo, porque MVCC resuelve el conflicto lector-escritor, no el conflicto escritor-escritor: dos transacciones que quieren modificar la misma fila todavía necesitan que alguien decida quién va primero.

**Granularidad:** un lock puede tomarse sobre una fila, una página, o una tabla completa. Cuanto más fino (fila), menos contención entre operaciones no relacionadas, pero más locks que gestionar (overhead de memoria y de verificación). Cuanto más grueso (tabla), menos locks que llevar la cuenta, pero cualquier operación bloquea a todas las demás aunque toquen filas distintas. Algunos motores **escalan** automáticamente: si una transacción acumula demasiados locks de fila, el motor los reemplaza por un solo lock de tabla para ahorrar memoria, a costa de bloquear más de lo estrictamente necesario.

**Deadlock:** ocurre cuando dos transacciones se bloquean mutuamente en un ciclo. T1 tiene el lock de la fila A y espera la fila B; T2 tiene el lock de la fila B y espera la fila A. Ninguna puede avanzar. Ningún motor "resuelve" esto dejando esperar para siempre: todos corren un detector de ciclos en background (o usan un timeout) y abortan a una de las dos transacciones para que la otra pueda continuar. El backend que recibe el abort debe reintentar la operación desde el principio.

### Sección 7.1: Cómo lo resuelve cada motor {#7-por-motor}

| Motor                    | Modelo de locking                                                                                                                                                                                                                                                                                                                                               | Granularidad                                                                                                                              | Dónde le cuesta más                                                                                                                                                                                                                                            |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **PostgreSQL**           | Lecturas normales no usan locks (MVCC, Bloque 8); escrituras y `SELECT FOR UPDATE`/`FOR SHARE` sí toman locks de fila explícitos. Detecta deadlocks activamente (`deadlock_timeout`) y aborta una transacción con error recuperable.                                                                                                                            | Fila y tabla; no escala automáticamente de fila a tabla.                                                                                  | Transacciones largas que sostienen muchos locks de fila simultáneos, o DDL (`ALTER TABLE`) que necesita un lock de tabla exclusivo y debe esperar a que terminen todas las transacciones activas sobre esa tabla.                                              |
| **MySQL InnoDB**         | Locks de fila para escrituras, más **gap locks** y **next-key locks** en `REPEATABLE READ` (su default) para prevenir phantom reads bloqueando también el espacio _entre_ filas, no solo las filas existentes.                                                                                                                                                  | Fila, con los gap locks cubriendo rangos del índice.                                                                                      | Los gap locks son la causa más común de deadlocks reportados en producción con MySQL: varios INSERT concurrentes en el mismo rango de índice compiten por gaps que se solapan.                                                                                 |
| **SQL Server**           | Sin RCSI, hasta las lecturas normales toman locks compartidos (S), el modelo opuesto a MVCC puro. Con RCSI activo, las lecturas dejan de necesitar locks, igual que en PostgreSQL.                                                                                                                                                                              | Fila, página o tabla, con **lock escalation** automático: muchos locks de fila en una sola transacción se convierten en un lock de tabla. | La lock escalation puede convertir una operación que debería tocar pocas filas en un bloqueo de tabla completa si el optimizador subestimó cuántas filas iba a tocar.                                                                                          |
| **Oracle**               | Locks de fila para escrituras; a diferencia de SQL Server, Oracle **nunca** escala un lock de fila a un lock de tabla completo para DML normal, es una decisión de diseño explícita.                                                                                                                                                                            | Fila y tabla, sin escalation automática.                                                                                                  | Transacciones que modifican muchísimas filas individualmente sostienen muchísimos locks de fila simultáneos sin que el motor los consolide, con el consumo de memoria correspondiente.                                                                         |
| **SQLite**               | El más grueso de todos: en modo journal clásico, un escritor toma un lock exclusivo sobre **todo el archivo**, y ningún lector puede leer mientras dura. En modo WAL, un escritor y varios lectores pueden coexistir, pero sigue habiendo como máximo un escritor a la vez para toda la base.                                                                   | Base de datos completa (journal mode) o tabla-por-escritor efectivo (WAL mode).                                                           | Cualquier aplicación con más de un escritor concurrente real siente esto de inmediato: SQLite está diseñado para poca concurrencia de escritura por elección, no por descuido.                                                                                 |
| **MongoDB (WiredTiger)** | Locks a nivel de documento para la mayoría de operaciones, más granular que un lock de colección. Ciertas operaciones administrativas (algunos comandos de índice, algunas fases de una transacción multi-documento) sí necesitan locks más amplios.                                                                                                            | Documento, colección o base de datos según la operación.                                                                                  | Transacciones multi-documento que tocan muchos documentos o que se extienden en el tiempo (ver el costo de MVCC distribuido en Bloque 8) sostienen más locks de documento simultáneos, y su límite de 60 segundos por transacción existe en parte por esto.    |
| **Redis**                | No necesita locks en el sentido clásico: el single-thread con event loop garantiza que solo un comando se ejecuta a la vez, así que no puede haber dos escrituras simultáneas por diseño, no por coordinación explícita.                                                                                                                                        | No aplica.                                                                                                                                | El costo se traslada por completo a la latencia: mientras un comando corre (aunque sea uno solo, sin nadie más "compitiendo"), todos los demás clientes esperan su turno en la cola del event loop.                                                            |
| **Cassandra**            | Sin locking entre nodos ni dentro de un nodo para el camino normal de escritura (por diseño, para no sacrificar disponibilidad). Para el puñado de casos que necesitan "solo uno puede hacer esto" (linealizabilidad), Cassandra ofrece **lightweight transactions** (LWT), un compare-and-swap coordinado con Paxos entre réplicas, deliberadamente costoso.   | No aplica al camino normal; una operación PAXOS-CAS por fila en el camino LWT.                                                            | Usar LWT como mecanismo general de concurrencia (en vez de para el puñado de casos que realmente lo necesitan) es lento por diseño: cada LWT implica varias rondas de mensajes entre réplicas antes de poder confirmar.                                        |
| **Neo4j**                | Como no usa MVCC (Bloque 8), depende enteramente de locks explícitos sobre nodos y relaciones para toda escritura, incluyendo bloqueos que se extienden a los nodos vecinos cuando una relación se crea o borra (para mantener consistente la adyacencia indexada del Bloque 6).                                                                                | Nodo y relación.                                                                                                                          | Grafos con nodos "hub" muy conectados (un nodo con miles de relaciones) concentran locks de muchas transacciones distintas sobre el mismo nodo, volviéndolo un cuello de botella de escritura incluso si las transacciones no tienen relación lógica entre sí. |
| **ClickHouse**           | No necesita locks de fila para el camino normal: cada INSERT crea una _part_ nueva e inmutable (Bloque 6), así que no hay "la misma fila" que dos escrituras puedan disputar. Operaciones que sí modifican datos existentes (`ALTER TABLE ... UPDATE/DELETE`, llamadas _mutations_) corren asíncronamente en background y sí requieren coordinación más amplia. | No aplica al INSERT; a nivel de partición para mutations.                                                                                 | Las mutations son pesadas por diseño (reescriben parts completas), así que un patrón de aplicación que espera `UPDATE`/`DELETE` frecuentes al estilo OLTP choca directamente con esta arquitectura.                                                            |

El detalle interno del lock manager de PostgreSQL, con la tabla de compatibilidad completa entre los distintos modos de lock y cómo se ve un deadlock real en los logs, está en [`postgresql_caso_estudio.md` §10](postgresql_caso_estudio.md#10-locks-interno).

---

## Bloque 8: Control de concurrencia multiversión (MVCC): versionado y visibilidad de datos {#8-mvcc}

### Sección 8.1: El problema universal {#8-problema-universal}

Cuando dos transacciones acceden al mismo dato al mismo tiempo, el motor tiene que decidir qué pasa. El capítulo anterior (Bloque 7) mostró una familia de solución, locking puro: cada lectura toma un lock compartido, cada escritura uno exclusivo, y un SELECT largo puede bloquear todos los UPDATE de esa tabla mientras dura. La otra familia de solución es el **control de concurrencia multiversión (MVCC)**: en vez de bloquear, el motor mantiene más de una versión física de cada dato viva a la vez. Cuando una transacción escribe, no destruye la versión anterior: crea una nueva y marca la vieja como obsoleta desde ese punto. Cuando otra transacción lee, ve la versión que le corresponde según el instante en que empezó, no necesariamente "lo más reciente". Un lector nunca espera a un escritor; un escritor nunca espera a un lector. Solo dos escritores que modifican exactamente el mismo dato compiten entre sí (y ese conflicto sí se resuelve con un lock, Bloque 7).

```
Sin MVCC (locking puro):

  T1 (lectura larga):  ──lee el dato compartido───────────────────► (30 s)
  T2 (escritura):            ────espera el lock de T1─────────────► escribe (bloqueada 30 s)

  T2 no puede tocar el dato hasta que T1 suelte su lock de lectura.

Con MVCC:

  T1 (lectura larga):  ──lee la versión v1 (su snapshot)───────────► (30 s)
  T2 (escritura):            ──crea v2 sin tocar v1── confirma v2 ─►
                              (T1 sigue viendo v1; no sabe que v2 existe)

  v1 y v2 coexisten mientras alguien las necesite.
  T1 y T2 nunca se bloquean entre sí, cada una trabaja con su propia versión.
```

El bloqueo del primer caso no significa que ese diseño esté roto o sea inviable, es un motor funcionando exactamente como fue diseñado. SQL Server (sin RCSI) y Neo4j operan así todos los días en producción real: cambian throughput bajo alta concurrencia de lectura y escritura por un modelo más simple de razonar, sin versiones que mantener ni limpiar después. No existe un motor que "necesite" MVCC para funcionar, es una de dos familias de solución igualmente válidas al mismo problema, no una que reemplaza a la otra.

### Sección 8.2: Cómo lo resuelve cada motor {#8-por-motor}

| Motor                                  | ¿Dónde vive la versión anterior?                                                                                                                                                                          | ¿Cómo decide qué versión ver?                                                                                                                                                                               | Quién limpia las versiones viejas, y cuándo se vuelve un problema                                                                                                                                                      |
| -------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **PostgreSQL**                         | En la fila misma: cada versión es una copia completa marcada con metadatos de sistema (`xmin`, `xmax`). Ambas copias viven en la misma página de disco hasta que se limpian.                              | Snapshot tomado al inicio de la transacción (o de cada statement, según el nivel de aislamiento), comparado contra los metadatos de cada versión.                                                           | El proceso `vacuum` (Bloque 9). Si no da abasto, la tabla crece de tamaño (_table bloat_) porque las versiones muertas siguen ocupando páginas.                                                                        |
| **MySQL InnoDB**                       | Aparte: la tabla solo guarda la versión más reciente confirmada; las versiones anteriores viven en el **undo log**. Leer una versión vieja implica reconstruirla aplicando registros de undo hacia atrás. | Un **read view**, equivalente conceptual del snapshot, tomado según el nivel de aislamiento (por transacción en `REPEATABLE READ`, su default).                                                             | Un **purge thread** en background limpia el undo log. Si una transacción larga queda abierta, el purge no avanza y el undo log crece sin límite hasta que esa transacción termine.                                     |
| **Oracle**                             | Aparte, igual que MySQL: la tabla tiene la versión actual, las viejas viven en el **undo tablespace**.                                                                                                    | Snapshot (Oracle lo llama _read consistency_) al inicio del statement o de la transacción, según el modo.                                                                                                   | El undo tablespace se reutiliza según `undo_retention`. Si una lectura larga necesita una versión ya sobrescrita, Oracle lanza `ORA-01555: snapshot too old`, un error explícito en vez de mostrar el dato equivocado. |
| **SQL Server**                         | Por defecto, ninguna: usa locking puro, no MVCC. MVCC es opt-in (RCSI o snapshot isolation); al activarlo, las versiones viejas se guardan en `tempdb`, aparte de la tabla.                               | Sin MVCC: no aplica, todo pasa por locks. Con RCSI/snapshot activo: un snapshot equivalente al de los demás.                                                                                                | `tempdb` se limpia automáticamente. Activar RCSI exige que no haya conexiones activas al momento del cambio; el costo se traslada a espacio e I/O en `tempdb`.                                                         |
| **MongoDB (WiredTiger)**               | En el documento mismo: WiredTiger mantiene una cadena de versiones (_update chain_) por documento dentro de su propio B-tree, conceptualmente igual a como PostgreSQL versiona la fila.                   | Snapshot tomado al inicio de la operación o de la transacción explícita, con el mismo principio de "veo el estado que existía cuando empecé".                                                               | El propio _checkpoint_ periódico de WiredTiger libera versiones obsoletas; no existe un proceso de limpieza separado como vacuum, porque el checkpoint ya absorbe esa función.                                         |
| **SQLite**                             | No implementa MVCC real. En modo _journal_, un escritor excluye a todos los lectores durante el commit, justo el problema que MVCC existe para evitar.                                                    | En modo **WAL** logra el efecto práctico (un escritor y varios lectores sin bloquearse) con un mecanismo distinto: los lectores ven el último estado confirmado en el archivo `-wal`, no versiones de fila. | El _checkpoint_ consolida el `-wal` en el archivo principal periódicamente; no hay tabla que crezca por versiones muertas.                                                                                             |
| **Cassandra** (wide-column en general) | No aplica: no hay una unidad transaccional multi-fila que aislar entre lectores y escritores.                                                                                                             | Cada celda lleva su propio timestamp; si dos escrituras concurrentes tocan la misma celda, gana la de timestamp más alto (_last-write-wins_).                                                               | No hay "versiones muertas" en el sentido de MVCC, pero el problema estructuralmente parecido lo resuelve la _compaction_ (Bloque 9).                                                                                   |
| **Neo4j**                              | No usa MVCC por defecto: como SQL Server sin RCSI, resuelve la concurrencia con locking puro sobre nodos y relaciones, no con versiones.                                                                  | No aplica; sin versiones no hay snapshot que tomar, un lector puede esperar a un escritor que tenga el lock del mismo nodo o relación.                                                                      | No hay versiones que limpiar por este mecanismo; el costo en su lugar es contención de locks bajo escritura concurrente sobre los mismos nodos (ver la fila de Neo4j en Bloque 2).                                     |
| **ClickHouse**                         | No aplica en el sentido clásico: no hay una fila que versionar, cada INSERT crea una _part_ nueva e inmutable.                                                                                            | Una consulta lee el conjunto de parts que existían al iniciar, un efecto similar a un snapshot, pero logrado por inmutabilidad de archivos, no por metadatos de visibilidad por fila.                       | No hay versiones muertas de una fila; el problema análogo son las parts viejas ya fusionadas, que el proceso de _merge_ en background limpia (mismo principio que la compaction de Cassandra, Bloque 9).               |

La fila de Cassandra es la más importante de leer con cuidado: no es que resuelva MVCC de otra forma, es que el problema que MVCC resuelve (darle a una transacción una vista consistente del pasado sin bloquear a nadie) **no existe** en un modelo que nunca prometió aislamiento transaccional multi-fila. Es la prueba de que MVCC no es un requisito universal de toda base de datos, es la solución de una familia de motores a un problema que ellos sí decidieron tener.

### Sección 8.3: El problema real: cuándo un snapshot no es lo que quieres {#8-problema-real}

Todo lo anterior describe MVCC como una ventaja sin costo: los lectores nunca esperan, los escritores nunca esperan a los lectores. Pero esa ventaja viene de una promesa muy específica, ves el dato **como era cuando tu transacción empezó**, no como es ahora mismo. Para un reporte o un dashboard eso es exactamente lo que quieres: una foto consistente, sin importar qué escriban otros mientras corre tu consulta. Para una decisión que va a terminar en una escritura, esa misma promesa puede ser la causa directa de un error de negocio real.

El ejemplo que lo hace concreto es una app bancaria:

```
Saldo real en la cuenta: $1,000

T1 (retiro de $900):
  BEGIN → snapshot toma el saldo: $1,000
  "¿hay $900 disponibles? según mi snapshot, sí"

  Mientras tanto, T2 (otro retiro) ya corrió y confirmó:
  T2: retira $800 → saldo real ahora es $200 → COMMIT

  T1 sigue viendo $1,000 en su snapshot (así funciona MVCC, por diseño)
  T1 aprueba el retiro de $900 → saldo pasaría a -$700

Sin un mecanismo adicional, MVCC no evita este resultado por sí solo:
la lectura fue perfectamente consistente con el momento en que empezó,
el problema es que esa decisión necesitaba el saldo actual, no el de hace
unos milisegundos.
```

Esto no es una falla de MVCC, es la consecuencia directa de lo que promete. El error está en usar un snapshot para una decisión que exige el dato más reciente.

### Sección 8.4: Por qué esto solo se nota con al menos dos transacciones a la vez {#8-dos-transacciones}

Aquí está la respuesta corta y directa: **sí, sin excepción**. Ver una versión anterior no es algo que pueda pasar con una sola transacción trabajando sola. Es estructural: para que exista una "versión anterior" tiene que existir, en algún momento, otra transacción que haya escrito una versión más nueva mientras la tuya seguía activa. Si eres la única conexión tocando ese dato, no hay nadie más creando versiones nuevas detrás de ti, así que nunca hay nada de qué quedarte "atrás". El fenómeno completo depende de que se cumplan estas tres condiciones **a la vez**:

1. Tu transacción ya tomó su snapshot (una "foto" del estado confirmado hasta ese instante).
2. **Otra transacción** modifica esa misma fila y confirma (hace `COMMIT`) **después** de que tu snapshot ya se fijó.
3. Tú vuelves a leer esa fila **mientras tu snapshot sigue vigente**.

Quita la condición 2 (nadie más escribe) y no hay versión anterior que ver, siempre verías lo mismo porque nada cambió. Es la prueba de que este efecto es, por definición, un fenómeno de concurrencia entre dos o más transacciones, nunca algo que le pase a una transacción sola.

Lo que sí varía entre motores y configuraciones es **cuánto dura tu foto**, es decir, cuánta oportunidad hay de que se cumpla la condición 3. Hay dos formas universales de resolver esa duración: renovar la foto en cada operación individual (así nunca te quedas viendo algo viejo por mucho tiempo), o mantener la misma foto fija durante toda una transacción o sesión más larga (así ves un estado consistente de principio a fin, a costa de poder quedarte atrás). En el mundo SQL esto tiene nombre, `READ COMMITTED` renueva la foto en cada _statement_, `REPEATABLE READ` la fija para toda la transacción, pero la pregunta de fondo (¿mi lectura usa una foto que se renueva seguido, o una que se mantiene fija por más tiempo?) aplica igual en cualquier motor con MVCC, sin importar cómo llame a sus niveles.

El ejemplo más fácil de ver usa una foto que dura toda la transacción:

```
Saldo real en la cuenta: $500

T1: abre una transacción con la foto fija para toda su duración
    → snapshot se fija AHORA: "todo lo confirmado hasta este instante es visible"
T1: lee saldo → 500          (ve la versión vigente en ese instante)

    Mientras T1 sigue abierta, en paralelo:
T2: abre su propia transacción
T2: modifica saldo = 300
T2: confirma                  ← el cambio ya es real y confirmado

T1: lee saldo otra vez (misma transacción) → sigue viendo 500
    NO ve 300, aunque T2 ya confirmó hace rato.

T1: confirma (o lo que sea que haga después)
```

Mientras la transacción de T1 siga abierta, cada lectura que haga sobre esa fila devolverá 500, sin importar cuántas veces T2 (o T3, T4...) la modifique y confirme mientras tanto. Con una foto que se renueva en cada operación individual, en cambio, la ventana es mucho más corta, el efecto solo se nota **dentro** de una misma operación en ejecución (por ejemplo, una que escanea muchas filas y tarda); la siguiente operación de T1 ya tomaría una foto nueva y sí vería el 300.

### Sección 8.5: Qué es automático y qué depende de ti {#8-automatico-vs-manual}

MVCC no es algo que se activa o se pide, corre siempre, en cada lectura y cada escritura, desde la primera operación aunque nunca hayas configurado nada. No existe un estado de "esta transacción no tiene MVCC" en un motor que lo implementa, lo único que cambia es la duración de la foto que viste arriba.

**Siempre automático, sin que lo pidas:**

- Crear una versión nueva en cada escritura.
- Tomar un snapshot en cada lectura, renovado en cada operación o fijo por transacción según la configuración.
- Decidir qué versión le corresponde ver a cada lector.
- Tomar el lock exclusivo en cada escritura (Bloque 7), incluso en motores que no usan locks para lecturas.

**Manual, solo en un caso específico:** cuando tu aplicación separa una lectura y una escritura en dos pasos distintos, y la escritura decide su valor con base en lo que devolvió esa lectura, exactamente el patrón del ejemplo bancario de arriba. Si tu operación completa es un solo paso atómico (por ejemplo, "restar 100 al saldo" expresado como una operación relativa, no como "poner el saldo en 900"), no hay nada manual que hacer, el motor aplica el cambio contra el valor que exista en ese instante sin que tu aplicación lo lea primero. El riesgo aparece solo cuando de verdad necesitas leer antes de decidir, y ahí hay dos soluciones universales: pedir el dato con un lock exclusivo desde la lectura (equivalente a `SELECT ... FOR UPDATE`), de modo que nadie pueda modificarlo entre tu lectura y tu escritura, o expresar la escritura como una operación relativa al valor actual en vez de un valor absoluto que calculaste tú. Cada motor maneja este riesgo de forma distinta cuando no usas ninguna de las dos soluciones, algunos detectan el conflicto y abortan la transacción con un error recuperable, otros simplemente aceptan la escritura sobre el dato obsoleto sin avisar. El catálogo completo de estas anomalías (lost update, write skew, y las demás), con el comportamiento exacto de cada motor y cuándo usar cada herramienta, está en `03_transacciones_concurrencia_aislamiento.md`, Bloque 3 a Bloque 5.

### Sección 8.6: Por qué tener MVCC no significa que las transacciones salgan gratis: el caso de MongoDB {#8-mongodb}

La fila de MongoDB en la tabla de arriba puede sorprender: si WiredTiger sí tiene MVCC real por documento, ¿por qué la fama de que "las transacciones en MongoDB cuestan caro"? La respuesta está en la diferencia entre **versionar un documento** y **coordinar esa versión entre varios documentos o varios shards**.

Una operación sobre un solo documento usa el MVCC de WiredTiger de forma local y barata, igual que un UPDATE de una sola fila en PostgreSQL. Pero una transacción que toca varios documentos, y sobre todo si esos documentos viven en shards distintos, exige que el snapshot de lectura quede **fijo (pinned)** en todos los shards involucrados durante toda la duración de la transacción, y que el commit se coordine con un protocolo de dos fases (2PC) entre los primaries de cada shard participante. Mientras ese snapshot está pinned, WiredTiger no puede liberar las versiones viejas que ese snapshot todavía podría necesitar, lo que retrasa la limpieza normal en cada nodo involucrado. Por eso MongoDB impone un límite duro de tiempo a las transacciones (60 segundos por default): no es una limitación arbitraria, es el reconocimiento de que mantener ese snapshot distribuido abierto tiene un costo real y creciente.

La lección universal detrás de este caso: **el costo de MVCC no es plano**. Versionar un solo dato es barato en casi cualquier motor; coordinar esa versión entre varias unidades de almacenamiento (documentos, shards, particiones) es donde el costo aparece, sin importar el motor.

### Sección 8.7: Cómo lo hace PostgreSQL por dentro {#8-postgresql}

El mecanismo exacto de PostgreSQL, para quien quiera el "por dentro" hasta el final, está en [`postgresql_caso_estudio.md` §6](postgresql_caso_estudio.md#6-mvcc-interno): dónde viven físicamente las versiones dentro de una DB page, los campos de sistema `xmin`/`xmax`/`ctid` que deciden visibilidad, qué pasa exactamente al ejecutar un UPDATE, cómo se construye un snapshot, el algoritmo de visibilidad completo aplicado paso a paso, un ejemplo con dos transacciones viendo dos resultados distintos y correctos a la vez, y el límite de 32 bits de los XIDs (transaction ID wraparound) que hace necesario el mecanismo de freeze descrito en Bloque 9 más abajo.

---

## Bloque 9: Recuperación de espacio de versiones obsoletas y límites de contadores de transacción {#9-vacuum}

### Sección 9.1: El problema universal {#9-problema-universal}

Ningún motor puede versionar datos para siempre sin limpiar. Cualquier motor que mantenga múltiples versiones de un dato vivo a la vez, sea con MVCC sobre la fila (PostgreSQL), un undo log aparte (MySQL, Oracle), una cadena de versiones por documento (MongoDB) o una estructura que nunca sobrescribe in-place (Cassandra, ClickHouse, y en general los motores de búsqueda basados en Lucene como Elasticsearch), tarde o temprano necesita recuperar el espacio de las versiones que ya nadie puede ver. Vacuum, autovacuum y el transaction ID wraparound de este capítulo **no son un problema universal en sí mismos**: son la forma concreta en que ese problema universal se manifiesta en PostgreSQL, como consecuencia directa de haber elegido versionar la fila con contadores de 32 bits, en vez de, por ejemplo, un undo log separado (que no tiene el problema de wraparound de la misma forma) o un LSM-tree (que lo resuelve con compaction).

### Sección 9.2: Cómo lo resuelve cada motor {#9-por-motor}

| Motor                                         | Cómo se manifiesta el problema                                                                                                                                                                                                                                                                                                                    | Cómo lo recupera                                                                                                                                                                                         | Límite o costo particular                                                                                                                                                                                                                                |
| --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **PostgreSQL**                                | _Table bloat_: versiones muertas (_dead tuples_) que ninguna transacción puede ver siguen ocupando espacio en las DB pages.                                                                                                                                                                                                                       | `vacuum` / `autovacuum` en background, con la _visibility map_ para saltarse páginas ya limpias.                                                                                                         | Contadores de transacción (XID) de 32 bits: si el vacuum anti-wraparound no da abasto, el riesgo es que filas vivas se vuelvan invisibles (ver más abajo).                                                                                               |
| **MySQL InnoDB**                              | No hay dead tuples en la tabla misma (siempre tiene la versión actual); el undo log es lo que crece.                                                                                                                                                                                                                                              | Un **purge thread** en background limpia el undo automáticamente.                                                                                                                                        | Si una transacción muy larga está activa, el purge no avanza y el undo log crece sin límite hasta que esa transacción termine. `OPTIMIZE TABLE` es el equivalente aproximado de `VACUUM FULL`.                                                           |
| **Oracle**                                    | Igual que MySQL: la tabla tiene la versión actual, el undo tablespace es lo que se llena.                                                                                                                                                                                                                                                         | El undo tablespace se reutiliza automáticamente según `undo_retention`.                                                                                                                                  | Si una lectura larga necesita una versión ya reutilizada, `ORA-01555: snapshot too old`. `SHRINK SPACE` recupera espacio físico en tablas fragmentadas.                                                                                                  |
| **SQL Server (con RCSI)**                     | Las versiones se acumulan en `tempdb`, no en las tablas principales.                                                                                                                                                                                                                                                                              | Limpieza automática cuando ninguna transacción activa las necesita.                                                                                                                                      | Sin RCSI no hay acumulación de versiones porque no hay MVCC (usa locking puro). `INDEX REBUILD`/`REORGANIZE` compacta índices fragmentados por separado.                                                                                                 |
| **MongoDB (WiredTiger)**                      | Las versiones obsoletas de cada documento se acumulan dentro de su B-tree hasta el próximo checkpoint.                                                                                                                                                                                                                                            | El _checkpoint_ periódico (cada 60s por defecto) libera las versiones que ningún snapshot activo necesita ya.                                                                                            | Si una transacción mantiene un snapshot _pinned_ mucho tiempo (ver la nota de MVCC en Bloque 8), retrasa esa limpieza en todo el nodo, no solo en el documento afectado.                                                                                 |
| **Cassandra**                                 | Un UPDATE o DELETE nunca modifica in-place: es una escritura nueva. El DELETE en particular escribe un **tombstone** (marcador de "esto fue borrado") en vez de borrar de inmediato. Con el tiempo se acumulan versiones viejas y tombstones en varios SSTables, el mismo table bloat causado por un LSM-tree en vez de por MVCC sobre un B-tree. | La **compaction** fusiona varios SSTables en uno, descartando versiones obsoletas: el mismo rol que cumple vacuum.                                                                                       | Los tombstones no se pueden borrar de inmediato: se conservan por `gc_grace_seconds` (10 días por defecto) para darle tiempo a otros nodos de enterarse del borrado antes de purgarlo, algo sin equivalente en un motor de un solo nodo como PostgreSQL. |
| **Neo4j**                                     | No acumula versiones muertas en el sentido de MVCC, porque no usa MVCC (ver Bloque 8): al no versionar nodos ni relaciones, no hay "tuplas viejas" que limpiar del mismo modo.                                                                                                                                                                    | No aplica un proceso equivalente a vacuum; el mantenimiento se concentra en compactar el store físico (nodos, relaciones, propiedades) cuando hay fragmentación por borrados, no en recuperar versiones. | El costo que sí existe es distinto: fragmentación del store en cargas con muchos borrados, que se resuelve con herramientas de mantenimiento offline, no con un proceso de limpieza continuo como autovacuum.                                            |
| **Elasticsearch** (motores basados en Lucene) | Un documento "actualizado" es, por dentro, un tombstone sobre la versión vieja más un documento nuevo completo, porque los segmentos de Lucene son inmutables.                                                                                                                                                                                    | El **merge** de segmentos en background fusiona varios en uno, descartando los documentos marcados como borrados.                                                                                        | Mientras un documento borrado o reemplazado no se compacta, sigue ocupando espacio y afecta el conteo de resultados hasta el próximo _refresh_; la búsqueda además es "near real-time", no inmediata (ver Bloque 4).                                     |
| **ClickHouse**                                | No hay fila que versionar (ver Bloque 8); el equivalente al bloat son las _parts_ pequeñas generadas por muchos INSERTs individuales, que se acumulan si el ritmo de escritura supera al de fusión.                                                                                                                                               | El proceso de **merge** en background (la misma familia MergeTree) fusiona parts pequeñas en parts más grandes de forma continua.                                                                        | Si los INSERTs llegan más rápido de lo que el merge puede fusionar, ClickHouse rechaza escrituras nuevas con el error real "too many parts", un límite de diseño explícito, no un timeout genérico.                                                      |

### Sección 9.3: Cómo lo hace PostgreSQL por dentro {#9-postgresql}

El mecanismo completo, tal como lo implementa PostgreSQL, está en [`postgresql_caso_estudio.md` §7](postgresql_caso_estudio.md#7-vacuum): cómo se acumulan los dead tuples y el table bloat, la visibility map que hace eficiente saltarse páginas ya limpias, qué hace exactamente VACUUM (y qué no, la diferencia con VACUUM FULL), cómo funciona autovacuum y sus umbrales de activación, el mecanismo de freeze que previene el transaction ID wraparound de 32 bits, la escalada de emergencia de cuatro niveles hasta el punto en que PostgreSQL detiene todas las escrituras, y las queries de monitoreo para vigilar ese riesgo antes de que se vuelva un incidente.

---

## Bloque 10: Replicación: cuando la durabilidad de un solo nodo no es suficiente {#10-replicacion}

**El problema universal:** todo lo que Bloque 4 y Bloque 5 explicaron (WAL, fsync, checkpoints) garantiza que un cambio confirmado sobreviva un crash **del mismo servidor**. No garantiza nada si ese servidor entero desaparece (falla el disco, se cae la máquina, se pierde el datacenter). La solución universal es la misma idea que ya conoces del WAL, pero extendida a otra máquina: en vez de solo escribir el cambio en un log local antes de aplicarlo, también **enviar ese mismo log a uno o más servidores adicionales** (réplicas), que lo aplican de forma independiente y quedan con una copia del mismo estado.

Esto no es una capa nueva conceptualmente, es el WAL del Bloque 4 con un destino adicional:

```
COMMIT en el nodo primario:
  1. Escribe en el WAL local
  2. fsync() del WAL local                          ← Bloque 4, ya lo conoces
  3. (según la configuración) envía el WAL a la(s) réplica(s)
  4. (según la configuración) espera que la réplica confirme
  5. Responde OK al cliente
```

El eje de decisión universal es **cuándo** responder OK: si el primario espera a que la réplica confirme antes del paso 5, es **replicación síncrona** (más lenta, pero un cambio confirmado existe garantizado en más de un lugar). Si el primario responde sin esperar a la réplica, es **replicación asíncrona** (más rápida, pero un crash justo después del COMMIT puede perder el cambio en la réplica, aunque el cliente ya recibió OK). La mayoría de los motores permiten elegir el punto medio, muchas réplicas asíncronas más una síncrona, o confirmación de "la mayoría" en vez de todas.

El otro eje universal es **qué se replica**: el log físico completo, byte por byte (replicación física, la réplica es un clon exacto, incluyendo su propio motor de almacenamiento) o una traducción a operaciones lógicas (replicación lógica, permite replicar solo parte de los datos, o hacia un motor distinto, a cambio de más overhead de traducción).

### Sección 10.1: Cómo lo resuelve cada motor {#10-por-motor}

| Motor            | Mecanismo                                                                                                                                                                                                                                                                       | Síncrona o asíncrona                                                                                                                                                                                     | Dónde le cuesta más                                                                                                                                                                                                                                                 |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **PostgreSQL**   | _Streaming replication_: envía el WAL (Bloque 4) directamente a las réplicas en cuanto se genera. Física (clon completo) por defecto; también soporta replicación lógica (decodifica el WAL a cambios de fila, permite replicar tablas específicas).                            | Configurable por réplica: `synchronous_commit = on/remote_apply` para síncrona, `off` (con `synchronous_standby_names` vacío) para asíncrona; puede mezclar varias réplicas asíncronas con una síncrona. | Con replicación síncrona, la latencia de red hacia la réplica se suma directamente a la latencia de cada COMMIT; con asíncrona, un failover puede perder las últimas transacciones que no llegaron a tiempo.                                                        |
| **MySQL InnoDB** | Basada en el **binary log** (binlog), no en el redo log interno; el binlog registra las operaciones de forma que otro servidor las pueda reproducir. Formatos: _statement-based_ (replica el SQL), _row-based_ (replica las filas afectadas, el default moderno), o mixto.      | _Semi-síncrona_ (el primario espera que al menos una réplica confirme haber recibido el binlog, no que lo haya aplicado) o asíncrona (default clásico).                                                  | El binlog es una escritura adicional a la del propio redo log de InnoDB (coordinadas con un mini-2PC interno, ver "WAL por motor" en Bloque 4), así que replicación activa siempre añade overhead de escritura, incluso sin réplicas lentas.                        |
| **SQL Server**   | _Always On Availability Groups_: envía el transaction log a las réplicas (_secondaries_); también existe _log shipping_, más simple y con más retraso, para escenarios que no necesitan failover automático.                                                                    | Configurable por secondary: _synchronous commit_ o _asynchronous commit_ mode, elegible individualmente por réplica dentro del mismo Availability Group.                                                 | Con varias réplicas síncronas, el COMMIT espera a la más lenta de todas ellas; con log shipping asíncrono, el RPO (cuánto se puede perder) depende de la frecuencia con la que se despachan los logs.                                                               |
| **Oracle**       | _Data Guard_: envía el redo log a bases _standby_, físicas (aplican el redo directamente, un clon exacto) o lógicas (lo traducen a SQL y lo ejecutan, permitiendo que la standby tenga estructuras distintas).                                                                  | Configurable: _Maximum Protection_ (síncrona, sin pérdida posible), _Maximum Availability_ (síncrona pero se degrada a asíncrona si la standby no responde), _Maximum Performance_ (asíncrona).          | _Maximum Protection_ puede literalmente detener la base primaria si ninguna standby está disponible para confirmar, prioriza no perder datos por encima de estar disponible.                                                                                        |
| **SQLite**       | Ninguno nativo: al ser un motor embebido de un solo archivo, no tiene concepto de red ni de réplicas. La replicación se resuelve por fuera, con herramientas externas (por ejemplo Litestream) que vigilan el WAL del archivo y lo copian a otro destino.                       | Depende completamente de la herramienta externa elegida.                                                                                                                                                 | Cualquier garantía de durabilidad multi-nodo es responsabilidad de esa capa externa, el motor mismo no participa ni la conoce.                                                                                                                                      |
| **MongoDB**      | _Replica sets_: un primary y varios secondaries que aplican el **oplog** (un log de operaciones, conceptualmente equivalente al WAL) del primary. Si el primary falla, los secondaries eligen automáticamente un nuevo primary (_election_).                                    | Tunable por escritura individual con `writeConcern`: `w: 1` (solo el primary confirma), `w: "majority"` (espera que la mayoría de las réplicas del set confirmen), hasta `w: <N>` explícito.             | `w: "majority"` en un set con réplicas lejanas geográficamente añade la latencia de esa distancia a cada escritura; con `w: 1`, una elección de nuevo primary tras una falla puede descartar escrituras que el cliente ya creía confirmadas.                        |
| **Redis**        | Replicación por propagación de comandos desde el primary a los replicas, asíncrona por diseño (Redis prioriza latencia). _Sentinel_ monitorea y coordina failover automático; _Redis Cluster_ además particiona los datos entre nodos, cada partición con sus propias réplicas. | Asíncrona; existe `WAIT` para pedir confirmación de N réplicas antes de continuar, pero no es el modo por defecto.                                                                                       | Una escritura confirmada al cliente puede no haber llegado todavía a ningún replica; si el primary muere en esa ventana, esos datos simplemente no existen en ningún otro lado.                                                                                     |
| **Cassandra**    | No hay primary: cada escritura se envía directamente a todas las réplicas responsables de esa partition key (determinadas por el anillo de hash consistente), sin que ningún nodo actúe como intermediario obligatorio.                                                         | Tunable por query con el _consistency level_ (`ONE`, `QUORUM`, `ALL`, etc.), tanto para escritura como para lectura, independientes entre sí.                                                            | Con `consistency level = ONE` en escritura y lectura, dos clientes pueden leer réplicas distintas con datos distintos justo después de una escritura (consistencia eventual real, no solo teórica); subir a `QUORUM` o `ALL` cambia ese trade-off por más latencia. |
| **Neo4j**        | _Causal clustering_: un grupo de _core servers_ replican las transacciones entre sí usando el protocolo Raft (consenso con mayoría), y pueden además tener _read replicas_ adicionales que solo reciben los cambios ya confirmados, sin participar en el consenso de escritura. | Escritura síncrona por diseño dentro del core (Raft exige mayoría antes de confirmar); las read replicas se actualizan de forma asíncrona respecto al core.                                              | Cada escritura paga el costo de una ronda de consenso Raft entre los core servers, un costo fijo que crece con la cantidad de core servers y la latencia de red entre ellos.                                                                                        |
| **ClickHouse**   | `ReplicatedMergeTree`: las _parts_ (Bloque 6) se replican entre nodos coordinándose a través de un servicio externo (ZooKeeper o ClickHouse Keeper), que lleva el registro de qué parts existen y en qué réplica.                                                               | Asíncrona: un INSERT se confirma localmente y se replica en background; existe `insert_quorum` para exigir que N réplicas confirmen antes de responder OK.                                               | El servicio de coordinación (Keeper/ZooKeeper) se vuelve un punto crítico, si no está disponible, los nodos no pueden coordinarse para saber qué parts replicar, aunque cada nodo individual siga funcionando para lecturas locales.                                |

El detalle interno de streaming replication en PostgreSQL, con el rol exacto del WAL sender, el WAL receiver, y cómo se ve el lag de replicación en `pg_stat_replication`, está en [`postgresql_caso_estudio.md` §11](postgresql_caso_estudio.md#11-replicacion-interna).
