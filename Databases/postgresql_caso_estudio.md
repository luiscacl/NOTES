**Caso de estudio: PostgreSQL por dentro**

1. [Arquitectura de procesos: de la conexión al CPU](#1-arquitectura)
2. [Memoria compartida y estructura de páginas](#2-memoria-paginas)
3. [Buffer pool por dentro](#3-buffer-pool)
4. [WAL interno: buffer, LSN, checkpoint y recovery](#4-wal-interno)
5. [Configuración de durabilidad: fsync vs synchronous_commit](#5-durabilidad)
6. [MVCC interno: xmin, xmax, ctid y el algoritmo de visibilidad](#6-mvcc-interno)
7. [Vacuum y mantenimiento](#7-vacuum)
8. [El planner por dentro: EXPLAIN, estadísticas y el optimizador de costos](#8-planner-interno)
9. [El B-tree de PostgreSQL por dentro](#9-btree-interno)
10. [El lock manager por dentro](#10-locks-interno)
11. [Streaming replication por dentro](#11-replicacion-interna)

```
╔══════════════════════════════════════════════════════════════════╗
║  CASO DE ESTUDIO: POSTGRESQL POR DENTRO                          ║
║  Un motor concreto, hasta el último detalle mecánico             ║
╚══════════════════════════════════════════════════════════════════╝
```

> **Qué es este archivo y cómo se relaciona con `01_internals_del_motor.md`**
>
> `01_internals_del_motor.md` explica, capa por capa, el problema arquitectónico universal
> que cualquier motor de base de datos tiene que resolver (concurrencia, caché en RAM,
> durabilidad, MVCC, recuperación de espacio), con una tabla comparando cómo lo resuelve
> cada tipo de motor (relacional, documento, clave-valor, wide-column, grafo, columnar).
> Este archivo es el complemento: toma **un solo motor, PostgreSQL**, y lo explica hasta
> el detalle mecánico completo, porque es el motor que más expone sus internals (xmin/xmax
> son columnas visibles, el WAL es legible, el catálogo es consultable con SQL). Nada de lo
> que sigue es universal. Es "así lo hace PostgreSQL", punto, sin adivinar qué tan parecido
> es a lo que hace cualquier otro motor. Para eso está el archivo universal.
>
> Léelo cuando quieras entender un motor real hasta el fondo, no como requisito para
> entender la arquitectura general.

---

## 1. Arquitectura de procesos: de la conexión al CPU {#1-arquitectura}

Una sola petición HTTP que hace un `UPDATE` atraviesa toda esta pila:

```
╔══════════════════════════════════════════════════════════════╗
║                    MÁQUINA DEL BACKEND                       ║
║                                                              ║
║  ┌──────────────────────────────────────────────────────┐    ║
║  │              Proceso del Backend                     │    ║
║  │                                                      │    ║
║  │  ┌────────────────────────────────────────────────┐  │    ║
║  │  │  Framework HTTP                                │  │    ║
║  │  │  (Node.js HTTP server, Gunicorn worker,        │  │    ║
║  │  │   Tomcat thread, Go net/http...)               │  │    ║
║  │  │  Recibe y gestiona conexiones HTTP             │  │    ║
║  │  └────────────────────┬───────────────────────────┘  │    ║
║  │                       │ despacha el request          │    ║
║  │  Tu código: auth · reglas de negocio · validación    │    ║
║  │                       │ ejecuta query                │    ║
║  │  ┌────────────────────▼───────────────────────────┐  │    ║
║  │  │           Driver (librería importada)          │  │    ║
║  │  │  psycopg2 · pg · mysql2 · JDBC                 │  │    ║
║  │  │  Gestiona conexiones DB (backend → DB)         │  │    ║
║  │  │                                                │  │    ║
║  │  │  ┌──────────────────────────────────────────┐  │  │    ║
║  │  │  │         Connection Pool                  │  │  │    ║
║  │  │  │  [conn1] [conn2] [conn3] ... [connN]     │  │  │    ║
║  │  │  │  cada una = socket TCP abierto y         │  │  │    ║
║  │  │  │  autenticado con el servidor             │  │  │    ║
║  │  │  └──────────────────────────────────────────┘  │  │    ║
║  │  │                                                │  │    ║
║  │  │  Serializa tu query al wire protocol           │  │    ║
║  │  │  Deserializa las filas de respuesta            │  │    ║
║  │  └────────────────────┬───────────────────────────┘  │    ║
║  └───────────────────────┼──────────────────────────────┘    ║
║                          │                                   ║
╚══════════════════════════╪═══════════════════════════════════╝
                           │
                    socket TCP persistente
                    wire protocol
                    (PostgreSQL Frontend/Backend Protocol)
                           │
╔══════════════════════════╪═══════════════════════════════════╗
║              MÁQUINA DEL SERVIDOR DE BASE DE DATOS           ║
║                          │                                   ║
║  ┌───────────────────────▼──────────────────────────────┐    ║
║  │  postmaster (proceso raíz)                           │    ║
║  │                                                      │    ║
║  │  · escucha conexiones entrantes en el puerto 5432    │    ║
║  │  · hace fork() por cada conexión nueva →             │    ║
║  │    genera un proceso backend hijo independiente      │    ║
║  └──────────────────────┬───────────────────────────────┘    ║
║                         │ fork()                             ║
║  ┌──────────────────────▼───────────────────────────────┐    ║
║  │  Backend process (uno activo por conexión)           │    ║
║  │                                                      │    ║
║  │  ┌────────────────────────────────────────────────┐  │    ║
║  │  │         Parser + Planner                       │  │    ║
║  │  │                                                │  │    ║
║  │  │  Recibe texto SQL por el socket                │  │    ║
║  │  │  SQL texto → AST → plan de ejecución           │  │    ║
║  │  │                                                │  │    ║
║  │  │  AST: árbol de nodos que representa el SQL     │  │    ║
║  │  │  UPDATE cuentas                                │  │    ║
║  │  │    └── SET saldo = saldo - 100                 │  │    ║
║  │  │          └── WHERE id = 1                      │  │    ║
║  │  │                                                │  │    ║
║  │  │  Plan: decisión de cómo ejecutarlo             │  │    ║
║  │  │  "buscar id=1 por índice primario, actualizar" │  │    ║
║  │  └────────────────────┬───────────────────────────┘  │    ║
║  │                       │                              │    ║
║  │  ┌────────────────────▼───────────────────────────┐  │    ║
║  │  │         Executor (Ejecutor)                    │  │    ║
║  │  │                                                │  │    ║
║  │  │  Recorre el plan nodo a nodo.                  │  │    ║
║  │  │  Para cada operación llama a las capas de      │  │    ║
║  │  │  almacenamiento en memoria compartida: MVCC,   │  │    ║
║  │  │  Lock Manager, Buffer Pool, WAL Buffer.        │  │    ║
║  │  └────────────────────┬───────────────────────────┘  │    ║
║  │                       │                              │    ║
║  │  ┌────────────────────▼───────────────────────────┐  │    ║
║  │  │         Transaction Manager                    │  │    ║
║  │  │                                                │  │    ║
║  │  │  Administra el ciclo de vida de la txn:        │  │    ║
║  │  │  · Asigna XID al 1er write (lazy, no al BEGIN) │  │    ║
║  │  │  · Registra qué txns están activas             │  │    ║
║  │  │  · En COMMIT: coordina WAL flush + confirma    │  │    ║
║  │  │  · En ROLLBACK o fallo: coordina deshacerlo    │  │    ║
║  │  └────────────────────┬───────────────────────────┘  │    ║
║  │                       │                              │    ║
║  │              ┌────────▼─────────┐                    │    ║
║  │              │  ¿Es una lectura │                    │    ║
║  │              │  o escritura?    │                    │    ║
║  │              └──┬───────────┬───┘                    │    ║
║  │   SELECT normal  │           │ INSERT/UPDATE/DELETE  │    ║
║  │  (sin FOR UPDATE)│           │ o SELECT FOR UPDATE   │    ║
║  │                 │           │                        │    ║
║  │  ┌──────────────▼──┐  ┌────▼──────────────────────┐ │    ║
║  │  │  MVCC Engine    │  │     Lock Manager          │ │    ║
║  │  │  (lectura)      │  │                           │ │    ║
║  │  │                 │  │  Solicita X lock sobre    │ │    ║
║  │  │  Toma snapshot  │  │  la fila a modificar      │ │    ║
║  │  │  decide qué     │  │                           │ │    ║
║  │  │  versión de     │  │  Si la fila ya tiene      │ │    ║
║  │  │  cada fila ver  │  │  X lock de otra txn:      │ │    ║
║  │  │  según xmin/    │  │  → espera o falla         │ │    ║
║  │  │  xmax del       │  │    (NOWAIT/timeout)       │ │    ║
║  │  │  snapshot       │  │                           │ │    ║
║  │  │                 │  │  Si no hay lock:          │ │    ║
║  │  │  Sin adquirir   │  │  → concede X lock         │ │    ║
║  │  │  ningún lock    │  │  → pasa al MVCC Engine    │ │    ║
║  │  └──────┬──────────┘  └────────────┬──────────────┘ │    ║
║  │         │                          │                 │    ║
║  │         │             ┌────────────▼──────────────┐  │    ║
║  │         │             │       MVCC Engine         │  │    ║
║  │         │             │     (para escrituras)     │  │    ║
║  │         │             │                           │  │    ║
║  │         │             │  NO modifica la fila      │  │    ║
║  │         │             │  existente. Crea versión  │  │    ║
║  │         │             │  nueva con xmin=XID_txn   │  │    ║
║  │         │             │  Marca vieja con xmax     │  │    ║
║  │         │             └────────┬──────────────────┘  │    ║
║  └─────────┼──────────────────────┼─────────────────────┘    ║
║            │ lee / escribe        │ lee / escribe            ║
║            ▼                      ▼                          ║
║  ┌─────────────────────────────────────────────────────┐     ║
║  │  MEMORIA COMPARTIDA DEL OS  (mmap / shmget)         │     ║
║  │  Todos los backends acceden al mismo bloque de RAM. │     ║
║  │                                                     │     ║
║  │  ┌─────────────────────────────────────────────┐    │     ║
║  │  │  WAL Buffer                                 │    │     ║
║  │  │  Log de cambios en RAM. Escrito PRIMERO     │    │     ║
║  │  │  (write-ahead). Solo escrituras generan WAL;│    │     ║
║  │  │  las lecturas van directo al Buffer Pool.   │    │     ║
║  │  │  En COMMIT: fsync al archivo WAL en disco.  │    │     ║
║  │  └─────────────────────────────────────────────┘    │     ║
║  │                                                     │     ║
║  │  ┌─────────────────────────────────────────────┐    │     ║
║  │  │  Buffer Pool                                │    │     ║
║  │  │  Caché de páginas de disco en RAM, 8 KB/pág.│    │     ║
║  │  │  hit  → acceso desde RAM (nanosegundos)     │    │     ║
║  │  │  miss → lee del disco, guarda aquí          │    │     ║
║  │  │  write → página "sucia" (dirty)             │    │     ║
║  │  │         → se vuelca a disco en checkpoint   │    │     ║
║  │  └─────────────────────────────────────────────┘    │     ║
║  │                                                     │     ║
║  │  ┌─────────────────────────────────────────────┐    │     ║
║  │  │  Lock Table                                 │    │     ║
║  │  │  Registro de qué txn tiene qué lock.        │    │     ║
║  │  │  Compartida por todos los backends para     │    │     ║
║  │  │  coordinar escrituras concurrentes.         │    │     ║
║  │  └─────────────────────────────────────────────┘    │     ║
║  └──────────────────┬──────────────────────────────────┘     ║
╚════════════════════╪════════════════════════════════════════╝
                     │
        ┌────────────┴──────────────────────┐
        │                                   │
   Ruta A — WAL (en cada COMMIT)    Ruta B — Datos (en cada checkpoint)
        │                                   │
        ▼                                   ▼  O_DIRECT
┌────────────────────────────┐  ┌───────────────────────────────┐
│  OS page cache             │  │  Bypass del OS page cache     │
│  (RAM del kernel)          │  │                               │
│                            │  │  Los archivos de datos se     │
│  El OS acumula escrituras. │  │  abren con O_DIRECT: el OS no │
│  Puede decir "OK" antes de │  │  interpone su caché.          │
│  escribir al disco físico. │  │  El motor gestiona su propio  │
│                            │  │  caché: el Buffer Pool.       │
│  fsync() en COMMIT:        │  │                               │
│  fuerza vaciado real al    │  │  Los datos van directo al     │
│  medio físico.             │  │  controlador de disco.        │
│  El COMMIT espera aquí.    │  │                               │
└─────────────┬──────────────┘  └───────────────┬───────────────┘
              │                                 │
╔═════════════╪═══════════════════════════════════╪══════════════╗
║             │    Disco físico / SSD / NVMe      │              ║
║             │                                   │              ║
║  ┌──────────▼──────────────┐  ┌─────────────────▼──────────┐  ║
║  │   WAL en disco          │  │   Archivos de datos        │  ║
║  │                         │  │                            │  ║
║  │  Escritura secuencial   │  │  Escritura aleatoria       │  ║
║  │  (siempre al final      │  │  (páginas en posiciones    │  ║
║  │   del log) Muy rápido   │  │   arbitrarias del disco)   │  ║
║  └─────────────────────────┘  └────────────────────────────┘  ║
╚══════════════════════════════════════════════════════════════╝
```

### Por qué el diagrama tiene esa estructura

El diagrama separa la **máquina del backend** de la **máquina del servidor de base de datos** porque son procesos distintos, habitualmente en servidores distintos, que se comunican a través de un socket TCP. La red es la frontera.

Dentro del servidor, gran parte del trabajo ocurre en RAM: el Buffer Pool, el WAL Buffer y la Lock Table viven en **memoria compartida del OS** (mmap/shmget), un bloque de RAM que el OS mapea simultáneamente en el espacio de cada proceso backend. Los backends no se comunican entre sí para acceder a la caché de páginas: todos ven el mismo bloque físico. El Parser, Planner, Executor y Transaction Manager viven dentro de cada proceso backend individual. El disco solo aparece cuando es imprescindible: el WAL se fuerza a disco en cada COMMIT (durabilidad) y las páginas sucias del Buffer Pool se vuelcan en checkpoints periódicos. La ruta del WAL pasa por el OS page cache y necesita `fsync()` para garantizar que los bytes lleguen al hardware. Los archivos de datos usan `O_DIRECT` y bypasean el OS page cache: el motor los escribe directamente al controlador, gestionando su propio caché mediante el Buffer Pool.

### Glosario del diagrama

**HTTP / WebSocket / gRPC**
Son los protocolos que usan el cliente (navegador, app móvil, otro microservicio) y tu backend para comunicarse. HTTP es el más común: el cliente hace una petición, el servidor responde. WebSocket mantiene la conexión abierta para comunicación en tiempo real (chats, notificaciones). gRPC es un protocolo binario eficiente entre microservicios internos. El diagrama empieza aquí para mostrar que lo que desencadena toda la cadena es una petición normal de tu aplicación.

**Framework HTTP / Servidor web**
El componente de tu backend que recibe y gestiona las conexiones HTTP entrantes. Vive dentro del proceso del backend y despacha cada request al código de tu aplicación. Ejemplos: el servidor HTTP integrado de Node.js (Express, Fastify, NestJS lo usan), Gunicorn (lanza N procesos worker para Django/Flask), Uvicorn (para FastAPI), Tomcat (para Spring Boot), el `net/http` estándar de Go.

Es importante no confundirlo con el driver de base de datos: el framework gestiona el lado HTTP (conexiones del usuario hacia el backend); el driver gestiona el lado DB (conexiones del backend hacia la base de datos). Son responsabilidades completamente separadas dentro del mismo proceso. El modelo de concurrencia del framework, cuántas peticiones puede procesar en paralelo, determina el tamaño óptimo del pool del driver.

**Driver de base de datos**
Una librería que importas en tu código y que vive dentro de tu proceso de backend. Es responsable de establecer la conexión física con el servidor, hablar su wire protocol, serializar tus queries y deserializar los resultados en estructuras de tu lenguaje (listas, diccionarios, objetos). Todo lo que toca la base de datos pasa por el driver.

Ejemplos: `psycopg2` (Python → PostgreSQL), `pg` (Node.js → PostgreSQL).

No confundir con el ORM (SQLAlchemy, Sequelize, Hibernate): el ORM vive encima del driver y genera SQL automáticamente, pero depende del driver para la conexión real. El ORM habla con el driver; el driver habla con el servidor.

**Connection Pool (pool de conexiones)**
Un conjunto de conexiones TCP ya abiertas y autenticadas, listas para usar. Abrir una conexión nueva tiene un costo real: handshake TCP, autenticación con el servidor, y en PostgreSQL crear un proceso nuevo en el servidor (puede tardar 5–10 ms). El pool pre-crea N conexiones al arrancar la aplicación y las presta temporalmente. Cuando la query termina, la conexión vuelve al pool en vez de cerrarse. Un pool de 20–50 conexiones puede servir miles de requests simultáneos porque cada request necesita la conexión solo durante milisegundos, no durante toda la sesión del usuario.

Existen dos tipos: pools **embebidos** en el driver (pg-pool), que viven dentro del proceso de tu backend; y pools **externos** como PgBouncer, que son procesos separados entre tu backend y el servidor de base de datos, útiles cuando hay muchos procesos de backend.

**Wire protocol (protocolo de comunicación)**
El formato estructurado en el que viajan los mensajes entre el driver y el servidor. PostgreSQL usa el "PostgreSQL Frontend/Backend Protocol". El SQL que escribes (texto) viaja dentro de ese protocolo, pero el protocolo define el enmarcado de mensajes, la codificación de tipos de datos, la gestión de errores y los prepared statements.

**Parser + Planner**
El Parser recibe el texto SQL y lo convierte en un AST (Abstract Syntax Tree, árbol de sintaxis abstracta): una representación en memoria de la estructura del query. El Planner toma ese árbol y genera el **plan de ejecución**: la estrategia concreta para ejecutar el query (qué índice usar, cómo hacer los JOINs, si un full scan es más barato que un índice scan). El motor ejecuta el plan, no el SQL original.

**Executor (Ejecutor)**
El componente que toma el plan del Planner y lo ejecuta paso a paso. El plan es una estructura de nodos; el Executor los recorre y, para cada nodo que accede a datos, llama a las capas de almacenamiento: MVCC Engine, Lock Manager, Buffer Pool. Es el puente entre la lógica del query y el acceso real a los datos. Sin el Executor, el plan sería solo una descripción inerte de qué hacer.

**Transaction Manager**
Mantiene un registro de qué transacciones están activas y asigna un **XID** (transaction ID): un entero único que identifica la transacción ante el MVCC Engine y el sistema de locks. El XID se asigna de forma **lazy**: no al abrir la transacción (BEGIN), sino cuando ocurre la primera escritura real. Una transacción de solo lectura puede no tener XID nunca. En COMMIT, coordina el flush del WAL, marca la transacción como confirmada en el commit log y libera todos los locks.

**Lock Manager**
Gestiona los locks (bloqueos) sobre filas y otros objetos. Cuando una transacción quiere modificar una fila, pide al Lock Manager un lock exclusivo (**X lock**) sobre esa fila. Si otra transacción activa ya tiene un X lock sobre la misma fila, la transacción espera (o falla si se especificó NOWAIT). Solo interviene en **escrituras** (INSERT, UPDATE, DELETE) y en `SELECT FOR UPDATE`. Las lecturas normales no adquieren ningún lock sobre las filas leídas, lo que permite que lectores y escritores coexistan sin bloquearse.

**MVCC Engine (Motor de Control de Concurrencia Multiversión)**
El componente que permite que múltiples transacciones lean y escriban simultáneamente sin bloquearse entre sí.

_Para lecturas:_ toma un snapshot al inicio de la transacción. Cuando lee una fila, aplica las reglas de visibilidad usando los campos `xmin` y `xmax` de cada versión para decidir cuál versión es visible para este snapshot. No adquiere ningún lock.

_Para escrituras:_ en vez de modificar la fila existente en su lugar (in-place), crea una **versión nueva** de la fila con `xmin = XID_de_esta_txn` y marca la versión vieja con `xmax = XID_de_esta_txn`. Ambas versiones coexisten en la misma página hasta que vacuum limpie las versiones muertas.

Un lector nunca bloquea a un escritor porque lee una versión vieja. Un escritor nunca bloquea a un lector porque crea una versión nueva. Solo dos escritores que modifican la **misma fila** se bloquean entre sí, a través del Lock Manager.

**WAL Buffer y WAL en disco**
WAL = Write-Ahead Log (log de escritura adelantada). Es un log secuencial donde se registra cada cambio **antes** de que esa modificación llegue a los archivos de datos en disco. "Write-ahead" significa exactamente eso: primero va el log, después los datos. El WAL Buffer es la parte del WAL que reside en RAM: los registros de cambio se acumulan aquí primero. En COMMIT, el WAL Buffer se vuelca al disco mediante `fsync()` antes de responder OK al cliente. Si el sistema cae después del COMMIT, al reiniciar la base de datos lee el WAL y rehace todos los cambios confirmados.

El WAL se escribe **secuencialmente** (siempre al final del archivo), lo que es muy eficiente. Los archivos de datos se escriben de forma **aleatoria** (cada página puede estar en cualquier posición del disco), lo que es más lento.

**Buffer Pool**
La caché de páginas de disco que el servidor mantiene en RAM. Los datos se almacenan en disco como páginas de tamaño fijo (8 KB). Cuando el servidor necesita leer o modificar una fila, busca primero la página en el Buffer Pool: **cache hit** → acceso en nanosegundos desde RAM; **cache miss** → lee la página del disco y la guarda en el Buffer Pool. Cuando se modifica una fila, la página se modifica en RAM y se marca como **dirty** (sucia). Las páginas dirty se escriben a disco en el checkpoint, no en el COMMIT. Esto es seguro porque el WAL garantiza que esas modificaciones se pueden rehacer si el sistema cae antes del checkpoint.

**OS page cache**
Un buffer de RAM que el kernel mantiene automáticamente. Las escrituras del **WAL** pasan por el OS page cache antes del disco físico: el kernel puede responder "OK" sin que los bytes hayan llegado al hardware. `fsync()` fuerza al kernel a vaciar esta caché al hardware y esperar la confirmación real. Sin `fsync`, un corte de luz después de un COMMIT podría perder datos aunque el servidor respondió OK.

Los **archivos de datos** (tablas, índices) son una excepción importante: el motor los abre con `O_DIRECT`, que le indica al OS que bypasee el page cache completamente. El motor gestiona su propio caché de datos, el Buffer Pool, y el OS no interfiere. Por eso el OS page cache solo importa en la ruta del WAL, no en la ruta de las páginas de datos.

### Cómo fluye un UPDATE completo, paso a paso

El siguiente ejemplo traza qué ocurre exactamente cuando ejecutas:

```python
conn.execute("UPDATE cuentas SET saldo = saldo - 100 WHERE id = 1")
conn.commit()
```

1. **Driver: serialización y envío.** El driver toma el string SQL, lo serializa al Frontend/Backend Protocol y lo envía por el socket TCP al servidor. Si la conexión viene de un pool, ya está abierta y autenticada; no hay overhead de reconexión.

2. **Parser + Planner: SQL → plan de ejecución.** El Parser recibe los bytes, los decodifica del wire protocol y convierte el texto SQL en un AST. El Planner analiza el AST, consulta las estadísticas de la tabla (tamaño, distribución de valores, índices disponibles) y genera el plan óptimo. En este caso: "buscar la fila con `id=1` usando el índice primario, leer la fila actual, calcular el nuevo saldo".

3. **Executor: arranca la ejecución del plan.** El Executor comienza a recorrer el plan nodo a nodo. Para cada nodo que accede a datos, llama a las capas de almacenamiento. El plan es la receta; el Executor es quien la sigue.

4. **Transaction Manager: gestión de la transacción.** El Transaction Manager verifica que haya una transacción abierta. Si el código usó `BEGIN` explícito, ya existe. Si no (modo autocommit), abre una transacción implícita. El XID **no se asigna todavía**: se asignará cuando ocurra la primera escritura real.

5. **Lock Manager: serialización de escritores concurrentes.** El Lock Manager verifica si alguna otra transacción activa tiene un X lock sobre la fila con `id=1`. Si lo tiene, esta transacción espera (o falla inmediatamente si se configuró NOWAIT o se superó el timeout `lock_timeout`). Si no hay conflicto, el Lock Manager concede un X lock a esta transacción, y **el XID se asigna aquí**, en la primera escritura.

6. **MVCC Engine (escritura): WAL primero, luego la página.** Este paso es el corazón del funcionamiento de PostgreSQL. El Executor busca la página que contiene `id=1` en el Buffer Pool (si no está en RAM, la lee del disco). El MVCC Engine **no modifica la fila existente**: escribe un registro en el WAL Buffer describiendo el cambio (`"XID 1500: UPDATE cuentas id=1, old saldo=500, new saldo=400"`), antes de tocar la página; crea una versión nueva de la fila (`saldo=400, xmin=1500, xmax=0`); marca la versión vieja (`saldo=500, xmin=1234, xmax=1500`); y marca la página como dirty en el Buffer Pool. En este momento ambas versiones coexisten en la página: transacciones con snapshot anterior a XID 1500 siguen viendo `saldo=500`, las que se inicien tras el COMMIT verán `saldo=400`.

7. **COMMIT: la única escritura a disco del flujo.** Cuando `conn.commit()` llega al servidor, el Transaction Manager coordina el COMMIT en cuatro pasos: hace fsync del WAL Buffer al archivo WAL en disco (la única operación de disco bloqueante de todo el flujo, toda la durabilidad de la transacción descansa aquí); marca XID=1500 como confirmado en `pg_xact`; libera el X lock sobre `id=1`, permitiendo que otras transacciones que estuvieran esperando continúen; y responde OK por el socket TCP.

8. **Driver: control devuelto al código.** El driver recibe el OK y devuelve el control a tu código. `conn.commit()` retorna.

**¿Y las páginas dirty del Buffer Pool?** Todavía no están en disco. El proceso de **checkpoint** (que corre en background periódicamente) las escribirá al disco de datos en algún momento posterior. Esto es completamente seguro: si el servidor cae antes del checkpoint, el WAL tiene el registro completo de todos los cambios confirmados y el proceso de recovery los rehace al reiniciar.

### Puntos clave del diagrama

- **El driver vive en tu proceso de backend**, no en el servidor de base de datos. La red (TCP o Unix socket) es la frontera entre los dos mundos.
- **El Executor es el componente que falta en la mayoría de explicaciones simplificadas.** El Planner genera el plan; el Executor lo ejecuta. Son responsabilidades distintas.
- **El flujo de un write es**: Executor → Transaction Manager (abre txn) → Lock Manager (concede X lock, asigna XID) → MVCC Engine (escribe WAL Buffer **1°**, modifica página en Buffer Pool **2°**). El orden WAL primero, datos después, es la garantía de durabilidad.
- **Los lectores nunca bloquean a los escritores y viceversa.** El MVCC Engine gestiona esto con versiones de filas y snapshots. Solo dos escritores sobre la **misma fila** se bloquean, a través del Lock Manager.
- **El WAL se escribe secuencialmente** (siempre al final del log), lo que es muy rápido. Los archivos de datos se escriben de forma aleatoria (cualquier posición del disco), lo que es más lento. Por eso separar el WAL en un NVMe dedicado mejora tanto el throughput en producción.
- **El fsync en el COMMIT** es el único punto donde la base de datos espera confirmación real del hardware. Es el costo de la durabilidad (puede tardar 0,1–10 ms según el disco), pero es lo que garantiza que un COMMIT confirmado no se pierda nunca ante una caída.
- **El WAL escribe a través del OS page cache** y por eso necesita `fsync()` en cada COMMIT: sin él, el kernel podría responder "OK" sin que los bytes llegaran al disco físico. Los archivos de datos usan `O_DIRECT` y bypasean el OS page cache completamente, van directo al controlador. Son dos rutas distintas al disco.
- **El Buffer Pool, el WAL Buffer y la Lock Table viven en memoria compartida del OS**, no dentro de un proceso individual. Todos los procesos backend mapean ese mismo bloque físico de RAM en su espacio de memoria virtual. Esto es lo que permite que 100 conexiones simultáneas compartan la misma caché de páginas sin coordinación explícita.

### Un proceso OS por conexión

Cuando una aplicación se conecta, el proceso principal **postmaster** llama a `fork()` y crea un proceso hijo nuevo dedicado exclusivamente a esa conexión. Ese proceso hijo se llama **backend process** en la terminología de PostgreSQL.

```
postmaster (PID 1000)
  │  Escucha en el puerto 5432.
  │  Por cada conexión entrante: fork() → nuevo proceso hijo.
  │
  ├── backend process (PID 1001)  ← conexión del cliente A
  │     • Proceso OS independiente con PID propio.
  │     • Atiende todas las queries de esa conexión.
  │     • Lee/escribe en el buffer pool (memoria compartida).
  │     • Si crashea, solo muere este proceso. Los demás siguen.
  │
  ├── backend process (PID 1002)  ← conexión del cliente B
  ├── backend process (PID 1003)  ← conexión del cliente C
  │
  ├── autovacuum launcher  ← proceso interno (limpieza de tablas)
  ├── WAL writer           ← proceso interno (flush del WAL a disco)
  ├── checkpointer         ← proceso interno (escribe dirty pages)
  └── background writer    ← proceso interno (prepara páginas)
```

PostgreSQL usa procesos para absolutamente todo: backend processes, procesos de mantenimiento, workers internos. No usa threads en ningún caso (decisión de diseño de los años 1990 para maximizar el aislamiento ante fallos).

**Ventaja:** un crash de un backend process solo mata esa conexión. Las demás siguen sin enterarse.

**Inconveniente:** `fork()` cuesta tiempo (~milisegundos) y memoria. Con aplicaciones web que abren y cierran conexiones constantemente, este costo se vuelve inaceptable. Por eso PostgreSQL necesita **connection pooling** (PgBouncer): el pooler mantiene N procesos abiertos permanentemente y los presta a los clientes sin hacer `fork()` en cada request.

### Diagrama integral: de la conexión del cliente al CPU

```
╔══════════════════════════════════════════════════════════════════════════════╗
║  CLIENTE (tu app: Express, Django, etc.)                                     ║
║                                                                              ║
║  conn = pg.connect("host=db.ejemplo.com port=5432 ...")                     ║
║  conn.execute("BEGIN")                                                       ║
║  conn.execute("UPDATE pedidos SET estado='enviado' WHERE id=42")            ║
║  conn.execute("COMMIT")                                                      ║
║                                                                              ║
║  El driver traduce esto a mensajes del protocolo PostgreSQL Wire Protocol    ║
║  y los envía por TCP al servidor.                                            ║
╚══════════════════════════════════════════════╦═══════════════════════════════╝
                                               ║ bytes TCP
                                               ▼
╔══════════════════════════════════════════════════════════════════════════════╗
║  RED + OS DEL SERVIDOR                                                       ║
║                                                                              ║
║  Los bytes llegan a la interfaz de red.                                      ║
║  El OS los deposita en el buffer del socket del puerto 5432.                ║
║  El OS despierta al proceso que estaba escuchando ese puerto.               ║
╚══════════════════════════════════════════════╦═══════════════════════════════╝
                                               ║
                                               ▼
╔══════════════════════════════════════════════════════════════════════════════╗
║  POSTMASTER (proceso OS, PID 1000), el proceso raíz de PostgreSQL            ║
║                                                                              ║
║  Estaba bloqueado en accept(). El OS lo despierta.                          ║
║  Llama a fork() → el OS crea un proceso hijo nuevo.                         ║
║  Vuelve a listen() esperando la siguiente conexión.                         ║
╚══════════════════════════════════════════════╦═══════════════════════════════╝
                                               ║ fork() → proceso nuevo
                                               ▼
╔══════════════════════════════════════════════════════════════════════════════╗
║  RAM DEL SERVIDOR                                                            ║
║                                                                              ║
║  ┌─────────────────────────────────────────────────────────────────────┐    ║
║  │  MEMORIA COMPARTIDA (visible para todos los backend processes)       │    ║
║  │                                                                      │    ║
║  │  ┌─────────────────────┐  ┌──────────────┐  ┌───────────────────┐  │    ║
║  │  │   BUFFER POOL       │  │  LOCK TABLE  │  │   WAL BUFFER      │  │    ║
║  │  │  caché de páginas   │  │  quién tiene │  │  escrituras       │  │    ║
║  │  │  leídas del disco   │  │  qué lock    │  │  pendientes       │  │    ║
║  │  └─────────────────────┘  └──────────────┘  └───────────────────┘  │    ║
║  └─────────────────────────────────────────────────────────────────────┘    ║
║                                                                              ║
║  ┌──────────────────────────────────┐  ┌──────────────────────────────────┐ ║
║  │  BACKEND PROCESS (PID 1001)      │  │  BACKEND PROCESS (PID 1002)      │ ║
║  │  conexión del cliente A          │  │  conexión del cliente B          │ ║
║  │                                  │  │                                  │ ║
║  │  estado_txn: IN_PROGRESS         │  │  estado_txn: IDLE                │ ║
║  │  xid: 7832                       │  │  xid: (ninguno todavía)          │ ║
║  │  stack propio                    │  │  stack propio                    │ ║
║  │  heap propio (privado)           │  │  heap propio (privado)           │ ║
║  │                                  │  │                                  │ ║
║  │  Lee buffer pool (shared) ───────┼──┼──→ mismo buffer pool             │ ║
║  │  Toma lock en lock table ────────┼──┼──→ misma lock table              │ ║
║  └──────────────────────────────────┘  └──────────────────────────────────┘ ║
║                                                                              ║
║  Memoria privada de cada proceso = aislada por el MMU / page tables.        ║
║  Memoria compartida = región de RAM física que el OS mapea en el espacio    ║
║  virtual de TODOS los procesos a la vez. Cómo funciona → sección 2.         ║
╚══════════════════════════════════════════════╦═══════════════════════════════╝
                                               ║
                                               ▼
╔══════════════════════════════════════════════════════════════════════════════╗
║  CPU (4 cores)                                                               ║
║                                                                              ║
║  El OS scheduler asigna cada proceso a un core según quién tiene trabajo.   ║
║                                                                              ║
║  Tiempo →                                                                    ║
║  Core 0: [backend 1001][backend 1001][backend 1001]  ← ejecutando tu txn    ║
║  Core 1: [backend 1002][OS kernel  ][backend 1002]  ← otra conexión         ║
║  Core 2: [postmaster  ][idle       ][postmaster  ]  ← esperando conexiones  ║
║  Core 3: [WAL writer  ][checkpointer][WAL writer ]  ← procesos internos PG  ║
║                                                                              ║
║  Cuando backend 1001 espera I/O de disco:                                   ║
║    → el OS lo bloquea y saca del core                                        ║
║    → pone en el core al siguiente proceso listo                              ║
║    → cuando el disco responde, el OS desbloquea a backend 1001               ║
║    → el scheduler lo vuelve a poner en un core cuando hay turno              ║
╚══════════════════════════════════════════════╦═══════════════════════════════╝
                                               ║ read/write syscalls
                                               ▼
╔══════════════════════════════════════════════════════════════════════════════╗
║  DISCO                                                                       ║
║                                                                              ║
║  Página en buffer pool?                                                      ║
║   SÍ (buffer hit)  → lee directo de RAM, sin tocar disco. Rápido.           ║
║   NO (buffer miss) → el OS hace la operación de I/O.                        ║
║                       El proceso espera bloqueado.                           ║
║                       El OS copia la página al buffer pool.                  ║
║                       El OS desbloquea el proceso.                           ║
║                                                                              ║
║  /var/lib/postgresql/data/base/...  ← archivos de tablas (páginas de 8 KB) ║
║  /var/lib/postgresql/data/pg_wal/   ← Write-Ahead Log                       ║
╚══════════════════════════════════════════════════════════════════════════════╝


RESUMEN DE RESPONSABILIDADES
──────────────────────────────────────────────────────────────────────────────
Quién gestiona qué:

  OS (kernel)          RAM física, page tables, CPU scheduling, I/O de disco,
                       señales entre procesos, aislamiento de memoria.

  Motor de base datos  Buffer pool, lock table, WAL, estado de transacciones,
                       MVCC, planificador de queries. Todo esto usando la
                       RAM y los servicios que el OS le asigna.

  Proceso (backend)    Una conexión, una transacción a la vez, stack + heap
                       propios. Accede a la memoria compartida del motor
                       para leer/escribir páginas y registrar locks.

  Transacción          No es una "cosa física", es una variable de estado
                       (IDLE / IN_PROGRESS) dentro del proceso que atiende
                       la conexión. Solo puede haber una por conexión.
──────────────────────────────────────────────────────────────────────────────
```

---

## 2. Memoria compartida y estructura de páginas {#2-memoria-paginas}

### Estructura interna de una DB page

Una DB page de 8 KB en PostgreSQL:

```
  ┌─────────────────────────────────────────────────────────────┐
  │  ENCABEZADO (24 bytes)                                      │
  │  LSN del último cambio, checksum, flags de estado           │
  ├─────────────────────────────────────────────────────────────┤
  │  MAPA DE SLOTS (ItemId array), crece desde arriba           │
  │  [slot 1: offset 8090, len=55] [slot 2: offset 8030, len=60]│
  │  Índice de dónde empieza cada fila dentro de la página      │
  ├─────────────────────────────────────────────────────────────┤
  │                                                             │
  │  ESPACIO LIBRE (para nuevas filas e items del índice)       │
  │                                                             │
  ├─────────────────────────────────────────────────────────────┤
  │  FILAS (tuples), crecen desde abajo hacia arriba             │
  │  [fila 2: 60 bytes]                                         │
  │  [fila 1: 55 bytes]                                         │
  └─────────────────────────────────────────────────────────────┘

  Cambiar 1 byte de fila 1:
    → el motor marca los 8 KB completos como "dirty" (sucia)
    → eventualmente escribe los 8 KB completos al disco
    → no existe "escribir solo los bytes modificados"
```

### Dónde viven el buffer pool, la lock table y el WAL buffer

En el diagrama de la sección 1 aparecen el buffer pool, la lock table y el WAL buffer como "MEMORIA COMPARTIDA" accesible por todos los backend processes de PostgreSQL. Esto genera una pregunta legítima: si los procesos tienen memoria privada e independiente (garantizada por el MMU), ¿cómo pueden compartir estas estructuras?

La respuesta es un mecanismo del OS llamado **shared memory (memoria compartida)**.

#### El problema: procesos separados que necesitan un área común

Los backend processes de PostgreSQL son procesos OS independientes: la page table de cada uno no tiene traducciones a la RAM privada de los demás. Pero el buffer pool necesita ser visible por todos: si el backend del cliente A carga la página 42 al buffer pool, el backend del cliente B tiene que poder leerla sin volver a disco.

#### La solución: el OS puede mapear la misma RAM física en múltiples procesos

El OS provee llamadas al sistema (`mmap`, `shmget`) que permiten crear una región de RAM física accesible desde múltiples procesos a la vez. La clave está en la page table: el OS crea entradas en la page table de _cada proceso_ que apuntan a las _mismas páginas físicas_ de la región compartida.

```
Al arrancar PostgreSQL (postmaster):

  postmaster llama a mmap() con el flag MAP_SHARED
  → el OS reserva una región de RAM física:
      [shared_buffers: buffer pool]
      [lock table]
      [WAL buffer]
      [otras estructuras internas]
  → esta región NO pertenece a ningún proceso individualmente
  → el OS la marca como compartible

  Cuando se crea cada backend process (con fork()):
  → el OS mapea automáticamente esa región en el espacio
    virtual del nuevo proceso
  → cada proceso tiene una dirección virtual diferente que
    apunta a las mismas páginas físicas compartidas

  Resultado en la RAM:

  Backend A              Backend B              Backend C
  Espacio virtual        Espacio virtual        Espacio virtual
  ┌──────────────┐       ┌──────────────┐       ┌──────────────┐
  │ stack propio │       │ stack propio │       │ stack propio │
  │ heap privado │       │ heap privado │       │ heap privado │
  │              │       │              │       │              │
  │ 0x7F000000   │       │ 0x7F000000   │       │ 0x7F000000   │
  │ "buffer pool"│       │ "buffer pool"│       │ "buffer pool"│
  └──────┬───────┘       └──────┬───────┘       └──────┬───────┘
         │                      │                      │
         └──────────────────────┴──────────────────────┘
                                │
                    RAM FÍSICA (mismos bytes físicos)
                    ┌──────────────────────────────────┐
                    │  buffer pool (ej: 4 GB)          │
                    │  lock table                      │
                    │  WAL buffer                      │
                    └──────────────────────────────────┘
```

Cada proceso tiene su stack y heap privados (invisibles para los demás), y además tiene un rango de direcciones virtuales que el OS mapeó a la shared memory: la misma RAM física que todos los demás procesos también ven.

**La memoria compartida no anula el aislamiento privado.** El stack y el heap privados de cada proceso siguen siendo completamente invisibles para los demás. Solo la región shared memory es accesible por todos. Es como tener una pizarra compartida en el centro de una oficina: cada empleado tiene su escritorio privado que nadie más toca, pero todos pueden leer y escribir en la pizarra.

#### Cuánta RAM ocupa la shared memory de PostgreSQL

Al arrancar, PostgreSQL reserva la shared memory de golpe. El parámetro principal es `shared_buffers` (el buffer pool). Si el servidor tiene 16 GB de RAM y configuras `shared_buffers = 4GB`, el OS reserva esos 4 GB al iniciar PostgreSQL aunque no haya ninguna conexión activa todavía.

Recomendación general: `shared_buffers = 25%` de la RAM total del servidor.

---

## 3. Buffer pool por dentro {#3-buffer-pool}

### Qué es el buffer pool exactamente

Es una región de RAM dividida en slots del tamaño exacto de una DB page (8 KB). El motor mantiene un índice que funciona como un directorio: `(tabla, número_de_página) → slot_en_el_buffer_pool`.

```
Buffer pool con shared_buffers = 256 MB:

  Índice (directorio en memoria):
  ┌─────────────────────────────────────────────────────────────┐
  │  tabla "pedidos",   página 42  →  slot 1017                 │
  │  tabla "pedidos",   página 43  →  slot 1018                 │
  │  tabla "usuarios",  página 7   →  slot 204                  │
  │  tabla "productos", página 1   →  slot 3                    │
  └─────────────────────────────────────────────────────────────┘

  Slots de datos (cada uno = 8 KB):
  ┌─────────┬─────────┬─────────┬─────────┬─────────┐
  │ Slot 1  │ Slot 2  │ Slot 3  │   ...   │ Slot N  │
  │ 8 KB    │ 8 KB    │ 8 KB    │         │ 8 KB    │
  │ vacío   │ vacío   │ página  │         │ página  │
  │         │         │ pág1    │         │ de X    │
  │         │         │ prods.  │         │         │
  └─────────┴─────────┴─────────┴─────────┴─────────┘

  Total de slots = 256 MB / 8 KB = 32.768 páginas en RAM simultáneas
```

### Buffer hit y buffer miss

```
Buffer HIT — la página ya está en RAM:

  backend necesita la fila id=42 de "pedidos"
  → calcula en qué página de disco está (lógica del índice)
  → busca en el directorio del buffer pool
  → ENCONTRADA en slot 1017
  → lee los bytes directamente de la RAM del buffer pool
  Tiempo: ~nanosegundos. Sin tocar el disco.

Buffer MISS — la página NO está en RAM:

  backend necesita la página 91 de "pedidos"
  → no está en el directorio del buffer pool
  → debe traerla del disco:
      1. Elegir un slot libre (o desalojar uno existente)
      2. Emitir syscall de lectura al OS
      3. El OS lee los 8 KB del disco → los copia al slot elegido
      4. Actualizar el directorio: página 91 → slot N
      5. Leer del slot en RAM
  Tiempo: ~0.1 ms (SSD) a ~10 ms (HDD)
  Durante la espera: el OS bloquea el proceso backend,
  que cede el CPU core a otro proceso.
```

### Dirty pages: qué pasa cuando se modifica una página

Cuando un backend hace un UPDATE, INSERT o DELETE sobre una página que está en el buffer pool:

```
Backend modifica fila 42 en la página 17 (ya en el buffer pool):

  1. Escribe en WAL buffer: "XID 7832: UPDATE cuentas id=1, 500→400"
     (el WAL siempre va ANTES que la modificación, ver sección 4)
  2. Modifica los bytes de fila 42 en el slot del buffer pool (en RAM)
  3. Marca el slot como dirty=true

  Estado del slot 17 ahora:
    En RAM (buffer pool): versión NUEVA con el cambio ← lo que todos ven
    En disco:             versión VIEJA sin el cambio  ← pendiente de actualizar
    Estado:               dirty — esperando ser flusheado

  Más tarde (en checkpoint):
    El checkpointer escribe el slot 17 al disco
    Estado: dirty=false (clean)
```

¿Por qué no escribir al disco en cada COMMIT? Porque el disco es lento y las transacciones serían lentas. El WAL (archivo secuencial) es suficiente para garantizar durabilidad: si hay crash, el motor lo usa para reconstruir el estado correcto. Las páginas de datos se escriben de forma diferida, en batch, durante el checkpoint.

### Eviction: qué pasa cuando el buffer pool está lleno

Cuando todos los slots están ocupados y hay que cargar una nueva página:

```
Buffer pool lleno → necesita liberar un slot:

  Algoritmo clock-sweep:
  → Recorre los slots en orden circular
  → Cada slot tiene un contador de "uso reciente"
  → Si contador > 0: decrementarlo y continuar
  → Si contador = 0: este slot es candidato

  Si el slot candidato es CLEAN (dirty=false):
    → Descartar. El disco ya tiene la versión vigente.
    → Costo: casi ninguno.

  Si el slot candidato es DIRTY (dirty=true):
    → Escribir al disco primero (flush forzado)
    → Luego descartar y reutilizar el slot
    → Costo: una escritura a disco (~0.1-10 ms)
```

**Por eso importa el tamaño del buffer pool:** un buffer pool pequeño causa más misses, más evictions de páginas dirty, más latencia. La recomendación típica es `shared_buffers = 25%` de la RAM del servidor.

### Por qué el motor no usa el page cache del OS

El OS tiene su propio caché de archivos en RAM (el "page cache" del kernel): cuando un programa lee un archivo, el OS guarda los datos en RAM por si se vuelven a necesitar. El motor podría simplemente confiar en eso.

Pero no lo hace, porque necesita **control directo sobre el orden de escritura**:

- El WAL debe llegar al disco _antes_ que las páginas de datos (la regla WAL). Si el motor dejara al OS decidir cuándo hacer flush, el OS podría escribir una página de datos antes que su entrada WAL, violando la garantía de durabilidad.
- El motor conoce mejor qué páginas son "calientes" (frecuentemente accedidas) que el OS, que no entiende de transacciones ni de MVCC.

Por eso PostgreSQL abre sus archivos de datos con `O_DIRECT` (Linux) para hacer bypass del OS page cache y tener control total sobre las escrituras.

---

## 4. WAL interno: buffer, LSN, checkpoint y recovery {#4-wal-interno}

### El problema que resuelve

El buffer pool está en RAM. Si el servidor pierde energía, todo lo que está en RAM (incluyendo todas las páginas dirty) se pierde. Los datos que el cliente ya recibió como confirmados (COMMIT) podrían desaparecer.

La solución obvia sería escribir cada página al disco en cada COMMIT. Pero eso es extremadamente lento: una transacción que modifica 100 páginas tendría que esperar 100 operaciones de escritura aleatoria al disco.

Hay otro problema: si el crash ocurre en medio de escribir una página de 8 KB al disco (el OS escribió los primeros 4 KB pero no los últimos), la página queda en un estado inválido parcialmente escrito.

### La solución: un log secuencial que registra el cambio primero

El WAL es un archivo especial donde el motor escribe **una descripción de cada cambio** antes de modificar las páginas de datos. Es un archivo de escritura estrictamente secuencial, siempre se añade al final, nunca en posiciones aleatorias.

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

### El WAL buffer: la etapa en RAM

El motor no escribe directamente al archivo WAL en disco con cada operación, eso sería demasiado lento. Primero acumula las entradas en el **WAL buffer**, una región de la shared memory. Solo cuando la transacción hace COMMIT, el WAL buffer se vuelca (fsync) al archivo en disco.

```
Flujo completo de un UPDATE hasta COMMIT:

  RAM (shared memory)                         DISCO
  ──────────────────────────────────────────  ──────────
  1. BEGIN
     Motor crea contexto de transacción XID=7832

  2. UPDATE cuentas SET saldo=400 WHERE id=1
     a. Busca página en buffer pool
        (buffer miss → la carga del disco si no está)
     b. Escribe en WAL BUFFER:
        "XID 7832: UPDATE cuentas id=1, old=500, new=400"
     c. Modifica la fila en el buffer pool (en RAM)
        Slot marcado como dirty=true

  3. COMMIT
     a. Escribe en WAL BUFFER: "XID 7832: COMMIT"
     b. fsync: WAL buffer → archivo WAL en disco  ────────→  WAL.log ✓
        (espera confirmación del disco)
     c. Responde OK al cliente

  4. Más tarde (checkpoint, en background):
     checkpointer escribe slots dirty → archivo de tabla ──→  tabla.dat ✓

  COMMIT garantiza: el WAL está en disco.
  Las páginas de datos llegarán al disco después, de forma diferida.
```

**La invariante del WAL:** el motor nunca escribe una página de datos al disco sin antes haber escrito (y confirmado con fsync) la entrada WAL correspondiente. Esta regla es lo que le da el nombre al mecanismo: _Write-Ahead_, el log va primero.

### Por qué el WAL es suficiente para garantizar durabilidad

Si hay un crash después del COMMIT pero antes de que las páginas dirty lleguen al disco: el WAL en disco tiene la entrada "XID 7832: UPDATE ... COMMIT"; las páginas de datos en disco tienen la versión vieja (antes del UPDATE); y al reiniciar, el motor lee el WAL, detecta que XID 7832 tiene COMMIT, y vuelve a aplicar el UPDATE sobre las páginas, reconstruyendo el estado final correcto.

El WAL describe _qué cambios_ hacer. Las páginas de datos son el resultado de aplicar esos cambios. Si las páginas están desactualizadas, el WAL sirve para recalcularlas.

### LSN: Log Sequence Number

Cada entrada del WAL tiene un número único, el **LSN (Log Sequence Number)**, que indica su posición en el log. Es como un número de página del WAL.

```
WAL en disco (archivo secuencial):
┌──────────────────────────────────────────────┐
│ LSN:5038  T_120  BEGIN                       │
│ LSN:5039  T_121  BEGIN                       │
│ LSN:5040  T_120  UPDATE productos id=7 5→4  │
│ LSN:5041  T_121  INSERT pedidos id=99        │
│ LSN:5042  T_123  UPDATE cuentas id=1 500→400 │
│ LSN:5043  T_121  COMMIT                      │
│ LSN:5044  T_120  COMMIT                      │
│ LSN:5046  T_123  COMMIT                      │
└──────────────────────────────────────────────┘
```

Cada página de datos almacena el **LSN del último cambio que se le aplicó**. Esto le permite al motor saber, durante el recovery, si una página ya tiene aplicado un cambio del WAL o si todavía hay que reaplicárselo.

El motor lleva registro de tres LSNs clave: el **último LSN escrito** al WAL buffer (en RAM, aún no en disco), el **último LSN flushed** al archivo WAL en disco (estos están garantizados), y el **LSN del último checkpoint** (hasta aquí, páginas de datos y WAL están sincronizados).

### Checkpoint: sincronizar el WAL con las páginas de datos

Sin checkpoints, el WAL crecería indefinidamente y el recovery tras un crash requeriría reproducir millones de entradas desde el principio.

```
Qué hace un checkpoint:

  1. El checkpointer (proceso interno de PG) toma nota del LSN actual
     Ejemplo: checkpoint en LSN 90.000

  2. Escribe al disco TODAS las páginas dirty del buffer pool
     que tienen LSN ≤ 90.000

  3. Escribe en el WAL: "CHECKPOINT en LSN 90.000"
     Ahora el motor sabe: "todo hasta LSN 90.000 está en disco"

  4. El WAL anterior a LSN 90.000 ya no es necesario para recovery
     → se puede reciclar (reutilizar el espacio en disco)

Sin checkpoints:                   Con checkpoint cada 5 min:
  WAL: LSN 1 → 100.000               Checkpoint en LSN 90.000
  Recovery: reproducir 100.000        Recovery: solo desde LSN 90.000
  entradas (lento, minutos)           (~5 min de operaciones, segundos)
```

**El costo del checkpoint:** escribir todas las páginas dirty de golpe causa un pico de I/O ("checkpoint spike") que satura el disco y afecta la latencia de las queries normales durante esos segundos.

El parámetro `checkpoint_completion_target = 0.9` (default en PostgreSQL) distribuye las escrituras durante el 90% del intervalo entre checkpoints, suavizando el impacto:

```
  checkpoint_timeout = 5 min, completion_target = 0.9:
  → las páginas dirty se escriben uniformemente durante 4.5 min

  Sin suavizado (target=0.1):
  I/O ████████████████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ tiempo
      ↑ spike concentrado al inicio

  Con suavizado (target=0.9):
  I/O ████████████████████████████████████████████░░ tiempo
      uniforme, casi imperceptible en las queries
```

### Recovery después de un crash

Al reiniciar, el motor no sabe qué páginas dirty llegaron al disco antes del crash. Usa el WAL para reconstruir el estado correcto:

```
Al reiniciar:

  1. Localizar la entrada CHECKPOINT más reciente en el WAL
     (hasta ese LSN, las páginas de datos y el WAL están sincronizados)

  2. Leer el WAL desde ese LSN hacia adelante:
     Transacciones con COMMIT    → REDO: volver a aplicar los cambios
     Transacciones sin COMMIT    → UNDO: revertir los cambios parciales

  3. La base queda en estado consistente → abrir para nuevas conexiones

Ejemplo:
  WAL: [T_A BEGIN][T_A UPDATE][T_A COMMIT][T_B BEGIN][T_B INSERT][CRASH]

  Recovery:
    T_A tiene COMMIT → REDO: asegurar que el UPDATE está en las páginas
    T_B no tiene COMMIT → UNDO: revertir el INSERT parcial
```

El recovery de PostgreSQL con checkpoints frecuentes tarda segundos o pocos minutos, no horas, porque solo hay que reproducir el WAL desde el último checkpoint.

---

## 5. Configuración de durabilidad: fsync vs synchronous_commit {#5-durabilidad}

Estos dos parámetros de PostgreSQL se confunden frecuentemente porque ambos "aceleran los commits". Son radicalmente distintos en lo que arriesgan.

**`fsync = OFF`** deshabilita todos los fsync, incluyendo WAL y checkpoint. El OS nunca es forzado a escribir nada al disco. Si el servidor crashea, el WAL puede quedar truncado, corrupto, o inconsistente con las páginas de datos. La base puede quedar en **estado irrecuperable**, no es pérdida de datos, es corrupción. El motor puede no poder arrancar.

**`synchronous_commit = off`** hace que el motor responda OK al cliente antes de esperar el fsync del WAL. La transacción se confirma al cliente inmediatamente. El WAL llega al disco unos ~200 ms después. Si el servidor crashea en esa ventana, esas transacciones se pierden. Pero el WAL que sí llegó al disco es siempre consistente, el motor arranca limpio.

| Parámetro | Riesgo en crash | ¿Base arranca? | ¿Corrupción? |
| --- | --- | --- | --- |
| `fsync = on` | Ninguno | Siempre | Nunca |
| `synchronous_commit = off` | Perder ~200ms de transacciones | Siempre | Nunca |
| `fsync = off` | Estado irrecuperable | Posiblemente no | Sí |

**Regla práctica:** `fsync = off` solo en dev/staging/instancias desechables, nunca en producción. `synchronous_commit = off` es válido para logs, métricas, eventos de baja criticidad donde perder los últimos commits es aceptable.

### Group commit: batching de fsync para alto throughput

Cada fsync tiene un costo fijo (0.01–10 ms según el hardware). Si el motor hiciera un fsync por cada COMMIT, el throughput máximo sería `1 / latencia_fsync` TPS, con un HDD serían apenas ~100–250 TPS.

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

Group commit es la razón por la que una base con muchas conexiones concurrentes puede superar ampliamente los TPS que la latencia de fsync sugeriría en teoría. Está activo por defecto en PostgreSQL sin configuración adicional.

### Latencias de hardware y TPS resultantes

| Hardware | Latencia fsync | TPS aproximados (sin group commit) |
| --- | --- | --- |
| HDD 7200 RPM | 4–10 ms | 100–250 |
| SATA SSD | 0.1–0.5 ms | 2.000–10.000 |
| NVMe PCIe 3.0 | 0.02–0.1 ms | 10.000–50.000 |
| NVMe PCIe 4.0 | 0.01–0.05 ms | 20.000–100.000 |
| BBWC (caché con batería) | < 0.01 ms | > 100.000 |

Optimizaciones para producción de alto throughput: WAL en NVMe dedicado, separado físicamente de los archivos de datos; controlador RAID con BBWC (Battery-Backed Write Cache), que responde `fsync()` en menos de 0.01 ms porque su DRAM tiene batería (durabilidad real con latencia de RAM); y group commit activo por defecto, sin configuración extra.

### Configuración concreta de PostgreSQL

```
fsync = on/off
  → on:  correcto en producción
  → off: NUNCA en producción

synchronous_commit = on | off | remote_apply
  → on (default):    WAL flusheado a disco antes de OK al cliente
  → off:             WAL en page cache del OS; pérdida posible, sin corrupción
  → remote_apply:    espera que la réplica aplique los cambios

wal_sync_method (cómo se hace el fsync del WAL):
  → fdatasync (default Linux): flush datos sin metadata del inodo
  → fsync:                     flush datos + metadata
  → open_sync / open_datasync: write síncrono con O_SYNC / O_DSYNC
```

---

## 6. MVCC interno: xmin, xmax, ctid y el algoritmo de visibilidad {#6-mvcc-interno}

### Dónde viven físicamente las versiones

No son una abstracción: viven dentro de las DB pages del buffer pool (sección 3). Cuando PostgreSQL hace un UPDATE, escribe una nueva versión de la fila en la misma DB page (si hay espacio libre) o en otra página. Ambas versiones coexisten como tuples dentro de los slots de la página:

```
DB page 42 en el buffer pool (después de UPDATE id=1 saldo 500→300):

  ┌─────────────────────────────────────────────────────────────────┐
  │  slot 1: [xmin=1234, xmax=1500, id=1, saldo=500]  ← VIEJA      │
  │  slot 2: [xmin=1500, xmax=0,    id=1, saldo=300]  ← NUEVA      │
  │  slot 3: [xmin=1100, xmax=0,    id=2, saldo=800]  ← otra fila  │
  └─────────────────────────────────────────────────────────────────┘

  Distintos backends leen la misma página pero cada uno evalúa qué
  slots son visibles según su snapshot. La versión vieja (slot 1)
  sigue ocupando espacio en los 8KB de la página hasta que vacuum
  la limpie (sección 7), esta es la causa del table bloat.
```

### Campos de sistema: xmin, xmax y ctid

Cada fila tiene tres campos ocultos que el motor usa para decidir visibilidad:

- **`xmin`**: el XID de la transacción que creó esta versión. Significa "esta versión existe desde que xmin confirmó".
- **`xmax`**: el XID de la transacción que eliminó o reemplazó esta versión. `0` significa que nadie la ha eliminado (versión viva).
- **`ctid`**: la ubicación física `(página, slot)` de esta versión. Cada UPDATE crea la versión nueva en un slot distinto, así que el ctid cambia entre versiones; los índices lo usan para saltar directo a la versión correcta.

```sql
SELECT xmin, xmax, ctid, * FROM cuentas WHERE id = 1;

┌──────┬──────┬───────┬────┬────────┐
│ xmin │ xmax │ ctid  │ id │ saldo  │
│ 1234 │  0   │ (0,1) │  1 │  500   │
└──────┴──────┴───────┴────┴────────┘
```

### Qué pasa físicamente en un UPDATE

PostgreSQL nunca modifica una fila en el mismo lugar. Al ejecutar:

```sql
UPDATE cuentas SET saldo = 300 WHERE id = 1;
-- Ejecutado por la transacción XID = 1500
```

deja la vieja intacta y escribe una nueva versión:

```
ANTES:
  slot 1: [xmin=1234, xmax=0,    id=1, saldo=500]  ← viva

DESPUÉS (T1500 activa, antes del COMMIT):
  slot 1: [xmin=1234, xmax=1500, id=1, saldo=500]  ← VIEJA ("reemplazada por T1500")
  slot 2: [xmin=1500, xmax=0,    id=1, saldo=300]  ← NUEVA ("creada por T1500")

Ambas versiones coexisten en la misma DB page del buffer pool.
La versión vieja permanece en disco hasta que vacuum la limpie (sección 7).
```

### El snapshot: la foto fija del estado de las transacciones

Cuando una transacción empieza (o hace su primer SELECT, según el isolation level), el motor le toma un **snapshot** del estado exacto de las transacciones activas en ese instante.

El snapshot responde una sola pregunta: **"en el momento en que empecé, ¿qué transacciones ya habían confirmado sus cambios y cuáles no?"** Las transacciones que ya confirmaron antes de mi snapshot, veo sus cambios. Las que estaban activas cuando tomé mi snapshot, no veo sus cambios, aunque confirmen después. Las que empezaron después de mi snapshot, no veo sus cambios. Esto garantiza que durante toda mi transacción veo un estado consistente del mundo, el mismo que existía cuando empecé, sin importar qué otros cambios ocurran simultáneamente.

```
Snapshot de T2000 (tomado al BEGIN o al primer SELECT):

  xmin_snap = 1450   ← XID mínimo entre las transacciones activas ahora.
                       Todo con XID < 1450 ya confirmó → siempre visible.

  xmax_snap = 2100   ← próximo XID a asignar.
                       Todo con XID >= 2100 aún no existe → invisible.

  xip_list = [1500, 1800]   ← transacciones EN PROGRESO en el instante
                               exacto del snapshot. Sus cambios no son visibles
                               aunque confirmen después de que yo empecé.
```

### Algoritmo de visibilidad

Para cada versión de fila `(xmin_fila, xmax_fila)` con un snapshot:

```
PASO 1 — ¿La creación de esta versión es visible para mí?

  xmin_fila < xmin_snap             → confirmó antes del snapshot: VISIBLE
  xmin_fila >= xmax_snap            → es del futuro:               NO visible
  xmin_fila está en xip_list        → activa cuando empecé:        NO visible
  xmin_fila == mi propio XID        → yo mismo la creé:            VISIBLE

PASO 2 — ¿Esta versión sigue "viva" para mí?

  xmax_fila = 0                     → nadie la eliminó:            VIVA
  xmax_fila >= xmax_snap            → eliminación futura:          VIVA
  xmax_fila está en xip_list        → activa cuando empecé:        VIVA
  xmax_fila de txn abortada         → rollback, no aplica:         VIVA
  Cualquier otro caso               → eliminación confirmada:       MUERTA

La versión ES VISIBLE si: PASO 1 = visible  Y  PASO 2 = viva
```

### Ejemplo completo: dos snapshots, dos resultados correctos

```
Estado de la DB page en el buffer pool:
  slot 1: [xmin=1234, xmax=1500, id=1, saldo=500]  ← versión A
  slot 2: [xmin=1500, xmax=0,    id=1, saldo=300]  ← versión B (T1500 aún activa)

Transacciones en progreso: T1500, T1800

────────────────────────────────────────────────────────────────────

T2000 toma snapshot:  xmin_snap=1450, xmax_snap=2100, xip=[1500, 1800]

  Versión A (xmin=1234, xmax=1500):
    Paso 1: xmin=1234 < 1450 → visible ✓
    Paso 2: xmax=1500 está en xip → eliminación no aplica → VIVA ✓
    → T2000 VE slot 1: saldo = 500

  Versión B (xmin=1500, xmax=0):
    Paso 1: xmin=1500 está en xip → NO visible ✗
    → T2000 NO VE slot 2

→ T2000 ve saldo = 500

────────────────────────────────────────────────────────────────────

T1500 hace COMMIT.

T3000 toma snapshot:  xmin_snap=1800, xmax_snap=3100, xip=[1800]

  Versión A (xmin=1234, xmax=1500):
    Paso 1: xmin=1234 < 1800 → visible ✓
    Paso 2: xmax=1500 < 1800 y T1500 ya confirmó → eliminación aplica → MUERTA ✗
    → T3000 NO VE slot 1

  Versión B (xmin=1500, xmax=0):
    Paso 1: xmin=1500 < 1800 y T1500 ya confirmó → visible ✓
    Paso 2: xmax=0 → VIVA ✓
    → T3000 VE slot 2: saldo = 300

→ T3000 ve saldo = 300

────────────────────────────────────────────────────────────────────
T2000 sigue viendo 500. T3000 ve 300.
Ambas son correctas para el momento en que empezó cada una.
Las dos versiones siguen en la misma DB page del buffer pool,
cada backend lee el slot que corresponde a su snapshot.
```

### Transaction ID wraparound: el límite de los XIDs de 32 bits

Los XIDs son enteros de 32 bits: el contador va de 3 hasta ~4.200 millones, luego vuelve a empezar. El motor usa **aritmética modular** para comparar XIDs: un XID está "en el pasado" si está dentro de los 2.100 millones anteriores al XID actual.

El riesgo: si una fila tiene `xmin=100` y el contador llega a `2.100.000.200`, la aritmética modular dice que `100` está "en el futuro", la fila viva se vuelve invisible. Los datos parecen desaparecer sin ningún error explícito. La solución (el mecanismo de freeze y el vacuum anti-wraparound) se explica en la sección 7.

---

## 7. Vacuum y mantenimiento {#7-vacuum}

### El problema: dead tuples y table bloat

Cada UPDATE en PostgreSQL deja la versión vieja con `xmax ≠ 0`. La versión vieja no se elimina inmediatamente porque otras transacciones activas pueden necesitar verla según su snapshot. Con el tiempo, una tabla con muchas escrituras acumula dead tuples que ninguna transacción puede ver, pero que siguen ocupando espacio en las DB pages:

```
Tabla "cuentas" después de 4 UPDATEs en la misma fila:

  ┌──────────────────────────────────────────────────┐
  │  DB page 0                                       │
  │  [xmin=100, xmax=200, id=1, saldo=500] ← MUERTA  │
  │  [xmin=200, xmax=350, id=1, saldo=480] ← MUERTA  │
  │  [xmin=350, xmax=500, id=1, saldo=460] ← MUERTA  │
  │  [xmin=500, xmax=720, id=1, saldo=440] ← MUERTA  │
  │  [xmin=720, xmax=0,   id=1, saldo=420] ← VIVA    │
  └──────────────────────────────────────────────────┘

Ninguna transacción activa puede ver las primeras 4 versiones.
Siguen en disco, ocupando espacio en la página.
La tabla ocupa ~5× más espacio del necesario para sus datos reales.
Los sequential scans recorren páginas repletas de basura → más I/O, más lento.
Los índices también acumulan entradas muertas → index bloat.
```

### Visibility map: la estructura que hace el vacuum eficiente

Cada tabla tiene un archivo auxiliar pequeño (`tabla_vm`) con 2 bits por cada DB page de la tabla. El vacuum actualiza estos bits cuando limpia una página.

**Bit 1, all-visible:** todas las filas de esta página son visibles para todas las transacciones activas actuales. No hay dead tuples. El vacuum puede saltarse esta página en el próximo ciclo sin leerla.

**Bit 2, all-frozen:** todas las filas de esta página tienen sus XIDs congelados (ver la sección de wraparound más abajo). El vacuum anti-wraparound también puede saltarla.

El beneficio práctico: en el próximo ciclo de autovacuum, la mayoría de las páginas de una tabla estable pueden saltarse. El autovacuum solo trabaja en las páginas realmente modificadas desde la última limpieza.

El visibility map también acelera los **Index Only Scans**: cuando un índice puede responder una query sin ir al heap, PostgreSQL necesita verificar visibilidad de cada fila. Si el bit all-visible de la página está activo, lo confirma directamente desde el VM sin leer el heap, evitando una operación de I/O por fila.

### Qué hace VACUUM

VACUUM escanea las páginas de la tabla y realiza varias tareas en un solo paso:

1. **Liberar dead tuples:** las versiones muertas que ninguna transacción necesita se marcan como espacio libre. Ese espacio queda disponible para nuevas filas de la misma tabla.
2. **Actualizar la visibility map:** si después de limpiar una página todas sus filas son visibles, se activa el bit all-visible. Los próximos ciclos saltan esa página.
3. **Congelar XIDs viejos (anti-wraparound):** las filas con xmin suficientemente viejo se convierten a `FrozenTransactionId` (ver más abajo).
4. **Actualizar estadísticas del planner:** `VACUUM ANALYZE` actualiza la distribución de valores que el query planner usa para generar planes eficientes.

```
VACUUM normal:
  ✓ Libera dead tuples como espacio reutilizable dentro de la tabla
  ✓ Actualiza el visibility map
  ✓ Congela XIDs viejos
  ✓ Actualiza stats del planner (solo con ANALYZE)
  ✗ NO reduce el tamaño del archivo en el filesystem
    El espacio liberado es reutilizable solo por esa misma tabla.

VACUUM FULL:
  ✓ Reescribe la tabla compactamente sin dead tuples
  ✓ SÍ reduce el tamaño del archivo en disco
  ✗ Requiere ACCESS EXCLUSIVE lock durante toda la operación
    Bloquea todos los SELECT, INSERT, UPDATE y DELETE sobre la tabla
  → En producción: preferir pg_repack (extensión externa, sin lock total)
```

### Autovacuum: vacuum automático en background

PostgreSQL tiene un proceso interno permanente, el **autovacuum launcher**, que monitorea todas las tablas y lanza workers cuando es necesario.

Umbral de activación (valores default):

```
dead_tuples > autovacuum_vacuum_threshold + autovacuum_vacuum_scale_factor × n_live_rows
              50 (fijo)                     0.2 (20%)

Ejemplo con tabla de 10.000 filas:
  umbral = 50 + 0.2 × 10.000 = 2.050 dead tuples
```

Los workers corren a menor prioridad. El parámetro `autovacuum_vacuum_cost_delay` (default: 2 ms) los throttlea: después de cada chunk de trabajo, el worker hace una pausa. Gentiles con el disco, pero lentos en tablas con alta escritura.

**Cuando el autovacuum no da abasto:** en tablas de alta escritura, el bloat puede acumularse más rápido de lo que los workers pueden limpiar.

```sql
-- Detectar tablas con bloat acumulado:
SELECT
    schemaname,
    relname,
    n_live_tup,
    n_dead_tup,
    ROUND(n_dead_tup::numeric / NULLIF(n_live_tup + n_dead_tup, 0) * 100, 1) AS pct_dead,
    last_autovacuum
FROM pg_stat_user_tables
ORDER BY n_dead_tup DESC;
```

Si `pct_dead` está consistentemente por encima del 20-30% en tablas activas, ajustes posibles:

```sql
-- Por tabla individual (más granular que cambiar el global):
ALTER TABLE pedidos SET (
    autovacuum_vacuum_scale_factor = 0.05,   -- activar con 5% en lugar de 20%
    autovacuum_vacuum_cost_delay = 0          -- sin throttle para esta tabla
);

-- Forzar vacuum con salida detallada:
VACUUM (VERBOSE, ANALYZE) mi_tabla;
```

### Transaction ID wraparound: el límite de 32 bits y cómo vacuum lo previene

#### La solución: freeze (congelamiento)

El vacuum convierte el `xmin` de filas suficientemente viejas al valor especial **FrozenTransactionId** (XID = 2). Este valor significa "siempre visible para cualquier transacción". Una vez congelada, la fila nunca participa en comparaciones de XID, el wraparound no la puede afectar.

```
Fila sin freeze:
  xmin = 100
  Con XID actual = 2.100.000.200 → aritmética modular: "¿pasado o futuro?"
  → riesgo de volverse invisible

Fila con freeze:
  xmin = FrozenXID (2) → "siempre visible, sin importar el contador"
  El contador puede dar la vuelta infinitas veces. Esta fila siempre se ve.
```

El vacuum congela las filas cuyo xmin tiene más de `vacuum_freeze_max_age` transacciones de antigüedad (default: 200 millones).

#### La escalada de emergencia de PostgreSQL

Si el autovacuum no congela a tiempo, PostgreSQL tiene cuatro niveles de respuesta:

```
Nivel 1 — ~200M de diferencia (vacuum_freeze_max_age):
  Autovacuum empieza a congelar filas de las tablas que lo necesitan.
  Comportamiento normal.

Nivel 2 — ~400M de diferencia (autovacuum_freeze_max_age):
  Autovacuum fuerza vacuum en esas tablas aunque tengan poca actividad.
  Ignora los umbrales normales de dead tuples — el anti-wraparound
  tiene prioridad.

Nivel 3 — ~1.600M de diferencia:
  PostgreSQL emite WARNING en los logs.
  Autovacuum sin throttle, muy agresivo.

NIVEL CRÍTICO — ~2.100M de diferencia:
  PostgreSQL detiene TODAS las escrituras del servidor.
  INSERT, UPDATE y DELETE son rechazados con error.
  Solo acepta conexiones de superusuario para ejecutar VACUUM.
  El servidor responde SELECTs pero es inutilizable para escrituras
  hasta que un superusuario resuelva el wraparound manualmente.
  → Incidente de producción grave y 100% evitable con monitoreo básico.
```

#### Monitoreo del riesgo de wraparound

```sql
-- Riesgo por base de datos:
SELECT
    datname,
    age(datfrozenxid)                  AS txid_age,
    2100000000 - age(datfrozenxid)     AS txids_restantes
FROM pg_database
ORDER BY txid_age DESC;

-- Las tablas más en riesgo:
SELECT
    schemaname || '.' || relname       AS tabla,
    age(relfrozenxid)                  AS table_txid_age,
    pg_size_pretty(pg_total_relation_size(oid)) AS tamanio
FROM pg_class
WHERE relkind = 'r'
ORDER BY age(relfrozenxid) DESC
LIMIT 20;

-- Zonas de referencia:
--   < 200M:    zona segura
--   200M-1B:   autovacuum working (normal)
--   > 1B:      verificar que autovacuum alcance
--   > 1.5B:    zona de alerta, considerar VACUUM manual
--   > 2B:      emergencia, considerar VACUUM inmediato
--   ~ 2.1B:    PostgreSQL detiene escrituras
```

---

## 8. El planner por dentro: EXPLAIN, estadísticas y el optimizador de costos {#8-planner-interno}

### Qué estadísticas guarda PostgreSQL y de dónde salen

Cada vez que corre `ANALYZE` (manual o vía autovacuum), PostgreSQL toma una muestra de filas de la tabla y guarda, por columna, un resumen en el catálogo `pg_statistic`:

```sql
SELECT attname, n_distinct, most_common_vals, most_common_freqs, histogram_bounds
FROM pg_stats
WHERE tablename = 'pedidos' AND attname = 'estado';

  attname | n_distinct |      most_common_vals       | most_common_freqs
 ---------+------------+------------------------------+--------------------
  estado  |          4 | {enviado,pendiente,cancelado,entregado} | {0.55,0.30,0.10,0.05}
```

- **`n_distinct`**: cuántos valores distintos tiene la columna (o una estimación, negativa si es proporcional al tamaño de la tabla en vez de un número fijo).
- **`most_common_vals` / `most_common_freqs`**: los valores más frecuentes y qué fracción de la tabla representa cada uno. Es la pieza clave para estimar selectividad: `WHERE estado = 'cancelado'` se estima en 10% de las filas, no 25% (que sería el promedio ingenuo de 1/4 valores).
- **`histogram_bounds`**: para el resto de los valores (los que no entraron en `most_common_vals`), un histograma de límites que permite estimar selectividad de rangos (`WHERE precio > 500`).

Sin `ANALYZE` reciente, estas estadísticas quedan congeladas en el momento en que se corrió por última vez. Una tabla que triplicó su tamaño desde el último `ANALYZE` sigue reportando el `n_distinct` y las frecuencias de cuando era un tercio de su tamaño actual.

### El modelo de costos: de dónde salen los números de EXPLAIN

El planner no mide tiempo real, calcula un **costo estimado** en unidades arbitrarias (por convención, 1.0 equivale al costo de leer una página secuencialmente). Los parámetros base:

```
seq_page_cost      = 1.0    -- costo de leer una página secuencialmente
random_page_cost   = 4.0    -- costo de leer una página al azar (default, asume HDD)
cpu_tuple_cost     = 0.01   -- costo de procesar una fila en CPU
cpu_index_tuple_cost = 0.005 -- costo de procesar una entrada de índice
```

Con SSD/NVMe, bajar `random_page_cost` a 1.1-2.0 refleja mejor la realidad (leer al azar ya casi no cuesta más que leer secuencial) y puede cambiar decisiones del planner hacia usar más índices.

```
Costo estimado de un Sequential Scan sobre una tabla de 10.000 páginas, 1.000.000 filas:
  costo = (10.000 páginas × seq_page_cost) + (1.000.000 filas × cpu_tuple_cost)
        = 10.000 × 1.0 + 1.000.000 × 0.01
        = 10.000 + 10.000 = 20.000

Costo estimado de un Index Scan que devuelve 1.000 filas (0.1% de selectividad):
  costo = (páginas del índice a leer × random_page_cost) + (1.000 filas × (cpu_index_tuple_cost + cpu_tuple_cost))
        ≈ pequeño número de páginas del índice + 1.000 × 0.015
        ≈ mucho menor que 20.000 → el planner elige el índice
```

### Leyendo un EXPLAIN ANALYZE real

```sql
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM pedidos WHERE cliente_id = 42;

Index Scan using idx_pedidos_cliente on pedidos
  (cost=0.42..8.44 rows=3 width=120)
  (actual time=0.015..0.018 rows=3 loops=1)
  Index Cond: (cliente_id = 42)
  Buffers: shared hit=4
Planning Time: 0.089 ms
Execution Time: 0.032 ms
```

- **`cost=0.42..8.44`**: costo estimado de arrancar (0.42, casi nada para un index scan) y costo total estimado (8.44), en las unidades arbitrarias descritas arriba.
- **`rows=3`** (estimado) vs **`rows=3`** (actual, tras `ANALYZE`): cuando estos números difieren mucho (estimado 3, real 300.000), es la señal más directa de estadísticas desactualizadas o de una estimación de selectividad mala.
- **`Buffers: shared hit=4`**: 4 páginas vinieron del buffer pool, cero fueron a disco (no aparece `read=`). Es la forma más directa de ver si una query está golpeando disco o viviendo enteramente en RAM.
- **`Planning Time` vs `Execution Time`**: en queries muy simples pero con muchas particiones o índices candidatos, el planning time puede acercarse o superar el execution time, una señal de que el planner mismo se volvió el cuello de botella.

### Cuándo el planner cambia de estrategia: el algoritmo genético

Para un JOIN de N tablas, el número de órdenes posibles de join crece factorialmente. PostgreSQL explora todas las combinaciones (búsqueda exhaustiva) mientras el número de tablas sea menor a `geqo_threshold` (12 por defecto). Por encima de ese umbral, cambia a un **algoritmo genético** (GEQO): genera órdenes de join al azar, los "cruza" y "muta" como en un algoritmo evolutivo, y se queda con el mejor encontrado en un tiempo acotado, sin garantía de que sea el óptimo global. Es una decisión consciente de trade-off: con 20 tablas, buscar exhaustivamente el orden óptimo tomaría más tiempo que ejecutar un plan subóptimo.

---

## 9. El B-tree de PostgreSQL por dentro {#9-btree-interno}

### La estructura exacta de un índice B-tree

Un índice B-tree en PostgreSQL es, físicamente, un conjunto de páginas de 8 KB igual que las páginas de datos (§2), organizadas en una jerarquía: una página raíz, páginas internas, y páginas hoja donde viven las entradas reales del índice.

```
Índice sobre pedidos.cliente_id (B-tree):

                    ┌─────────────┐
                    │   RAÍZ      │   página 0
                    │ [50 | 120]  │
                    └──┬───┬───┬──┘
              ┌────────┘   │   └────────┐
              ▼            ▼            ▼
      ┌──────────┐  ┌──────────┐  ┌──────────┐
      │ interna  │  │ interna  │  │ interna  │   (en tablas grandes, más niveles)
      │ [10|30]  │  │ [70|90]  │  │[150|200] │
      └─┬──┬──┬──┘  └─┬──┬──┬──┘  └─┬──┬──┬──┘
        ▼  ▼  ▼        ▼  ▼  ▼        ▼  ▼  ▼
      ┌────────────────────────────────────────┐
      │  HOJAS: (cliente_id, ctid) ordenadas    │
      │  (42, (17,3)) (42, (17,7)) (43, (9,1))  │  ← aquí vive cada entrada
      └────────────────────────────────────────┘
```

Cada entrada de una hoja es un par `(valor_indexado, ctid)`: el `ctid` es la ubicación física exacta `(página, offset)` de la fila en el heap (§6). El índice nunca contiene el resto de las columnas de la fila (salvo con `INCLUDE`), solo la clave indexada y el puntero.

### Por qué 3-4 niveles alcanzan para millones de filas

Con páginas de 8 KB y una clave de índice típica (un entero, 8 bytes, más overhead de la entrada), cada página puede guardar varios cientos de entradas. Con un *fanout* (hijos por nodo) de, digamos, 300:

```
Nivel 0 (raíz):      1 página        →       300 punteros
Nivel 1:            300 páginas      →    90.000 punteros
Nivel 2:         90.000 páginas      → 27.000.000 punteros (hojas)

Con 3 niveles de altura, un índice puede indexar 27 millones de filas
y seguir necesitando solo 3 lecturas de página para llegar a cualquiera.
```

Esto explica por qué buscar en un índice sobre una tabla de 1.000 filas o de 100 millones de filas cuesta prácticamente lo mismo: la altura del árbol crece logarítmicamente, no linealmente.

### Qué pasa exactamente en un INSERT: page split

Cuando una hoja se llena y hay que insertar una entrada más, PostgreSQL divide la página en dos (*page split*): mueve la mitad de las entradas a una página nueva, actualiza el puntero del nivel superior para que apunte a ambas, y si el nivel superior también se llena, el split se propaga hacia arriba.

```
Antes del split (hoja llena, 100% ocupada):
  [10, 15, 20, 25, 30, 35, 40, 45]  ← página 42, sin espacio para más

Insertar 22:
  1. Divide la página 42 en dos
  2. Página 42: [10, 15, 20, 22]
     Página 88 (nueva): [25, 30, 35, 40, 45]
  3. Actualiza el puntero del padre: ahora apunta a 42 Y a 88

El costo: escribir dos páginas en vez de una, más actualizar el padre.
Con una primary key aleatoria (UUID), los splits ocurren en posiciones
impredecibles del árbol constantemente. Con una primary key secuencial
(SERIAL/IDENTITY), casi todos los inserts van al final del árbol,
sin provocar splits en el resto de la estructura.
```

### Index Only Scan: cuándo el índice basta sin tocar el heap

Normalmente, tras encontrar el `ctid` en el índice, el motor todavía tiene que ir al heap a leer la fila completa y, sobre todo, a verificar si esa versión es visible para el snapshot actual (§6), porque el índice no guarda `xmin`/`xmax`. La **visibility map** (§7) evita ese segundo viaje cuando es seguro: si la página del heap está marcada *all-visible*, el motor sabe que cualquier fila ahí es visible para cualquier transacción, y puede responder solo con lo que hay en el índice, sin leer el heap.

```sql
EXPLAIN SELECT cliente_id FROM pedidos WHERE cliente_id = 42;

Index Only Scan using idx_pedidos_cliente on pedidos
  Index Cond: (cliente_id = 42)
  Heap Fetches: 0     -- ninguna lectura al heap fue necesaria
```

Si `Heap Fetches` es mayor que cero, significa que algunas páginas no estaban marcadas *all-visible* (por ejemplo, por escrituras recientes) y el motor tuvo que verificar visibilidad directamente en el heap para esas filas.

---

## 10. El lock manager por dentro {#10-locks-interno}

### Los ocho modos de lock a nivel de tabla

Más allá de los locks de fila del §7, PostgreSQL tiene ocho modos de lock a nivel de tabla, cada uno compatible con un subconjunto de los demás. La jerarquía va de "casi no bloquea a nadie" a "bloquea todo":

```
AccessShareLock          ← SELECT normal
RowShareLock             ← SELECT FOR UPDATE / FOR SHARE
RowExclusiveLock         ← INSERT, UPDATE, DELETE
ShareUpdateExclusiveLock ← VACUUM, ANALYZE, CREATE INDEX CONCURRENTLY
ShareLock                ← CREATE INDEX (sin CONCURRENTLY)
ShareRowExclusiveLock    ← CREATE TRIGGER, algunos ALTER TABLE
ExclusiveLock            ← poco usado directamente
AccessExclusiveLock      ← ALTER TABLE, DROP TABLE, TRUNCATE, VACUUM FULL
```

La regla práctica más importante: `AccessExclusiveLock` (el más agresivo, usado por la mayoría de los DDL) es incompatible con **todos** los demás modos, incluyendo `AccessShareLock` de un simple `SELECT`. Un `ALTER TABLE` en una tabla con tráfico de lectura constante tiene que esperar a que termine cada `SELECT` en curso antes de poder tomar su lock, y mientras espera, bloquea a los `SELECT` nuevos que lleguen después.

### Ver bloqueos activos en producción

```sql
SELECT
    blocked.pid AS blocked_pid,
    blocked.query AS blocked_query,
    blocking.pid AS blocking_pid,
    blocking.query AS blocking_query
FROM pg_stat_activity blocked
JOIN pg_locks bl ON bl.pid = blocked.pid AND NOT bl.granted
JOIN pg_locks kl ON kl.locktype = bl.locktype
    AND kl.database IS NOT DISTINCT FROM bl.database
    AND kl.relation IS NOT DISTINCT FROM bl.relation
    AND kl.granted
JOIN pg_stat_activity blocking ON blocking.pid = kl.pid
WHERE blocked.pid != blocking.pid;
```

Esta query cruza `pg_locks` (qué locks existen, otorgados o en espera) con `pg_stat_activity` (qué query corre cada proceso) para mostrar exactamente quién bloquea a quién, en tiempo real.

### Cómo detecta PostgreSQL un deadlock

PostgreSQL no previene deadlocks de antemano, los detecta después de que ocurren. Cuando un proceso lleva más de `deadlock_timeout` (1 segundo por defecto) esperando un lock, construye un **grafo de espera** (quién espera a quién) y busca un ciclo:

```
T1 tiene lock sobre fila A, espera lock sobre fila B
T2 tiene lock sobre fila B, espera lock sobre fila A

Grafo de espera:  T1 → T2 → T1   (ciclo)

PostgreSQL detecta el ciclo y aborta una de las dos transacciones
(la que detectó el ciclo al esperar) con:

ERROR: deadlock detected
DETAIL: Process 1234 waits for ShareLock on transaction 5678;
        blocked by process 5678.
        Process 5678 waits for ShareLock on transaction 1234;
        blocked by process 1234.
HINT: See server log for query details.
```

La transacción abortada debe reintentarse desde el principio; la otra continúa normalmente en cuanto se libera el lock que estaba esperando. Bajar `deadlock_timeout` detecta deadlocks más rápido pero incrementa el costo de construir el grafo de espera en cada verificación; en la práctica, rara vez se ajusta del default.

---

## 11. Streaming replication por dentro {#11-replicacion-interna}

### Los procesos involucrados

En el primario, un proceso **WAL sender** por cada réplica conectada lee el WAL (§4) a medida que se genera y lo transmite por una conexión de replicación dedicada. En cada réplica, un proceso **WAL receiver** recibe esos bytes y los escribe en su propio WAL local, y un proceso de **recovery** los aplica sobre las páginas de datos de la réplica, exactamente el mismo mecanismo REDO que se usa tras un crash (§4), corriendo de forma continua en vez de una sola vez.

```
PRIMARIO                              RÉPLICA
┌─────────────────┐                  ┌─────────────────┐
│  Backend process│                  │                 │
│  hace COMMIT     │                  │                 │
│       │          │                  │                 │
│       ▼          │                  │                 │
│  WAL local        │                  │                 │
│       │           │                  │                 │
│       ▼           │                  │                 │
│  WAL sender  ──────┼──── streaming ──┼──→ WAL receiver │
│                    │     replication │       │         │
└────────────────────┘                  │       ▼         │
                                        │  WAL local      │
                                        │       │         │
                                        │       ▼         │
                                        │  proceso de     │
                                        │  recovery (REDO)│
                                        │       │         │
                                        │       ▼         │
                                        │  páginas de datos│
                                        └─────────────────┘
```

### Replication slots: evitar que el primario recicle WAL que la réplica todavía necesita

Sin un mecanismo de coordinación, el primario podría reciclar un segmento de WAL (§4) antes de que una réplica lenta llegara a leerlo, dejándola sin forma de continuar salvo reconstruir desde cero. Un **replication slot** resuelve esto: el primario recuerda, por cada slot, hasta qué punto esa réplica específica ya confirmó haber recibido el WAL, y no recicla ningún segmento posterior a ese punto.

```sql
SELECT slot_name, active, restart_lsn FROM pg_replication_slots;

 slot_name | active | restart_lsn
-----------+--------+-------------
 replica_1 | t      | 3F/2A001000
```

El costo del otro lado: si una réplica con slot se desconecta y no vuelve, el primario sigue acumulando WAL sin reciclar indefinidamente, esperándola, lo que puede llenar el disco del primario. Un slot de una réplica que ya no existe es una causa real de incidentes de espacio en disco.

### Midiendo el lag de replicación

```sql
SELECT
    application_name,
    sent_lsn,
    write_lsn,
    flush_lsn,
    replay_lsn,
    sent_lsn - replay_lsn AS bytes_de_lag
FROM pg_stat_replication;
```

Cuatro LSN (§4) marcan el progreso de cada réplica: `sent_lsn` (lo que el primario ya envió), `write_lsn` (lo que la réplica ya escribió en su WAL local, aún no confirmado a disco), `flush_lsn` (lo que la réplica ya confirmó con fsync), `replay_lsn` (lo que la réplica ya aplicó a sus páginas de datos y por lo tanto es visible en queries contra esa réplica). La diferencia entre `sent_lsn` y `replay_lsn` es el lag real: cuánto WAL generado en el primario todavía no es visible al leer de la réplica.

### Configurar replicación síncrona

```sql
-- En el primario:
ALTER SYSTEM SET synchronous_standby_names = 'replica_1';
-- El COMMIT en el primario ahora espera a que replica_1 confirme
-- (según synchronous_commit: on espera flush_lsn, remote_apply espera replay_lsn)

-- Para exigir que al menos 2 de 3 réplicas confirmen:
ALTER SYSTEM SET synchronous_standby_names = 'ANY 2 (replica_1, replica_2, replica_3)';
```

Con `remote_apply`, el COMMIT no solo espera a que la réplica tenga el cambio en su WAL, espera a que ya esté aplicado y sea visible para queries contra esa réplica, la garantía más fuerte disponible y también la más lenta.
```
