# Conexiones

**Bloque 1 — Conexiones y drivers**

- [Sección 1.1 — Visión general: dos tipos de conexiones completamente distintos](#1-vision-general)
- [Sección 1.2 — Tipo 1: conexiones HTTP (usuario → backend)](#1-tipo-http)
- [Sección 1.3 — Tipo 2: conexiones de base de datos (backend → DB)](#1-tipo-db)
- [Sección 1.4 — Cuántas conexiones necesita tu backend](#1-cuantas-conexiones)

**Bloque 2 — Connection pooling**

- [Sección 2.1 — Por qué existe el pool](#2-por-que-existe)
- [Sección 2.2 — Qué estado tiene una conexión y dónde vive](#2-estado-conexion)
- [Sección 2.3 — Pool embebido vs. pooler externo](#2-embebido-vs-externo)
- [Sección 2.4 — Modos de pooling: session mode vs. transaction mode](#2-modos-pooling)
- [Sección 2.5 — Qué se rompe en transaction mode](#2-que-se-rompe)
- [Sección 2.6 — Pooling en motores sin un pooler externo típico](#2-sin-pooler-tipico)
- [Sección 2.7 — El antipatrón que paraliza el pool: transacciones abiertas](#2-antipatron)
- [Sección 2.8 — Conexiones "zombie": cuando la red de en medio cierra la conexión sin avisar](#2-zombie)
- [Sección 2.9 — Para qué te sirve saber esto](#2-para-que-sirve)

---

**Alcance de este bloque**

**Qué es universal aquí** (aplica a cualquier base de datos cliente-servidor, sin importar
el modelo de datos): el concepto de conexión, distinto de una conexión HTTP · qué pasa a
nivel de sistema operativo cuando un motor acepta una conexión nueva · el problema de
connection pooling y su doble modo (por sesión o por transacción).

**Qué es específico de cada motor:** el mecanismo concreto para atender una conexión nueva
(proceso, thread, o asignación de un thread ya existente) y su costo real varían por motor,
cubiertos en la tabla del Bloque 1 · los drivers de ejemplo (psycopg2, pg, JDBC, mysql2)
son de motores SQL relacionales, con notas de MongoDB, Redis y Cassandra donde aplica.

**Qué se mueve a otro archivo:** todo lo que es 100% transaccional, no de la conexión en sí
(qué es una transacción, autocommit, ACID, savepoints, aislamiento) vive en
`03_transacciones_concurrencia_aislamiento.md`, Bloque 1 en adelante. Este archivo se detiene justo en el
punto donde una conexión entrega el control a una transacción, sin entrar en lo que esa
transacción garantiza o cómo se controla por dentro.

Para ver cómo motores NoSQL manejan conexiones de forma diferente
→ `guia_panorama_bases_de_datos.md` §6 (MongoDB), §7 (Redis/DynamoDB), §8 (Cassandra)

## Bloque 1: Conexiones y drivers {#1-conexiones-y-drivers}

### Sección 1.1: Visión general: dos tipos de conexiones completamente distintos {#1-vision-general}

En una aplicación web típica existen dos tipos de conexiones TCP que no tienen nada que ver entre sí. Confundirlas es uno de los errores conceptuales más comunes:

```
                    USUARIOS
          (navegadores, apps móviles)
                      │
          ┌───────────┴───────────┐
          │   Conexiones HTTP     │  ← Tipo 1
          │   Puerto 80 / 443     │
          │   Miles simultáneas   │
          │   Cortas, sin estado  │
          └───────────┬───────────┘
                      │
          ┌───────────▼───────────┐
          │   Servidor Backend    │
          │  (Node.js / Django /  │
          │   Spring / etc.)      │
          └───────────┬───────────┘
                      │
          ┌───────────┴───────────┐
          │  Conexiones DB        │  ← Tipo 2
          │  Puerto 5432 / 3306   │
          │  Pocas (pool de 5–50) │
          │  Persistentes, con    │
          │  estado, costosas     │
          └───────────┬───────────┘
                      │
          ┌───────────▼───────────┐
          │  Servidor de Base     │
          │  de Datos             │
          └───────────────────────┘
```

#### Por qué son tan distintos en cantidad

La conexión de base de datos no representa a un usuario. Representa un canal de trabajo activo. Miles de usuarios HTTP pueden ser atendidos por un pool pequeño de conexiones DB porque la mayoría del tiempo un request no está esperando la base de datos:

```
10,000 usuarios conectados a tu app
  → generan requests HTTP de ~50ms de duración media
  → cada request necesita la DB solo ~5ms (mientras ejecuta la query)
  → el resto del tiempo (45ms) el request procesa lógica, renderiza, etc.
  → nunca hay 10,000 queries a la DB al mismo tiempo
  → en el peor caso: 10,000 × (5ms / 50ms) = 1,000 queries simultáneas
  → con un pool de 50 conexiones y queries de 5ms: 50 × (1000ms/5ms) = 10,000 queries/segundo
  → 50 conexiones al pool son suficientes para 10,000 usuarios
```

Como en un banco: puede haber 1,000 clientes en fila, pero solo necesitas 20 cajeros porque cada transacción dura segundos.

---

### Sección 1.2: Tipo 1: conexiones HTTP (usuario → backend) {#1-tipo-http}

Son las conexiones que abren los navegadores o apps móviles hacia tu servidor de aplicaciones.

- **Protocolo**: HTTP/1.1, HTTP/2, HTTPS, WebSocket.
- **Puerto del servidor**: 80 (HTTP), 443 (HTTPS), o el que configures en desarrollo (3000, 8080).
- **Ciclo de vida**: en HTTP/1.1 clásico, cada request abre y cierra la conexión (o la reutiliza brevemente con keep-alive). En HTTP/2 una sola conexión TCP multiplexa muchos requests simultáneos. Son conexiones cortas.
- **Estado**: sin estado por diseño. Cada request es independiente. La identidad del usuario viaja en un JWT, cookie o header, no en la conexión en sí.
- **Costo de crear**: bajo. Un handshake TCP de 3 pasos y ya. No hay autenticación de usuarios ni procesos que se creen en el servidor.
- **Cantidad**: miles simultáneas sin problema. El sistema operativo y el web server las manejan con threads o un event loop.
- **Quién las gestiona**: el web server integrado en el framework (el HTTP server de Node.js, Gunicorn, Tomcat, etc.) o un proxy inverso delante (Nginx, Caddy).

---

### Sección 1.3: Tipo 2: conexiones de base de datos (backend → DB) {#1-tipo-db}

Son las conexiones que tu backend abre hacia el servidor de base de datos para ejecutar queries. A diferencia de las HTTP, **son persistentes y costosas**: se abren una vez al arrancar la aplicación y se mantienen abiertas indefinidamente. No se cierran entre requests.

- **Protocolo**: wire protocol propietario del motor (PostgreSQL Frontend/Backend Protocol, MySQL protocol, TDS para SQL Server, protocolo nativo de Cassandra, Bolt para Neo4j). No es HTTP.
- **Puerto del servidor DB**: 5432 (PostgreSQL), 3306 (MySQL), 1433 (SQL Server), 27017 (MongoDB), 6379 (Redis), 9042 (Cassandra), 7687 (Neo4j).
- **Ciclo de vida**: persistentes. Se abren al arrancar y permanecen abiertas hasta que la app se detiene.
- **Estado**: con estado propio en el servidor. Cada conexión tiene su isolation level (en motores SQL), variables de sesión, y puede tener una transacción abierta.
- **Costo de crear**: variable según el motor, desde microsegundos (crear un thread) hasta varios milisegundos (`fork()` de un proceso). El detalle exacto por motor está en la tabla de abajo.
- **Cantidad**: pequeño pool de 5–50 conexiones por instancia de backend, salvo en motores donde una sola conexión multiplexa muchas operaciones internamente (ver la nota de Cassandra en la tabla).
- **Quién las gestiona**: el connection pool del driver (pg-pool, HikariCP, psycopg2 pool, el driver oficial de MongoDB) o un pooler externo (PgBouncer, ProxySQL).

#### Qué pasa a nivel de sistema operativo al abrir una conexión, por motor

Todo motor cliente-servidor resuelve la misma pregunta al aceptar una conexión nueva: ¿qué unidad de ejecución del OS le asigna? La respuesta es la misma que ya viste en `01_internals_del_motor.md` §2 (modelo de concurrencia), aplicada específicamente al momento de conectar:

| Motor                | Qué pasa al aceptar la conexión                                                                                                                                                                                                              | Costo aproximado                                | Límite típico de conexiones simultáneas                                                                          |
| -------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| **PostgreSQL**       | `postmaster` hace `fork()`: crea un proceso OS nuevo dedicado a esa conexión.                                                                                                                                                                | ~1–10 ms                                        | `max_connections` (default 100)                                                                                  |
| **MySQL InnoDB**     | `mysqld` crea un thread nuevo dentro del mismo proceso, sin `fork()`.                                                                                                                                                                        | ~microsegundos                                  | `max_connections` (default 151)                                                                                  |
| **SQL Server**       | SQLOS asigna un thread de un pool ya existente; no crea nada nuevo en el caso común.                                                                                                                                                         | Prácticamente inmediato                         | Depende de la edición y la licencia                                                                              |
| **Oracle**           | Modo dedicado: `fork()` de un proceso _shadow_ por conexión, igual que PostgreSQL. Modo _shared server_: reutiliza un proceso ya activo de un pool interno.                                                                                  | Dedicado: unos ms. Shared server: microsegundos | `processes` / `sessions` (configurable)                                                                          |
| **SQLite**           | No aplica: no hay red ni servidor. "Conectar" es abrir el archivo `.sqlite` desde el mismo proceso que ya está corriendo.                                                                                                                    | Prácticamente gratis (abrir un archivo)         | No hay límite de conexiones de red; el límite real es cuántos procesos del host pueden abrir el archivo a la vez |
| **MongoDB (mongod)** | El driver abre un socket TCP; `mongod` asigna un thread de su pool interno de workers.                                                                                                                                                       | Bajo, microsegundos a un par de ms              | Configurable (miles), el límite práctico suele ser el OS (file descriptors)                                      |
| **Redis**            | Abre un socket TCP; el único hilo con event loop simplemente registra el nuevo file descriptor.                                                                                                                                              | Prácticamente gratis                            | `maxclients` (default 10,000)                                                                                    |
| **Cassandra**        | El driver del cliente abre varias conexiones nativas por nodo (no una por operación), multiplexando muchas queries concurrentes sobre pocas conexiones mediante _stream IDs_. Cada nodo asigna un thread de su pool interno a cada conexión. | Bajo                                            | Configurable por host en el driver, no por el servidor                                                           |
| **Neo4j**            | Conexión Bolt sobre TCP; la JVM asigna un thread de su pool.                                                                                                                                                                                 | Bajo                                            | `dbms.connector.bolt.thread_pool_max_size` (configurable)                                                        |
| **ClickHouse**       | Conexión TCP o HTTP; se asignan threads del pool de ejecución, más orientado a la query individual que a sostener una conexión persistente en el sentido OLTP.                                                                               | Bajo                                            | `max_connections` (default 4096)                                                                                 |

La fila de Cassandra merece atención aparte: a diferencia del resto, donde el patrón es "una conexión activa por operación en curso", el driver de Cassandra deliberadamente abre pocas conexiones por nodo y las reutiliza para muchas operaciones concurrentes al mismo tiempo (multiplexación por _stream ID_, similar en espíritu al pipelining de HTTP/2). Es la misma pregunta de este archivo, cuántas conexiones necesito, resuelta con una estrategia distinta a la de un pool tradicional.

El costo real de `fork()` en PostgreSQL, con el árbol de procesos completo, está en [`postgresql_caso_estudio.md` §1](postgresql_caso_estudio.md#1-arquitectura).

#### Puertos y cómo se identifica cada conexión

El servidor de base de datos escucha en un **puerto fijo y conocido**:

```
PostgreSQL  →  5432
MySQL       →  3306
SQL Server  →  1433
Oracle      →  1521
MongoDB     →  27017
Redis       →  6379
Cassandra   →  9042
Neo4j       →  7687
```

Cuando el driver abre una conexión, el sistema operativo le asigna automáticamente un **puerto efímero** al cliente: un número alto (rango ~49152–65535) elegido al azar entre los disponibles en ese momento. Cada conexión queda identificada por una **4-tuple** única:

```
(IP cliente,  puerto cliente,  IP servidor,  puerto servidor)
(10.0.1.5,    52341,           10.0.0.1,     5432)   ← conexión 1
(10.0.1.5,    52342,           10.0.0.1,     5432)   ← conexión 2
(10.0.1.5,    52343,           10.0.0.1,     5432)   ← conexión 3
```

Las tres conexiones van al mismo servidor y al mismo puerto 5432, pero el OS las distingue por el puerto efímero del cliente. Así puede haber 50 conexiones abiertas simultáneamente hacia el mismo servidor sin que se mezclen. El mecanismo es idéntico sin importar el motor, solo cambia el puerto fijo del servidor.

**Unix domain socket: sin IP ni puerto**

Cuando el backend y la base de datos corren en la misma máquina (o el mismo contenedor), el driver puede conectarse mediante un **Unix domain socket** en vez de TCP. En lugar de IP:puerto, la "dirección" es un archivo especial en el sistema de archivos:

```
PostgreSQL crea: /var/run/postgresql/.s.PGSQL.5432
MySQL crea:      /var/run/mysqld/mysqld.sock
Redis crea:      /var/run/redis/redis.sock (si se configura unixsocket)

El driver se conecta a ese archivo.
La comunicación pasa por el kernel sin pasar por la pila de red TCP:
sin headers, sin checksums, sin latencia de loopback.
Resultado: menor overhead, ligeramente más rápido que TCP local.
```

En producción, cuando app y base de datos están en el mismo host, los drivers de PostgreSQL, MySQL y Redis suelen preferir el Unix socket automáticamente si está disponible.

#### Flujo de apertura

1. El driver abre un socket TCP al servidor (`IP:puerto`). El OS asigna el puerto efímero al cliente.
2. El servidor responde con un mensaje de bienvenida (parte del wire protocol).
3. El driver envía usuario, contraseña y nombre de base de datos.
4. El servidor verifica y confirma: conexión lista.
5. A partir de aquí: el driver envía comandos, el servidor responde con resultados. El socket permanece abierto.

Lo que ocurre exactamente en el paso 1 del lado del servidor (fork de un proceso, creación de un thread, o asignación de un thread ya existente) es justo lo que compara la tabla de arriba.

#### Estado de la conexión

Cada conexión tiene estado propio en el servidor: isolation level actual (en motores SQL), variables de sesión, y si hay una transacción abierta o no. Esto importa con connection pooling en modo transacción: el estado de sesión no persiste entre requests porque la próxima request puede recibir una conexión diferente, ver Bloque 2.

Una conexión no pertenece a un usuario. Pertenece al pool y se presta temporalmente mientras dura una query. Cuando la query termina, la conexión regresa al pool lista para el siguiente request, aunque sea de otro usuario completamente distinto.

### Sección 1.4: Cuántas conexiones necesita tu backend {#1-cuantas-conexiones}

Framework y driver conviven en el mismo servidor backend pero tienen responsabilidades completamente separadas. Esta distinción es el punto de partida:

```
┌──────────────────────────────────────────────────────────────────┐
│                      Servidor Backend                             │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│  Framework / web server                                           │
│  (Node.js HTTP server, Gunicorn, Tomcat, Go net/http)            │
│  → gestiona conexiones HTTP (usuario → backend)                  │
│    recibe requests, asigna workers, envía respuestas HTTP         │
│    No sabe nada de la base de datos.                              │
│                                                                   │
│        │ asigna cada request a un worker                          │
│        ▼                                                          │
│                                                                   │
│  Worker (proceso OS / thread OS / goroutine / event loop)        │
│  → ejecuta el código de la aplicación (handler, ORM, lógica)    │
│    cuando el código llama al driver, el worker espera            │
│    No gestiona conexiones DB por sí mismo.                       │
│                                                                   │
│        │ llama al driver cuando el código necesita la DB         │
│        ▼                                                          │
│                                                                   │
│  Driver + pool embebido                                           │
│  (pg-pool, psycopg2 pool, HikariCP, database/sql)                │
│  → gestiona conexiones DB (backend → DB)                         │
│    mantiene el pool abierto, presta conexiones a workers         │
│    No sabe nada de HTTP.                                          │
│                                                                   │
└──────────────────────────────────────┬───────────────────────────┘
                                       │ conexiones TCP persistentes
                                       ▼
                                Servidor DB (PostgreSQL, MongoDB…)
```

**El driver siempre gestiona las conexiones DB. El framework no las toca.** Lo que cambia entre Node.js, Gunicorn, Spring y Go es el **modelo de concurrencia HTTP** de cada framework, cuántos workers existen y si bloquean al esperar la respuesta de la DB. Ese modelo es el único input que determina cuántas conexiones necesita el pool del driver.

**¿Qué es un worker? (relación con proceso e hilo)**

Ya vimos que un **proceso** es un programa corriendo de forma aislada con su propia memoria, y que un **hilo** (_thread_) es una unidad de ejecución más ligera que vive dentro de un proceso y comparte su memoria con otros hilos del mismo proceso.

Un **worker** no es una tercera cosa distinta: es el término que usan los frameworks web para nombrar a la unidad de ejecución que atiende requests. Lo que varía es qué hay por debajo:

| Framework         | El "worker" es...         | ¿Se bloquea en I/O? |
| ----------------- | ------------------------- | ------------------- |
| Gunicorn          | un proceso OS completo    | Sí                  |
| Tomcat / Spring   | un thread del servidor    | Sí                  |
| Go net/http       | una goroutine por request | No (scheduler)      |
| Node.js / FastAPI | el event loop (1 thread)  | No (async I/O)      |

La palabra "worker" es vocabulario de aplicación ("el que hace el trabajo"), mientras que "proceso" e "hilo" son vocabulario del sistema operativo ("cómo está implementado ese trabajo"). El framework elige si cada worker será un proceso, un thread, o algo más ligero, pero desde el punto de vista de tu código de aplicación, siempre es "la cosa que ejecuta el código de tu request".

---

El framework gestiona el lado HTTP, recibir requests del navegador, despacharlos, enviar respuestas, pero no ejecuta las queries a la DB. El driver gestiona el lado DB, enviar la query, recibir resultados, pero no sabe nada de HTTP. Lo que une las dos mitades es el **worker**: la unidad de ejecución del framework que maneja cada request de principio a fin y que, durante ese ciclo, llama al driver cuando el código de la aplicación necesita ir a la base de datos.

**El worker maneja el request completo: de HTTP a DB y de vuelta**

El worker no solo "recibe" la petición del navegador. Es responsable de todo el ciclo de vida del request:

```
[Navegador]
    │  HTTP GET /pedidos/42
    ▼
[Framework: acepta la conexión TCP, parsea el request HTTP,
            asigna un worker disponible para procesarlo]
    │
    ▼
[Worker — la unidad de ejecución del framework]
    │
    ├─ 1. Ejecuta middleware (auth, logging, validaciones...)
    ├─ 2. Invoca el route handler de la URL
    ├─ 3. El handler llama al ORM o al driver directamente:
    │        db.query(...)
    │
    │        El driver va al pool y pide una conexión:
    │        ┌────────────────────────────────────────┐
    │        │  Pool de conexiones (dentro del driver) │
    │        │  [conn1: LIBRE] [conn2: libre] [conn3]  │
    │        └──────────┬─────────────────────────────┘
    │                   │  pool presta conn1 al worker
    │                   ▼
    ├─ 4. conn1 envía la query al servidor DB por TCP
    │
    ├─ 5. ←─── AQUÍ está la diferencia entre frameworks ───→
    │         El worker espera la respuesta del servidor DB
    │
    ├─ 6. Recibe el resultado; el pool recupera conn1 (queda libre)
    ├─ 7. El handler construye la respuesta HTTP con los datos
    │
    ▼
[Navegador recibe HTTP 200 con el resultado]
```

El worker ejecuta el código de tu aplicación, llama al driver, y sostiene la conexión prestada mientras dura la query (pasos 4–6). Cuando la respuesta llega, la conexión regresa al pool y el worker envía el HTTP response. La conexión no pertenece al request ni al usuario, pertenece al pool, que la presta al worker durante el tiempo mínimo necesario.

**El paso 5 es la clave para el pool: ¿el worker bloquea o no?**

Lo que determina cuántas conexiones necesita el pool es exactamente qué hace el worker en el paso 5 mientras espera al servidor DB. Hay dos modelos:

**Modelo bloqueante** (Gunicorn, Tomcat): el worker se detiene por completo. No puede atender otro request hasta que llegue la respuesta. La conexión del pool sigue prestada y el worker está paralizado durante todo ese tiempo.

```
  Worker 1: [recv A]──[lanza query]────────────────[DB resp]──[send HTTP]
  Worker 2: [recv B]──[lanza query]──────────[DB resp]────────[send HTTP]
  Worker 3: [recv C]──[lógica app, sin DB]───────────────────[send HTTP]
  Worker 4: [esperando nuevo request]

            Snapshot en este momento:
            conn1 → prestada a Worker 1 (bloqueado esperando DB)
            conn2 → prestada a Worker 2 (bloqueado esperando DB)
            conn3 → libre

  Si llega request D ahora → Worker 4 lo toma, y si hace query
  necesitará conn3. Si llegara un 5.° request y todos los workers
  están bloqueados, el request espera en cola.
```

Con N workers bloqueantes, el pool necesita como máximo N conexiones, una por worker, porque cada worker puede tener como máximo una query activa a la vez.

**Modelo async** (Node.js, FastAPI async): el worker lanza la query y **no se detiene**. Registra un callback con qué hacer cuando llegue la respuesta y sigue procesando otros requests. La conexión sigue prestada, pero el worker está libre para atender otras cosas.

```
  Event loop (1 solo "worker"):

  [recv A]──[lanza query A]── · · ·(conn1 prestada)· · ──[DB resp A]──[send HTTP A]
       │
       └──[recv B]──[lanza query B]── · · ·(conn2 prestada)· ──[DB resp B]──[send HTTP B]
                 │
                 └──[recv C]──[sin DB]──[send HTTP C]
                           │
                           └──[recv D]──[lanza query D]···

            Snapshot en este momento:
            conn1 → prestada (query de A en vuelo)
            conn2 → prestada (query de B en vuelo)
            conn3 → libre

  Con 1 solo "worker" (el event loop), hay 2 conexiones prestadas
  simultáneamente. El worker no está bloqueado por ninguna.
```

Con el modelo async, el número de conexiones activas no depende del número de workers sino de cuántas queries están en vuelo en ese instante. Un solo worker puede necesitar más de una conexión al mismo tiempo.

**Tipos de worker según el framework**

Con ese ciclo claro, qué es exactamente un "worker" depende del framework:

- **Proceso OS** (Gunicorn): cada worker es un proceso Unix independiente. Bloquea en el paso 5. Un proceso = máximo una conexión prestada a la vez.
- **Thread OS** (Tomcat, Spring Boot): igual en comportamiento, pero son hilos dentro del mismo proceso del OS. Un thread = máximo una conexión prestada a la vez.
- **Event loop** (Node.js, FastAPI async): un único thread que no bloquea en el paso 5. Puede sostener múltiples conexiones prestadas simultáneamente aunque haya un solo worker.
- **Goroutine** (Go): hilo ultra-ligero gestionado por el runtime de Go, no por el OS. Bloquea en el paso 5 igual que un thread, pero son tan baratas de crear que puedes tener miles activas al mismo tiempo.

La regla es: **el pool del driver debe tener tantas conexiones como queries puedan estar activas en la DB al mismo tiempo**, que es exactamente lo que determina el tipo y cantidad de workers del framework.

Lo que sigue describe el modelo HTTP de cada framework y la configuración del pool del driver que le corresponde. El framework nunca gestiona conexiones DB: lo que varía es el modelo de concurrencia HTTP que determina cuántas el driver necesita mantener abiertas.

**Node.js (Express, Fastify, NestJS)**

Node.js tiene un único thread con un event loop no bloqueante. Un solo proceso puede manejar miles de requests HTTP concurrentes porque las operaciones de I/O (incluyendo queries a la DB) son asíncronas: el event loop lanza la query y continúa atendiendo otros requests mientras espera la respuesta.

```
Node.js process (1 thread, event loop)
  │
  ├── request A → lanza query a DB → espera async...
  ├── request B → lanza query a DB → espera async...
  ├── request C → lanza query a DB → espera async...
  └── request D → procesa lógica de negocio (sin DB)

  En este momento: 3 queries en vuelo simultáneamente.
  El pool del driver necesita al menos 3 conexiones disponibles.
```

Con Node.js, el pool del driver (pg-pool) típico es de **5–20 conexiones por proceso**. Con `cluster` (el módulo de Node.js que lanza múltiples procesos hijos idénticos para aprovechar todos los núcleos del CPU) cada proceso tiene su propio pool independiente, así que se multiplica: `pool_size × num_procesos`.

```javascript
// pg (Node.js → PostgreSQL)
const pool = new Pool({ max: 10 }); // 10 conexiones max por proceso
```

**Python con Gunicorn (Django, Flask, FastAPI sync)**

Gunicorn lanza N worker processes. Cada worker es un proceso Python que maneja **un request a la vez** de forma bloqueante: hace la query, espera el resultado, devuelve la respuesta, recién entonces toma el siguiente request.

```
gunicorn -w 4  →  4 worker processes

Worker 1: request A → query DB (bloqueado esperando respuesta)
Worker 2: request B → query DB (bloqueado esperando respuesta)
Worker 3: request C → procesando (sin DB ahora)
Worker 4: esperando nuevo request

Conexiones activas a la DB en este momento: 2
Pool del driver necesario: ~4 conexiones (una por worker, en el peor caso)
```

Con 4 workers, **4–8 conexiones en el pool del driver** son suficientes. Tener 100 no mejora nada: cada worker solo puede tener activa una conexión a la vez.

**FastAPI / Django async con Uvicorn**

Con async/await (como Node.js): un solo proceso puede tener múltiples queries en vuelo. Comportamiento similar al event loop de Node.js. Pool del driver de **5–20 conexiones por proceso**.

**Java Spring Boot (o cualquier framework con thread pool)**

Tomcat (el servidor de aplicaciones embebido en Spring Boot por defecto) usa un **thread pool**: mantiene un conjunto fijo de threads del OS creados al arrancar y listos para atender requests. Cuando llega un request HTTP, Tomcat asigna un thread disponible del pool a ese request hasta que termina. Si todos los threads están ocupados, los requests entrantes esperan en cola hasta que uno se libere. El número de threads en el pool es configurable y determina el paralelismo máximo del servidor.

Cada thread bloquea mientras espera la respuesta de la DB. El pool del driver (HikariCP) debe ser proporcional al tamaño del thread pool HTTP del servidor.

```
Tomcat thread pool: 200 threads
  → hasta 200 queries simultáneas posibles
  → HikariCP (pool del driver) con max 200 conexiones

En la práctica: la mayoría de los threads no hacen queries
en todo momento, así que 20–50 conexiones suelen bastar.
HikariCP default: 10.
```

**Go**

Go usa **goroutines**: unidades de ejecución gestionadas por el runtime de Go en lugar del OS. A diferencia de un thread del OS (que ocupa ~1–2 MB de stack por defecto), una goroutine empieza con ~4–8 KB y el runtime las multiplexa sobre un conjunto pequeño de threads del OS reales. El resultado: crear 10,000 goroutines es viable y normal en Go; crear 10,000 threads del OS no lo es en ningún lenguaje.

Cada goroutine que hace una query a la DB bloquea esperando la respuesta, igual que un thread. Pero como son tan baratas, el número de goroutines activas no limita el pool, lo que sí lo limita es el costo de las conexiones en el servidor de la DB, que es costoso independientemente del lenguaje, así que el pool del driver sigue siendo pequeño.

```go
db.SetMaxOpenConns(25)   // máximo 25 conexiones abiertas
db.SetMaxIdleConns(10)   // 10 conexiones idle en el pool
```

**Tabla resumen**

| Framework       | Conexiones por proceso   | Razonamiento                         |
| --------------- | ------------------------ | ------------------------------------ |
| Node.js (async) | 5–20                     | N queries async en vuelo             |
| Python Gunicorn | ≈ número de workers      | 1 query bloqueante por worker        |
| FastAPI async   | 5–20                     | Igual que Node.js                    |
| Java (threads)  | ≈ tamaño del thread pool | 1 query bloqueante por thread        |
| Go              | 10–50                    | Goroutines ligeras, la DB es lo caro |

Regla de oro: `pool_size` ≈ número de workers que pueden hacer queries simultáneamente. Más no es mejor, más significa más presión sobre el servidor DB. El techo real es `pool_size × num_instancias < límite_de_conexiones_del_motor` (ver la columna "Límite típico" de la tabla al inicio de esta sección), o usar un pooler externo para romper ese techo (Bloque 2).

#### Framework y motor no dependen uno del otro, pero su combinación sí determina el riesgo

El framework no elige el motor, y al motor le da igual si la query le llega desde un worker bloqueante o desde un event loop async. Pero un mismo `pool_size` significa algo completamente distinto según en qué fila de la tabla "qué pasa al abrir una conexión" caiga el motor de destino, porque lo que cambia es cuánto cuesta cada conexión que ese pool sostiene y qué tan bajo es el techo del servidor:

|                                                  | Motor caro de conectar (PostgreSQL, Oracle en modo dedicado)                                                                                                                                                                                                                                                                           | Motor barato de conectar (MySQL, MongoDB, Redis, Cassandra)                                                                                                                                              |
| ------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Framework bloqueante** (Gunicorn, Tomcat)      | **Riesgo alto.** Cada worker-proceso o worker-thread sostiene una conexión cara (`fork()` repetido), y el número de conexiones crece 1:1 con el número de workers. Al escalar instancias, el límite bajo del motor (100 en PostgreSQL por defecto) se agota rápido. Es la combinación que más urgentemente necesita un pooler externo. | **Riesgo moderado.** El número de conexiones sigue atado 1:1 al número de workers, pero cada una es barata de crear y el límite del servidor es holgado, así que hay más margen antes de tocar el techo. |
| **Framework async** (Node.js, FastAPI async, Go) | **Riesgo moderado.** Pocas conexiones reales alcanzan para muchas queries en vuelo, así que hay menos `fork()` en total que con un framework bloqueante. Pero sigue siendo importante limitar el pool: cada conexión abierta cuesta lo mismo, solo se abren con menos frecuencia.                                                      | **Riesgo bajo.** Pocas conexiones ya cubren mucha concurrencia, y cada una es barata y está lejos del límite del servidor. Es la combinación con menos fricción de todas.                                |

En la práctica: Gunicorn con muchos workers-proceso sobre PostgreSQL sin pooler es el escenario de mayor riesgo, porque cada worker es un proceso pesado hablando con un motor donde cada conexión también es un `fork()` caro sobre un límite bajo. Node.js sobre MongoDB está en el extremo contrario, casi sin fricción, porque ambos lados son baratos de escalar. Esta tabla no cambia nada de lo explicado arriba, solo te dice en qué esquina de esa combinación conviene invertir en un pooler externo antes de que duela (Bloque 2).

#### El pool tiene dos límites: el backend y la base de datos

Hasta ahora solo hemos visto el lado del backend. Pero el servidor de base de datos también tiene un límite máximo de conexiones simultáneas, ya visto en la tabla de arriba. Cuando tienes múltiples instancias del backend (escala horizontal), el problema se multiplica:

```
Sin escalar:
  1 instancia backend × pool de 20 = 20 conexiones al motor

Escalando a 10 instancias:
  10 instancias × pool de 20 = 200 conexiones al motor
  → si el límite del motor es 100, las conexiones extra son rechazadas

Con un pooler externo:
  10 instancias backend  →  pooler  →  motor
  cada una con pool=20      pool=20     límite=100
  (200 conexiones hacia     (solo 20 conexiones reales hacia el motor)
   el pooler)

El pooler multiplexa: cuando ninguna de las 10 instancias está
haciendo una query activa, las 20 conexiones reales con el motor
se comparten. Las 200 conexiones "virtuales" de los backends
existen como sockets con el pooler, no con el motor real.
```

---

## Bloque 2: Connection pooling {#2-pooling}

### Sección 2.1: Por qué existe el pool {#2-por-que-existe}

Crear una conexión nueva tiene un costo real, medido en la tabla del Bloque 1: desde microsegundos (un thread) hasta varios milisegundos (`fork()` de un proceso). Con 1,000 requests por segundo, si cada uno abriera y cerrara su propia conexión, el servidor invertiría más tiempo creando conexiones que ejecutando queries.

El pool resuelve esto: abre un conjunto fijo de conexiones al arrancar la app y las reutiliza indefinidamente. Los workers (Bloque 1) las piden prestadas para ejecutar una query y las devuelven al pool cuando terminan.

```
Sin pool (ejemplo con PostgreSQL, el caso más caro de la tabla del Bloque 1):
  cada request: fork() ~10ms + auth ~2ms + query ~2ms = 14ms de overhead
  → más overhead que trabajo real

Con pool de 20 conexiones:
  Al arrancar la app: 20 fork() → 20 conexiones listas
  Cada request: toma conexión libre (~0ms) → query ~2ms → devuelve al pool
  → el costo de fork() se paga una sola vez al iniciar
```

En motores donde abrir una conexión es barato (MySQL crea un thread, Redis solo registra un file descriptor), el pool igual tiene sentido, no por el costo de abrir, sino para **limitar el total de conexiones simultáneas** que le llegan al servidor.

---

### Sección 2.2: Qué estado tiene una conexión y dónde vive {#2-estado-conexion}

Una conexión no es solo un canal de bytes. Tiene **estado de sesión** que vive en el proceso o thread del servidor que la atiende, no en el pool del driver de tu app.

```
Tu proceso de app (driver)          Servidor PostgreSQL
  ┌───────────────────┐               ┌─────────────────────────────────────┐
  │  Pool del driver  │               │  Backend process (uno por conexión) │
  │  [conn1] ─────────────────────►  │  isolation level configurado        │
  │  [conn2]          │  socket TCP   │  SET search_path = 'mi_schema'      │
  │  [conn3]          │  abierto      │  SET work_mem = '64MB'              │
  └───────────────────┘               │  PREPARE buscar_user AS ...         │
                                      │  CREATE TEMP TABLE tmp_calc ...     │
                                      │  BEGIN → transacción activa         │
                                      └─────────────────────────────────────┘
```

No es código SQL almacenado en el pool. El pool solo mantiene abierto el socket TCP, el canal. Lo que persiste en cada conexión (el isolation level configurado, las variables de sesión, los prepared statements, la transacción activa) vive del otro lado de ese socket, en el proceso o thread del servidor.

Cuando un worker toma conn1 del pool, hereda todo el estado que el request anterior dejó en ese proceso del servidor. Si ese request modificó una variable de sesión y no la revirtió, el nuevo request verá ese valor sin haberlo pedido. **Este comportamiento es el centro del problema en pooling.**

---

### Sección 2.3: Pool embebido vs. pooler externo {#2-embebido-vs-externo}

El driver (pg-pool, psycopg2, HikariCP, el driver oficial de MongoDB) siempre incluye un **pool embebido**: ya existe, tú solo lo configuras. Vive dentro del proceso de la app.

**Instancia de la app** es una copia del proceso corriendo: un contenedor Docker, un proceso en un servidor. No es lo mismo que un worker. Un worker es la unidad de ejecución dentro de una instancia (Bloque 1); una instancia puede tener muchos workers. Si despliegas tres contenedores, tienes tres instancias, cada una con su propio pool embebido:

```
instancia 1  workers [W1][W2][W3]  pool del driver (20 conn) ─────┐
instancia 2  workers [W1][W2][W3]  pool del driver (20 conn) ──────►  motor
instancia 3  workers [W1][W2][W3]  pool del driver (20 conn) ─────┘   límite = 100

  3 instancias × 20 conn =  60 conexiones → OK
 10 instancias × 20 conn = 200 conexiones → supera el límite → rechazadas
```

El **pooler externo** es un proceso separado que se sienta entre tus instancias y la base de datos. Las instancias conectan a él en lugar de al motor directamente. Tiene su propio pool de conexiones reales y las multiplexa:

```
10 instancias de la app
  │  cada una apunta su driver al pooler en vez de al motor
  ▼
Pooler → Motor (solo 20 conexiones reales, sin importar cuántas instancias haya)
```

Para tu código el cambio es mínimo: el string de conexión del driver apunta a la IP del pooler en vez de a la del motor. El resto es igual. Los poolers externos más comunes son específicos de motor: **PgBouncer** para PostgreSQL, **ProxySQL** para MySQL.

---

### Sección 2.4: Modos de pooling: session mode vs. transaction mode {#2-modos-pooling}

Todo pooler externo, sin importar el motor que tenga detrás, resuelve el mismo dilema: cuánto tiempo le presta una conexión real a un worker que la pidió. **"Cliente"** en este contexto es un worker de tu app que conecta al pooler para pedirle una conexión real hacia la base de datos. Hay dos formas de resolverlo, y ambas existen tanto en PgBouncer (PostgreSQL) como en ProxySQL (MySQL):

**Session mode:** el worker retiene la conexión real durante toda la vida de su sesión con el pooler, hasta que desconecta explícitamente.

```
Worker 1 conecta al pooler → recibe conn1 (exclusiva hasta que Worker 1 desconecte)
Worker 2 conecta al pooler → recibe conn2 (exclusiva hasta que Worker 2 desconecte)
Worker 1 hace COMMIT, queda idle → conn1 sigue reservada para Worker 1, bloqueada
```

- El estado de sesión persiste sin sorpresas entre transacciones.
- Escalabilidad nula: 1,000 workers conectados necesitan 1,000 conexiones reales hacia el motor. El pooler no ahorra nada.

**Transaction mode (el default de PgBouncer; en ProxySQL es "multiplexing" y se comporta igual):** el worker retiene la conexión real solo durante la duración de una transacción. Al hacer COMMIT o ROLLBACK, la conexión vuelve al pool del pooler.

```
Worker 1: BEGIN → el pooler asigna conn1 → ... → COMMIT → conn1 vuelve al pool
Worker 2:                          el pooler asigna conn1 → COMMIT → vuelve al pool
(conn1 sirve a workers alternados; nadie la retiene entre transacciones)
```

- 1,000 workers activos pueden compartir 20 conexiones reales.
- El estado de sesión NO persiste entre transacciones: al hacer COMMIT, el worker suelta la conexión y la próxima transacción puede recibir una diferente con estado distinto.

El nivel de aislamiento tiene el mismo problema si se configura por sesión en vez de por transacción, algo que aplica igual en PostgreSQL, MySQL o SQL Server detrás de cualquier pooler en modo transacción (la advertencia completa está en `03_transacciones_concurrencia_aislamiento.md`, Bloque 5, sección "Configuración y advertencia sobre pools").

---

### Sección 2.5: Qué se rompe en transaction mode {#2-que-se-rompe}

Cualquier estado configurado en una transacción se pierde en la siguiente porque la próxima puede ir a una conexión distinta, con estado distinto. El ejemplo más claro es PostgreSQL detrás de PgBouncer:

```sql
-- Transacción 1:
SET search_path = 'mi_schema';  -- modifica estado en conn1
COMMIT;                          -- conn1 vuelve al pool

-- Transacción 2 (puede recibir conn2, sin ese search_path):
SELECT * FROM usuarios;          -- busca en schema equivocado → resultado incorrecto
```

```sql
-- Prepared statements manuales también fallan:
-- Transacción 1:
PREPARE buscar_usuario AS SELECT * FROM usuarios WHERE id = $1;
COMMIT;  -- conn1 regresa al pool; el prepared statement vivía en el proceso de conn1

-- Transacción 2 (puede ir a conn2, cuyo proceso no tiene ese prepared statement):
EXECUTE buscar_usuario(10);
-- ERROR: prepared statement "buscar_usuario" does not exist
```

MySQL detrás de ProxySQL en modo multiplexado tiene la misma trampa con variables de sesión (`SET @variable`) y con `PREPARE`/`EXECUTE`: si la conexión detrás del pooler cambia entre statements, la variable o el prepared statement simplemente no está ahí.

En transaction mode, sea PgBouncer o ProxySQL: **cada transacción debe ser autocontenida**. No uses `SET` fuera de una transacción esperando que persista, no uses prepared statements manuales, no uses mecanismos de notificación por conexión (`LISTEN`/`NOTIFY` en PostgreSQL).

---

### Sección 2.6: Pooling en motores sin un pooler externo típico {#2-sin-pooler-tipico}

PgBouncer y ProxySQL existen porque abrir una conexión en PostgreSQL o MySQL es relativamente caro (Bloque 1), y porque esos motores tienen un límite de conexiones bajo en relación a lo que una app moderna necesita. Varios motores no relacionales resuelven el mismo problema de forma distinta, y por eso no aparece con tanta frecuencia un "PgBouncer de MongoDB":

| Motor         | Cómo resuelve el pooling                                                                                                                                                             | Por qué no necesita (tanto) un pooler externo                                                                                                                     |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **MongoDB**   | El driver oficial mantiene su propio pool por instancia de `MongoClient` (`maxPoolSize`, default 100), reutilizado automáticamente entre operaciones.                                | Abrir una conexión es barato (un thread, no un `fork()`), y el límite de conexiones del servidor es alto por defecto.                                             |
| **Redis**     | Los clientes (`ioredis`, `redis-py`) mantienen un pool pequeño, a veces solo un puñado de conexiones persistentes.                                                                   | El servidor es single-thread: más conexiones no dan más paralelismo real, así que no hay incentivo para pools grandes ni para multiplexar con un proceso externo. |
| **Cassandra** | El driver ya gestiona su propio pool de conexiones nativas por nodo, multiplexando muchas operaciones concurrentes sobre pocas conexiones (ver la nota de _stream IDs_ en Bloque 1). | El multiplexado ya resuelve, dentro del propio driver, el problema que PgBouncer resuelve por fuera para PostgreSQL.                                              |

La lección general: un pooler externo dedicado (PgBouncer, ProxySQL) es una solución a un problema específico, conexiones caras de crear y un límite bajo en el servidor, no un requisito universal de cualquier motor. Donde ese problema no existe con la misma intensidad, el pool embebido del driver ya es suficiente.

---

### Sección 2.7: El antipatrón que paraliza el pool: transacciones abiertas {#2-antipatron}

Si un worker abre una transacción y no la cierra, por bug, excepción no capturada, o código incompleto, la conexión queda inmovilizada, sin importar qué pooler esté detrás:

```
Worker 1: BEGIN → INSERT ... → [cuelgue o olvido de COMMIT]

  → conn1 queda ligada a Worker 1 indefinidamente
  → el pooler no puede reasignarla aunque Worker 1 no esté haciendo nada
  → el pool tiene una conexión menos disponible hasta que la sesión se cierre
```

En PostgreSQL, esto es un problema doble: la conexión queda bloqueada y, además, la transacción sostiene un snapshot MVCC que impide a vacuum reciclar versiones antiguas de filas, causando table bloat con el tiempo. El detalle de por qué está en `postgresql_caso_estudio.md` §7.

```sql
-- Prevenir en PostgreSQL: cierra automáticamente transacciones inactivas tras 30s
-- En postgresql.conf o por sesión:
idle_in_transaction_session_timeout = '30s'
```

---

### Sección 2.8: Conexiones "zombie": cuando la red de en medio cierra la conexión sin avisar {#2-zombie}

Entre tu backend y el servidor de base de datos casi siempre hay algo más que un cable directo: un load balancer, un NAT gateway, un firewall corporativo. Cada una de esas piezas suele cerrar conexiones TCP que llevan un rato sin actividad, sin que eso tenga nada que ver con el pool ni con el motor:

```
Backend ──── conexión TCP idle 6 minutos ────► Load Balancer ──── DB

  El load balancer cierra la conexión inactiva a los 350s (límite típico
  de un AWS NLB) porque para él, no para el pool, esa conexión lleva
  demasiado tiempo sin tráfico.

  Ni el backend ni la DB reciben aviso de este cierre: el load balancer
  simplemente deja de reenviar paquetes. El pool sigue creyendo que
  esa conexión está viva y lista para prestarse.
```

El problema aparece en el peor momento posible: cuando un worker toma esa conexión "zombie" del pool y le envía una query. El sistema operativo del backend no sabe que el otro extremo ya no existe hasta que intenta usarla, así que el error llega recién ahí, no al guardar la conexión en el pool:

```
Node.js:  Error: Connection terminated unexpectedly / ECONNRESET
Python:   psycopg2.OperationalError: server closed the connection unexpectedly
Java:     java.sql.SQLException: This connection has been closed
```

Es un problema **universal de cualquier conexión TCP persistente**, no de un motor en particular: le pasa igual a PostgreSQL, MySQL, MongoDB, Redis o Cassandra si la conexión pasa por un intermediario con su propio timeout de inactividad. Los límites típicos varían: un NAT gateway de nube ronda los 300-350 segundos, un firewall corporativo puede ser más agresivo (60-120s) o más laxo (una hora).

Dos mecanismos, complementarios, lo resuelven:

- **TCP keepalive**: el sistema operativo envía paquetes de sondeo periódicos por conexiones inactivas para mantenerlas "vivas" ante cualquier intermediario, y para detectar rápido si el otro extremo ya no responde. La mayoría de los drivers lo pueden activar (`keepAlive: true` en pg, `tcpKeepAlive` en HikariCP).
- **Reciclar conexiones del pool antes que la red**: si configuras el tiempo de vida máximo de una conexión en el pool (`idleTimeoutMillis`, `maxLifetime`) por debajo del timeout más corto de tu infraestructura intermedia, el driver cierra y reabre la conexión de forma controlada antes de que un intermediario lo haga a la fuerza sin avisar.

La regla práctica es simple: si tu load balancer corta a los 350s, tu pool no debería dejar una conexión idle más de, digamos, 240-300s. El costo de una reconexión ocasional es mucho menor que un error de conexión reventando un request de un usuario real.

---

### Sección 2.9: Para qué te sirve saber esto {#2-para-que-sirve}

**El pool embebido ya existe, solo necesitas configurarlo**

Viene incluido en el driver. No tienes que instalarlo. Sí tienes que ajustar su tamaño según el modelo de concurrencia de tu framework (Bloque 1) y configurar timeouts para evitar conexiones colgadas:

```javascript
// pg-pool (Node.js → PostgreSQL)
const pool = new Pool({
  max: 10, // ajusta según Bloque 1
  idleTimeoutMillis: 30_000, // cierra conexiones inactivas tras 30s
  connectionTimeoutMillis: 5_000, // error si no hay conexión libre en 5s
});
```

```python
# psycopg2 (Python → PostgreSQL)
from psycopg2 import pool
cp = pool.ThreadedConnectionPool(minconn=2, maxconn=10, dsn="...")
```

```java
// HikariCP (Java → PostgreSQL o MySQL)
config.setMaximumPoolSize(10);
config.setIdleTimeout(30_000);
config.setConnectionTimeout(5_000);
```

**Un pooler externo solo lo necesitas al escalar a múltiples instancias, en los motores donde abrir una conexión es caro**

- Una instancia de la app → pool embebido es suficiente, no necesitas nada más.
- Múltiples instancias (contenedores, auto-scaling) en PostgreSQL o MySQL → añade PgBouncer o ProxySQL para que el total de conexiones no supere el límite del motor.
- En motores donde el pool embebido del driver ya resuelve el problema (MongoDB, Redis, Cassandra), normalmente no hace falta ninguna pieza adicional.

**No cambia la elección de motor**

PostgreSQL necesita pooling más urgente que MySQL porque `fork()` es costoso. MySQL tiene el mismo tipo de límite de conexiones pero el costo de creación es menor. En ambos casos la arquitectura es idéntica: pool embebido para una instancia, pooler externo al escalar. Esto no es un criterio para elegir un motor u otro, ambos resuelven el problema igual de bien con las herramientas correctas.
