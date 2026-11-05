# Panorama Experto de Bases de Datos

Guía técnica completa para entender todos los tipos de bases de datos, sus diferencias reales, sus estructuras internas, y cómo elegir la correcta para cada problema.

> Para internals de transacciones, MVCC, WAL y concurrencia ver: `guia_transacciones_db.md`

---

## Tabla de Contenidos

**Parte 1 — Fundamentos transversales**

- [1. El modelo de capas](#capas)
- [2. Estructuras de almacenamiento en disco](#estructuras)
- [3. El espectro de consistencia](#consistencia)
- [4. Modelos de distribución](#distribucion)

**Parte 2 — Deep dive por categoría**

- [5. Bases de datos relacionales](#relacional)
- [6. Bases de datos de documento](#documento)
- [7. Bases de datos clave-valor](#kv)
- [8. Wide-column stores](#widecolumn)
- [9. Motores de búsqueda](#busqueda)
- [10. Bases de datos de serie de tiempo](#timeseries)
- [11. Bases de datos de grafo](#grafo)
- [12. Bases de datos columnares / OLAP](#olap)
- [13. Bases de datos vectoriales](#vectorial)

**Parte 3 — Decisiones de arquitectura**

- [14. El fenómeno de convergencia](#convergencia)
- [15. Polyglot persistence](#polyglot)
- [16. Framework de decisión](#decision)
- [17. Costo, vendor lock-in y cómo validar antes de comprometerte](#costo-lockin)
- [18. Anti-patrones comunes](#antipatrones)

---

# PARTE 1 — FUNDAMENTOS TRANSVERSALES

---

## 1. El modelo de capas {#capas}

### Por qué importa separar las capas

Cuando alguien dice "usa PostgreSQL" o "usa Cassandra" está tomando decisiones simultáneas en ocho capas distintas sin separarlas. Esto confunde la comparación porque dos motores pueden coincidir en varias capas y diferir radicalmente en una sola — y esa diferencia es la que importa para tu problema.

Ejemplo: PostgreSQL y MongoDB coinciden en usar B-tree como estructura de almacenamiento (capa 1) y en correr en un solo nodo o con réplicas (capa 2), pero difieren completamente en modelo de datos (capa 4) y en interfaz de consulta (capa 5). Si compararas solo la capa 1 parecerían intercambiables. Si compararas solo la capa 4 parecerían no tener nada en común.

El modelo de capas da un vocabulario para hacer comparaciones quirúrgicas: en lugar de "PostgreSQL vs MongoDB" la pregunta se vuelve "¿en qué capa difieren y esa diferencia importa para mi caso?".

---

### Antes de las capas: perfil de carga de trabajo

Comparar capas asume que ya sabes qué buscar. El error más común es empezar justo ahí, por la arquitectura: te enamoras de un motor por sus propiedades técnicas (SSI, LSM-tree, HNSW) antes de haber filtrado si tu problema siquiera las necesita. Antes de bajar a la Capa 0, contesta esto sobre tu propia carga — la misma red social usada en toda esta sección sirve de ejemplo.

**Y sí, esto roza directamente la Capa 4 (modelo de datos) — con toda intención.** Son las dos preguntas que de verdad conviene resolver antes de tocar un motor, pero preguntas de naturaleza distinta: el perfil de carga pregunta **cuánto y cómo se accede** al dato (verbos — leer, escribir, cuánto pesa, qué tan rápido), sin necesidad todavía de saber **qué forma** tiene ese dato ni qué relaciones expresa, que es exactamente lo que resuelve la Capa 4. Podés responder toda la tabla de abajo sin haber decidido todavía si tu app va a ser relacional, documento o grafo — por eso el perfil de carga va primero, y por eso la conclusión de esta sección llama a la Capa 4 "la única capa que conviene resolver antes de escribir código": el perfil de carga es el insumo con el que se toma esa decisión bien, no un sustituto de ella.

| Qué observar en tu caso                                                                                                                                                        | Por qué importa                                                                                                                                                                                                                                                                | Hacia dónde empuja                                                                                                                                                                                |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Ratio lectura/escritura y patrón de acceso dominante: ¿point lookup, range scan, full-text, similitud vectorial, agregación analítica, join multi-tabla, o traversal de grafo? | Cada patrón apunta a una estructura distinta en la Capa 1 — ahí se explica por qué, no hace falta repetirlo aquí                                                                                                                                                               | 95% lecturas de feed vs. escritura pesada tipo logging llevan a estructuras y motores completamente distintos antes de comparar una sola capa                                                     |
| Tipo de escritura dominante: ¿append-only, updates frecuentes sobre el mismo registro, batch/bulk, o upsert?                                                                   | Sobrescribir in-place y solo añadir al final son disciplinas de I/O opuestas — ya vistas en "Un mismo dominio, ocho formas distintas" más abajo                                                                                                                                | En la red social, un post nuevo es casi siempre append-only; el contador de "me gusta" es update frecuente — dos cargas opuestas dentro de la misma app                                           |
| Volumen hoy y hacia dónde crece en 1-3 años — no solo cuánto pesa hoy                                                                                                          | Un dataset que cabe cómodo en RAM hoy pero va a triplicarse no debería diseñarse como si fuera a quedarse del tamaño actual (mecánica de sharding y cardinalidad de partition keys en Capa 2 y Sección 4)                                                                      | Diseñar para sharding desde el día 1 "porque algún día vamos a crecer" agrega complejidad operativa que la mayoría de proyectos no necesita todavía — súmalo cuando el volumen lo exija, no antes |
| Latencia y throughput tolerables, en p99 (no en promedio), y si la carga es pareja o llega en picos                                                                            | El promedio esconde justo el outlier que rompe un SLA; un motor que aguanta bien tráfico parejo puede no aguantar el mismo volumen en picos                                                                                                                                    | Clasificar la carga como OLTP, OLAP o HTAP —distinción que el árbol de decisión de la Sección 16 ya usa— filtra de entrada familias enteras de motores                                            |
| Multi-tenencia: ¿la carga es de un solo cliente, o de muchos clientes/organizaciones compartiendo el mismo esquema?                                                            | Un tenant mucho más grande que los demás genera una hot partition en Capa 2 si la clave de partición es el tenant; y aislar los datos de cada cliente por convención de la aplicación (en vez de por mecanismo del motor) es exactamente el riesgo que la Capa 7 ya desarrolla | Muchos tenants con datos sensibles entre sí → pesa más tener RLS nativo o una estrategia de partición por tenant pensada desde el diseño, no agregada después                                     |
| Retención y ciclo de vida del dato: ¿se conserva para siempre, o expira después de un tiempo (sesiones, caché, logs con retención de N días)?                                  | Un motor con TTL nativo (Redis, DynamoDB, Cassandra) expira registros solo; sin TTL nativo, borrar lo viejo es un job operativo recurrente que compite por recursos con el tráfico real                                                                                        | Si buena parte del dato tiene fecha de caducidad natural, un motor con TTL nativo ahorra trabajo operativo que de otro modo se repite para siempre                                                |

Ninguna de estas preguntas menciona todavía un motor ni una estructura de almacenamiento. Esa es la idea: filtran candidatos antes de que la arquitectura de las capas siguientes tenga oportunidad de seducirte con una propiedad técnica que tu carga real ni siquiera va a ejercitar.

---

### Las 8 capas

```
  más visible al usuario
        ↓
┌──────────────────────────────────────────────────────────────────┐
│  CAPA 7: CONTROL DE ACCESO Y SEGURIDAD                           │
│  RLS/ACL granular │ RBAC │ IAM externo │ cifrado en tránsito     │
├──────────────────────────────────────────────────────────────────┤
│  CAPA 6: INTEGRIDAD DECLARATIVA                                  │
│  FK │ CHECK │ UNIQUE │ NOT NULL enforced ◄──► delegado a la app  │
├──────────────────────────────────────────────────────────────────┤
│  CAPA 5: INTERFAZ DE CONSULTA                                    │
│  SQL │ CQL │ API documentos │ Cypher │ comandos KV │ Query DSL   │
│  Vector query (similarity search)                                │
├──────────────────────────────────────────────────────────────────┤
│  CAPA 4: MODELO DE DATOS                                         │
│  Tablas │ Documentos │ Clave-valor │ Wide-column │ Grafo │ Serie │
│  Vectorial (embeddings)                                          │
├──────────────────────────────────────────────────────────────────┤
│  CAPA 3: AISLAMIENTO Y CONSISTENCIA                              │
│  ACID serializable ◄──────── tunable (QUORUM) ────────► Eventual │
├──────────────────────────────────────────────────────────────────┤
│  CAPA 2: MODELO DE DISTRIBUCIÓN                                  │
│  Nodo único │ Primary-replica │ Sharding │ Consenso │ P2P ring   │
├──────────────────────────────────────────────────────────────────┤
│  CAPA 1: ESTRUCTURA DE ALMACENAMIENTO                            │
│  B-tree │ LSM-tree │ Columnar │ Inverted index │ Hash │ Graph    │
│  Vector index (HNSW/IVF)                                         │
├──────────────────────────────────────────────────────────────────┤
│  CAPA 0: ALMACENAMIENTO FÍSICO                                   │
│  Disco HDD/SSD/NVMe │ RAM │ RAM con persistencia en disco        │
└──────────────────────────────────────────────────────────────────┘
        ↑
  más cercano al hardware
```

Las capas están numeradas de abajo hacia arriba: las más fundamentales (hardware) tienen número bajo; las más visibles al usuario tienen número alto. Una decisión en una capa baja **restringe** las opciones viables en las capas superiores — por ejemplo, elegir LSM-tree en capa 1 hace que ACID en capa 3 sea técnicamente costoso de implementar.

Las Capas 6 y 7 rompen un poco esa lógica de "distancia al hardware": integridad declarativa y seguridad no son un paso más cerca o más lejos del disco, son políticas que se activan o no una vez que las capas 0-5 ya están decididas — por eso quedan arriba del todo, no porque sean lo más visible en el sentido de una interfaz, sino porque son lo último que se configura, no lo primero que se diseña.

---

### Capa 0 — Almacenamiento físico

La decisión aquí es **dónde vive el dato físicamente y a qué costo se accede a él**.

- **Disco HDD:** acceso secuencial (~100 MB/s) es órdenes de magnitud más barato que acceso aleatorio (seek time de ~5ms). Toda estructura de datos en capa 1 que corra sobre HDD tiene que minimizar accesos aleatorios — de ahí que B-tree agrupe datos en páginas y que LSM-tree haga writes secuenciales.
- **Disco SSD/NVMe:** acceso aleatorio sigue siendo más lento que secuencial pero la diferencia se reduce (~100µs vs ~10µs). Abre estructuras que en HDD eran prohibitivas. NVMe llega a ~10µs de latencia y cientos de miles de IOPS.
- **RAM:** latencia de ~100ns, sin diferencia entre acceso secuencial y aleatorio. Desbloquea estructuras que serían inutilizables en disco (hash tables gigantes, skip lists de millones de nodos).
- **RAM + persistencia en disco:** RAM para servir lecturas y escrituras; disco solo para durabilidad (append al log, snapshots periódicos). Redis opera así: sirve todo desde RAM, escribe al disco en background. El dato se puede perder si hay fallo entre el write en RAM y el flush al disco (configurable).

Esta capa no es "igual en todos" — la elección del medio físico define qué trade-offs de capa 1 son viables y cuál es el techo de rendimiento del motor.

#### Qué tener en cuenta al elegir el medio físico

| Qué observar                                                                        | Por qué importa                                                                                      | Hacia qué medio apunta                                                                                                                     |
| ----------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| ¿El dataset completo cabe en RAM, con margen de crecimiento?                        | RAM borra la diferencia entre acceso aleatorio y secuencial; en disco esa diferencia es la que manda | Cabe con margen → RAM o RAM+persistencia. No cabe → disco                                                                                  |
| ¿Qué tan grave es perder los últimos milisegundos de escritura si el proceso muere? | RAM sin journaling adecuado pierde lo no confirmado a disco todavía                                  | Tolerancia cero → disco con WAL, o RAM+persistencia síncrona. Tolerancia a perder unos ms → RAM+persistencia asíncrona (Redis por defecto) |
| Presupuesto disponible por GB almacenado                                            | RAM cuesta varias veces más por GB que SSD/NVMe, y estos más que HDD                                 | Dataset grande y presupuesto ajustado → SSD/NVMe. Dataset que cabe y presupuesto lo permite → RAM                                          |

---

### Capa 1 — Estructura de almacenamiento

La decisión aquí es **cómo se organizan físicamente los datos en el medio de almacenamiento**. Esta es la capa más técnicamente diferenciadora: define el carácter de lectura/escritura del motor, si soporta rangos, qué tan eficiente es la compresión, y cómo envejece el sistema con la carga.

La tabla resume, para cada estructura, cómo funciona, cuándo conviene, cuándo es mala idea (y por qué) y dónde aparece en la práctica:

| Estructura                   | Cómo funciona                                                                                                                                                                                                             | Cuándo brilla                                                                                                                                                                              | Cuándo se vuelve pésima                                                                                                                                                                                                 | Dónde aparece                                                       |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| **B-tree**                   | Árbol balanceado de páginas de tamaño fijo (~8KB) que se mantiene ordenado en cada escritura. Las escrituras modifican páginas existentes en su lugar.                                                                    | Lecturas puntuales y por rango con una proporción de escritura moderada. Es el patrón clásico de OLTP, con muchas más lecturas que escrituras, y estas últimas se toleran como random I/O. | Escritura masiva y secuencial, como logs, eventos o feeds, porque cada escritura mueve páginas al azar y ese random I/O termina siendo el cuello de botella de todo el sistema.                                         | InnoDB (MySQL), PostgreSQL, SQL Server. Ver sección 2.1.            |
| **LSM-tree**                 | Escrituras como appends en RAM que se vuelcan a disco en background y luego se compactan. Casi todo motor LSM-tree usa un Bloom filter por archivo para saltarse de entrada los que seguro no contienen la clave buscada. | Escritura muy alta y secuencial, que se absorbe en RAM sin tocar disco en el camino crítico de cada escritura individual.                                                                  | Cargas donde las lecturas dominan y necesitas latencia mínima y predecible, porque una sola lectura puede tener que revisar varios archivos en distintos niveles antes de encontrar el dato.                            | Cassandra, RocksDB, LevelDB, HBase. Ver sección 2.2.                |
| **Skip list**                | Lista enlazada organizada en niveles que mantiene sus elementos ordenados en memoria, con acceso, inserción y rango en tiempo logarítmico esperado.                                                                       | Datos que caben enteros en RAM y necesitan mantenerse ordenados con soporte de rango, algo que una tabla hash no puede ofrecer.                                                            | El dataset no cabe en memoria. Aquí el motivo no es el tipo de operación sino el volumen de datos, porque a diferencia de B-tree o LSM-tree, esta estructura no está pensada para amortiguar el costo de ir a disco.    | Redis (sorted sets, `ZADD`/`ZRANGE`), memtable de motores LSM-tree. |
| **Columnar**                 | Datos organizados por columna, no por fila, con compresión extrema porque valores similares quedan en secuencia.                                                                                                          | Consultas que leen pocas columnas sobre millones de filas, el patrón clásico de agregación analítica (`AVG`, `SUM`, `GROUP BY`).                                                           | Cargas OLTP con muchas actualizaciones puntuales fila por fila, porque modificar una sola fila obliga a tocar varios bloques de columnas distintos en vez de un solo bloque contiguo.                                   | ClickHouse, Redshift, BigQuery. Ver sección 2.3.                    |
| **Bitmap index**             | Un bit por cada valor posible de una columna de baja cardinalidad, marcando qué filas tienen ese valor. Combinar bitmaps de columnas distintas con AND/OR es muy rápido y compacto.                                       | Filtros sobre columnas de baja cardinalidad, como país o estado de un pedido, combinando varias condiciones a la vez.                                                                      | Columnas de alta cardinalidad. Tampoco aquí el motivo es el tipo de operación sino una propiedad del dato, porque el número de bitmaps crece con la cantidad de valores distintos y la estructura deja de ser compacta. | Oracle, y como complemento de motores columnares como ClickHouse.   |
| **Inverted index**           | Mapeo de cada término a la lista ordenada de documentos que lo contienen (posting list).                                                                                                                                  | Búsqueda de texto completo, encontrar todos los documentos que contienen un término, no un documento específico.                                                                           | Acceso por clave primaria exacta, porque no es su propósito y cualquier estructura de propósito general (hash, B-tree) la supera ahí sin esfuerzo.                                                                      | Elasticsearch, Lucene.                                              |
| **Hash**                     | Tabla hash sobre disco o RAM con acceso por clave exacta en tiempo O(1).                                                                                                                                                  | Acceso por clave exacta en O(1), y nada más que eso.                                                                                                                                       | Cualquier consulta por rango o resultados ordenados, porque no hay forma de responder eso sin escanear toda la tabla.                                                                                                   | Redis (tipos hash), índices secundarios de otros motores.           |
| **Graph-native**             | Nodos y aristas almacenados con punteros directos entre sí (listas de adyacencia nativas).                                                                                                                                | Recorridos de relaciones (traversal) en tiempo proporcional al grado del nodo, sin importar el tamaño total de la base de datos.                                                           | Queries analíticas que tocan todos los nodos a la vez, porque no está optimizada para escaneos masivos como sí lo está columnar.                                                                                        | Neo4j, Amazon Neptune.                                              |
| **Vector index (HNSW, IVF)** | Estructura optimizada para _approximate nearest neighbor_ (ANN). HNSW construye un grafo de navegación en capas; IVF particiona el espacio en clusters y busca solo en los más cercanos al centroide.                     | Encontrar los k vectores más parecidos a uno de consulta en un espacio de cientos o miles de dimensiones, aceptando una respuesta aproximada a cambio de velocidad.                        | Resultados exactos garantizados, porque tanto HNSW como IVF sacrifican exactitud por diseño, no por un error de implementación.                                                                                         | Pinecone, Milvus, Weaviate, pgvector.                               |

El medio físico disponible en L0 atraviesa toda la tabla. Si todo el dataset cabe en RAM, hash y skip list son viables incluso con escritura aleatoria; si vive en disco, la estructura elegida debe minimizar activamente la aleatoriedad, que es exactamente la obsesión que separa a B-tree de LSM-tree.

#### Por qué se siente tan parecida a la Capa 4, y en qué se diferencian de verdad

Es una observación correcta: casi cada fila de la tabla de la Capa 4 tiene aquí una pareja natural, relacional con B-tree, documento con B-tree, wide-column con LSM-tree, columnar con columnar, grafo con graph-native, serie de tiempo con su variante de LSM, vectorial con HNSW/IVF. Parece la misma decisión contada dos veces. No lo es, y la prueba ya apareció antes en este documento sin nombrarla así: MyRocks es MySQL, exactamente el mismo modelo relacional de tablas y filas de siempre (L4 sin cambiar), corriendo sobre RocksDB, un motor LSM-tree (L1), en vez del B-tree clásico de InnoDB. El modelo de datos no cambió una coma; la estructura de almacenamiento sí.

Eso es posible porque L4 y L1 responden preguntas de naturaleza distinta. L4 responde una pregunta **semántica** sobre el significado y las relaciones de tu dato, que se puede describir en una pizarra sin mencionar un disco, una página o un algoritmo. L1 responde una pregunta de **ingeniería pura** sobre cómo acomodar bytes para que las operaciones más frecuentes sean baratas, y no le importa si esos bytes representan un post, una lectura de sensor o una transacción bancaria; solo le importa si se leen mucho o poco, si se escriben en orden o al azar, y si se piden por rango o por igualdad exacta.

Lo que de verdad determina L1 es el **patrón de acceso** que tu modelo de datos trae consigo, no el modelo en sí. Por eso casi siempre van de la mano (wide-column empuja a escritura secuencial, que es justo lo que LSM-tree optimiza), pero cuando el patrón de acceso real se aparta de lo que su modelo sugiere, como en MyRocks, la pareja se rompe y ahí conviene pagar el costo de una estructura fuera de lo común.

---

### Capa 2 — Modelo de distribución

La decisión aquí es **cuántos nodos participan y cómo se coordinan para escribir y leer**. A diferencia de L4, esta capa no nace de tu dominio, ya que nadie modela "el feed de la red social" y concluye directamente "necesito peer-to-peer": esta capa nace de tres cosas ajenas a la forma de tu dato, que son cuánto volumen tienes, dónde están tus usuarios, y qué tan grave es que el sistema deje de responder si algo falla. Otra diferencia importante frente a L4 es que las opciones de abajo no son un menú paralelo de formas igualmente válidas, sino una progresión de complejidad y costo operativo. Nadie debería elegir la última opción solo porque "así escalan las apps grandes".

| Opción                            | En qué consiste                                                                                                                                                                                                                                                                                                                                                                                                                                     | Escalado   | Dónde vive el dato                                                                                  |
| --------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- | --------------------------------------------------------------------------------------------------- |
| **Nodo único**                    | Un proceso, un servidor. Sin coordinación de red. Máxima consistencia, mínima complejidad. El límite es la capacidad de un solo servidor.                                                                                                                                                                                                                                                                                                           | Vertical   | Todo en un solo lugar, sin réplica ni partición                                                     |
| **Primary-replica**               | Un nodo primario recibe todas las escrituras; las réplicas reciben copias asíncronas y pueden servir lecturas. Escala lecturas, no escrituras. El lag de replicación introduce ventanas de inconsistencia temporal.                                                                                                                                                                                                                                 | Horizontal | Solo réplica, cada servidor tiene el dato completo                                                  |
| **Sharding**                      | Las filas de una tabla (o de un grupo de tablas relacionadas que comparten shard key) se distribuyen entre múltiples servidores de base de datos independientes, cada uno con su propio disco, su propio WAL, su propio lock manager y su propia memoria, según una clave de partición (hash o rango). Cada shard escala sus propias escrituras de forma independiente, pero las queries que cruzan shards requieren scatter-gather y son costosas. | Horizontal | Solo partición, cada shard tiene una porción exclusiva, no una copia                                |
| **Consenso distribuido** (NewSQL) | Cada escritura pasa por un protocolo de consenso (Raft, Paxos) entre múltiples nodos antes de confirmarse. Garantiza ACID distribuido sin sacrificar consistencia. El costo es latencia extra (una ronda de red mínima) en cada escritura.                                                                                                                                                                                                          | Horizontal | Partición y réplica, cada rango de claves vive en su propio grupo de nodos que se coordina entre sí |
| **Peer-to-peer** sin coordinador  | Todos los nodos son equivalentes (Cassandra, DynamoDB). Las escrituras se enrutan al nodo responsable del rango de claves (consistent hashing). Alta disponibilidad sin punto de falla único. Consistencia eventual como consecuencia natural de no coordinar.                                                                                                                                                                                      | Horizontal | Partición y réplica, cada clave vive en su propio conjunto de N nodos, sin coordinación previa      |

#### Sharding no es partitioning, y tampoco es functional partitioning

Tres términos que se usan como sinónimos y no lo son. **Partitioning** divide las filas de una tabla en fragmentos más manejables, pero esos fragmentos siguen viviendo dentro del mismo proceso de base de datos, con el mismo disco, el mismo WAL y el mismo lock manager, tal como funciona el particionamiento nativo de PostgreSQL u Oracle. **Sharding** hace esa misma división de filas, pero entre procesos de base de datos independientes, cada uno con su propio disco, su propio WAL, su propio lock manager y su propia memoria. La diferencia entre ambos no es conceptual, es física: partitioning nunca sale de un proceso, mientras que sharding, por definición, siempre implica más de un proceso y por lo tanto siempre implica red entre ellos. Por eso una tabla dividida en particiones dentro de un solo PostgreSQL sigue siendo, en términos de esta capa, un **nodo único**; recién se vuelve sharding cuando esas particiones viven en servidores distintos.

**Functional partitioning** (también llamada federación, o database-per-service en arquitecturas de microservicios) es un tercer concepto, distinto de los dos anteriores. En vez de dividir las filas de una misma tabla, distribuye tablas completas y distintas entre servidores distintos: cada servidor tiene su propio esquema, típicamente correspondiente a un dominio de negocio, como usuarios en un servidor, inventario en otro y pagos en un tercero. No es un peldaño más en la escalera de arriba, porque no resuelve el problema de que una sola tabla crezca más allá de un nodo. Resuelve un problema distinto, que es cómo dividir el sistema completo por dominio, algo que en la práctica suele decidirse a nivel de arquitectura de servicios y no dentro de un único motor de base de datos.

#### Por qué es una escalera con una horquilla, no un menú

Sharding "puro" (solo partición, sin réplica) casi nunca se usa solo en producción, porque en la práctica siempre se le agrega replicación por shard para no perder ese pedazo si el servidor se cae. Por eso consenso y peer-to-peer son, en el fondo, "sharding + una forma de replicar cada partición", no alternativas a sharding.

Y esa forma de replicar es la horquilla real. Nodo único → primary-replica es casi el camino universal (agregar réplicas de solo lectura es barato y no toca la aplicación), pero cuando también la **escritura** supera a un solo nodo, el camino se bifurca según qué se sacrifica al coordinar cada partición replicada. El grupo de nodos de cada partición puede ponerse de acuerdo antes de confirmar (consenso: conservas ACID, pagas con latencia) o aceptar la escritura sin esperar ese acuerdo (peer-to-peer: nunca esperas, pagas con consistencia eventual). Esta bifurcación es el teorema CAP aplicado en concreto (desarrollado a fondo en la sección 3.3–3.4): no puedes tener consistencia fuerte, disponibilidad total y tolerancia a partición de red al mismo tiempo.

#### Qué tener en cuenta al decidir cuánto distribuir

| Qué observar en tu caso                       | Por qué importa                                                                                                                                                                                                                  | Hacia dónde empuja                                                                                                                                                                                              |
| --------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Volumen total de datos y qué tan rápido crece | Un solo servidor moderno (NVMe, cientos de GB de RAM) aguanta más de lo que la mayoría de sistemas necesitan, y subir de nivel en L0 (mejor hardware) suele ser más barato y más reversible que saltar a distribución horizontal | Si cabe cómodo con años de margen, quédate en nodo único o primary-replica                                                                                                                                      |
| Dónde están tus usuarios                      | Servir una lectura desde el otro lado del planeta añade cientos de milisegundos sin importar qué tan bien indexado esté tu motor                                                                                                 | Una sola región → nodo único o primary-replica basta. Varios continentes con necesidad de baja latencia local → sharding o peer-to-peer geo-distribuido                                                         |
| Qué tan grave es un downtime                  | Un primary caído detiene todas las escrituras hasta que se promueve una réplica (segundos a minutos, según el motor)                                                                                                             | Si eso es inaceptable para el negocio, necesitas consenso (failover automático coordinado) o peer-to-peer (sin nodo único de falla)                                                                             |
| Capacidad operativa real del equipo           | Peer-to-peer y consenso distribuido no son gratis de operar, porque exigen entender repair, compactación, rebalanceo de shards y resolución de conflictos                                                                        | Equipos sin esa experiencia suelen terminar aplicando sharding sobre su base relacional existente (vía Citus, Vitess) en vez de migrar a un motor peer-to-peer nativo, aunque el volumen "califique" para ambos |

---

### Capa 3 — Aislamiento y consistencia

La decisión aquí es **qué promete el motor sobre la corrección de los datos bajo acceso concurrente y bajo fallas de nodos o red**. El nombre de esta capa junta dos preguntas distintas a propósito, y en vez de mezclarlas en una sola tabla como antes, van separadas: **aislamiento** es qué tan bien se comporta una transacción cuando otra corre al mismo tiempo, en un solo nodo. **Consistencia** es si las réplicas de un sistema distribuido están de acuerdo entre sí, una pregunta que un motor de nodo único ni siquiera enfrenta. Ninguna de las dos es la "C" de ACID en su sentido estricto (que las constraints se mantengan íntegras), eso ya se resolvió en la Capa 6.

#### Aislamiento: un solo nodo, transacciones concurrentes

| Nivel                          | Pregunta que responde                                                                                                                                   | Cómo se logra                                                                                                                                                        | Motores (y ejemplo concreto)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | Costo en latencia/throughput                                                                  | Cuándo vale la pena pagarlo                                                                      |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| **ACID serializable**          | "¿Puedo asumir que mis transacciones corrieron una por una, sin importar cuántas corran en paralelo?"                                                   | El motor detecta o bloquea conflictos entre transacciones concurrentes antes de comprometerlas. Nunca hay dirty reads, lost updates, write skew ni phantoms.         | PostgreSQL, CockroachDB, SQL Server y SQLite ofrecen `SERIALIZABLE` explícito y sí cumplen esta definición completa (con mecanismos distintos entre sí, ver la tabla de mecanismos más abajo). Ejemplo: PostgreSQL con `ISOLATION LEVEL SERIALIZABLE` aborta una de las dos transacciones si producirían un resultado imposible en cualquier orden serial. **Oracle es la excepción que confirma por qué la etiqueta no basta** (ver más abajo): también ofrece un nivel llamado `SERIALIZABLE`, pero verificado que no cumple esta misma definición, es optimista y solo protege la misma fila, nunca previene write skew a pesar del nombre. El detalle completo, motor por motor, incluyendo por qué Oracle falla aquí específicamente, está en `03_transacciones_concurrencia_aislamiento.md` §4. | El más alto, porque detectar conflictos o bloquear reduce cuánta concurrencia real aprovechas | Invariantes que cruzan varias filas o entidades bajo alta contención (dinero, cupos, inventario) |
| **ACID con aislamiento débil** | "¿Mis transacciones están protegidas de los casos más comunes, como dirty reads, pero pueden pisarse entre sí en casos menos comunes, como write skew?" | Locking parcial o snapshots que evitan las anomalías más comunes (dirty read) pero no todas, sobre todo write skew, que solo serializable resuelve de forma general. | MySQL InnoDB usa esto como su nivel por defecto (`REPEATABLE READ`); PostgreSQL, Oracle y SQL Server también viven aquí por defecto, en su nivel `READ COMMITTED`. Ejemplo: MySQL InnoDB, gracias al next-key locking (gap locks), cierra la mayoría de los casos de phantom read en la práctica, aunque el estándar SQL no lo exige a este nivel y persisten casos límite documentados incluso por el propio MySQL.                                                                                                                                                                                                                                                                                                                                                                                  | Moderado, casi ninguna transacción espera algo extra                                          | La mayoría de aplicaciones OLTP; los phantoms raros se toleran o se manejan en la aplicación     |

Estos dos niveles no son puntos de una sola escala lineal, cada uno responde una pregunta distinta. Esta tabla es deliberadamente más gruesa que la taxonomía estándar SQL-92 de cuatro niveles (Read Uncommitted, Read Committed, Repeatable Read, Serializable): esos cuatro solo describen motores relacionales de un nodo, y se resumen en estos dos según si protegen write skew o no. Read Uncommitted no aparece porque en la práctica ningún motor serio lo implementa de verdad (PostgreSQL, por ejemplo, lo trata como Read Committed).

#### Por qué la etiqueta del nivel no basta

Dos motores que dicen "ACID", o incluso el mismo nombre de nivel ("Serializable", "Repeatable Read"), no garantizan el mismo comportamiento observable. Martin Kleppmann lo demostró de forma concreta con su proyecto Hermitage: corrió la misma batería de pruebas de aislamiento en PostgreSQL, MySQL y SQL Server, y "Repeatable Read" resultó ser una garantía distinta en cada uno de los tres motores. Kyle Kingsbury (Jepsen) encontró algo todavía más incómodo analizando PostgreSQL 12.3: su nivel `SERIALIZABLE`, la garantía más fuerte que ofrece, permitía en producción una anomalía real (conocida como G2-item) bajo ciertas condiciones de concurrencia. En ninguno de los dos casos la etiqueta predijo el comportamiento real.

Esto no es una crítica puntual a PostgreSQL o MySQL, es la misma idea que sostiene todo "El modelo de capas" (juzgar por el mecanismo, no por la etiqueta) aplicada ahora dentro de la propia Capa 3: la tabla de arriba dice qué garantía obtienes, pero no cómo la obtiene el motor, y ese "cómo" es justo lo que determina qué le pasa a tu transacción cuando compite con otra, y qué le exige a tu código.

#### Qué mecanismo produce cada garantía de aislamiento, y qué le exige a tu código

| Mecanismo                                         | Dónde aparece                                                                                                                                                                        | Qué le pasa a tu transacción en conflicto                                                                                               | Qué le exige a tu código                                                                                                                                              |
| ------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Locking pesimista (espera)                        | MySQL InnoDB en escrituras bajo su nivel débil por defecto (`UPDATE`/`DELETE`/`SELECT ... FOR UPDATE`, con next-key locks); SQL Server sin RCSI en su nivel débil (S locks en cada SELECT), y también en su propio `SERIALIZABLE`, con locks de rango que además bloquean el espacio entre filas para prevenir phantoms; SQLite, de forma trivial, un solo lock sobre el archivo completo, porque solo permite un escritor a la vez para toda la base               | La segunda transacción espera a que la primera libere el lock; en MySQL y SQL Server, con varios locks en juego, la espera puede formar un ciclo y terminar en deadlock real; en SQLite no hay ciclo posible porque solo existe un lock para toda la base, así que la segunda transacción solo espera o recibe `SQLITE_BUSY` si se agota el tiempo | Sin retry logic obligatorio, pero tu aplicación debe tolerar la espera y, en MySQL/SQL Server, manejar el abort ocasional por deadlock; en SQLite basta con reintentar tras un `SQLITE_BUSY`, nunca hay que resolver un ciclo                                                      |
| MVCC + detección optimista (abort y reintento)    | PostgreSQL en `SERIALIZABLE` (SSI); Oracle en su propio `SERIALIZABLE`, con una versión más simple del mismo mecanismo (la excepción ya explicada en la tabla de niveles de arriba)                                                                                                | Depende de qué tipo de conflicto: si dos transacciones leen y escriben filas distintas pero forman un ciclo de dependencias ("estructura peligrosa"), ninguna espera, el motor lo rastrea con locks SIREAD no bloqueantes y aborta una al comprometer; si dos transacciones escriben la misma fila, sí hay espera real, heredada de Repeatable Read y no eliminada por SSI, la segunda espera a que la primera termine y puede abortar igual         | Tu código necesita retry logic explícito para la transacción abortada, sin importar cuál de los dos conflictos la produjo                                             |
| MVCC + consenso distribuido (coordinación de red) | CockroachDB y YugabyteDB (Raft), Google Cloud Spanner (Paxos); los tres usan su protocolo de consenso en cada commit para que su SERIALIZABLE valga entre nodos, no solo dentro de uno | Cada commit espera a que una mayoría de réplicas del rango confirme vía consenso, sin bloquear a otras transacciones sobre otros rangos | No hace falta retry logic por esto, pero sí tolerar una latencia de commit variable, marginal en un clúster de una sola región, dominante en despliegues multi-región |

Dos matices, ya verificados contra documentación oficial, que evitan una simplificación excesiva de esta tabla: ni InnoDB ni PostgreSQL son "puramente" un solo mecanismo. InnoDB combina MVCC no bloqueante para lecturas ordinarias con locking pesimista real solo para escrituras y lecturas explícitas de bloqueo, así que no todo en InnoDB espera. Y en PostgreSQL, "optimista" no describe todo lo que pasa bajo `SERIALIZABLE` (SSI): la documentación oficial es explícita en que este nivel "no introduce ningún bloqueo más allá del que ya está presente en Repeatable Read", no que elimine ese bloqueo. Dos escrituras concurrentes sobre la misma fila siguen esperando, en `SERIALIZABLE` igual que en Read Committed o Repeatable Read, igual que en InnoDB: eso nunca cambia entre niveles. Lo que SSI añade, y ahí sí es genuinamente optimista, es la detección sin bloqueo de conflictos lectura-escritura entre filas distintas, vía locks SIREAD, que no le impiden avanzar a nadie y solo se revisan al comprometer. La diferencia de mecanismo entre ambos motores está exactamente en qué pasa cuando se pide `SERIALIZABLE`, ya desarrollado con el ejemplo completo de write skew en "Desglose de motores que parecen iguales", más abajo en esta sección.

#### Consistencia: varios nodos, réplicas que pueden no estar de acuerdo

Aquí se repite la misma separación de arriba, lectura contra escritura, pero entre réplicas en vez de entre transacciones: **linearizabilidad distribuida** responde si una escritura condicional sobre un dato replicado es segura contra otra escritura concurrente que compita por el mismo dato (el equivalente distribuido de "protejo mi escritura"). **Tunable** y **eventual** responden qué tan fresca es una lectura (el equivalente distribuido de "qué le muestro a quien lee"). Confundir estas dos preguntas es tan fácil como confundir aislamiento con las constraints de la "C" de ACID: un motor puede darte quorum de lectura sin darte protección alguna contra dos escrituras concurrentes, son garantías independientes.

| Nivel                                  | Pregunta que responde                                                                                                                                          | Cómo se logra                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | Motores (y ejemplo concreto)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | Costo en latencia/throughput                                                                                                                               | Cuándo vale la pena pagarlo                                                                                                                       |
| -------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Linearizabilidad distribuida (CAS)** | "¿Puedo hacer un compare-and-swap seguro sobre un dato que vive en varias réplicas, sin que dos escrituras concurrentes se lo pisen en silencio?"              | Un consenso entre las réplicas que tienen ese dato antes de aplicar la escritura condicional, explícito en algunos motores, ya incorporado por diseño en otros (el mecanismo exacto, y por qué varía entre motores, está en la tabla de mecanismos de esta subsección). | Cassandra, Lightweight Transactions (LWT), sobre una partition; DynamoDB, condition expressions, sobre un ítem; MongoDB, con `readConcern: "linearizable"` combinado con `writeConcern: "majority"`, sobre un solo documento; CockroachDB y Spanner lo dan por defecto en cualquier escritura, no como modo aparte. Ejemplo: un `UPDATE ... IF saldo = 100` sin LWT en Cassandra es inseguro, dos clientes concurrentes podrían leer 100 y escribir los dos "con éxito"; con LWT, solo uno gana.                                                | Alto en Cassandra, una ronda completa de consenso por escritura condicional; alto también en MongoDB, cuya propia documentación advierte que una lectura `"linearizable"` puede tardar notablemente más que una lectura por defecto; ya incluido en el costo normal de escribir en DynamoDB, CockroachDB y Spanner | Invariantes que cruzan réplicas y no toleran perder una escritura concurrente (reservas, cupos, contadores exactos)                               |
| **Consistencia tunable**               | "¿Cuántas réplicas tienen que confirmar para que ESTA escritura o ESTA lectura cuente como exitosa?" Se decide por operación, no una vez para todo el sistema. | Cada request especifica cuántas réplicas debe consultar: `ONE` (1 réplica), `QUORUM` (mayoría), `ALL` (todas). Es una perilla de latencia-vs-consistencia que gira el cliente, no el motor.                                                                                                                                                                                                                                                                                                                                                 | Cassandra y ScyllaDB (ONE/QUORUM/ALL); Azure Cosmos DB lo lleva más lejos con cinco niveles nombrados (strong, bounded staleness, session, consistent prefix, eventual); DynamoDB lo ofrece de forma más simple, como una lectura fuertemente consistente opcional; MongoDB con su propio espectro de `readConcern` (`local`, `available`, `majority`, hasta `linearizable`). Ejemplo: en Cassandra, escribir y leer ambos con `QUORUM` garantiza leer el valor más reciente porque los conjuntos de réplicas se solapan, mientras que escribir y leer con `ONE` puede devolver un valor viejo. | Variable por operación: `QUORUM` espera ida y vuelta a más de un nodo, `ONE` no espera nada                                                                | Cuando distintas operaciones del mismo sistema toleran distinta latencia (ej. perfil de usuario vs. saldo de cuenta)                              |
| **Consistencia eventual**              | "Si dejo de escribir ahora mismo, ¿en algún momento futuro todas las réplicas van a coincidir?" Sin garantía de cuándo, ni de qué ves mientras tanto.          | Replicación asíncrona sin coordinación en el camino crítico de la escritura. En Cassandra, DynamoDB y MongoDB esto no es una arquitectura aparte, es simplemente la posición más débil de la misma perilla de la fila "Tunable" (`ONE`, lectura eventual, `readConcern: "local"`); en Redis Cluster y motores como Elasticsearch o ClickHouse sí es la única opción, no existe una perilla que pedir algo más fuerte.                                                                                                                       | Redis en modo cluster (confirmado en su propia documentación: ni siquiera `WAIT` da consistencia fuerte real); Elasticsearch (near real-time, ver la Capa 1) y ClickHouse en tablas distribuidas, ninguno con un modelo de quorum que ajustar. Ejemplo: en cualquiera de estos, una lectura justo después de escribir puede devolver todavía el valor anterior, sin ninguna forma de pedir lo contrario.                                                                                                                                                                                        | El más bajo, nunca esperas confirmación de otros nodos                                                                                                     | Datos donde "hasta que converja" es aceptable (contadores, catálogos, recomendaciones), y donde el motor ni siquiera te da la opción de pedir más |

**Eventual** y **tunable** viven en el eje de "qué tan fresca es mi lectura", pero para la mayoría de los motores no son dos arquitecturas independientes, son el mismo dial: Cassandra, DynamoDB y MongoDB están en las dos filas de arriba porque **eventual es simplemente la posición más débil de su propio "tunable"**, no un modo aparte. Solo Redis Cluster, Elasticsearch y ClickHouse son genuinamente "eventual" en el sentido fuerte, sin ninguna perilla que gire hacia algo más fuerte. **Linearizabilidad distribuida** vive en un eje distinto, y ahí no hay gradiente, o el motor te lo da o no te lo da en absoluto, la misma naturaleza de todo-o-nada que **ACID serializable** tiene en el eje de un solo nodo. Esto explica por qué en la tabla de fingerprints una celda dice "Eventual · tunable por operación (ONE/QUORUM/ALL)": Cassandra tiene eventual como comportamiento por defecto, pero cada query puede pedir más consistencia a cambio de más latencia, mientras que otra celda dice simplemente "ACID", donde no hay perilla que girar por operación, ya que el nivel se fija una vez por transacción con `ISOLATION LEVEL`.

#### Qué mecanismo produce cada garantía de consistencia, y qué le exige a tu código

| Mecanismo                                                 | Dónde aparece                                                                                                                                                             | Qué le pasa a tu transacción en conflicto                                                                                                                       | Qué le exige a tu código                                                                                                                                 |
| --------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| CRDT (resolución matemática, sin coordinación)            | Redis Enterprise (Active-Active/CRDB), Riak (tipos de dato CRDT nativos), Automerge o Yjs a nivel de aplicación (edición colaborativa tipo Google Docs)                   | Ambas escrituras se aplican y se combinan según una regla matemática definida de antemano, sin que ninguna "gane" ni se aborte                                  | Ningún conflicto que manejar en el código, pero solo existe una regla de combinación para tipos de dato específicos, no para datos arbitrarios           |
| Last-write-wins                                           | Cassandra (resolución por defecto entre réplicas), versiones tempranas de DynamoDB. Ya visto en la Sección 4 (Modelos de distribución)                                    | Gana la escritura con timestamp más reciente                                                                                                                    | Riesgo real de pérdida silenciosa de la escritura perdedora, ver esa sección para el detalle                                                             |
| CAS vía consenso explícito (compare-and-swap distribuido) | Cassandra, Lightweight Transactions, con Paxos, solo cuando la pides explícitamente; DynamoDB no necesita esto, ya lo tiene por diseño (un único líder por partition key) | La escritura condicional espera una ronda completa de consenso entre réplicas antes de confirmar; sin ella, dos escrituras concurrentes podrían "ganar" las dos | Tu código debe pedirlo explícitamente en Cassandra (`IF`), y tolerar la latencia extra de esa ronda de consenso, mucho más alta que una escritura normal |

---

Para el desglose completo de los catorce motores relacionales, de documento, wide-column y distribuidos, con qué arquitectura usa cada uno para aislamiento (MVCC, locking, OCC, SSI) y para consistencia (Quorum, CAS) y por qué conviene uno sobre otro para transacciones específicamente, ver `03_transacciones_concurrencia_aislamiento.md` §4, la referencia consolidada sobre este tema, esta sección es solo el mapa inicial. Para la formalización completa del trade-off de consistencia (BASE, el teorema CAP, su extensión PACELC, y la derivación matemática de por qué `QUORUM + QUORUM > RF` da consistencia fuerte en Cassandra), ver más abajo la sección 3, "El espectro de consistencia": esa sección explica el porqué detrás de la fila "Consistencia tunable" de arriba.

---

### Capa 4 — Modelo de datos

La decisión aquí es **qué forma tiene el dato, qué relaciones puede expresar de forma nativa, y qué queries son eficientes vs costosas**. Modelar datos es, en el fondo, traducir las entidades de tu dominio y la forma en que se relacionan entre sí a una estructura que una máquina pueda guardar y consultar. Esa traducción no es neutral, porque la forma que elijas aquí determina, antes de tocar un motor concreto, qué preguntas van a ser baratas de responder durante toda la vida del sistema y cuáles van a exigir trabajo adicional constante, como desnormalizar, hacer joins en el cliente, mantener índices secundarios o migrar datos entre modelos.

Las formas disponibles son las que realmente compiten por tu decisión al elegir un motor. La tabla resume, para cada una, qué asume sobre tu dato, cuándo conviene, cuándo es mala idea (y por qué) y qué motores la implementan:

| Modelo                     | Qué asume sobre tu dato                                                                                                                                                                                                                                  | Elígelo cuando                                                                                                                                        | Es mala idea cuando                                                                                                                                                                     | Motores                               |
| -------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------- |
| **Relacional (tablas)**    | Filas y columnas con esquema fijo, definido por adelantado para toda la tabla. Los joins son una operación nativa que el optimizador del motor sabe planear.                                                                                             | Tus entidades tienen relaciones complejas entre sí y necesitas que se mantengan consistentes bajo escritura concurrente.                              | El esquema cambia todo el tiempo o varía mucho entre registros, porque cada cambio de esquema es una migración que afecta a toda la tabla.                                              | PostgreSQL, MySQL, SQL Server, Oracle |
| **Documento**              | Objetos JSON/BSON con esquema flexible por documento. El documento completo es la unidad de atomicidad, aunque los índices se definen por campo y operadores como `$set` modifican solo el campo que tocan, sin mover el documento entero.               | Tu entidad se lee y se escribe casi siempre como un todo (un perfil, un pedido, un artículo) y su integridad no depende de otras entidades.           | El invariante que más te importa cruza varias entidades a la vez, como un pago que descuenta inventario y cierra una orden, porque esa atomicidad no existe entre documentos distintos. | MongoDB, Couchbase                    |
| **Clave-valor**            | El valor es un blob opaco que el motor no entiende ni puede indexar. El único acceso posible es por clave exacta, típicamente en O(1).                                                                                                                   | El acceso siempre es "dame el valor de esta clave" a muy alta frecuencia, como una sesión, un contador o una caché.                                   | Necesitas filtrar, ordenar o buscar por cualquier campo distinto de la clave, porque el motor no puede indexar el contenido del valor.                                                  | Redis, DynamoDB, Memcached            |
| **Wide-column**            | Cada fila vive en el nodo que decide su clave de partición, con columnas que pueden variar de una fila a otra. No confundir con "columnar", porque aquí "columna" describe cómo se agrupan los datos de una fila esparsa, no cómo se almacenan en disco. | Necesitas escritura masiva repartida entre muchos nodos sin coordinador central y el acceso de lectura siempre puede pasar por la clave de partición. | Tus consultas filtran por campos fuera de la clave de partición o necesitas transacciones multi-fila, porque el modelo renuncia a la coordinación entre nodos.                          | Cassandra, HBase, Bigtable            |
| **Grafo**                  | Nodos y aristas con propiedades propias. Recorrer relaciones cuesta proporcional al grado del nodo, no al tamaño total de la base de datos.                                                                                                              | La pregunta central es sobre la relación misma y su profundidad, como "amigos de amigos" o el camino más corto entre A y B.                           | Tu carga es mayormente agregaciones masivas sobre todo el dataset o necesitas repartir los datos entre muchos nodos, porque el traversal no se presta a partición por clave.            | Neo4j, Amazon Neptune                 |
| **Columnar**               | Mismo modelo lógico de filas y columnas que el relacional, pero el almacenamiento físico agrupa los valores columna por columna.                                                                                                                         | Necesitas agregaciones analíticas sobre millones de filas y cada consulta solo toca dos o tres columnas.                                              | Tu carga es transaccional con muchas actualizaciones puntuales fila por fila, porque modificar una fila obliga a tocar varios bloques de columnas distintos.                            | ClickHouse, Redshift, BigQuery        |
| **Serie de tiempo**        | Cada registro es una tupla `(timestamp, métrica, tags)`. Los timestamps crecientes permiten compresión muy agresiva (delta encoding, Gorilla compression).                                                                                               | Tus consultas son casi siempre por rango temporal más una agregación, rara vez por un punto exacto.                                                   | Necesitas editar registros existentes con frecuencia o hacer joins complejos entre entidades, porque el modelo asume eventos ya ocurridos e inmutables.                                 | InfluxDB, TimescaleDB, Prometheus     |
| **Vectorial (embeddings)** | Cada registro es un vector denso que representa el significado semántico de un texto, imagen o audio. La única operación nativa es similitud, no igualdad exacta.                                                                                        | La pregunta es "dame los k elementos más parecidos en significado a este", no "dame la fila donde X = Y".                                             | Necesitas un resultado exacto en vez de aproximado, porque la búsqueda ANN sacrifica precisión perfecta a cambio de velocidad.                                                          | Pinecone, Milvus, Weaviate, pgvector  |

#### Un mismo dominio, ocho formas distintas

La forma más clara de ver que el modelo de datos se decide por entidad y no por aplicación es modelar una sola app, una red social simple, bajo las ocho formas de arriba:

| Necesidad dentro de la misma app                                                                                       | Modelo natural  | Por qué                                                                                                                                                               |
| ---------------------------------------------------------------------------------------------------------------------- | --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Usuario, sus posts y comentarios, con integridad referencial (borrar un usuario reasigna o borra en cascada sus posts) | Relacional      | El invariante ("nada queda huérfano") cruza varias tablas a la vez, y eso es exactamente lo que joins y transacciones multi-tabla resuelven bien.                     |
| El post individual (texto, metadata, tags), que se abre y se lee completo casi siempre                                 | Documento       | Todo el contenido se pide junto en el 95% de los casos; partirlo en tablas normalizadas solo produce joins que nunca vas a evitar.                                    |
| Contador de "me gusta", sesión activa, caché del feed ya calculado                                                     | Clave-valor     | Acceso siempre por un identificador exacto, altísima frecuencia, sin necesidad de filtrar por ningún otro campo.                                                      |
| El feed cronológico de cada usuario, escrito constantemente por millones de usuarios a la vez                          | Wide-column     | El cuello de botella es la escritura (un post nuevo debe aparecer en miles de feeds a la vez), no la lectura, así que hay que repartirla entre nodos sin coordinador. |
| "Sugerir amigos de amigos" o encontrar la cadena de conexión entre dos usuarios                                        | Grafo           | Es un traversal de profundidad variable; en tablas relacionales sería un self-join recursivo que se degrada mal en cuanto la profundidad crece.                       |
| Reporte de engagement por semana, país y tipo de contenido, sobre tres años de historial                               | Columnar        | Agregas pocas columnas sobre miles de millones de filas históricas, así que el resto de cada fila (texto del post, etc.) es peso muerto para esa consulta.            |
| Log de cada clic, scroll y reproducción, consultado después por rangos de tiempo                                       | Serie de tiempo | Cada evento es inmutable, llega ordenado cronológicamente, y casi siempre se agrega por rango, nunca se edita.                                                        |
| "Más publicaciones como esta" basado en el contenido, no en tags puestos a mano                                        | Vectorial       | La comparación es de significado, no de igualdad de campos, porque necesitas medir distancia entre embeddings, no filtrar columnas.                                   |

Las ocho filas describen la **misma aplicación**. Ninguna app real de este tamaño usa un solo modelo para todo, y modelar todo como relacional "porque así empezamos" es tan equivocado como modelarlo todo como documentos "porque es más flexible". El modelo se decide entidad por entidad (la sección 15, "Polyglot persistence", desarrolla esto a fondo).

#### Qué tener en cuenta al modelar cada entidad

Sobre la misma red social, las preguntas concretas que separan un buen modelo de uno que vas a lamentar en producción:

- **¿Qué se lee o se escribe junto, en la práctica, no en teoría?** El post y sus comentarios "están relacionados", pero si el 95% de las veces el comentario se escribe y se muestra sin tocar el post entero, forzarlos a vivir en el mismo documento (para "aprovechar" que están relacionados) es tan mal diseño como separarlos en cinco tablas cuando siempre se piden juntos.
- **¿Qué debe ser atómico, y con qué otra cosa?** Borrar un usuario y sus posts en cascada es un invariante que debe sostenerse sí o sí, y eso empuja a un modelo relacional con garantías fuertes de atomicidad. Que el contador de "me gusta" tarde 200ms en reflejarse globalmente no rompe nada, y eso permite clave-valor con consistencia eventual (el mismo término que usa la Capa 3, no una "consistencia relajada" genérica). Tratar ambos casos con el mismo nivel de rigor es sobre-diseñar el segundo y sub-diseñar el primero.
- **¿Cómo crece el volumen, y quién genera esa escritura?** El feed no crece con el número de posts, sino con el número de usuarios activos multiplicado por sus seguidores. Modelarlo como si fuera una tabla más de tamaño moderado es la razón por la que "funciona en pruebas y se cae en producción".
- **¿La relación que existe es la misma que la que realmente recorres?** "Amigos" es una relación de un salto que una tabla intermedia relacional maneja bien. "Amigos de amigos de amigos" ya es un problema distinto, porque la relación siempre existió y lo que cambió es cuánta profundidad de esa relación tu consulta necesita recorrer.
- **¿Qué tan caro es equivocarte aquí, comparado con equivocarte en otra capa?** Esta pregunta es la que conecta con el resto de la sección, y se responde en la conclusión de esta sección ("Qué capas pesan más, y cómo una decisión arrastra a las demás"), después de la Capa 5.

#### Del diseño al motor: qué empuja cada modelo en L1, L2 y L3

Todo lo anterior es **diseño puro**. Decidir que el feed "es" wide-column es una conclusión sobre la forma del dato y su patrón de acceso, y en esa frase no aparece ningún motor, ningún disco, ningún protocolo de red. Pero esa conclusión no se queda aislada. En cuanto la tomas, empieza a empujar, con distinta fuerza según el caso, hacia decisiones concretas en L1, L2 y L3, que ya sí son **arquitectura del sistema que vas a operar**, no diseño de tu dominio. La columna de L3 usa a propósito el vocabulario ya definido en esa capa (aislamiento para lo que pasa dentro de un nodo, consistencia para lo que pasa entre réplicas), en vez de la etiqueta genérica "ACID", para no caer en la misma confusión que esa sección ya advirtió. Así se ve ese empujón, entidad por entidad:

| Entidad (L4 ya decidido)                              | Empuja en L1 (estructura)                                                                                                     | Empuja en L2 (distribución)                                                                                                                            | Empuja/permite en L3 (aislamiento y consistencia)                                                                                                                                                                                                                                                                                                                                          |
| ----------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Usuario/posts con integridad referencial (relacional) | B-tree, porque necesitas updates in-place y lecturas por rango con índices, no solo por clave                                 | Nodo único o primary-replica, porque los joins y transacciones multi-tabla son costosos de sostener sin un coordinador central                         | Fuerza poco sobre el nivel de aislamiento, ni siquiera exige serializable, pero sí exige atomicidad, porque el invariante de cascada debe sostenerse aunque otra transacción esté escribiendo al mismo tiempo, y eso ya lo resuelve el locking a nivel de fila que el motor usa para las foreign keys, sin importar el nivel de aislamiento elegido                                        |
| Post individual (documento)                           | B-tree por colección (ej. WiredTiger), que sigue siendo acceso aleatorio eficiente, solo que la unidad es el documento entero | Primary-replica o sharding por ID del post, porque el documento es autocontenible y fácil de repartir sin coordinación fina                            | No te obliga a usar transacciones multi-documento ni a pedir un nivel de aislamiento fuerte entre ellas. El único invariante real de esta entidad, que los campos de un mismo post cambien todos juntos o ninguno, ya lo cubre la atomicidad de un solo documento, y como ningún invariante de este post depende de otro documento, no queda nada que aislar entre transacciones distintas |
| Contador/sesión/caché (clave-valor)                   | Hash table o skip list, idealmente en RAM, porque necesitas O(1) por clave exacta y nunca rangos                              | Nodo único si cabe en RAM, o partición por hash de la clave si no                                                                                      | Permite consistencia eventual sin costo real, porque perder un incremento por 200ms no rompe el negocio                                                                                                                                                                                                                                                                                    |
| Feed distribuido (wide-column)                        | LSM-tree, porque la escritura es el cuello de botella y necesitas appends secuenciales, no updates in-place                   | Peer-to-peer sin coordinador, porque tienes que aceptar escritura en cualquier nodo sin esperar consenso                                               | **Fuerza** consistencia eventual entre réplicas. No es una preferencia de diseño, sino la consecuencia física de haber elegido L1+L2 así (el mismo acoplamiento natural de la sección "Acoplamiento entre capas")                                                                                                                                                                          |
| Grafo social                                          | Graph-native, con listas de adyacencia y punteros directos en vez de B-tree                                                   | Nodo único o cluster, porque el traversal de profundidad variable no se presta a partición por clave y cruzar particiones en cada salto sería carísimo | No empuja nada en ningún eje. El modelo de grafo no exige ni prohíbe aislamiento fuerte, por eso Neo4j soporta transacciones ACID completas (con read committed como default, igual que PostgreSQL, y serializable disponible adquiriendo locks de escritura manualmente), y tampoco obliga a ninguna postura particular sobre consistencia distribuida                                    |
| Reporte de engagement (columnar)                      | Columnar (familia MergeTree), pensado para agregación sobre pocas columnas y muchas filas                                     | Sharding, porque el volumen histórico no cabe ni conviene en un solo nodo                                                                              | Permite consistencia eventual en las tablas distribuidas, porque nadie necesita que el reporte esté consistente al milisegundo                                                                                                                                                                                                                                                             |
| Log de eventos (serie de tiempo)                      | LSM/TSM optimizado para timestamps, con escritura secuencial pura y casi ningún update                                        | Depende del volumen, desde un nodo hasta un clúster                                                                                                    | Permite consistencia eventual, porque el evento ya ocurrió y verlo con un segundo de retraso no rompe nada                                                                                                                                                                                                                                                                                 |
| Recomendaciones (vectorial)                           | Vector index (HNSW/IVF), pensado para ANN y no para B-tree ni LSM                                                             | Depende del volumen de vectores, desde nodo único hasta sharding gestionado                                                                            | Permite consistencia eventual o best-effort, porque una recomendación levemente desactualizada no es un error de negocio                                                                                                                                                                                                                                                                   |

El patrón detrás de la tabla es el siguiente. Entre más fuerte sea la necesidad de **escritura distribuida sin coordinador** que tu modelo de L4 conlleva (feed, log de eventos a gran escala), más fuerte es el empujón hacia LSM-tree en L1 y peer-to-peer en L2, y esos dos juntos son los que terminan forzando L3 hacia consistencia eventual entre réplicas, no el modelo de datos por sí solo. El caso del grafo prueba que esto no es automático. Un modelo de datos puede exigir una estructura muy particular en L1 (graph-native) sin ejercer ninguna presión sobre L2 o L3, de ahí que "elegí grafo" no te diga nada por adelantado sobre qué tan fuerte necesitas el aislamiento entre transacciones o la consistencia entre réplicas, mientras que "elegí wide-column para escritura masiva sin coordinador" casi siempre sí te lo dice.

---

### Capa 5 — Interfaz de consulta

La decisión aquí es **cómo las aplicaciones le hablan al motor**. Es la capa más visible pero la menos técnicamente diferenciadora — el mismo motor puede tener múltiples interfaces (ClickHouse acepta SQL estándar y también tiene una API HTTP/binary).

- **SQL:** lenguaje declarativo estándar ISO. Portable entre motores (con variaciones de dialecto). Expresivo para joins, subqueries, window functions, agregaciones. La portabilidad es parcial — `EXPLAIN`, hints de optimización y tipos de datos varían entre motores.
- **CQL (Cassandra Query Language):** sintaxis similar a SQL pero sin joins, sin subqueries, sin `ORDER BY` general — refleja las limitaciones de las capas 2 y 3 de Cassandra. El lenguaje está diseñado para que las queries ineficientes sean difíciles de escribir.
- **API de documentos (MongoDB):** queries en JSON. Expresiva para campos anidados y arrays. `$lookup` existe pero es costoso — el modelo asume que no hay joins frecuentes. El pipeline de agregación es potente para transformaciones en el motor.
- **Cypher (Neo4j):** lenguaje de traversal de grafos. `MATCH (a:Person)-[:FRIEND]->(b:Person)` describe un patrón de grafo, no una join de tablas. Intuitivo para queries relacionales complejas que en SQL requerirían CTEs recursivos.
- **Comandos Redis:** protocolo binario, comandos específicos por tipo de dato (`GET`/`SET`, `HGET`/`HSET`, `ZADD`/`ZRANGE`, `LPUSH`/`LRANGE`). No hay lenguaje de query — cada comando opera sobre una estructura de datos concreta. El modelo mental es "estructuras de datos en red", no "base de datos".
- **Query DSL (Elasticsearch):** JSON estructurado enviado por HTTP. Muy expresivo para búsqueda de texto completo con filtros, boosting de relevancia, faceting y agregaciones. Verboso comparado con SQL pero más flexible para búsqueda.
- **Vector query / similarity search:** la query especifica un vector y un método de distancia, no una condición booleana. `SELECT * FROM items ORDER BY embedding <-> '[0.1, 0.2, ...]' LIMIT 10` (pgvector) o `index.query(vector=[...], top_k=10)` (Pinecone). Muchos motores combinan esto con filtros tradicionales sobre metadata ("los 10 más parecidos, pero solo de la categoría X") — _hybrid search_.

Las bases de datos vectoriales (Pinecone, Milvus, Weaviate, Qdrant, o la extensión `pgvector` sobre PostgreSQL), populares como motor de búsqueda semántica y RAG en aplicaciones de IA, no introducen una capa nueva: simplemente ocupan una combinación distinta de las mismas ocho — índice HNSW/IVF en L1, modelo vectorial en L4, query por similitud en L5 — igual que documento, wide-column o serie de tiempo ocupan cada uno su propia combinación. Esa es la utilidad del modelo de capas: cualquier motor nuevo, por especializado que parezca, se ubica con las mismas ocho preguntas.

#### Qué tener en cuenta más allá de la capacidad técnica

Más adelante, "SQL vs NoSQL" explica a fondo por qué esta capa es la menos fiable para juzgar qué puede o no puede hacer un motor. Eso no la vuelve irrelevante — solo significa que cuando importa, importa por razones prácticas y de equipo, no de capacidad:

| Qué observar                                   | Por qué importa                                                                                                                                                        |
| ---------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| ¿Qué tan portable necesita ser tu código?      | SQL estándar tiene el ecosistema de herramientas (BI, ORMs, drivers) más amplio; un lenguaje propietario (CQL, Cypher, un Query DSL) ata más al motor específico       |
| ¿Tu equipo ya conoce este lenguaje?            | Aprender uno nuevo (Cypher, el pipeline de agregación de MongoDB) tiene un costo real de velocidad de desarrollo, no de la base de datos en sí                         |
| ¿Qué herramientas externas necesitas conectar? | Dashboards de BI y frameworks de reporting suelen hablar solo SQL — un motor técnicamente superior pero sin conector SQL puede salir más caro de operar en la práctica |

---

### Capa 6 — Integridad declarativa

La decisión aquí es **si el motor te impide físicamente guardar un dato inconsistente, o si esa responsabilidad es 100% de tu aplicación**. No es una pregunta menor de sintaxis: es la diferencia entre un bug que falla ruidosamente en el momento del insert, y un bug que deja basura silenciosa en la base de datos durante meses hasta que alguien la encuentra en un reporte.

- **Enforcement completo en el motor:** `FOREIGN KEY`, `CHECK`, `UNIQUE` y `NOT NULL` se declaran una vez en el esquema y el motor rechaza cualquier operación que los viole, sin importar qué parte de la aplicación (o qué bug futuro) intente saltárselos.
- **Enforcement parcial:** el motor valida la forma del dato (tipos, campos requeridos) pero no relaciones entre entidades — por ejemplo, validación de esquema JSON que confirma que un campo existe y tiene el tipo correcto, sin poder garantizar que una referencia a otra colección exista de verdad.
- **Sin enforcement:** el motor guarda lo que le des, sin importar su forma ni sus referencias. La integridad — incluida la referencial — es responsabilidad exclusiva del código de la aplicación, en cada lugar donde escribe.

#### Qué tener en cuenta al decidir

| Qué observar en tu caso                                                          | Por qué importa                                                                                                                                                            | Hacia dónde empuja                                                                                                                               |
| -------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| ¿Qué tan grave es que un invariante se rompa sin que nadie lo note de inmediato? | Un `FOREIGN KEY` convierte un bug silencioso en un error visible en el momento exacto en que ocurre, no meses después                                                      | Invariantes de negocio (dinero, inventario, "nada queda huérfano") → enforcement completo                                                        |
| ¿Cuántos puntos de tu código escriben en la misma entidad?                       | Cada punto de escritura adicional sin enforcement del motor es una nueva oportunidad de que un bug rompa el invariante, y todos tienen que mantenerlo correctamente a mano | Muchos servicios o equipos escribiendo la misma entidad → enforcement completo reduce la superficie de error                                     |
| ¿El volumen de escritura es tan alto que validar cada una tiene un costo real?   | El enforcement no es gratis: cada `FOREIGN KEY` es una verificación adicional en cada insert/update                                                                        | Escritura masiva tipo logging o eventos, donde un registro huérfano ocasional no rompe nada de negocio → enforcement parcial o nulo es aceptable |

#### Ejemplo concreto

Sobre la misma red social: borrar un usuario que tiene posts existentes. Con `FOREIGN KEY ... ON DELETE CASCADE` (PostgreSQL, MySQL, CockroachDB), el motor mismo se encarga de que no queden posts huérfanos — no existe código de aplicación capaz de olvidarlo, porque nunca tuvo la opción. Sin ese enforcement, la limpieza depende de que cada lugar del código que borra un usuario recuerde también borrar o reasignar sus posts — y la documentación oficial de MongoDB es explícita en este punto: recomienda "incorporar lógica en la aplicación para mantener las referencias consistentes", porque el motor no lo hace por ti. AWS documenta la misma idea para DynamoDB: al no existir integridad referencial nativa, recae más responsabilidad en el equipo de desarrollo para garantizarla.

#### Por qué importa comparada con las demás capas

Es, de las ocho, una de las más baratas de corregir hacia adelante (agregar una constraint a una tabla que ya existe es una migración menor) pero una de las más caras de descubrir que faltaba: para cuando un dato huérfano o inconsistente aparece en un reporte, ya lleva tiempo circulando por el sistema, y limpiarlo retroactivamente es mucho más trabajo que haberlo prevenido desde el esquema.

---

### Capa 7 — Control de acceso y seguridad

La decisión aquí es **quién puede ver o modificar qué dato, y si esa regla la aplica el motor mismo o cada aplicación que se conecta a él**. Es la capa que determina si puedes cumplir ciertos requisitos de control de acceso con el motor solo, o si necesitas construir y mantener esa lógica en cada servicio que toque la base de datos.

- **Autorización granular nativa (row-level y column-level):** el motor puede restringir el acceso a filas o columnas específicas según quién hace la consulta, sin que la aplicación tenga que filtrar nada por su cuenta.
- **Autorización solo a nivel de tabla/colección completa:** un usuario o rol tiene acceso a toda la tabla o a nada — cualquier restricción más fina (“solo tus propios mensajes”) queda en manos del `WHERE` que la aplicación decida escribir.
- **Autorización delegada a una capa externa:** el control de acceso no vive en el motor sino en la infraestructura que lo rodea (políticas de identidad y permisos del proveedor cloud).

#### Qué tener en cuenta al decidir

| Qué observar en tu caso                                                                         | Por qué importa                                                                                                                                                             | Hacia dónde empuja                                                                                                 |
| ----------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| ¿Qué pasa si una query de tu aplicación tiene un bug y olvida el filtro de permisos?            | Con autorización nativa a nivel de fila, el motor sigue bloqueando el acceso aunque la query de la app esté mal escrita — es una segunda capa de defensa, no la única       | Datos sensibles multi-usuario (mensajes privados, historiales médicos) → autorización nativa granular              |
| ¿Cuántos servicios o equipos distintos consultan la misma base?                                 | Cada servicio adicional es una oportunidad más de que alguien construya el filtro de acceso incorrecto — centralizar la regla en el motor evita repetirla mal en cada lugar | Muchos consumidores de los mismos datos → autorización nativa reduce el riesgo de una implementación inconsistente |
| ¿El control de acceso ya vive naturalmente en tu infraestructura (roles de tu proveedor cloud)? | Si tu equipo ya administra permisos de identidad centralizados, duplicar esa lógica dentro del motor puede ser trabajo redundante                                           | Ecosistema ya centrado en IAM de un proveedor cloud → autorización delegada puede ser suficiente                   |

#### Ejemplo concreto

Sobre la misma red social: un usuario que solo debería poder leer sus propios mensajes privados. PostgreSQL resuelve esto con Row-Level Security (disponible desde la versión 9.5): una política declarada una vez en el esquema, con comportamiento _default-deny_ — una tabla con RLS habilitado bloquea el acceso a **cualquier** consulta que no cumpla la política, sin importar qué código la generó. Sin ese mecanismo, la misma regla depende de que cada endpoint de la aplicación arme correctamente la cláusula `WHERE usuario_id = :usuario_actual` — y un solo endpoint que lo olvide expone mensajes de otros usuarios.

#### Por qué importa comparada con las demás capas

El argumento serio detrás de esta capa no es "el motor te obliga a cumplir tal regulación" — ni el RLS de PostgreSQL ni ningún control nativo son, por sí solos, un certificado de cumplimiento de GDPR, HIPAA o PCI-DSS: esos estándares exigen un resultado (que el acceso esté controlado y el dato cifrado), no una implementación específica, y se pueden cumplir igual construyendo el control en la aplicación. El argumento real es de **defensa en profundidad**: un control nativo en el motor sigue funcionando incluso cuando la capa de aplicación falla, mientras que un control que vive solo en la aplicación desaparece por completo en cuanto esa aplicación tiene un bug o alguien se conecta a la base de datos por otra vía.

---

### Conclusión — Qué capas pesan más, y cómo una decisión arrastra a las demás

Ya viste las ocho capas explicadas de abajo hacia arriba. Queda una pregunta que ninguna respondió por separado: si tuvieras que priorizar en qué capa piensas primero y en cuáles solo vas a reaccionar después, ¿en qué orden vas? No las ocho pesan igual. El peso de cada una depende de dos cosas concretas, no de una intuición: qué tan caro es corregir un error ahí meses después, y a cuántas capas de arriba arrastra esa decisión aunque nunca lo hayas notado.

#### Por qué el peso no es el mismo en las ocho capas

Imagina las ocho capas como una fila de fichas de dominó, pero de tamaños distintos. Tumbar una ficha pequeña apenas mueve la de al lado. Tumbar la ficha grande mueve toda la fila que viene después, y devolverla a su lugar significa reconstruir la fila entera. Dos preguntas te dicen qué tan grande es cada ficha:

1. **¿Qué tan caro es corregir un error aquí, ya en producción?** Un error de Capa 0 (elegiste SSD y hoy te conviene NVMe) se arregla cambiando de disco, sin tocar una sola línea de código de la aplicación. Un error de Capa 4 (modelaste el inventario de una tienda como documentos sueltos y ahora necesitas que un pago, una orden y el descuento de stock ocurran como una sola unidad) se arregla reescribiendo el modelo de esa entidad y migrando datos reales sin apagar el servicio.
2. **¿A cuántas capas de arriba condiciona esta decisión, aunque no lo hayas decidido a propósito?** Elegir grafo como modelo de datos no obliga a nada en distribución ni en consistencia (Neo4j es tan ACID como PostgreSQL). Elegir wide-column sí obliga: empuja casi automáticamente hacia LSM-tree, hacia peer-to-peer y hacia consistencia eventual, sin que hayas tomado esas tres decisiones de forma consciente.

Con esas dos preguntas en mente, así queda el orden real de importancia, de mayor a menor:

**Paso 0, antes de la Capa 4: el perfil de carga de trabajo no es una capa, pero pesa como la más grande de todas.** Las otras ocho son decisiones que tomas: relacional o documento, B-tree o LSM-tree. El perfil de carga no es una decisión, es una respuesta a una pregunta anterior a todas: ¿cómo se usa de verdad tu aplicación? Todo lo que decides en la Capa 4, y después en la Capa 2 y 3, se construye sobre esa respuesta. Si la respuesta estaba mal, no elegiste mal una capa. Construiste bien sobre una base que no correspondía.

Ejemplo concreto: un equipo asume que su aplicación es de lectura pesada, porque así son la mayoría de apps con dashboard, y modela todo en relacional normalizado para queries flexibles. En producción, la operación que más se repite resulta ser "marcar notificación como leída": un cambio pequeño que cada usuario dispara cientos de veces por minuto. El modelo de datos de la Capa 4 no estaba mal diseñado para lo que el equipo pensaba que necesitaba. Estaba mal diseñado porque pensaban que necesitaban otra cosa. Corregirlo no es cambiar una capa. Es volver a preguntar cómo se usa la aplicación de verdad, y recién ahí rehacer lo que dependía de esa respuesta.

**1. Capa 4, modelo de datos: la única que conviene resolver antes de escribir código.**

Ejemplo: una tienda en línea guarda el catálogo de productos y el carrito de compra en la misma tabla relacional, "porque ya existe la tabla de productos". Funciona en pruebas. En producción, cada vez que alguien agrega algo al carrito compite por locks con las lecturas del catálogo, que es lo que más tráfico recibe de toda la aplicación. El error no se corrige subiendo un índice: catálogo (se lee muchísimo, cambia poco) y carrito (se escribe por usuario, es efímero) son dos entidades con patrones de acceso opuestos, y tratarlas como una sola es el tipo de decisión que exige, meses después, separar el modelo, migrar datos reales y tocar cada parte del código que asumía la forma anterior. Por eso el modelo de datos es la única capa donde de verdad conviene invertir tiempo de diseño antes de tocar un motor.

**2. Capa 2 (distribución) y Capa 3 (consistencia): las dos variables reales que decides justo después, independientes entre sí.**

El modelo de datos sugiere un rumbo, pero no decide por ti. Ejemplo: decidiste que el feed de una app social es wide-column porque se escribe muchísimo. Eso sugiere peer-to-peer en distribución, pero si tu app tiene diez mil usuarios activos en un solo país, ese volumen ni siquiera justifica salir de un primary-replica normal. El volumen real y la geografía de tus usuarios son entradas de la Capa 2 que el modelo de datos no puede anticipar por sí solo. Con la consistencia pasa algo parecido, pero en forma de techo y piso: el techo lo pone la Capa 2 (si elegiste peer-to-peer, nunca vas a tener ACID completo, sin importar cuánto lo quieras después), y el piso lo pone la Capa 4 (si un invariante de negocio cruza varias entidades, como "el total de una orden debe coincidir con la suma de sus líneas", ninguna cantidad de "ya va a converger" te salva de corromper ese dato). Entre ese techo y ese piso, lo único que aporta la Capa 3 es el precio en rendimiento de subir un nivel: pedir SERIALIZABLE cuando tu problema tolera REPEATABLE READ no te hace más correcto, solo más lento.

**3. Capa 1, estructura de almacenamiento: casi siempre es un resultado, no una decisión.**

Ejemplo: si ya modelaste tu feed como wide-column y ya resolviste que necesitas escritura sin coordinador, terminas en LSM-tree casi por inercia, sin haberlo "elegido" de forma consciente en ningún momento. La excepción es rara y por eso llama la atención cuando aparece: MyRocks (MySQL corriendo sobre RocksDB en lugar de InnoDB) es de los pocos casos donde alguien rompe esa relación automática a propósito, porque su patrón de escritura específico lo justifica, y aun así paga el costo de construir MVCC sobre una estructura que no nació pensada para eso.

**4. Capa 0, almacenamiento físico: casi nunca la eliges desde cero.**

Ejemplo: si eliges Redis, ya elegiste vivir en RAM, aunque nunca hayas escrito esa palabra en ninguna decisión de arquitectura. Lo único que sí controlas, una vez dentro de un motor basado en disco, es el tier (HDD, SSD o NVMe) según tu presupuesto, sin que eso cambie nada más del diseño.

**5. Capa 5, interfaz de consulta: la más visible, y la que menos debería pesar en tu decisión.**

Ejemplo: descartar MongoDB porque "no tiene JOIN" es juzgar por la capa equivocada. Su aggregation pipeline (`$lookup`, `$group`) resuelve algo muy parecido, solo que con otra sintaxis. Si tu aplicación de verdad necesita joins constantes entre cinco entidades con filtros que cambian según quién pregunta, el problema real está en la Capa 4 (elegiste el modelo equivocado para ese caso), no en que el lenguaje le falte una palabra.

**6. Capas 6 y 7, integridad y seguridad: no cascadean como la Capa 4, pero miden un costo distinto — el de un daño que ya ocurrió, no el de una migración.**

Las dos preguntas que ordenaron el resto de esta lista (¿qué tan caro es corregir el error?, ¿a cuántas capas de arriba condiciona?) casi no aplican aquí de la misma forma: una tabla sin `FOREIGN KEY` sigue siendo perfectamente relacional, y un motor sin RLS nativo no deja de ser viable por eso — ninguna de las dos fuerza nada en las capas de abajo. Por eso, en términos de cascada, pesan poco, parecido a la Capa 5. Pero el costo de descubrirlas tarde no se mide en código que hay que reescribir, sino en daño que ya está hecho: una fila huérfana que ya se usó para facturar, o un acceso indebido que ya ocurrió, no se "corrigen" con una migración — se investigan y se reparan a posteriori, si es que se pueden reparar del todo. Son baratas de posponer al diseñar y caras de haber pospuesto cuando algo falla.

#### Cómo una decisión temprana arrastra a las demás

El orden anterior no es solo una lista de prioridades. Es también el orden en que una elección empuja a las siguientes, con distinta fuerza según el caso. Dos cadenas completas, ya vistas por partes a lo largo de esta sección, lo muestran de principio a fin:

```
Modelar el feed de una red social como wide-column (Capa 4)
  → necesitas absorber la escritura de millones de usuarios sin coordinador
  → eso empuja a LSM-tree en Capa 1 (appends secuenciales, no updates in-place)
  → eso empuja a peer-to-peer en Capa 2 (aceptar escritura en cualquier nodo,
    sin esperar consenso de los demás)
  → y eso FUERZA eventual en Capa 3: ya no es una preferencia de diseño, es
    la consecuencia física de haber elegido así las dos capas anteriores

Modelar usuarios y posts con integridad referencial como relacional (Capa 4)
  → el invariante "nada queda huérfano" cruza varias tablas a la vez
  → eso EXIGE ACID en Capa 3, sin excepción posible
  → eso empuja a B-tree en Capa 1 (updates in-place, lecturas por rango
    con índices)
  → eso empuja a nodo único o primary-replica en Capa 2, porque los joins
    y las transacciones multi-tabla son costosos de sostener sin un
    coordinador central
```

Nota la diferencia entre las dos cadenas: en la primera, la Capa 4 empuja hacia abajo, capa por capa, hasta forzar la consistencia como último eslabón. En la segunda, la Capa 4 exige la consistencia de forma directa y desde ahí empuja hacia el almacenamiento y la distribución. No hay una única dirección de arrastre. Lo que sí es constante en ambas es que la Capa 4 siempre es el origen de la cadena, nunca su destino: ninguna decisión de estructura, distribución o consistencia obliga jamás a cambiar qué significa tu dato.

#### En qué deberías preocuparte realmente

Toda esta jerarquía se traduce en preguntas muy concretas que vale la pena hacerte sobre tu propio proyecto, no sobre la teoría:

| Capa       | Pregúntate esto sobre tu proyecto real                                                               | Ejemplo concreto                                                                                                                                                                                       |
| ---------- | ---------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Capa 4     | ¿Qué se lee y qué se escribe junto, en la práctica y no en teoría?                                   | El carrito de compra y el catálogo casi nunca se consultan juntos: modelarlos como la misma entidad genera contención donde no debería haberla                                                         |
| Capa 2 y 3 | ¿Cuánto volumen tengo hoy en serio, y qué tan grave es una inconsistencia de unos segundos?          | Una app con diez mil usuarios en un solo país no necesita peer-to-peer, aunque su modelo de datos "sugiera" escritura distribuida                                                                      |
| Capa 1     | ¿Estoy peleando contra la estructura que el motor ya eligió por mí?                                  | Usar UUIDs aleatorios como clave primaria en MySQL InnoDB fragmenta el clustered index; en PostgreSQL no pasa nada, porque el heap no depende del orden de la clave primaria                           |
| Capa 0     | ¿Mi dataset cabe en RAM con margen de crecimiento real?                                              | Un catálogo que hoy pesa 5GB pero crecerá a 200GB en tres años no debería vivir en Redis como fuente de verdad, solo como caché                                                                        |
| Capa 5     | ¿Estoy juzgando el motor por su lenguaje o por lo que de verdad puede hacer?                         | Cassandra no tiene JOIN porque su distribución lo prohíbe de raíz; MongoDB sí puede pedirlo con `$lookup`, solo que es más lento                                                                       |
| Capa 6     | Si un dato queda huérfano o inconsistente, ¿lo rechaza el motor o lo descubro después en un reporte? | MongoDB documenta explícitamente que la integridad referencial entre colecciones es responsabilidad de la aplicación; en PostgreSQL, `FOREIGN KEY ... ON DELETE CASCADE` lo resuelve sin código propio |
| Capa 7     | Si una query de mi aplicación tiene un bug de permisos, ¿algo más lo detiene?                        | Con RLS en PostgreSQL, una policy sigue bloqueando el acceso indebido aunque el `WHERE` de la app esté mal escrito; sin eso, el bug expone el dato tal cual                                            |

Esta tabla es, en el fondo, un resumen para consultar rápido: cuando dudes por dónde empezar a evaluar un problema o un motor, revísala en este orden (Capa 4 primero, luego Capa 2 y 3, luego Capa 1, 0 y 5, y por último Capa 6 y 7 una vez que el resto ya está decidido).

Esto también explica por qué el orden se invierte cuando, en lugar de diseñar tu propio sistema, evalúas un motor que ya existe (desarrollado a fondo en "Cómo evaluar un motor desconocido", más adelante en esta sección). Al diseñar, la Capa 1 es la última que conviene fijar, porque depende de todo lo anterior. Al evaluar un motor ajeno, es la primera que puedes investigar, porque es lo único observable desde afuera: averiguas qué estructura de almacenamiento usa antes de poder inferir nada sobre su modelo de distribución o sus garantías. No es una contradicción, es el mismo mapa de dependencias leído en la dirección opuesta.

**Nota al margen:** las ocho capas cubren la estructura del motor, pero no agotan la complejidad de un proyecto real. Cosas como diseño para cargas de IA (embeddings, RAG), SLAs estrictos de throughput y latencia, disponibilidad exigida en varias regiones, cumplimiento normativo, costo de licencias, vendor lock-in, el mercado de talento disponible para operar el motor, o los internals de durabilidad (WAL, fsync, checkpointing) son ejes adicionales que también pesan en una decisión seria. Ya aparecen desarrollados más adelante: vectorial en L1/L4/L5 (sección 13), el trade-off CAP/PACELC (sección 3), el framework de decisión técnico (sección 16), todo lo que no es técnico —costo, lock-in, mercado de talento y cómo validar antes de comprometerte— en la sección 17, y los internals de WAL/fsync/replicación y control de concurrencia a nivel de motor en `guia_transacciones_db.md`.

---

### Fingerprint de los motores principales

Cada motor tiene un "fingerprint" — su posición en cada una de las ocho capas. Dos motores con fingerprints distintos no son intercambiables aunque sirvan al mismo problema superficial. Dos motores con fingerprints idénticos son competidores directos y la diferencia está en la implementación, no en el diseño.

| Motor                    | L0 — Físico                                | L1 — Estructura                                           | L2 — Distribución                                                                          | L3 — Aislamiento/Consistencia                                                                                                                                    | L4 — Modelo                                                                  | L5 — Interfaz                                    | L6 — Integridad                                                                                         | L7 — Seguridad                                                                |
| ------------------------ | ------------------------------------------ | --------------------------------------------------------- | ------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- | ------------------------------------------------ | ------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| **PostgreSQL**           | Disco                                      | B-tree + heap (tabla separada del índice)                 | Nodo único / primary-replica (sharding no nativo, se agrega con Citus)                     | ACID completo · Serializable real (SSI)                                                                                                                          | Relacional extendido (arrays, JSONB+GIN, rangos, tipos custom)               | SQL estándar · PL/pgSQL · extensiones            | FK/CHECK/UNIQUE/NOT NULL enforced nativamente                                                           | RLS nativo desde 9.5, default-deny                                            |
| **MySQL InnoDB**         | Disco                                      | B-tree clustered (tabla almacenada en hojas de PK)        | Nodo único / primary-replica (sharding no nativo, se agrega con Vitess)                    | ACID · RR por defecto (`REPEATABLE READ`) · Serializable vía locking                                                                                             | Relacional estándar (JSON sin GIN, tipos básicos)                            | SQL dialecto MySQL                               | FK/UNIQUE/NOT NULL enforced; CHECK real solo desde 8.0.16                                               | Sin RLS nativo — se simula con VIEWs + GRANT                                  |
| **SQLite**               | Disco / RAM                                | B-tree + heap (igual que PostgreSQL)                      | Nodo único (librería en proceso, sin distribución)                                         | ACID completo (WAL mode)                                                                                                                                         | Relacional                                                                   | SQL                                              | FK soportado pero OFF por defecto (requiere `PRAGMA foreign_keys=ON`)                                   | Sin usuarios/roles — control depende del filesystem del SO                    |
| **CockroachDB** (NewSQL) | Disco                                      | LSM-tree (Pebble) por nodo, con MVCC por encima           | Sharding nativo y automático por rango + consenso (Raft)                                   | ACID completo · Serializable por defecto, sin excepción                                                                                                          | Relacional (mismo modelo que PostgreSQL, particionado de forma transparente) | SQL (dialecto compatible con PostgreSQL)         | FK/CHECK/UNIQUE/NOT NULL enforced (mismo modelo que PostgreSQL)                                         | RLS nativo desde v25.2 (sintaxis compatible con PostgreSQL)                   |
| **MongoDB**              | Disco                                      | B-tree (WiredTiger)                                       | Primary-replica / sharding nativo (mongos + shard key)                                     | ACID por documento · multi-documento posible incluso entre shards (desde v4.2) · tunable por `readConcern` (`local`→`linearizable`)                              | Documento (JSON/BSON anidado, schema flexible)                               | API documentos (JSON)                            | Sin FK; `$jsonSchema` valida forma del documento, no referencias entre colecciones                      | RBAC nativo, deshabilitado por defecto (`--auth`)                             |
| **Redis**                | RAM + disco (AOF/RDB)                      | Hash / skip list / listpack                               | Nodo único / cluster (particionado nativo por hash slots)                                  | Consistente en nodo único · eventual en cluster (replicación async)                                                                                              | Clave-valor con tipos ricos                                                  | Comandos binarios                                | Sin constraints — el valor es un blob opaco                                                             | ACL nativo desde v6 — granular por comando, categoría y patrón de key         |
| **Memcached**            | RAM pura, sin persistencia                 | Hash table en RAM                                         | Sharding decidido por el cliente (consistent hashing), sin coordinación entre servidores   | Ninguna garantía, es una caché, no un registro: perder un valor al reiniciar es el comportamiento esperado, no una anomalía                                      | Clave-valor (blob opaco, sin tipos)                                          | Comandos de texto simples (`get`/`set`/`delete`) | Sin constraints — cache puro, sin schema                                                                | SASL a nivel de conexión completa, sin ACL granular                           |
| **DynamoDB**             | Disco (serverless, opaco)                  | Opaco (no publicado por AWS)                              | Distribuido global, sharding nativo y gestionado por AWS (multi-AZ, multi-región opcional) | Eventual por defecto · strong consistent reads disponible por operación · linearizable en escrituras condicionales, por diseño (un solo líder por partition key) | KV / documento                                                               | API HTTP propia                                  | Sin FK; condition expressions y `TransactWriteItems` dan atomicidad por ítem, no integridad referencial | IAM con condition keys (`dynamodb:LeadingKeys`) por partition key             |
| **Cassandra**            | Disco                                      | LSM-tree                                                  | Peer-to-peer (ring), partición nativa por consistent hashing                               | Eventual · tunable por operación (ONE/QUORUM/ALL) · linearizable solo con Lightweight Transactions (Paxos), sobre una partition                                  | Wide-column                                                                  | CQL                                              | Sin FK; lightweight transactions (Paxos) simulan UNIQUE vía `IF NOT EXISTS`                             | RBAC por rol (keyspace→tabla); sin RLS nativo                                 |
| **ScyllaDB**             | Disco (SSD-optimizado, I/O sin JVM)        | LSM-tree                                                  | Peer-to-peer (ring), partición nativa por consistent hashing                               | Eventual · tunable por operación (ONE/QUORUM/ALL) · linearizable solo con Lightweight Transactions (Paxos), sobre una partition                                  | Wide-column                                                                  | CQL                                              | Igual que Cassandra (compatible)                                                                        | Igual que Cassandra — RBAC por rol, sin RLS                                   |
| **Elasticsearch**        | Disco                                      | Inverted index (Lucene)                                   | Primary-replica / sharding nativo (fijado al crear el índice)                              | Eventual, variante "near real-time" (índice visible tras refresh ~1s) · réplicas pueden servir datos stale                                                       | Documento                                                                    | Query DSL (JSON sobre HTTP)                      | El mapping valida tipo por campo, no integridad referencial entre índices                               | RBAC básico gratis; document/field-level security requiere licencia Platinum+ |
| **InfluxDB**             | Disco                                      | TSM (variante de LSM optimizada para timestamps)          | Nodo único / cluster (sharding nativo solo en Enterprise/Cloud)                            | Eventual, sin perilla tunable por operación                                                                                                                      | Serie de tiempo                                                              | InfluxQL / Flux                                  | Sin constraints declarativos (schema de tags/fields libre)                                              | Tokens por bucket (Operator/All-Access/Read/Write); sin granularidad de fila  |
| **Neo4j**                | Disco                                      | Graph-native (listas de adyacencia con punteros directos) | Nodo único / cluster (Raft); sharding no nativo (Fabric, limitado)                         | ACID completo (un solo grupo Raft, sin shards que coordinar)                                                                                                     | Grafo (nodos + aristas con propiedades)                                      | Cypher                                           | Uniqueness constraint en Community; existence/type/key constraints solo en Enterprise                   | RBAC por label/relationship-type y property-based access control (Enterprise) |
| **DuckDB**               | Disco / RAM (embebido, igual que SQLite)   | Columnar (ejecución vectorizada)                          | Nodo único (librería en proceso, sin distribución)                                         | ACID completo                                                                                                                                                    | Columnar / analítico                                                         | SQL                                              | PK/FK/CHECK/UNIQUE con enforcement real (sin `ON DELETE CASCADE`)                                       | Sin RBAC de datos — sandboxing de capacidades (`enable_external_access`)      |
| **ClickHouse**           | Disco                                      | Columnar (familia MergeTree)                              | Primary-replica / sharding nativo pero manual (topología fija en configuración)            | Eventual en tablas distribuidas · atómico por nodo/shard local                                                                                                   | Columnar                                                                     | SQL (variante ClickHouse)                        | `CHECK` real (evaluado en INSERT); sintaxis FK aceptada pero nunca enforced                             | RBAC nativo + `ROW POLICY` nativo para filtrar filas por rol                  |
| **Pinecone** (vector DB) | Disco/RAM (serverless, gestionado y opaco) | Vector index (HNSW)                                       | Distribuido, sharding nativo y gestionado (opaco)                                          | Eventual · best-effort, sin niveles configurables                                                                                                                | Vectorial (embeddings) + metadata                                            | API HTTP propia (query por similitud)            | Sin constraints — id único por diseño del índice, metadata solo para filtrar                            | API keys + RBAC por organización/proyecto                                     |

Las filas siguen, de arriba a abajo, el mismo orden de categorías de la Parte 2 (relacional → documento → clave-valor → wide-column → búsqueda → serie de tiempo → grafo → columnar/OLAP), con vectorial al final como categoría adicional.

**Cómo leer la tabla:** compara dos filas columna por columna. Si una celda dice lo mismo en dos filas, esa capa no es la fuente de diferencia — busca en las demás. Seis comparaciones ya resueltas en este documento, visibles directamente en la tabla:

- **ScyllaDB y Cassandra** tienen fingerprint idéntico en las ocho capas: es el mismo diseño, con implementación distinta (ver "Cómo evaluar un motor desconocido").
- **PostgreSQL y MySQL**, aunque parecen iguales a primera vista, difieren en L1, L3 y L4 — ver el desglose completo más abajo, en "Desglose de motores que parecen iguales".
- **PostgreSQL y CockroachDB** comparten L4 y L5 casi palabra por palabra — ambos relacionales, ambos SQL — pero difieren en L1, L2 y L3: la misma lección de "SQL vs NoSQL" (que el lenguaje no predice la arquitectura), ahora visible fila contra fila en vez de solo en prosa.
- **DuckDB y ClickHouse** comparten L1 y L4 (ambos columnares) pero difieren en L2 — ClickHouse sigue el acoplamiento natural "columnar + sharding + eventual" de "Acoplamiento entre capas" al pie de la letra; DuckDB lo rompe, mostrando en la misma fila de la tabla que columnar no exige distribuir, igual que MyRocks rompe la pareja "relacional + B-tree" en la Capa 1.
- **Memcached** está en la tabla como el extremo opuesto a PostgreSQL/CockroachDB en L3: no es solo "eventual", es la ausencia total de garantía — el punto de referencia útil para entender qué tan poco compromiso puede asumir un motor y seguir siendo valioso para un caso de uso específico (caché, no registro de verdad).
- **Cuando la celda de L2 dice "sharding" a secas, fíjate si es nativo o agregado por fuera.** MongoDB, Cassandra, ScyllaDB, CockroachDB, DynamoDB, Elasticsearch y ClickHouse lo traen de fábrica; PostgreSQL y MySQL no, por eso sus celdas ahora aclaran que requiere Citus o Vitess. Esa es la razón por la que CockroachDB sostiene "sin excepción" en L3 y PostgreSQL no lo promete para ese escenario: uno nació con consenso incluido, el otro le agrega la pieza después.

---

### SQL vs NoSQL: ¿por qué existe la separación, y qué tanto debería importarte?

No hace falta volver a recorrer motor por motor, ya que la tabla de arriba ya lo hace. Lo que vale la pena resolver aquí es otra cosa: por qué la industria separa el mundo en "SQL" y "NoSQL" como si fuera un eje técnico, si ya viste que el fingerprint de estos motores no tiene nada parecido a un patrón de dos bloques. La respuesta corta es: **es un accidente histórico que se volvió etiqueta de marketing, y sí, hay algo real debajo, pero no es lo que el nombre sugiere, y no es donde deberías mirar para elegir motor.**

#### De dónde viene el nombre, literalmente

"NoSQL" no nació como una propuesta de arquitectura, y ni siquiera nació con el significado que tiene hoy. El acrónimo lo usó por primera vez Carlo Strozzi en 1998 para nombrar una base de datos relacional ligera que simplemente no exponía SQL como interfaz, un proyecto que no tiene relación con el movimiento actual. El nombre se reutilizó en 2009 como el hashtag de un meetup en San Francisco organizado por Johan Oskarsson (con Eric Evans ayudando a popularizarlo poco después), para juntar en una sala a gente que trabajaba con sistemas inspirados en dos papers de Google y Amazon (Bigtable, 2006; Dynamo, 2007), sistemas construidos para escalar horizontalmente en cientos de nodos baratos, algo que el relacional de esa época, pensado para un servidor grande y vertical, no hacía bien. El nombre describe **lo que estos sistemas rechazaban** (el default relacional de los 2000s), no lo que tenían en común estructuralmente. Es un término definido en negativo, como llamar "no-mamíferos" a peces, aves e insectos porque ninguno amamanta. La clasificación es válida para excluir, pero inútil para diseñar, porque un pez y un insecto no se parecen entre sí solo por no ser mamíferos.

Esto no es una opinión, es demostrable con lo que ya construiste en la tabla de fingerprints: Neo4j soporta transacciones ACID completas (atomicidad, consistencia de esquema, aislamiento y durabilidad) con el mismo nivel de aislamiento por defecto que PostgreSQL, read committed, aunque a diferencia de PostgreSQL, para llegar a un aislamiento serializable Neo4j exige adquirir manualmente locks de escritura en vez de ofrecerlo listo para usar con una sola palabra clave. MongoDB usa B-tree (L1 igual a PostgreSQL). Redis en modo nodo único tampoco tiene ningún problema de consistencia distribuida (L2 igual a PostgreSQL), aunque ahí la comparación se detiene: su L3 sigue siendo mucho más débil, porque sus comandos individuales son atómicos pero no hay durabilidad garantizada por defecto ni un rollback real dentro de una transacción `MULTI`/`EXEC`. Si "NoSQL" fuera una categoría de diseño, estos tres deberían compartir _algo_ estructural con Cassandra o DynamoDB más allá de "no usar tablas normalizadas con SQL como interfaz principal". No lo comparten. Comparten la etiqueta, no el diseño.

La prueba funciona también al revés: DuckDB y ClickHouse hablan el mismo SQL, ambos son "columnar" en L4, y aun así uno vive embebido en un solo proceso (L2 nodo único, como SQLite) y el otro nació asumiendo sharding distribuido. Comparten L4 y L5 casi palabra por palabra y son, en la práctica, dos arquitecturas distintas. Si "SQL" fuera una categoría de diseño, esto no podría pasar.

#### Lo real que la etiqueta esconde, y que no se llama "SQL" ni "NoSQL"

Bajo el nombre sí hay dos variables técnicas genuinas que importan, y la confusión ocurre porque la etiqueta las mezcla como si fueran una sola:

**Variable 1: el trade-off de CAP/PACELC** (desarrollado a fondo en 3.3–3.4). Un sistema que necesita aceptar escrituras en muchos nodos sin coordinador, tolerando que la red falle entre ellos, está forzado por física de redes, no por preferencia estética, a resignar algo de consistencia (AP) o algo de disponibilidad (CP). Esto es lo que realmente produce el acoplamiento LSM-tree + peer-to-peer + eventual que viste en "Acoplamiento entre capas": no es que a los autores de Cassandra les gustara menos el ACID, es que ACID distribuido sin consenso es imposible de sostener a esa escala y latencia. Esta variable no tiene absolutamente nada que ver con el lenguaje de consulta: CockroachDB habla SQL y en L3 conserva el mismo ACID completo que PostgreSQL, pero en L2 exige el mismo tipo de arquitectura distribuida que Cassandra (sharding, rebalanceo, coordinación entre nodos), solo que tomando la rama opuesta de la horquilla de la Capa 2, consenso (Raft) en vez de peer-to-peer. Paga con latencia lo que Cassandra paga con inconsistencia temporal. Es "SQL" por etiqueta, "como PostgreSQL" en L3, y "tan operativamente complejo como Cassandra" en L2, así que compara su fila con las de ambos en la tabla de fingerprints de arriba.

**Variable 2: la forma de las peticiones que tu aplicación realmente hace** (Capa 4, ya visto). Esto tampoco tiene relación con el lenguaje. Es enteramente independiente de si el motor "habla SQL": puedes necesitar traversal de grafo y tenerlo con o sin ACID; puedes necesitar búsqueda semántica y tenerla con o sin SQL encima (pgvector la ofrece con SQL). El "esquema flexible" que la gente suele citar como el sello distintivo de NoSQL no es una tercera variable independiente, es simplemente una propiedad de esta misma Capa 4: el modelo de documento la trae por diseño, el relacional no, y ninguno de los dos hechos tiene relación con el lenguaje de consulta ni con el trade-off de CAP/PACELC de la Variable 1.

Estas dos variables son ortogonales entre sí y ninguna se llama "SQL" o "NoSQL". Por eso preguntar "¿uso SQL o NoSQL?" es hacer la pregunta equivocada dos veces al mismo tiempo, sin saberlo.

#### La pregunta correcta: qué le pides a la base de datos, no cómo se llama

Lo único que debería determinar tu elección de motor es caracterizar, con precisión, la petición típica que tu aplicación le hace a la base de datos, y qué le exiges cuando algo falla. En la práctica esto se reduce a responder dos preguntas.

**Pregunta 1: ¿cuál es la forma de la operación más frecuente?**

| Forma de la petición                                                                      | Lo que exige                              | Capas que resuelven esto                  |
| ----------------------------------------------------------------------------------------- | ----------------------------------------- | ----------------------------------------- |
| "Dame el valor de esta clave exacta, muchísimas veces por segundo"                        | Latencia mínima, sin relaciones           | L1 hash/skip list en RAM · L4 clave-valor |
| "Necesito juntar 4-5 entidades relacionadas con filtros que cambian según quién pregunta" | Joins ad hoc sobre schema estable         | L4 relacional · L5 SQL                    |
| "Leo y escribo un documento completo casi siempre entero, rara vez cruzo con otro"        | Acceso atómico a un blob semiestructurado | L4 documento                              |
| "Recorro relaciones de profundidad variable (amigos de amigos, ruta más corta)"           | Traversal nativo, no self-joins           | L1/L4 grafo                               |
| "Necesito insertar más rápido de lo que un solo disco aguanta, sin perder disponibilidad" | Escritura distribuida sin coordinador     | L1 LSM-tree · L2 peer-to-peer             |
| "Rankeo resultados por relevancia de texto libre"                                         | Inverted index, scoring                   | L1 inverted index · L5 query DSL          |
| "Agrego millones de filas históricas sobre pocas columnas"                                | Compresión columnar, column scan          | L1/L4 columnar                            |
| "Busco lo semánticamente más parecido, no lo exactamente igual"                           | ANN sobre vectores                        | L1/L4/L5 vectorial                        |

**Pregunta 2: ¿qué le pasa a mi negocio si dos escrituras concurrentes sobre la MISMA entidad se entrelazan mal?** (dinero, inventario, asientos, cupos). Esta pregunta es independiente de la anterior, determina cuánta Capa 3 necesitas, y la tabla de fingerprints ya tiene un ejemplo real en cada punta: desde ACID serializable (PostgreSQL, CockroachDB) hasta la ausencia total de garantía (Memcached, donde perder el dato es el comportamiento esperado), pasando por eventual (DynamoDB) y tunable por operación (Cassandra).

La pregunta 1 apunta a L1/L4/L5, la forma del dato y de la interfaz. La pregunta 2 apunta a L3, y es independiente: un motor de grafo puede necesitar aislamiento fuerte (Neo4j) igual que uno relacional; un motor de documentos puede vivir con consistencia eventual si el "negocio" es contar vistas de un post. Una vez que tienes las dos respuestas, vas a la tabla de fingerprints de arriba y buscas la fila cuya combinación de capas coincida, y ese es el proceso profesional real. "SQL o NoSQL" no aparece en ningún punto de ese proceso porque no es una variable del problema.

#### La respuesta directa

Entonces: ¿es marketing, o debería preocuparte solo el tipo de queries? **Las dos cosas, pero no en partes iguales.** El nombre "SQL vs NoSQL" sí es, en gran medida, un residuo de marketing de un movimiento de 2009 que agrupó motores dispares bajo una bandera común para venderse como alternativa al status quo, y dos décadas de "convergencia" (sección 14: PostgreSQL con JSONB, MongoDB con transacciones multi-documento, MySQL con JSON, ClickHouse con SQL estándar) han seguido borrando incluso la poca señal que el nombre tenía al principio. Pero no deberías tirar el nombre a la basura por marketing y quedarte sin nada. Deberías reemplazarlo por las dos variables que sí son reales y medibles: el trade-off CAP/PACELC que tu escala y distribución geográfica te fuerzan a tomar, y la forma exacta de las peticiones que tu aplicación hace. Esas dos variables sí predicen qué motor te conviene. La etiqueta "SQL vs NoSQL" no predice nada, predice, como mucho, en qué año se fundó la empresa que lo vende.

#### En qué capas te deberías preocupar realmente

La conclusión de "El modelo de capas" (arriba) ya estableció el veredicto general, así que no hace falta repetirlo, solo aplicarlo a la pregunta concreta de esta sección: **L4 es donde se empieza**. **L2 y L3 son las siguientes dos variables reales**, independientes entre sí y de L4. **L1 casi siempre es consecuencia**, no punto de partida (su propia sección lo demuestra con MyRocks). **L0** rara vez entra en la conversación salvo por presupuesto. Y **L5**, la capa que dispara la pregunta "SQL o NoSQL" en primer lugar, es, de las ocho, la menos fiable como evidencia de lo que un motor puede hacer. Eso último es lo que vale la pena desarrollar, porque es la raíz del malentendido:

#### Por qué L5 engaña: cómo leer una ausencia en el lenguaje sin adivinar

Justo porque L5 es la menos fiable, hay que saber leerla bien. La trampa fácil es asumir que si el lenguaje de consulta de un motor no tiene una palabra o comando para hacer algo que SQL sí tiene, por ejemplo si no existe la palabra `JOIN`, esa operación es directamente imposible en ese motor. La trampa contraria es asumir lo opuesto: que como "es solo cuestión de lenguaje", nunca hay una consecuencia real detrás. Ninguna de las dos suposiciones es segura, porque el lenguaje (L5) no te dice por sí solo si lo que falta es de vocabulario o de arquitectura. La forma correcta de averiguarlo es revisar las capas de abajo hacia arriba, empezando en L4 y subiendo hasta L5, en vez de quedarte adivinando mirando solo el lenguaje. Supón que encontraste que el lenguaje de un motor no tiene una palabra o comando para algo que en SQL sí existe, por ejemplo que no tiene `JOIN`. Antes de concluir nada, revisa en este orden:

| Paso | Capa a revisar                   | Pregúntate                                                                                                               | Si la respuesta es "no", ahí está el límite                                                               |
| ---- | -------------------------------- | ------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------- |
| 1    | L4, modelo de datos              | ¿El modelo puede representar lo que quiero pedir?                                                                        | **L4**, por ejemplo clave-valor no modela relaciones, así que la operación no tiene sentido en ese modelo |
| 2    | L1, estructura de almacenamiento | Si L4 sí lo modela, ¿la estructura permite acceder a esa relación de forma eficiente, sin escanear todo?                 | **L1**, por ejemplo B-tree por colección en MongoDB, sin índice compartido entre colecciones              |
| 3    | L2, distribución                 | Si L1 lo permite dentro de un nodo, ¿la distribución deja cruzar esa frontera sin un scatter-gather costoso entre nodos? | **L2**, por ejemplo en Cassandra un join cruzaría particiones, y el diseño lo rechaza a propósito         |
| 4    | L5, interfaz                     | Si L4, L1 y L2 sí lo permiten y aun así el lenguaje no te deja pedirlo (o lo pide con otra sintaxis)                     | **Ninguno real**. Es puramente cosmético, la capacidad existe y solo cambia cómo se invoca                |

Tres ejemplos ya vistos, leídos con este filtro:

- **MongoDB** no tiene `GROUP BY`, pero su _aggregation pipeline_ (`$group`, `$match`, `$project`) hace lo mismo, así que la búsqueda llega hasta el paso 4 sin encontrar límite real abajo. Es puramente sintáctico.
- **Neo4j** no escribe transacciones en SQL, pero soporta transacciones ACID completas, invocadas con `BEGIN`/`COMMIT` en Cypher en vez de en SQL. Su nivel de aislamiento por defecto (read committed) coincide con el default de PostgreSQL, aunque a diferencia de PostgreSQL, llegar a serializable exige adquirir manualmente locks de escritura en vez de pedirlo con una sola palabra clave. Ese matiz vive en L3, pero el límite que estamos rastreando aquí (la ausencia de sintaxis SQL) sigue sin encontrar nada real abajo: se queda en el paso 4.
- **Cassandra** (CQL) no tiene joins porque el límite real aparece en el paso 3 (L2): los datos viven particionados entre nodos y un join exigiría cruzar esa frontera en cada consulta, rompiendo la latencia predecible que es su propuesta de valor. El lenguaje simplemente refleja, con honestidad, una restricción que ya existía dos capas más abajo. `$lookup` de MongoDB es el caso intermedio: la sintaxis existe (a diferencia de Cassandra), pero es lenta porque el límite aparece antes, en los pasos 1 y 2 (L1: B-tree por colección; L4: modelo denormalizado por diseño). El lenguaje "permite" pedirlo, pero las capas de abajo cobran el precio.

La regla que queda de este ejercicio: cuando el lenguaje de un motor carece de algo que SQL sí tiene, no preguntes "¿lo tiene o no?". Pregunta "¿en qué capa se detuvo la búsqueda?", subiendo desde L4. Si la respuesta aparece en L4, L1 o L2, estás ante una diferencia real de lo que el motor puede hacer, y debe pesar en tu elección. Si la búsqueda llega hasta L5 sin encontrar un límite real abajo, estás ante una diferencia de vocabulario nada más, y no debería pesar en absoluto.

#### Cómo responder, en la práctica, cuando te preguntan "¿SQL o NoSQL?"

Con las dos piezas anteriores, qué capas importan y cómo leer L5 sin engañarte, la respuesta profesional deja de ser elegir un bando y se vuelve un procedimiento corto de tres pasos.

**Paso 1: nombra el problema real, no la trivia histórica.**

> "Esa pregunta agrupa motores con arquitecturas demasiado distintas entre sí como para generalizar. Neo4j soporta transacciones ACID igual que PostgreSQL, aunque con mecánica distinta para llegar a aislamiento serializable; MongoDB guarda sus datos con una estructura casi idéntica a la de muchos relacionales; Cassandra no se parece a ninguno de los dos. Y el error más común al intentar generalizar de todas formas es juzgar por el lenguaje de consulta, que es, de las ocho cosas que definen a un motor, la menos confiable como evidencia de lo que puede o no puede hacer. Prefiero preguntarte tres cosas concretas sobre tu caso: esas sí deciden."

**Paso 2: pregunta, en el mismo orden de prioridad de capas visto arriba (L4 → L3 → L2).** Nota que L0, L1 y L5 no aparecen aquí a propósito: L1 es consecuencia de L4, y de L2 cuando aplica (más L0, que rara vez pesa, ver Capa 1), L0 es presupuesto de infraestructura, y L5 ya se estableció como la capa menos fiable.

| Pregunta que haces                                                                                                                                                                                                      | Capa que ilumina | Qué te dice la respuesta                                                                                                                                                                                 |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| "¿Cuál es la forma de la petición más frecuente?" (punto exacto por clave, join entre varias entidades, documento completo, traversal de relaciones, texto libre rankeado, agregación histórica, o similitud semántica) | **L4**           | Fija el modelo de datos. Con eso ya queda resuelta la mayor parte de la elección de motor.                                                                                                               |
| "¿Qué le pasa a tu negocio si dos escrituras concurrentes sobre la MISMA entidad se entrelazan mal?" (dinero, inventario, cupos)                                                                                        | **L3**           | Determina cuánto aislamiento necesitas, sin importar la respuesta anterior. Si la respuesta es "nada grave, se corrige solo", puedes permitirte eventual y ganar otras cosas a cambio.                   |
| "¿Cuánto necesitas escalar en escritura, y en cuántas regiones?"                                                                                                                                                        | **L2**           | Si cabe en un servidor grande o en primary-replica en una sola región, el trade-off de CAP/PACELC ni se activa. Puedes tener el modelo de datos que quieras sin pagar el costo de consistencia eventual. |

Si en este punto te responden con un contraejemplo puntual ("pero MongoDB no tiene joins", "pero Cassandra no tiene transacciones"), no lo tomes como una objeción a resolver de memoria. Pásalo por el filtro de cuatro pasos de "Por qué L5 engaña" (arriba) y te dice, en segundos, si es una limitación real de alguna capa o solo una diferencia de vocabulario.

**Paso 3: cierra con la síntesis honesta.**

> "Con esas tres respuestas casi nunca termino discutiendo si algo es 'SQL' o 'NoSQL'. Termino comparando motores concretos capa por capa, con la tabla de fingerprints de arriba como referencia. Es normal terminar en un motor relacional para un caso 'moderno' de alta escritura (extensiones como Citus, o NewSQL como CockroachDB), o en MongoDB para un caso con relaciones complejas si el acceso real es siempre por documento completo. La etiqueta no predijo ninguno de los dos casos. L4, L3 y L2 sí."

Esta respuesta funciona porque demuestra, en el orden en que el que pregunta la nota, tres cosas: que no repetiste un cliché de blog, que entiendes que la decisión vive en capas concretas y no en una etiqueta binaria, y que puedes aterrizarla en motores reales apenas haga falta. Es el mismo procedimiento de "Cómo evaluar un motor desconocido" (más abajo), aplicado hacia atrás: en vez de partir de un motor y describir su fingerprint, partes del fingerprint que tu problema necesita y buscas qué motor lo cumple.

---

### Desglose de motores que parecen iguales: PostgreSQL vs MySQL InnoDB

La sección anterior mostró que la etiqueta "SQL vs NoSQL" no predice nada útil porque agrupa motores dispares. El siguiente caso prueba lo mismo desde el otro extremo: dos motores que comparten la etiqueta "SQL" — PostgreSQL y MySQL InnoDB, ambos relacionales, ambos ACID, ambos con primary-replica — tampoco son intercambiables entre sí. La tabla de fingerprints muestra que ambos usan disco (L0), ambos tienen primary-replica (L2) y ambos exponen SQL (L5). A primera vista parecen idénticos. No lo son: las diferencias están en L1, L3 y L4, y son arquitecturalmente significativas — el mismo tipo de diferencia que la pregunta 1 del framework anterior (forma de la petición) y la pregunta 2 (qué exiges bajo concurrencia) están diseñadas para exponer.

#### L1 — Estructura: heap vs clustered index

Esta es la diferencia más profunda y la que más consecuencias en cadena produce.

**PostgreSQL — B-tree + heap:**

La tabla (heap) y sus índices son estructuras separadas en disco. Las filas se almacenan en páginas del heap en orden de inserción, sin relación con ninguna clave. Cada índice — incluyendo el de clave primaria — es un B-tree independiente cuyos nodos hoja almacenan `(clave → ctid)`, donde `ctid` es un puntero físico de la forma `(número_de_página, slot_en_esa_página)` que apunta a la fila en el heap.

```
Buscar por PK en PostgreSQL:

Índice PK (B-tree):               Heap (tabla):
  raíz                            página 0: [fila id=1][fila id=5][...]
    → hoja: id=3 → ctid=(2, 1)   página 1: [fila id=2][fila id=7][...]
                         │        página 2: [fila id=4][fila id=3][...]
                         └──────────────────────────────↑
                                  acceso a página 2, slot 1
```

Todos los índices son equivalentes en costo: todos hacen el mismo recorrido B-tree → ctid → heap. Un índice secundario no cuesta más que el primario.

**MySQL InnoDB — B-tree clustered:**

La tabla no existe separada del índice primario. Las filas están almacenadas físicamente **en las páginas hoja del B-tree de la clave primaria**. El B-tree de PK es la tabla. Los índices secundarios no almacenan ctid — almacenan el valor de la clave primaria, y para llegar al dato completo hacen dos recorridos de B-tree: secundario → PK, PK → dato.

```
Buscar por PK en MySQL InnoDB:

B-tree de PK (= la tabla):
  raíz
    → nodo interno: id > 100 ir derecha
      → hoja: id=3, nombre="Ana", edad=25   ← dato completo aquí, sin acceso extra

Buscar por índice secundario (idx_nombre) en MySQL InnoDB:

B-tree secundario:                B-tree de PK (= la tabla):
  hoja: nombre="Ana" → id=3  →   hoja: id=3, nombre="Ana", edad=25
                          ↑
                    segundo recorrido necesario
```

**Consecuencias en cadena de L1:**

```
                          PostgreSQL (heap)        MySQL InnoDB (clustered)
──────────────────────────────────────────────────────────────────────────
Acceso por PK             B-tree + 1 heap read     B-tree (dato en la hoja)
Acceso por índice 2°      B-tree + 1 heap read     2 recorridos B-tree
Inserts con PK aleatoria  Sin fragmentación extra   B-tree fragmenta (splits)
  (UUIDs, hashes)         (heap acumula al final)   Las hojas quedan ~50% llenas
PK auto-increment         Sin diferencia notable    Inserts siempre al final
                                                    del B-tree → sin splits
Versiones MVCC            Dead tuples en el heap    Versiones en undo log
  (filas antiguas)        → VACUUM las limpia       → se limpian automáticamente
                          periódicamente              al cerrar la transacción
Covering index            Possible con INCLUDE()    Natural (PK siempre cubre)
```

**Recomendación práctica de L1:** en MySQL InnoDB usa siempre PKs auto-increment o secuenciales. UUIDs aleatorios como PK causan fragmentación severa del clustered index (cada insert puede romper páginas en cualquier posición del árbol). En PostgreSQL el heap no tiene ese problema — los inserts siempre van al final de las páginas disponibles independientemente del valor de la PK.

---

#### L3 — Consistencia: SSI vs locking reads

Ambos implementan ACID, pero su nivel `SERIALIZABLE` funciona de manera radicalmente distinta bajo concurrencia alta.

**PostgreSQL — SSI (Serializable Snapshot Isolation):**

PostgreSQL implementa `SERIALIZABLE` con SSI: un algoritmo que detecta ciclos de dependencia entre transacciones concurrentes **sin usar locks adicionales**. Cada transacción trabaja sobre su snapshot (como en `REPEATABLE READ`), pero el motor rastrea dependencias de lectura-escritura entre transacciones. Si detecta que un conjunto de transacciones concurrentes produciría un resultado imposible de obtener en ningún orden serial, aborta la que rompe la serializabilidad.

```
Anomalía "write skew" — dos transacciones leen el mismo dato
y cada una escribe en base a lo que leyó:

T1: lee saldo_cuenta_A=100, escribe saldo_cuenta_B=saldo_A-50
T2: lee saldo_cuenta_B=100, escribe saldo_cuenta_A=saldo_B-50

En REPEATABLE READ: ambas transacciones ven el estado inicial →
  ambas hacen el update → resultado final incorrecto.

En SERIALIZABLE con SSI: PostgreSQL detecta el ciclo de dependencias
  (T1 leyó algo que T2 escribió, T2 leyó algo que T1 escribió) →
  aborta una de las dos → la aplicación reintenta.
  Sin locks adicionales, sin espera, solo detección y abort.
```

**MySQL InnoDB — Serializable vía locking reads:**

El nivel `SERIALIZABLE` de MySQL convierte implícitamente **todos los `SELECT` en `SELECT ... LOCK IN SHARE MODE`**. Es decir, cada lectura adquiere un shared lock sobre las filas leídas. Esto previene que otra transacción modifique esas filas mientras la primera no haya terminado.

```
La misma anomalía "write skew" en MySQL SERIALIZABLE:

T1: SELECT saldo FROM cuentas WHERE id=A  → adquiere S-lock sobre fila A
T2: SELECT saldo FROM cuentas WHERE id=B  → adquiere S-lock sobre fila B
T1: UPDATE cuentas SET saldo=... WHERE id=B → necesita X-lock → ESPERA (T2 tiene S-lock)
T2: UPDATE cuentas SET saldo=... WHERE id=A → necesita X-lock → ESPERA (T1 tiene S-lock)
→ DEADLOCK → uno de los dos aborta
```

La anomalía está prevenida, pero el mecanismo es distinto: PostgreSQL usa detección de dependencias + abort selectivo; MySQL usa locks + espera + deadlock. Bajo contención alta, MySQL `SERIALIZABLE` produce más deadlocks y más tiempo de espera que PostgreSQL `SERIALIZABLE`. PostgreSQL permite más concurrencia porque no bloquea lecturas.

---

#### L4 — Modelo de datos: extendido vs estándar

Ambos son relacionales, pero el conjunto de tipos nativos y capacidades de indexación difiere significativamente.

```
Tipo / Capacidad               PostgreSQL         MySQL InnoDB
─────────────────────────────────────────────────────────────────
Arrays nativos                 Sí  (int[], text[]) No
JSONB con índices GIN          Sí                  No (JSON existe pero no GIN)
Tipos de rango                 Sí (daterange,       No
                               numrange, tsrange)
UUID nativo                    Sí                  No (VARCHAR(36) o BINARY(16))
Tipos geométricos              Sí (point, polygon)  Parcial (spatial types)
Tipos custom (CREATE TYPE)     Sí                  No
Herencia de tablas             Sí                  No
Extensiones (PostGIS, etc.)    Sí (ecosistema rico) No
Full-text search               Sí (tsvector, GIN)  Sí (FULLTEXT index)
```

El impacto práctico de JSONB+GIN en PostgreSQL: puedes crear un índice GIN sobre un campo JSON y hacer queries como `WHERE datos @> '{"ciudad": "Madrid"}'` que usan el índice. En MySQL, la misma query sobre un campo JSON es siempre un full scan del campo — sin índice posible sobre el contenido interno del JSON.

---

#### L0, L2, L5 — donde sí son equivalentes

- **L0 (físico):** ambos sobre disco, sin diferencia relevante.
- **L2 (distribución):** para la mayoría de deployments son equivalentes. Ambos tienen primary-replica nativa. Para sharding, MySQL tiene Vitess (proxy externo) y PostgreSQL tiene Citus (extensión integrada); ninguno tiene sharding nativo sin herramientas adicionales.
- **L5 (interfaz):** ambos hablan SQL, ambos tienen stored procedures, ambos tienen drivers en todos los lenguajes. Las diferencias de dialecto (funciones de ventana, CTEs, sintaxis de hints) son reales pero menores comparadas con L1, L3 y L4.

---

#### Cuándo importa cada diferencia

```
Si tu aplicación necesita...          La diferencia relevante es...
──────────────────────────────────────────────────────────────────────
Transacciones SERIALIZABLE bajo       L3 — PostgreSQL SSI escala mejor
alta concurrencia                     que MySQL locking

PKs con UUIDs o valores aleatorios    L1 — PostgreSQL heap no fragmenta;
                                      MySQL InnoDB sí, con impacto en write perf

Almacenar y consultar JSON con        L4 — PostgreSQL JSONB+GIN es indexable;
filtros sobre campos internos         MySQL JSON no lo es

Arrays o rangos como tipos de dato    L4 — PostgreSQL los tiene nativos;
                                      MySQL necesita VARCHAR o tablas extra

CRUD estándar sin tipos complejos     L0/L2/L5 — equivalentes; la elección
y sin SERIALIZABLE frecuente          es operacional (DBA skills, tooling,
                                      licencia, comunidad)
```

---

### Acoplamiento entre capas

Esta sección existe porque la conclusión de "El modelo de capas" dejó una idea abierta que vale la pena cerrar con nombres concretos: cuando el modelo de datos empuja hacia una estructura de almacenamiento y una distribución determinadas, esas capas no solo cascan una vez, casi siempre terminan repitiéndose juntas, motor tras motor, en proyectos que no tienen relación entre sí. Eso no es casualidad ni copia: algunas combinaciones se refuerzan físicamente entre sí y facilitan el trabajo de las demás, mientras que otras son raras porque exigen ingeniería adicional deliberada para poder coexistir. Conocer estas parejas de memoria sirve para dos cosas muy prácticas: primero, te deja adivinar con buena probabilidad cómo es el resto del fingerprint de un motor en cuanto conoces una sola de sus capas (así funciona "Cómo evaluar un motor desconocido", justo después de esta sección); segundo, te permite reconocer cuándo un motor rompe una pareja común a propósito, porque esa ruptura casi siempre es la contribución técnica central del proyecto, no un detalle menor.

#### Acoplamientos naturales: por qué van juntos

| Acoplamiento (L1 + L2 + L3)                            | Motores que lo usan                            | Por qué se refuerzan entre sí                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| ------------------------------------------------------ | ---------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| LSM-tree + Peer-to-peer + Eventual                     | Cassandra, DynamoDB, ScyllaDB                  | LSM-tree maximiza el throughput de escritura con appends secuenciales. La distribución peer-to-peer maximiza la disponibilidad de escritura al no depender de un coordinador que pueda bloquear. La consistencia eventual es la consecuencia directa de aceptar escrituras en varios nodos sin coordinación: no se puede garantizar un orden global sin un protocolo de consenso que añadiría latencia. Los tres se eligen juntos porque comparten el mismo trade-off, sacrificar consistencia fuerte a cambio de throughput de escritura y disponibilidad. |
| B-tree + Nodo único o primary-replica + ACID           | PostgreSQL, MySQL, SQL Server, Oracle          | B-tree modifica páginas in-place con locks de página o fila, algo simple de sostener cuando todo el proceso vive en un solo nodo. ACID exige que el WAL y las páginas sean coherentes, y dentro de un solo nodo eso es una operación local, sin coordinación de red de por medio. Escalar este mismo patrón a varios nodos sin perder ACID es justo el problema que resuelve NewSQL, con el costo que se explica en la fila de abajo.                                                                                                                       |
| Columnar + Sharding + Eventual                         | ClickHouse, Redshift, BigQuery                 | OLAP no necesita transacciones ACID porque los datos se cargan en batch, no fila por fila en tiempo real. La consistencia eventual es aceptable para un análisis donde el resultado de ayer sigue siendo útil aunque no sea el de hace diez segundos. El sharding es necesario porque los datasets analíticos casi siempre son demasiado grandes para caber en un solo nodo.                                                                                                                                                                                |
| RAM + Hash o skip list + Nodo único + Eventual         | Redis                                          | Si el dataset completo cabe en RAM no hace falta una distribución compleja. Las hash tables y skip lists en RAM dan latencias de microsegundos, estructuras que en disco serían inutilizables. La persistencia queda en segundo plano porque el valor de Redis está en la latencia, no en la durabilidad, y la consistencia eventual viene simplemente de que la replicación entre réplicas es asíncrona.                                                                                                                                                   |
| Relacional (L4) + Integridad declarativa enforced (L6) | PostgreSQL, MySQL, CockroachDB, SQLite, DuckDB | El modelo relacional nació precisamente para garantizar integridad referencial de forma declarativa — es su propuesta de valor original, no un extra. Los motores que abandonaron el modelo de tablas para escalar horizontalmente (wide-column, documento, KV) también abandonaron el enforcement, porque validar una referencia entre particiones distintas en cada escritura es exactamente el tipo de coordinación cara que esos motores existen para evitar.                                                                                           |

#### Acoplamientos rotos: cuándo y por qué cuestan más

| Acoplamiento roto                                                | Motores que lo logran                                            | Qué mecanismo lo hace posible, y qué cuesta                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| ---------------------------------------------------------------- | ---------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| LSM-tree + ACID                                                  | MyRocks (MySQL sobre RocksDB), TiKV (TiDB), Pebble (CockroachDB) | LSM-tree no modifica datos in-place, siempre escribe versiones nuevas. Sostener ACID exige que el commit sea atómico y que las lecturas vean un snapshot consistente, aunque existan varias versiones del mismo dato repartidas en distintas SSTables. Es posible, pero exige construir MVCC por encima del LSM-tree, coordinando qué versión es "visible" para cada transacción: un componente nada trivial, y por eso solo lo hacen unos pocos motores.                                                                           |
| Distribuido + ACID                                               | CockroachDB, Google Spanner, TiDB (NewSQL)                       | ACID distribuido exige que todos los nodos involucrados en una transacción acuerden el commit antes de confirmarlo al cliente (2PC más consenso), lo que añade al menos una ronda de red de latencia en el camino crítico de cada escritura. Spanner resolvió parte de esa coordinación con relojes atómicos (TrueTime) para el ordenamiento global; CockroachDB usa Raft por rango de claves. En ambos casos, la latencia de escritura termina siendo más alta que en un motor de nodo único con ACID.                             |
| Columnar + distribuido + control de acceso por fila (L1/L2 + L7) | ClickHouse (`ROW POLICY` nativo)                                 | RLS suele asociarse a motores relacionales de nodo único porque cada fila ya pasa por un plan de query centralizado, donde agregar un filtro por usuario es barato. ClickHouse es columnar y se distribuye por sharding manual — el perfil opuesto — y aun así implementa row policies nativas en su capa de ejecución de queries. Prueba que el control de acceso por fila no depende de L1 ni L2: depende de si el motor decidió invertir en ese punto de control, sin importar su arquitectura de almacenamiento o distribución. |

Cuando encuentres un motor que rompe un acoplamiento común, busca primero el mecanismo que lo hace posible: casi siempre es la razón de ser del proyecto, no un detalle secundario de su implementación.

---

### Cómo evaluar un motor desconocido

Este orden es, a propósito, el inverso del orden de diseño que cierra la conclusión de "El modelo de capas" (L4 primero, L1 casi al final). Ahí tenía sentido porque tú controlas la decisión completa, de arriba hacia abajo. Aquí no: te encuentras con un motor que no conoces y no tienes acceso a la cadena de decisiones que lo originó, solo puedes observarlo desde afuera. Y lo único que casi siempre puedes buscar y confirmar en minutos es su estructura de almacenamiento (L1), así que el orden se invierte y empiezas por ahí.

#### El método, paso a paso

| Paso | Capa                        | Qué preguntarte                                                                           | Qué te dice el resultado                                                                                                                                                                                                             |
| ---- | --------------------------- | ----------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1    | L1 — Estructura             | ¿Qué estructura de almacenamiento usa? (búscalo directamente: "\[motor\] storage engine") | Define el carácter fundamental: ¿write-heavy o read-heavy?, ¿OLTP o OLAP? Si es LSM-tree, espera L3 eventual salvo que haya un mecanismo explícito para ACID. Si es columnar, espera L4 analítico y L3 eventual.                     |
| 2    | L2 — Distribución           | ¿Puede escalar más allá de un nodo? ¿Cómo? ¿Hay coordinador o protocolo de consenso?      | Confirma o contradice lo que ya sugería L1: si ambas apuntan al mismo acoplamiento natural (ver "Acoplamiento entre capas"), el fingerprint ya casi está completo.                                                                   |
| 3    | L3 — Consistencia           | ¿Qué garantiza exactamente? ¿Es tunable por operación?                                    | Si el resultado rompe el acoplamiento natural de L1 + L2, busca qué mecanismo concreto lo sostiene (revisa "Acoplamientos rotos"): casi siempre es la razón de ser del proyecto.                                                     |
| 4    | L4 y L5 — Modelo e interfaz | ¿Qué forma tiene el dato? ¿Qué lenguaje expone?                                           | Son las capas más visibles y las menos diferenciadoras: SQL sobre ClickHouse no lo convierte en PostgreSQL, y que CQL no tenga joins no oculta que L2 y L3 de Cassandra son fundamentalmente distintos a los de un motor relacional. |

Dos ejemplos completos, aplicando el método a un motor que resulta ser una variante conocida y a uno que resulta ser una categoría nueva.

#### Ejemplo 1: ScyllaDB (resulta ser una variante conocida)

Búsqueda inicial: _"ScyllaDB storage engine"_ → LSM-tree.

| Capa | Lo que encuentras                                                     |
| ---- | --------------------------------------------------------------------- |
| L1   | LSM-tree, igual que Cassandra                                         |
| L2   | Peer-to-peer (ring), igual que Cassandra                              |
| L3   | Eventual, tunable por operación (ONE/QUORUM/ALL), igual que Cassandra |
| L4   | Wide-column, igual que Cassandra                                      |
| L5   | CQL, compatible con Cassandra                                         |

**Conclusión:** fingerprint idéntico a Cassandra en las ocho capas. La diferencia real vive debajo de todas ellas: una reimplementación en C++ que elimina el overhead de la JVM y del garbage collection. No es un motor distinto en diseño, es Cassandra con mejor implementación del runtime.

#### Ejemplo 2: Pinecone (resulta ser una categoría nueva)

Búsqueda inicial: _"Pinecone index type"_ → HNSW.

| Capa | Lo que encuentras                                                                                                                                                              |
| ---- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| L1   | Vector index (HNSW): ni B-tree ni LSM, carácter de lectura completamente distinto (la query es "los k más parecidos", no "la fila donde X = Y")                                |
| L2   | Distribuido, con sharding gestionado internamente por el propio servicio (corre sobre infraestructura de AWS/GCP, pero la orquestación es de Pinecone, no de esos proveedores) |
| L3   | Eventual / best-effort, sin niveles configurables (confirma el patrón: cuando L1 rompe con las estructuras clásicas, L3 rara vez es ACID)                                      |
| L4   | Vectorial + metadata                                                                                                                                                           |
| L5   | API HTTP propia, query por similitud                                                                                                                                           |

**Conclusión:** fingerprint sin precedente entre los motores ya vistos en este documento, el primero con L1 vectorial. No es una variante de otro motor de la tabla, es una categoría nueva porque resuelve un problema (similitud en espacios de alta dimensión) que ninguna estructura anterior resuelve bien.

Los dos ejemplos muestran los dos únicos resultados posibles de aplicar el método: o el fingerprint coincide con uno que ya conoces (ScyllaDB) y la diferencia real está en la implementación, no en el diseño, o no coincide con nada visto antes (Pinecone) y entonces sí estás ante una categoría genuinamente nueva.

---

## 2. Estructuras de almacenamiento en disco {#estructuras}

### Por qué existen diferentes estructuras

El problema fundamental es que **leer y escribir eficientemente son objetivos en tensión**:

- Para leer rápido: necesitas datos ordenados y agrupados en disco
- Para escribir rápido: necesitas hacer lo mínimo posible al momento de escribir

No existe una estructura óptima para ambos simultáneamente. La elección de estructura define el carácter del motor.

---

### 2.1 B-tree (árbol B y B+tree)

**Optimizado para:** lecturas aleatorias, updates in-place, OLTP general  
**Lo usan:** PostgreSQL, MySQL InnoDB, SQL Server, Oracle, SQLite, MongoDB (WiredTiger)

#### Estructura

Un B+tree es un árbol balanceado donde:

- Los nodos internos solo guardan claves (para navegar)
- Las hojas guardan los datos reales (o punteros a ellos)
- Todas las hojas están enlazadas entre sí → scans de rango eficientes

```
B+tree de índice sobre columna "edad":

                    ┌──────────────┐
                    │   30  │  60  │   ← nodo raíz (solo claves)
                    └──┬────┴────┬─┘
            ┌──────────┘         └──────────┐
       ┌────┴────┐                     ┌────┴────┐
       │ 15 │ 22 │                     │ 45 │ 55 │   ← nodos internos
       └─┬──┴──┬─┘                     └─┬──┴──┬─┘
         │     │                         │     │
    ┌────┴┐  ┌─┴───┐                ┌────┴┐  ┌─┴───┐
    │15,17│→ │22,28│ → ... → ... → │45,52│→ │55,58│  ← hojas enlazadas
    └─────┘  └─────┘               └─────┘  └─────┘
      (datos reales están en las hojas o apuntan a ellos)
```

#### Cómo funciona una lectura

```
Buscar edad = 52:

1. Raíz: 52 > 30 y 52 < 60 → ir a hijo derecho
2. Nodo interno: 52 > 45 y 52 < 55 → ir a hoja izquierda
3. Hoja: encontrar 52 → retornar dato

Costo: altura del árbol = O(log n)
Para 1 millón de filas, altura típica ≈ 3-4 niveles
→ 3-4 lecturas de página para cualquier búsqueda puntual
```

#### Cómo funciona una escritura (insert)

```
Insertar edad = 50:

1. Navegar hasta la hoja correcta (igual que lectura)
2. Insertar 50 en la hoja
3. Si la hoja tiene espacio → listo
4. Si la hoja está llena → PAGE SPLIT:
   - Dividir hoja en dos
   - Promover la clave media al nodo padre
   - Si el padre también se llena → split en cascada hacia arriba
```

El page split es el costo principal de escritura en B-tree. En tablas muy activas, los splits generan fragmentación → el DBA necesita hacer `VACUUM` (PostgreSQL) o `OPTIMIZE TABLE` (MySQL) periódicamente.

#### Por qué es bueno para OLTP

- Lectura puntual siempre en O(log n) — predecible
- Update in-place: modificar un valor no mueve datos a otro lugar
- Soporta queries de rango eficientemente (hojas enlazadas)
- Compatible con MVCC: PostgreSQL puede guardar múltiples versiones de una fila en las páginas del B-tree

#### Limitación principal

Escrituras aleatorias en disco. Cada insert/update puede tocar múltiples páginas en posiciones arbitrarias del disco. En HDDs esto era catastrófico (seek time). En SSDs es mejor pero aún genera write amplification.

---

### 2.2 LSM-tree (Log-Structured Merge-tree)

**Optimizado para:** escrituras masivas a alta velocidad, write-heavy workloads  
**Lo usan:** Cassandra, RocksDB, LevelDB, InfluxDB, ScyllaDB, parte de MongoDB, TiKV (motor de TiDB) y Pebble (motor de CockroachDB, sucesor de RocksDB)

#### La idea central

En lugar de modificar datos en su lugar (como B-tree), el LSM-tree **nunca sobreescribe**. Toda escritura es un insert en RAM; el ordenamiento y la escritura a disco ocurren en background.

El nombre "tree" es confuso: **no es un árbol de nodos con punteros** como un B-tree. La jerarquía de niveles en disco (L0, L1, L2…) es lo que le da el nombre.

El LSM-tree está compuesto por tres estructuras de datos distintas: la **MemTable** en RAM, los **SSTables** en disco, y un **Bloom filter** por cada SSTable.

---

#### Estructura 1 — MemTable (RAM)

La MemTable es una estructura **ordenada por clave** que vive completamente en RAM. Toda escritura va aquí primero. Estar ordenada es lo que permite producir un SSTable con claves en orden mediante un único recorrido lineal in-order, sin ningún sort adicional.

Dos implementaciones concretas:

---

**Red-Black Tree (árbol rojo-negro) — LevelDB**

Un árbol binario de búsqueda donde cada nodo guarda un bit adicional de color. Ese bit no almacena datos del usuario — es una propiedad interna de balance.

```
struct RBNode {
    key:   []byte
    value: []byte
    color: RED | BLACK
    left:  *RBNode
    right: *RBNode
}
```

El árbol mantiene tres invariantes que juntas garantizan el balance:

1. La raíz es siempre negra
2. Un nodo rojo nunca tiene un hijo rojo — no hay dos rojos consecutivos en ningún camino
3. Todo camino desde la raíz hasta un nodo nulo tiene exactamente el mismo número de nodos negros

La invariante 3 exige que todos los caminos tengan la misma "profundidad negra". La invariante 2 impide que un camino tenga rojos consecutivos. Combinadas, la diferencia entre el camino más largo y el más corto es a lo sumo 2× → **altura máxima = 2·log₂(n+1) → O(log n) garantizado siempre**, no en promedio.

Cuando un insert viola una invariante, el árbol se repara con dos operaciones O(1):

- **Recoloreo:** cambiar el campo `color` de un nodo
- **Rotación:** relinkear tres nodos para reestructurar un subárbol sin cambiar el orden de las claves

```
Estado inicial:

         usuario:123 [N]          N = negro, R = rojo
        /               \
usuario:100 [R]     usuario:150 [R]

Insertar "usuario:200":
  1. Navegar como BST normal → posición: hijo derecho de usuario:150
  2. Nuevo nodo nace ROJO (siempre)

         usuario:123 [N]
        /               \
usuario:100 [R]     usuario:150 [R]
                              \
                          usuario:200 [R]  ← viola invariante 2:
                                             dos rojos consecutivos

  Reparación (caso: el tío usuario:100 es rojo):
    → recolorear: usuario:100 [N], usuario:150 [N]
    → el abuelo pasaría a rojo, pero es la raíz → se mantiene negro (invariante 1)

         usuario:123 [N]
        /               \
usuario:100 [N]     usuario:150 [N]
                              \
                          usuario:200 [R]   ✓ las tres invariantes se cumplen
```

Recorrido in-order (visitar subárbol izquierdo → nodo actual → subárbol derecho):

```
  usuario:100 → usuario:123 → usuario:150 → usuario:200   ← orden ascendente
```

Este recorrido es O(n) y produce las claves ya ordenadas, lo que permite escribir el SSTable en un solo pasada secuencial.

- Insert / Lookup: O(log n) **garantizado** (no promedio)
- Recorrido in-order: O(n)

---

**Skip List (lista de saltos) — RocksDB, Cassandra**

Estructura puramente en RAM, no en disco. La base es una lista enlazada simple con todos los elementos en orden ascendente (nivel 0). Sobre esa base se construyen listas enlazadas adicionales (nivel 1, nivel 2, nivel 3…) que conectan solo un subconjunto de los nodos, creando atajos para saltar sobre varios elementos de una vez durante la búsqueda.

Lo fundamental para entender la estructura: **no existen copias del dato en distintas listas**. Cada nodo es una única struct en RAM con su clave, su valor, y un arreglo de punteros `forward`. `forward[i]` apunta al siguiente nodo que también participa en el nivel `i`. Si un nodo solo participa en los niveles 0 y 1, su arreglo tiene 2 punteros. Si participa en los niveles 0, 1, 2 y 3, tiene 4 punteros. Los "niveles" en el diagrama no son listas separadas — son las vistas que se obtienen al seguir solo los punteros `forward[i]` de cada nodo.

```
struct SLNode {
    key:     []byte
    value:   []byte
    forward: []*SLNode  // forward[i] = siguiente nodo en el nivel i
}
```

Cuántos niveles tiene un nodo se decide **aleatoriamente al insertarlo**: 50% de probabilidad de quedarse solo en nivel 0, 25% de tener niveles 0-1, 12.5% de tener niveles 0-2, etc. Esta distribución aleatoria garantiza en promedio que cada nivel tenga ~50% de los nodos del nivel anterior — sin ningún rebalanceo explícito.

Ejemplo con 7 nodos (las letras son claves de un carácter — claves arbitrarias elegidas para que el diagrama quepa, podrían ser cualquier string):

Punteros concretos de cada nodo:

```
Nodo "A":  forward = [ →C,  →C,  →F,  →P ]   (participa en niveles 0,1,2,3)
                        L0   L1   L2   L3
Nodo "C":  forward = [ →F,  →F ]               (participa en niveles 0,1)
                        L0   L1
Nodo "F":  forward = [ →H,  →H,  →K ]          (participa en niveles 0,1,2)
                        L0   L1   L2
Nodo "H":  forward = [ →K,  →K ]               (participa en niveles 0,1)
                        L0   L1
Nodo "K":  forward = [ →M,  →P,  →P ]          (participa en niveles 0,1,2)
                        L0   L1   L2
Nodo "M":  forward = [ →P ]                    (participa solo en nivel 0)
                        L0
Nodo "P":  forward = [ nil, nil, nil, nil ]    (fin de lista en todos los niveles)
```

Vista lógica — cada columna vertical es el mismo nodo con múltiples punteros:

```
nivel 3:  [A]─────────────────────────────────────────[P]
           │                                           │
nivel 2:  [A]───────────[F]────────────[K]────────────[P]
           │             │              │              │
nivel 1:  [A]────[C]────[F]────[H]────[K]────────────[P]
           │      │      │      │      │              │
nivel 0:  [A]──[C]──[F]──[H]──[K]──[M]──[P]    ← lista base, todos los nodos
```

Buscar "H" (se empieza siempre en el nivel más alto disponible):

```
Posición: nodo A, nivel 3
  A.forward[3] = P  →  "P" > "H"  →  no avanzar, bajar a nivel 2

Posición: nodo A, nivel 2
  A.forward[2] = F  →  "F" < "H"  →  avanzar a F

Posición: nodo F, nivel 2
  F.forward[2] = K  →  "K" > "H"  →  no avanzar, bajar a nivel 1

Posición: nodo F, nivel 1
  F.forward[1] = H  →  "H" == "H"  →  encontrado ✓

Total: 4 comparaciones en lugar de 6 (recorrer toda la lista base)
```

- Insert / Lookup: O(log n) promedio
- El nivel 0 ya está ordenado → recorrido lineal O(n) para el flush, igual que red-black tree
- Ventaja sobre red-black tree: no requiere rebalanceo. El insert solo asigna punteros en el nuevo nodo y actualiza los punteros de sus predecesores por nivel. Esto permite implementaciones **lock-free** usando CAS (compare-and-swap) sobre los punteros individuales, sin bloquear la estructura entera. RocksDB usa esta propiedad para escrituras concurrentes en la MemTable.

---

#### Estructura 2 — SSTable en disco (inmutable)

Un SSTable (Sorted String Table) es un archivo plano en disco con claves ordenadas. Es **inmutable**: una vez escrito, nunca se modifica. Si una clave se actualiza, la nueva versión va a la MemTable activa y produce un SSTable nuevo en L0. Las dos versiones del dato coexisten en disco hasta que la compactación las fusiona y descarta la más antigua.

Estructura física del archivo en disco:

```
SSTable-001.sst:

byte 0:
┌──────────────────────────────────────────────────────────┐
│  DATA BLOCK 0  (~4KB, comprimido con snappy o lz4)       │
│    "usuario:100" → "Carlos"                              │
│    "usuario:115" → "Beatriz"                             │
│    "usuario:123" → "Ana"                                 │
└──────────────────────────────────────────────────────────┘
byte 4096:
┌──────────────────────────────────────────────────────────┐
│  DATA BLOCK 1  (~4KB, comprimido)                        │
│    "usuario:130" → "Diego"                               │
│    "usuario:145" → "Elena"                               │
│    "usuario:150" → "Luis"                                │
└──────────────────────────────────────────────────────────┘
  ... más DATA BLOCKs ...
byte 8192:
┌──────────────────────────────────────────────────────────┐
│  INDEX BLOCK  (sin comprimir, se carga completo en RAM)  │
│  ┌─────────────────────┬──────────────────────────────┐  │
│  │  primera clave      │  offset del bloque en disco  │  │
│  ├─────────────────────┼──────────────────────────────┤  │
│  │  "usuario:100"      │  0                           │  │
│  │  "usuario:130"      │  4096                        │  │
│  └─────────────────────┴──────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
byte 8320:
┌──────────────────────────────────────────────────────────┐
│  BLOOM FILTER  (se carga en RAM — ver Estructura 3)      │
└──────────────────────────────────────────────────────────┘
byte 8450:
┌──────────────────────────────────────────────────────────┐
│  FOOTER  (tamaño fijo — siempre los últimos N bytes del  │
│           archivo, posición calculable sin leer nada más)│
│    offset del INDEX BLOCK   → 8192                       │
│    offset del BLOOM FILTER  → 8320                       │
│    magic number de verificación                          │
└──────────────────────────────────────────────────────────┘
```

**Offset** = número de bytes desde el inicio del archivo. El sistema operativo tiene la llamada `pread(fd, buffer, size, offset)` que salta directamente a esa posición en disco y lee `size` bytes — sin leer los bytes anteriores. Así, conocer el offset de un data block permite leer exactamente ese bloque con un solo acceso a disco.

El motor abre el SSTable leyendo primero el footer: está en `tamaño_archivo - sizeof(footer)` bytes del inicio, posición calculable sin leer nada. El footer da los offsets del index block y el bloom filter. Ambos se cargan completos en RAM. A partir de ahí, cualquier búsqueda hace **exactamente 1 acceso a disco** (al data block correspondiente).

Buscar "usuario:145":

```
1. Bloom filter (RAM)
     → "probablemente sí" → continuar

2. Index block (RAM): búsqueda binaria por la última entrada con first_key ≤ target
     entrada 0: "usuario:100" ≤ "usuario:145"  → candidato
     entrada 1: "usuario:130" ≤ "usuario:145"  → mejor candidato
     no hay entrada 2
     → offset = 4096

3. pread(offset=4096, size=~4KB) → descomprimir DATA BLOCK 1
   → buscar "usuario:145" → "Elena"  ✓
```

---

#### Estructura 3 — Bloom filter (RAM, serializado al final del SSTable)

Una lectura en LSM-tree puede tener que revisar varios SSTables. Sin ayuda, cada uno requeriría un acceso a disco aunque la clave no esté. El bloom filter descarta SSTables definitivamente antes de hacer ningún I/O.

**Estructura:** un arreglo contiguo de M bits en RAM (todos en 0 al inicio), más K funciones de hash independientes. No guarda las claves — solo sus "huellas" en el arreglo de bits. Se construye al crear el SSTable procesando cada clave, y se serializa como bytes al final del archivo `.sst` para recargarse en RAM cuando se abre el archivo.

**Insertar una clave** (se hace al construir el SSTable, antes de escribirlo):

```
Arreglo de 12 bits, K=3 funciones de hash:
posición:  [ 0   1   2   3   4   5   6   7   8   9  10  11 ]
bits:      [ 0   0   0   0   0   0   0   0   0   0   0   0 ]

Insertar "usuario:123":
  h1("usuario:123") = 2
  h2("usuario:123") = 5
  h3("usuario:123") = 9
  → poner en 1 las posiciones 2, 5, 9

posición:  [ 0   1   2   3   4   5   6   7   8   9  10  11 ]
bits:      [ 0   0   1   0   0   1   0   0   0   1   0   0 ]
                    ↑           ↑               ↑

Insertar "usuario:100":
  h1("usuario:100") = 1
  h2("usuario:100") = 5    ← ya estaba en 1, no cambia
  h3("usuario:100") = 11
  → poner en 1 las posiciones 1, 11

posición:  [ 0   1   2   3   4   5   6   7   8   9  10  11 ]
bits:      [ 0   1   1   0   0   1   0   0   0   1   0   1 ]
                ↑                                       ↑
```

**Consultar una clave** (al momento de leer, antes de tocar el disco):

```
¿Está "usuario:999"?
  h1("usuario:999") = 3  →  bits[3] = 0
  → Con un solo bit en 0, DEFINITIVAMENTE NO ESTÁ.
  → No se abre el data block. Cero I/O de disco.

¿Está "usuario:150"?
  h1("usuario:150") = 2  →  bits[2] = 1
  h2("usuario:150") = 5  →  bits[5] = 1
  h3("usuario:150") = 11 →  bits[11] = 1
  → Todos en 1 → "probablemente sí" → hay que leer el SSTable.

  "usuario:150" nunca fue insertado en este bloom filter.
  Sus hashes apuntan a posiciones que quedaron en 1 por otras claves.
  Esto es un falso positivo.
```

La garantía es asimétrica:

- **Falso negativo: imposible.** Una clave que sí existe tiene sus K posiciones en 1 desde que fue insertada. Nunca se borran bits. Siempre responderá "sí".
- **Falso positivo: posible** (~1% con 10 bits/clave). Los K bits de una clave inexistente pueden estar todos en 1 por coincidencia con otras claves. Hay que leer el data block para confirmar.

Con 10 bits/clave, un SSTable de 64MB (~320.000 claves de 20 bytes promedio) tiene un bloom filter de ~400KB en RAM.

---

#### Niveles — la jerarquía que da el nombre "tree"

Los SSTables se organizan en **niveles numerados** (L0, L1, L2…). Los niveles son una agrupación lógica: físicamente todos los SSTables son archivos en el mismo sistema de archivos del disco, y el motor lleva un archivo de metadatos (manifiesto) que registra a qué nivel pertenece cada uno. Cada nivel tiene un límite de tamaño total ~10× mayor que el anterior. Cuando un nivel supera su límite, el motor ejecuta la compactación hacia el nivel siguiente.

```
RAM:
  MemTable  (red-black tree o skip list)
      │
      │  flush: recorrido in-order de la MemTable
      │         → escribe un archivo SSTable nuevo en L0
      ▼
L0   ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
     │ SST-001  │ │ SST-002  │ │ SST-003  │ │ SST-004  │   4-8 archivos típico
     │          │ │          │ │          │ │          │
     │ rango:   │ │ rango:   │ │ rango:   │ │ rango:   │   rangos SOLAPADOS:
     │ A, C, F  │ │ B, C, G  │ │ A, D, H  │ │ E, F, K  │   "C" puede estar en
     └──────────┘ └──────────┘ └──────────┘ └──────────┘   SST-001 y SST-002
          │
          │  compactación: merge-sort de todos los SSTables de L0 entre sí
          │  → resuelve versiones duplicadas de una misma clave, descarta las viejas
          │  → escribe nuevos SSTables donde los rangos no se solapan
          │  → borra los SSTables originales de L0
          ▼
L1   ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   ~10MB total
     │ SST-L1-1 │ │ SST-L1-2 │ │ SST-L1-3 │ │ SST-L1-4 │
     │          │ │          │ │          │ │          │   rangos SIN solapamiento:
     │ rango:   │ │ rango:   │ │ rango:   │ │ rango:   │   "C" solo puede estar
     │  A – D   │ │  E – H   │ │  I – M   │ │  N – Z   │   en SST-L1-1
     └──────────┘ └──────────┘ └──────────┘ └──────────┘
          │
          │  compactación L1 → L2
          ▼
L2   (mismo patrón, rangos sin solapamiento, ~100MB total)
L3   (mismo patrón, ~1GB total)
...
```

**Por qué L0 tiene solapamientos y L1+ no:** cada SSTable de L0 se crea de un flush de la MemTable independiente. Dos flushs sucesivos pueden haber recibido escrituras de rangos de claves que se cruzan — no hay coordinación entre ellos. La compactación L0→L1 es la primera vez que esos archivos se fusionan entre sí: hace un merge-sort de todos, resuelve duplicados, y produce un conjunto de SSTables donde cada rango de claves vive en exactamente un archivo.

---

#### Cómo funciona una lectura

```
GET "usuario:123"

1. MemTable (RAM)                  búsqueda en red-black tree o skip list → O(log n)
                                   si está → retorna inmediatamente

2. Cada SSTable en L0              hay que revisar TODOS — sus rangos se solapan,
                                   no se puede descartar sin comprobar
   por cada SSTable:
     bloom filter (RAM)          → si algún bit es 0: skip, cero I/O
     index block (RAM)           → búsqueda binaria → offset del data block
     pread al offset              → leer y descomprimir ~4KB → buscar la clave
                                   si está → retorna

3. L1                              búsqueda binaria sobre los rangos del nivel
                                   → exactamente 1 SSTable candidato
   bloom filter → index block → pread → buscar
   si está → retorna

4. L2, L3, …                      igual que L1, 1 SSTable candidato por nivel

Peor caso: clave inexistente, todos los bloom filters dan falso positivo
  → una lectura de disco por cada SSTable de L0 + una por cada nivel L1, L2, L3…
```

---

#### Cómo funciona una escritura

```
SET "usuario:123" = "Ana"

1. Insert en MemTable (RAM)        O(log n). La escritura termina aquí para el cliente.

   ── en background ──────────────────────────────────────────────────────────

2. Cuando la MemTable se llena (~64MB):
   recorrido in-order → escribe un archivo SSTable nuevo en L0
   (la MemTable está ordenada → el recorrido es O(n), sin sort)

3. Cuando L0 acumula ~4-8 SSTables:
   merge-sort de todos los SSTables de L0 + SSTables de L1 con rangos solapados
   → escribe nuevos SSTables en L1 con rangos sin solapamiento
   → descarta versiones viejas de claves duplicadas
   → borra los archivos originales de L0 y L1

4. Cuando L1 supera su límite de tamaño:
   mismo proceso: L1→L2, luego L2→L3… en cascada hacia abajo
```

---

#### Comparación B-tree vs LSM-tree

| Aspecto             | B-tree                    | LSM-tree                                                             |
| ------------------- | ------------------------- | -------------------------------------------------------------------- |
| Estructura en RAM   | Buffer pool (páginas)     | MemTable (RB-tree / skip list)                                       |
| Estructura en disco | Árbol de páginas B+       | Archivos SSTable planos por nivel                                    |
| Escritura           | Modifica página en disco  | Insert en RAM, sin I/O inmediato                                     |
| Lectura puntual     | O(log n), 1 lookup        | MemTable + todos en L0 + 1 por nivel L1+                             |
| Lectura de rango    | Hojas enlazadas, rápida   | SSTables ordenados, razonable                                        |
| Updates/Deletes     | Modifica el dato in-place | Nueva versión en MemTable; la vieja se descarta durante compactación |

---

### 2.3 Almacenamiento columnar

**Optimizado para:** consultas analíticas (OLAP) que agregan muchas filas usando pocas columnas  
**Lo usan:** ClickHouse, Amazon Redshift, Google BigQuery, Apache Parquet, DuckDB

#### El problema que resuelve

En almacenamiento por filas (OLTP), para calcular `AVG(salario)` sobre 10 millones de empleados, el motor debe leer todas las filas completas — incluyendo nombre, dirección, teléfono — solo para llegar a la columna salario.

```
Almacenamiento por fila (PostgreSQL, MySQL):
  Disco: [id1,nombre1,edad1,salario1,dept1] [id2,nombre2,edad2,salario2,dept2] ...

  Para AVG(salario): lee TODO lo anterior → 95% de datos leídos son basura

Almacenamiento columnar (ClickHouse):
  Disco: [id1,id2,id3,...] [nombre1,nombre2,...] [salario1,salario2,salario3,...]

  Para AVG(salario): lee SOLO el archivo de salarios → 100% de datos útiles
```

#### Compresión extrema

Los valores de la misma columna son del mismo tipo y frecuentemente similares entre sí. Esto permite compresión agresiva:

```
Columna "departamento" (10M filas, solo 5 valores posibles):
  Sin comprimir: 10,000,000 × 20 bytes = 200 MB

  Dictionary encoding:
    Diccionario: {0:"Ventas", 1:"RRHH", 2:"IT", 3:"Legal", 4:"Finanzas"}
    Datos:       [0,0,1,2,0,3,2,2,0,4,0,0,1,...] ← 1 byte por fila
    Comprimido:  10,000,000 × 1 byte = 10 MB → compresión 20x

Columna "fecha" (serie temporal ordenada):
  Sin comprimir: 10M × 8 bytes = 80 MB

  Delta encoding:
    Base: 2024-01-01
    Deltas: [+0s, +1s, +1s, +1s, +2s, ...] ← enteros pequeños
    Comprimido: ~5-10 MB → compresión 8-16x
```

ClickHouse puede alcanzar compresiones de 5x–20x sobre los datos crudos, lo que implica menos I/O de disco y más datos en caché.

#### Predicate pushdown y projection pushdown

```sql
SELECT departamento, AVG(salario)
FROM empleados
WHERE año = 2024
GROUP BY departamento;
```

- **Projection pushdown**: solo se leen las columnas `departamento`, `salario`, `año` — el resto nunca se toca
- **Predicate pushdown**: dentro del archivo de `año`, se aplica el filtro `año = 2024` antes de descomprimir datos completos → saltea row groups donde el min/max no incluye 2024

#### Por qué es terrible para OLTP

Insertar una sola fila nueva requiere actualizar N archivos (uno por columna). Actualizar el salario de un empleado requiere encontrar su posición en el archivo de salarios (que puede tener 10M valores) y reescribir el bloque. La arquitectura no fue diseñada para esto.

---

### 2.4 Inverted index (índice invertido)

**Optimizado para:** búsqueda de texto por relevancia, full-text search  
**Lo usan:** Elasticsearch, Solr, Meilisearch, Sphinx; también como feature dentro de PostgreSQL (pg_trgm, tsvector)

#### El problema que resuelve

Un B-tree puede responder `WHERE email = 'ana@email.com'` (igualdad exacta) o `WHERE precio < 100` (rango numérico). Pero no puede responder eficientemente "¿qué documentos contienen las palabras 'base' y 'datos' cerca una de otra, ordenados por relevancia?".

#### Estructura

```
Documentos originales:
  Doc 1: "PostgreSQL es una base de datos robusta"
  Doc 2: "MySQL es una base de datos popular para web"
  Doc 3: "Las bases de datos NoSQL escalan horizontalmente"

Pipeline de análisis (tokenización + normalización):
  "PostgreSQL es una base de datos robusta"
  → [postgresql, base, datos, robusta]  ← stop words removidas, lowercase

Inverted index construido:
  "base"         → [Doc1(pos:4), Doc2(pos:4), Doc3(pos:2)]
  "datos"        → [Doc1(pos:5), Doc2(pos:5), Doc3(pos:3)]
  "postgresql"   → [Doc1(pos:1)]
  "mysql"        → [Doc2(pos:1)]
  "nosql"        → [Doc3(pos:4)]
  "robusta"      → [Doc1(pos:6)]
  "popular"      → [Doc2(pos:5)]
  "escalan"      → [Doc3(pos:5)]
  "horizontalmente" → [Doc3(pos:6)]
```

#### Cómo funciona una búsqueda

```
Query: "base de datos PostgreSQL"
→ tokens: [base, datos, postgresql]

Paso 1 — Lookup en inverted index:
  "base"        → [Doc1, Doc2, Doc3]
  "datos"       → [Doc1, Doc2, Doc3]
  "postgresql"  → [Doc1]

Paso 2 — Intersección:
  Docs que tienen los 3 términos: Doc1

Paso 3 — Ranking (TF-IDF o BM25):
  "postgresql" aparece en solo 1 doc de 3 → alta rareza → alto peso
  "base" aparece en 3 docs → baja rareza → menor peso
  Doc1 tiene alta frecuencia de términos relevantes → score alto

Resultado: Doc1 con score 0.95
```

#### Segmentos — la unidad inmutable

Elasticsearch y Lucene no actualizan el índice in-place. Cada batch de documentos nuevos crea un **segmento** nuevo (un inverted index inmutable). Periódicamente, los segmentos se fusionan (similar a la compactación del LSM-tree).

```
Tiempo 1: [Segmento-1: Doc1, Doc2]
Tiempo 2: [Segmento-1: Doc1, Doc2] + [Segmento-2: Doc3, Doc4]
Tiempo 3: merge → [Segmento-merged: Doc1, Doc2, Doc3, Doc4]
```

Esto explica por qué los documentos recién indexados pueden no aparecer inmediatamente en búsquedas — el segmento nuevo aún no está disponible para búsqueda hasta que se hace un **refresh** (por defecto cada 1 segundo en Elasticsearch).

---

### 2.5 Hash tables en memoria

**Optimizado para:** acceso O(1) a valores por clave exacta  
**Lo usan:** Redis, Memcached, etcd (parcialmente), cualquier motor con índices hash

#### Estructura

```
Hash table con 8 buckets:

  hash("usuario:123") % 8 = 3

  Bucket 0: → vacío
  Bucket 1: → [key:"session:abc", value:"..."]
  Bucket 2: → vacío
  Bucket 3: → [key:"usuario:123", value:"Ana"] → [key:"usuario:131", value:"Sol"]
  Bucket 4: → [key:"contador:visitas", value:42]
  ...
  Bucket 7: → vacío

  GET usuario:123:
    hash("usuario:123") % 8 = 3
    Ir a bucket 3
    Recorrer linked list hasta encontrar key exacta
    → "Ana"  (O(1) amortizado)
```

Redis usa esta estructura para sus strings y hashes, pero además implementa estructuras más sofisticadas encima de ella.

---

## 3. El espectro de consistencia {#consistencia}

### 3.1 ACID — las garantías de los motores transaccionales

ACID no es un estándar único — cada propiedad tiene grados de implementación. Ver `guia_transacciones_db.md` para el deep dive. Aquí el resumen orientado a comparación entre motores:

**Atomicidad:** toda la transacción ocurre o ninguna parte ocurre.

```sql
BEGIN;
UPDATE cuentas SET saldo = saldo - 100 WHERE id = 1;
UPDATE cuentas SET saldo = saldo + 100 WHERE id = 2;
COMMIT;
-- Si falla cualquier UPDATE, ninguno se aplica
```

**Consistencia:** la transacción lleva la base de datos de un estado válido a otro estado válido (según las constraints definidas).

**Isolation (aislamiento):** el nivel más discutido. No existe un solo nivel — hay un espectro:

```
READ UNCOMMITTED → READ COMMITTED → REPEATABLE READ → SERIALIZABLE
   (más rápido,                                        (más lento,
    más anomalías)                                      sin anomalías)
```

**Durabilidad:** una transacción commiteada sobrevive crashes. Se logra con WAL/redo log.

### 3.2 BASE — el modelo de los sistemas distribuidos

BASE no es un acrónimo elegante sino la contraparte pragmática de ACID para sistemas que priorizan disponibilidad y escala:

- **B**asically Available: el sistema responde aunque algunos nodos fallen
- **S**oft state: el estado puede cambiar con el tiempo sin input externo (durante sincronización)
- **E**ventually consistent: eventualmente todos los nodos convergen al mismo estado

#### Qué significa "eventually consistent" en la práctica

```
Usuario A escribe: { usuario:123, nombre: "Ana García" }
  → escribe en Nodo-1 (disponible)
  → Nodo-1 replica a Nodo-2 en background (delay: 50ms)

Usuario B lee usuario:123 desde Nodo-2 durante esos 50ms:
  → ve nombre: "Ana" (versión anterior)
  → esto es correcto en un sistema BASE

Después de 50ms:
  → Nodo-2 recibe la replicación
  → Ahora ambos nodos tienen "Ana García"
  → El sistema convergió — "eventually consistent"
```

Esto es aceptable para muchos casos: mostrar el nombre de un usuario, contar likes, mostrar inventario aproximado. **No** es aceptable para: saldos bancarios, reservas de asientos, inventario que no puede quedar negativo.

### 3.3 El teorema CAP

Esta es la pieza que la sección 1 dejó pendiente. Ahí, la Capa 2 se bifurcaba entre consenso (conservas ACID, pagas con latencia) y peer-to-peer (nunca esperas, pagas con consistencia eventual), y se dijo que esa horquilla era "el teorema CAP aplicado en concreto". Aquí está el porqué, formalizado.

En un sistema distribuido con múltiples nodos, solo puedes garantizar **dos de tres** propiedades simultáneamente cuando ocurre una falla de red:

```
         Consistency
         (todos los nodos ven
          los mismos datos)
               △
              / \
             /   \
            /     \
           /  CAP  \
          /         \
         ─────────────
Availability         Partition
(el sistema siempre   Tolerance
 responde)           (funciona aunque
                      la red falle)
```

La **partición de red** (P) siempre puede ocurrir en sistemas distribuidos. Por eso la elección real es entre **CP** (sacrifica disponibilidad) o **AP** (sacrifica consistencia):

```
Escenario: la red entre Nodo-A y Nodo-B se corta

  ┌────────┐   ╳╳╳╳╳╳   ┌────────┐
  │ Nodo-A │  red rota   │ Nodo-B │
  └────────┘             └────────┘

Sistema CP (e.g., CockroachDB, HBase, ZooKeeper):
  → Nodo-B deja de aceptar escrituras hasta restaurar la red
    (o hasta que su parte del rango recupere mayoría de votos)
  → Consistente, pero no disponible durante la partición
  → Es la misma rama de consenso que ya viste en la Capa 2 y en
    "Acoplamientos rotos" (Distribuido + ACID) — el mecanismo
    exacto está en la sección 4.5 y 4.6

Sistema AP (e.g., Cassandra, DynamoDB, CouchDB):
  → Ambos nodos siguen aceptando escrituras por separado
  → Disponible, pero pueden tener datos diferentes
  → Cuando la red se restaura, se resuelven conflictos (last-write-wins, vector clocks, etc.)
  → Es la misma rama de peer-to-peer que ya viste en la Capa 2 y en
    "Acoplamientos naturales" (LSM-tree + Peer-to-peer + Eventual) —
    el mecanismo exacto está en la sección 4.7
```

### 3.4 PACELC — la extensión de CAP

CAP solo describe el comportamiento durante una partición de red. PACELC extiende esto:

```
  Si hay Partición → elige entre Availability o Consistency (CAP)
  Else (sin partición) → elige entre Latency o Consistency

  PACELC = Partition → Availability/Consistency, Else Latency/Consistency
```

Esto importa porque incluso sin fallas, un sistema puede elegir:

- Responder rápido con datos que podrían estar ligeramente desactualizados (latencia baja, consistencia relajada)
- Esperar confirmación de todos los réplicas antes de responder (latencia alta, consistencia fuerte)

| Motor               | Partición | Sin partición                    |
| ------------------- | --------- | -------------------------------- |
| DynamoDB (eventual) | AP        | EL (eventual, baja latencia)     |
| DynamoDB (strong)   | CP        | EC (consistente, mayor latencia) |
| Cassandra (ONE)     | AP        | EL                               |
| Cassandra (QUORUM)  | CP        | EC                               |
| PostgreSQL          | CP        | EC                               |
| CockroachDB         | CP        | EC                               |

Nótese que PostgreSQL y CockroachDB comparten la misma fila (CP / EC) aunque uno viva en un solo nodo y el otro esté distribuido en varios: PACELC describe el trade-off, no la arquitectura que hay detrás. Este es justo el precio en rendimiento que la Capa 3 de la sección 1 prometía y no desarrollaba: pedir EC (consistencia fuerte, incluso sin partición) es lo que hace que CockroachDB pague latencia extra en cada escritura, mientras que un sistema EL como Cassandra en modo ONE nunca la paga.

### 3.5 Niveles de consistencia ajustables (Cassandra como ejemplo)

Cassandra permite elegir el nivel de consistencia por query, no por configuración global:

```
Cluster de 5 nodos. Datos replicados en 3 nodos (RF=3).

Niveles de consistencia:

ONE:    escribe/lee en 1 nodo → respuesta inmediata, puede estar desactualizado
QUORUM: escribe/lee en 3 nodos (mayoría) → más lento, pero consistente
ALL:    escribe/lee en 5 nodos → máxima consistencia, cualquier nodo caído falla la query
LOCAL_QUORUM: quorum dentro del datacenter local → balance entre consistencia y latencia

Regla para consistencia fuerte:
  write_level + read_level > replication_factor
  QUORUM + QUORUM = 3 + 3 > 3 → consistente
  ONE + ONE = 1 + 1 < 3 → potencialmente stale
```

---

## 4. Modelos de distribución {#distribucion}

### 4.1 Un nodo (single node)

El modelo más simple. Toda la carga en una sola máquina.

```
  ┌──────────────────────┐
  │   Servidor único     │
  │  CPU │ RAM │ Disco   │
  │  PostgreSQL / Redis  │
  └──────────────────────┘
         ▲
   todas las queries
```

**Límite real:** en 2024, un servidor moderno puede tener 96 cores, 6TB de RAM y 100TB de NVMe SSD. Para la mayoría de aplicaciones, escalar verticalmente (más hardware) es más simple y barato que distribuir.

**Cuándo es suficiente:** la mayoría de startups y aplicaciones medianas nunca necesitan más que esto bien tuneado.

### 4.2 Primary-replica (maestro-esclavo)

```
  ┌──────────────┐
  │   Primary    │ ← todas las escrituras
  │  (lectura +  │
  │   escritura) │
  └──────┬───────┘
         │ replicación asíncrona o síncrona
    ┌────┴────┐
    ▼         ▼
┌────────┐ ┌────────┐
│Replica1│ │Replica2│ ← lecturas distribuidas aquí
└────────┘ └────────┘
```

**Replicación asíncrona** (default en PostgreSQL, MySQL):

- Primary confirma el commit sin esperar a réplicas
- Réplicas aplican los cambios con cierto delay (replication lag)
- Si el primary falla antes de replicar, se puede perder la última transacción

**Replicación síncrona** (PostgreSQL `synchronous_commit = on`):

- Primary espera confirmación de al menos 1 réplica antes de confirmar
- Sin pérdida de datos en failover
- Mayor latencia en escrituras

**Replication lag:** cuánto tiempo tarda una réplica en tener los cambios del primary. Típicamente milisegundos, pero puede crecer bajo carga alta. Implicación: si lees de una réplica, puedes ver datos hasta N milisegundos desactualizados.

### 4.3 Multi-primary

Múltiples nodos aceptan escrituras simultáneamente.

```
  ┌──────────┐   ←→  ┌──────────┐
  │ Primary-1│       │ Primary-2│
  │ escribe  │ ←───→ │  escribe │
  └──────────┘ sync  └──────────┘
```

El problema central: si Primary-1 y Primary-2 modifican el mismo registro al mismo tiempo, hay un conflicto. Soluciones:

- **Last-write-wins (LWW):** gana la escritura con timestamp más reciente. Simple pero puede perder datos si los relojes no están sincronizados.
- **Consensus protocol (Raft/Paxos):** todos los nodos acuerdan un orden de operaciones antes de aplicarlas. Correcto pero introduce latencia de red.

**Dónde encaja frente a la escalera de la Capa 2:** multi-primary no es un peldaño nuevo — es una variante del lado de **réplica sin partición** (como primary-replica), solo que con más de un escritor a la vez en vez de uno. LWW es la resolución de conflictos propia del lado AP (la misma familia que consistencia eventual — de hecho es el mecanismo que la Capa 3 cita como ejemplo de "eventual"); un protocolo de consenso aquí es la misma jugada del lado CP que ya se vio con NewSQL, aplicada a réplicas completas en vez de a particiones.

### 4.4 Sharding (particionamiento horizontal)

Los datos se dividen entre múltiples nodos. Cada nodo guarda un subconjunto.

```
Tabla usuarios, 100M filas, sharding por usuario_id:

  usuario_id 1-25M      → Shard-1 (Nodo A)
  usuario_id 25M-50M    → Shard-2 (Nodo B)
  usuario_id 50M-75M    → Shard-3 (Nodo C)
  usuario_id 75M-100M   → Shard-4 (Nodo D)

Query: SELECT * FROM usuarios WHERE id = 30000000
  → Router calcula: 30M está en Shard-2 → va directo a Nodo B

Query: SELECT COUNT(*) FROM usuarios WHERE ciudad = 'CDMX'
  → debe ir a los 4 shards → scatter-gather → lento
```

**Sharding por rango** (como arriba): fácil de predecir qué nodo tiene el dato, pero vulnerable a hotspots (si los usuarios nuevos siempre tienen IDs altos, Shard-4 recibe toda la carga).

**Sharding por hash**: `hash(usuario_id) % N_shards`. Distribuye carga uniformemente, pero las queries de rango ahora van a todos los shards.

**Consistent hashing** (Cassandra, DynamoDB): los nodos se distribuyen en un "anillo". Cada nodo es responsable de un arco del anillo. Agregar/quitar nodos solo rebalancea una fracción de los datos.

```
Anillo de consistent hashing (4 nodos):

             0
          ╱     ╲
      Nodo-D   Nodo-A
        │           │
      256         64
        │           │
      Nodo-C   Nodo-B
          ╲     ╱
            192

  hash(clave) = 100 → entre 64 y 192 → Nodo-B
  hash(clave) = 200 → entre 192 y 256 → Nodo-C
```

### 4.5 NewSQL — SQL distribuido con ACID

El objetivo: combinar la escala horizontal del sharding con las garantías ACID del SQL tradicional.

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Nodo-1    │    │   Nodo-2    │    │   Nodo-3    │
│  Shard A    │    │  Shard B    │    │  Shard C    │
│  (líder)    │    │  (líder)    │    │  (líder)    │
└──────┬──────┘    └──────┬──────┘    └──────┬──────┘
       │                  │                  │
       └──────────────────┴──────────────────┘
              Protocolo Raft/Multi-Paxos
              (consenso distribuido)
```

**Raft simplificado:**

1. Un nodo es elegido **líder** para cada shard (o rango de claves)
2. El líder recibe todas las escrituras para su rango
3. El líder replica a la mayoría de seguidores antes de confirmar
4. Si el líder falla, los seguidores eligen un nuevo líder

Costo: cada escritura requiere ronda de red para consenso → latencia mayor que PostgreSQL de un solo nodo.

**Motores NewSQL:** CockroachDB, TiDB, YugabyteDB, Google Spanner (propietario)

### 4.6 Two-Phase Commit (2PC): cuando una transacción cruza varios shards

Raft, en 4.5, resuelve el consenso **dentro de un shard**: las réplicas de ese rango de claves se ponen de acuerdo entre sí sobre el orden de las escrituras. Pero ¿qué pasa cuando una sola transacción necesita escribir en Shard-A y en Shard-B al mismo tiempo, cada uno con su propio grupo de Raft, sin relación entre sí? Ahí es donde entra 2PC (Two-Phase Commit): el protocolo que coordina un commit atómico entre varios grupos independientes.

```
        Coordinador              Shard-A                Shard-B
             │                       │                      │
             │                    tiempo                    │
             │                       ▼                      │
  FASE 1     │──── PREPARE ─────────>│                      │
  (preparar) │──── PREPARE ──────────┼─────────────────────>│
             │                       │ bloquea sus filas    │ bloquea sus filas
             │<─── "sí, lista" ──────│                      │
             │<─── "sí, lista" ──────┼──────────────────────│
             │                       │                      │
             │   todos dijeron "sí" → se puede confirmar    │
             │                       │                      │
  FASE 2     │──── COMMIT ──────────>│                      │
  (confirmar)│──── COMMIT ───────────┼─────────────────────>│
             │                       │ aplica y libera      │ aplica y libera
             │                       │      locks           │      locks
             ▼                       ▼                      ▼
                          transacción completa

  ── Camino alterno: algún shard responde "no" o no responde a tiempo ──

             │<─── "no puedo" ───────┼──────────────────────│
             │                       │                      │
  ABORT      │──── ABORT ───────────>│                      │
             │──── ABORT ─────────────┼────────────────────>│
             │                       │ deshace lo           │ deshace lo
             │                       │   preparado          │   preparado
```

**El costo real:** una transacción que cruza shards paga dos rondas de red distintas, no una: la ronda de consenso interno de Raft dentro de cada shard (4.5), más la ronda de PREPARE/COMMIT de 2PC entre los shards. Por eso una transacción de un solo shard en CockroachDB, Spanner o TiDB es casi tan rápida como en un motor de nodo único, y una que cruza varios shards siempre es más lenta, sin excepción.

**La misma pieza explica por qué PostgreSQL + Citus no llega tan lejos como CockroachDB.** Citus también usa 2PC para transacciones que cruzan workers, pero cada worker es un PostgreSQL de nodo único, sin el equivalente de Raft coordinándolo internamente. Si un worker se cae justo después de responder "lista" en la fase de PREPARE, no hay un grupo de consenso que resuelva automáticamente qué pasó con esa parte de la transacción, mientras que en CockroachDB el propio grupo de Raft de ese shard sí lo resuelve. Es el mecanismo exacto detrás de lo que ya decía la tabla de fingerprints: PostgreSQL con Citus "pierde parte de esta fuerza" en cuanto una transacción cruza a otro shard.

**Una pieza más, mencionada brevemente:** 2PC solo garantiza el "todo o nada" entre shards, no el orden global entre transacciones que corrieron en shards distintos. Esa parte la resuelven relojes especializados (TrueTime en Spanner, Hybrid Logical Clocks en CockroachDB), que le dan a cada transacción una marca de tiempo confiable para saber qué pasó antes y qué después, incluso entre grupos de Raft que no se conocen entre sí.

#### El costo real en la práctica, y qué hace la industria bancaria al respecto

Todo lo anterior tiene una consecuencia muy concreta: **una transacción distribuida con consistencia fuerte nunca es gratis, y ninguna cantidad de buena ingeniería la vuelve tan barata como una transacción local.** Por eso la industria financiera, que es justo donde más se necesita ACID, casi nunca resuelve esto con "más 2PC" — lo resuelve evitando 2PC en primer lugar, siempre que puede:

- **La mayoría de bancos siguen corriendo su ledger central en un motor de nodo único o primary-replica** (Oracle, DB2, SQL Server, y cada vez más PostgreSQL), no porque no conozcan NewSQL, sino porque un solo servidor bien dimensionado sostiene un volumen de transacciones enorme sin pagar nunca el costo de 2PC. La sección 1 ya lo explicaba: escalar verticalmente sigue siendo más simple y más barato que distribuir, mientras alcance.
- **Cuando sí necesitan escala horizontal, shardean por cliente o por cuenta**, exactamente como discutimos antes, para que una transferencia entre las cuentas de un mismo cliente caiga casi siempre en un solo shard y nunca pague 2PC.
- **Para lo que de verdad cruza fronteras, entre bancos distintos (una transferencia SWIFT o interbancaria), ni siquiera lo intentan como una transacción ACID distribuida.** Es físicamente imposible pedirle a dos bancos distintos que bloqueen filas en tiempo real y esperen a un coordinador común. En su lugar usan el patrón **Saga**: la operación se parte en varios pasos, cada uno una transacción ACID local y corta ("descontar de la cuenta origen", "confirmar recepción en el banco destino"), con un registro durable de cada paso. Si un paso falla a mitad de camino, no se hace rollback de todo como en 2PC: se ejecuta una transacción compensatoria (una reversa) que deshace lo ya hecho. El dinero puede pasar segundos u horas "en tránsito" entre un paso y el siguiente, algo que jamás se toleraría dentro de un solo shard, pero que es exactamente lo que ya viste como aceptable en el lado eventual del espectro: consistencia fuerte donde el costo es bajo, algo más relajado (pero auditable y reversible) donde cruzar la frontera en tiempo real sería imposible o carísimo.

### 4.7 Peer-to-peer: el otro lado de la horquilla

Frente al consenso de 4.5 y 4.6, donde todos los nodos involucrados se ponen de acuerdo antes de confirmar, el peer-to-peer resuelve el mismo problema (cómo coordinar escrituras entre varios nodos) con la decisión contraria: nunca esperar ese acuerdo.

```
        Cliente                Nodo-A                 Nodo-B, Nodo-C...
           │                      │                    (réplicas de la clave)
           │──── escritura ──────>│                          │
           │                      │──── replica ────────────>│
           │<─── confirmación ────│                          │
           │   (no espera a que Nodo-B/C respondan, salvo    │
           │    que se pida QUORUM o ALL — ver sección 3.5)  │
```

El nodo que recibe la escritura no necesita el visto bueno de nadie más antes de confirmar al cliente. Eso es justo lo opuesto a la fase de PREPARE de 2PC. Pero aceptar escrituras sin coordinación previa deja tres huecos abiertos (¿cómo se entera el clúster de quién está vivo?, ¿qué pasa si el nodo dueño de la clave está caído?, ¿cómo se corrige una réplica que se quedó atrás?), y cada uno se resuelve con su propio mecanismo, en su propio momento:

**1. Gossip protocol — cómo el clúster sabe quién sigue vivo, todo el tiempo, en segundo plano**

```
 t=0    Nodo-A ──gossip──> Nodo-C   (vecino elegido al azar)
 t=1              Nodo-C ──gossip──> Nodo-D
 t=2                        Nodo-D ──gossip──> Nodo-B
        ...en pocas rondas, todo el clúster conoce el estado de Nodo-A,
        sin que ningún nodo central lo haya anunciado
```

**2. Hinted handoff — qué pasa si el dueño de la clave está caído justo cuando llega la escritura**

```
 Escritura para la clave K (dueño real: Nodo-B, que está caído)
        │
        ▼
 Nodo-A la acepta en su lugar y guarda un "hint":
        (K, valor, "entregar a Nodo-B en cuanto vuelva")
        │
        ▼
 Nodo-B vuelve a estar disponible
        │
        ▼
 Nodo-A le entrega el hint → Nodo-B ya tiene el dato
```

**3. Read repair — cómo se corrige una réplica atrasada, sin un proceso de sincronización aparte**

```
 Lectura de la clave K con QUORUM (2 de 3 réplicas)
        │
        ├── Réplica-1 responde: "v2" (la más reciente)
        └── Réplica-2 responde: "v1" (se quedó atrás)
        │
        ▼
 El cliente recibe "v2" (se compara por timestamp/versión)
        │
        ▼
 En background: se actualiza la Réplica-2 a "v2"
```

**El resultado:** ningún nodo necesita el visto bueno de nadie antes de aceptar una escritura, así que nunca hay una ronda de red bloqueante como en 2PC o en Raft. Esa es la razón mecánica, no solo conceptual, por la que el peer-to-peer siempre termina siendo AP: la disponibilidad se gana exactamente donde el consenso la sacrifica.

---

# PARTE 2 — DEEP DIVE POR CATEGORÍA

---

## 5. Bases de datos relacionales {#relacional}

### El problema que resuelven

Los datos del mundo real tienen relaciones. Un pedido pertenece a un cliente, contiene múltiples productos, cada producto tiene un proveedor. Estas relaciones son el núcleo del negocio, no un detalle de implementación.

El modelo relacional (Codd, 1970) resuelve esto con:

- **Tablas** como unidad de organización (relaciones matemáticas)
- **Claves foráneas** para expresar relaciones entre tablas
- **SQL** como lenguaje declarativo para consultar cualquier combinación de tablas
- **Normalización** para eliminar redundancia y anomalías de actualización

### Anatomía interna de un motor relacional

```
Query SQL
    │
    ▼
┌──────────────────────────────────┐
│  PARSER                          │  ← valida sintaxis SQL
│  → AST (árbol sintáctico)        │
├──────────────────────────────────┤
│  PLANNER / OPTIMIZER             │  ← elige el plan de ejecución
│  → evalúa estadísticas           │    (qué índice usar, orden de joins)
│  → genera plan de ejecución      │
├──────────────────────────────────┤
│  EXECUTOR                        │  ← ejecuta el plan
│  → Seq Scan, Index Scan, etc.    │
│  → Hash Join, Nested Loop, etc.  │
├──────────────────────────────────┤
│  STORAGE ENGINE                  │  ← lee/escribe páginas
│  → Buffer pool, B-tree, WAL      │
├──────────────────────────────────┤
│  DISCO                           │
└──────────────────────────────────┘
```

### 5.1 PostgreSQL

**Diseñado para:** corrección, features avanzados, extensibilidad  
**Fortaleza principal:** el motor más completo en features SQL + mejor implementación de MVCC

#### MVCC sin locks para lecturas

PostgreSQL implementa MVCC (Multi-Version Concurrency Control) a nivel de fila: cada fila tiene `xmin` (transacción que la creó) y `xmax` (transacción que la borró). Las lecturas nunca bloquean escrituras y viceversa.

```
Tabla heap page con MVCC visible:

┌──────────────────────────────────────────────────────┐
│ xmin=100, xmax=0,   id=1, nombre="Ana"   ← versión actual
│ xmin=95,  xmax=100, id=1, nombre="Andrea" ← versión anterior (visible para txn <100)
└──────────────────────────────────────────────────────┘
```

Ver `guia_transacciones_db.md` §6 para el algoritmo completo de visibilidad.

#### Features únicos de PostgreSQL

- **JSONB**: documentos JSON indexables con operadores nativos `@>`, `->>`
- **Arrays**: columnas de tipo `INTEGER[]`, `TEXT[]` con índices GIN
- **Full-text search**: `tsvector` + `tsquery` + índices GIN/GIST
- **Extensiones**: PostGIS (geoespacial), TimescaleDB (series de tiempo), pgvector (embeddings de IA), pg_trgm (búsqueda fuzzy)
- **Tipos de datos propios**: UUID, INET, CIDR, MACADDR, rangos temporales (`tsrange`)
- **Window functions**: `ROW_NUMBER()`, `LAG()`, `LEAD()`, `PARTITION BY`
- **CTEs y queries recursivas**: para jerarquías, árboles, grafos simples
- **Partial indexes**: `CREATE INDEX ON pedidos (estado) WHERE estado = 'pendiente'`
- **Índices de expresión**: `CREATE INDEX ON usuarios (LOWER(email))`

#### Cuándo elegir PostgreSQL

- Aplicaciones que requieren integridad referencial fuerte
- Queries analíticas moderadas mezcladas con OLTP
- Cuando necesitas features avanzados (geoespacial, JSON, arrays)
- Como base para extensiones (TimescaleDB sobre PostgreSQL = series de tiempo con SQL completo)
- Default para aplicaciones nuevas cuando no hay razón específica para otra cosa

#### Limitaciones

- Escala horizontal de escrituras requiere extensiones (Citus, Patroni) o migrar a NewSQL
- VACUUM puede causar overhead en tablas con alta rotación de datos (problema de bloat)
- Configuración por defecto es conservadora — requiere tuning para producción

---

### 5.2 MySQL InnoDB

**Diseñado para:** web apps de lectura intensiva, simplicidad operacional  
**Fortaleza principal:** performance en workloads read-heavy, ecosistema masivo (LAMP stack)

#### Diferencias clave vs PostgreSQL

La diferencia más profunda entre estos dos motores — heap vs. índice clustered, y su efecto en cadena sobre inserts con PK aleatoria, covering indexes, y cómo cada uno limpia versiones MVCC (VACUUM vs. undo log) — ya se desarrolló a fondo en la Sección 1 ("Desglose de motores que parecen iguales: PostgreSQL vs MySQL InnoDB"), con los diagramas completos de la estructura física. No hace falta repetirlo aquí; lo que sigue es lo que ese desglose no cubre.

**Diferencias de SQL:**

| Feature                     | PostgreSQL      | MySQL                                |
| --------------------------- | --------------- | ------------------------------------ |
| RETURNING en INSERT/UPDATE  | Sí              | Sí (v8.0+)                           |
| CTEs writables              | Sí              | Sí (v8.0+)                           |
| Window functions            | Sí              | Sí (v8.0+)                           |
| Full outer join             | Sí              | Sí                                   |
| DISTINCT ON                 | Sí              | No                                   |
| Arrays nativos              | Sí              | No                                   |
| JSONB con índices           | Sí (más maduro) | Sí (v5.7+, menos features)           |
| Check constraints evaluados | Siempre         | Solo en v8.0.16+                     |
| Partial indexes             | Sí              | No (simulable con índices generados) |

**Cuándo elegir MySQL:**

- Ecosistema LAMP/LEMP heredado
- Aplicaciones donde la velocidad de lectura simple es prioridad
- Cuando el equipo tiene experiencia MySQL y el esquema es sencillo
- Replicación MySQL es más simple de operar que PostgreSQL (binlog vs WAL)

**Cuándo elegir PostgreSQL sobre MySQL:**

- Necesitas integridad referencial estricta (PostgreSQL la enforcea siempre; MySQL depende del motor y configuración)
- Queries complejas con CTEs, window functions, tipos avanzados
- Aplicaciones nuevas donde no hay razón histórica para MySQL

---

### 5.3 SQL Server

**Diseñado para:** ecosistema Microsoft, reporting y BI, cargas empresariales  
**Fortaleza principal:** integración con el stack Microsoft, SQL Server Reporting Services, SSIS

#### Features únicos de SQL Server

- **Columnstore indexes**: índices columnares dentro de tablas relacionales — permite OLAP dentro del mismo motor OLTP (HTAP)
- **In-memory OLTP (Hekaton)**: tablas y procedimientos almacenados en memoria con lock-free concurrency — muy alta throughput para tablas hot
- **Temporal tables**: tablas con historial automático versionado por tiempo (similar a `FOR SYSTEM_TIME AS OF` en SQL:2011)
- **Always Encrypted**: los datos se cifran en el cliente, el servidor nunca ve los valores en claro
- **Row-level security**: políticas de acceso a nivel de fila sin cambiar queries de la aplicación

```sql
-- Temporal table (historial automático):
SELECT * FROM empleados
FOR SYSTEM_TIME AS OF '2023-01-01 12:00:00'
WHERE id = 42;
-- Retorna el valor que tenía ese registro en esa fecha exacta
```

**Cuándo elegir SQL Server:**

- Ecosistema Microsoft (.NET, Azure, Active Directory, Power BI)
- Necesitas reporting integrado (SSRS) o ETL (SSIS)
- Regulación que requiere auditoría temporal (temporal tables es excelente)
- Presupuesto para licencias empresariales con soporte oficial

---

### 5.4 Oracle Database

**Diseñado para:** cargas empresariales críticas, alta disponibilidad, máxima consistencia  
**Fortaleza principal:** el motor más maduro y robusto para cargas OLTP de misión crítica

#### Features únicos de Oracle

- **RAC (Real Application Clusters)**: múltiples instancias activas leen/escriben el mismo storage compartido — no sharding, sino acceso compartido real al mismo disco
- **Flashback**: `SELECT * FROM empleados AS OF TIMESTAMP SYSTIMESTAMP - INTERVAL '1' HOUR` — viajar en el tiempo sin backup
- **Partitioning avanzado**: range, list, hash, composite, interval partitioning — transparente para la aplicación
- **Advanced Compression**: compresión de datos sin pérdida en tablas heap (no solo columnar)
- **Hint system**: control fino del optimizer cuando las estadísticas no son suficientes

**Cuándo elegir Oracle:**

- Sistemas financieros, ERP (SAP sobre Oracle), telecomunicaciones de alta disponibilidad
- Cuando el costo de downtime supera por mucho el costo de licencia
- Migración de sistemas Oracle existentes

**La realidad del mercado:** Oracle es costoso en licencias y operational complexity. Las startups y empresas nuevas raramente lo eligen hoy. PostgreSQL cubre el 90% de sus features en el segmento moderno.

---

### 5.5 SQLite

**Diseñado para:** base de datos embebida, sin servidor, un solo archivo  
**Fortaleza principal:** cero configuración, portable, ideal para desarrollo y apps locales

#### Arquitectura diferente a todos los demás

SQLite no es cliente-servidor. Es una **biblioteca** que se linka directamente en la aplicación. No hay servidor, no hay conexiones de red, no hay procesos separados.

```
Otros motores:
  App → socket TCP → Proceso PostgreSQL → disco

SQLite:
  App → llamada de función SQLite (en el mismo proceso) → disco

  El "servidor" es la biblioteca compilada dentro de tu app
```

#### Limitaciones de concurrencia

SQLite usa locks a nivel de archivo. Solo puede haber **una escritura** al mismo tiempo (lock exclusivo de archivo). Múltiples lecturas simultáneas son posibles (con WAL mode habilitado).

**WAL mode de SQLite:**

```
Sin WAL: reader y writer se bloquean mutuamente
Con WAL: readers leen la versión committed del archivo principal
         writer escribe en el WAL (archivo separado)
         → lecturas y escrituras no se bloquean mutuamente
         → checkpoints periódicos fusionan WAL al archivo principal
```

**Cuándo elegir SQLite:**

- Apps móviles (iOS/Android — SQLite es nativo en ambos)
- Apps de desktop
- Archivos de configuración complejos
- Testing: puedes usar SQLite en tests en lugar de levantar PostgreSQL
- Aplicaciones embebidas (dispositivos IoT, routers)
- **NO** para aplicaciones web con múltiples usuarios concurrentes escribiendo

---

### 5.6 Diferencias críticas entre motores relacionales

|                   | PostgreSQL          | MySQL         | SQL Server | Oracle   | SQLite  |
| ----------------- | ------------------- | ------------- | ---------- | -------- | ------- |
| ACID completo     | ✓                   | ✓\*           | ✓          | ✓        | ✓       |
| MVCC              | ✓                   | ✓             | ✓          | ✓        | ✓ (WAL) |
| Partial indexes   | ✓                   | ✗             | ✓          | ✓        | ✓       |
| Arrays nativos    | ✓                   | ✗             | ✗          | ✗        | ✗       |
| JSONB/JSON        | ✓✓                  | ✓             | ✓          | ✓ (v21c) | ✗       |
| Full-text search  | ✓                   | ✓             | ✓          | ✓        | ✗       |
| Window functions  | ✓                   | ✓             | ✓          | ✓        | ✓       |
| CTEs recursivas   | ✓                   | ✓             | ✓          | ✓        | ✓       |
| Extensible        | ✓✓                  | ✗             | ✗          | ✗        | ✗       |
| Open source       | ✓                   | ✓ (GPL)       | ✗          | ✗        | ✓       |
| Costo             | Gratis              | Gratis        | $$$        | $$$$$    | Gratis  |
| Escala horizontal | Extensiones (Citus) | Ext. (Vitess) | AG/FCI     | RAC      | ✗       |

\* MySQL requiere motor InnoDB; MyISAM (legacy) no tiene ACID.

---

### 5.7 NewSQL: relacional con escala horizontal nativa

La fila "Escala horizontal" de la tabla anterior muestra el punto ciego de los cinco motores clásicos: todos escalan horizontalmente **agregando algo externo** (una extensión, un proxy, un cluster de alta disponibilidad) sobre una arquitectura pensada para nodo único o primary-replica. NewSQL (CockroachDB, Google Spanner, TiDB) es la categoría que nace resolviendo esto desde el diseño, no con un parche encima.

La promesa es "relacional y ACID, como PostgreSQL, pero distribuido nativamente, como Cassandra" — y la cumplen, pero no gratis: cada escritura pasa por un protocolo de consenso (Raft en CockroachDB y TiDB, TrueTime en Spanner) antes de confirmarse, lo que añade al menos una ronda de red de latencia que un PostgreSQL de nodo único nunca paga. Es exactamente el mismo trade-off que ya se explicó a fondo en la Capa 2 ("la escalera con horquilla") y en "SQL vs NoSQL" (CockroachDB comparte L4 y L5 con PostgreSQL, pero en L2 opera como Cassandra).

**Cuándo usar NewSQL en vez de PostgreSQL + extensiones:**

- Necesitas escritura distribuida globalmente (multi-región) sin sacrificar ACID
- El equipo prefiere que la distribución sea parte del motor, no una pieza operativa adicional (Citus, Vitess) que hay que mantener por separado
- Puedes pagar la latencia extra del consenso en cada escritura a cambio de no pensar en sharding manual

**Cuándo NO:** si tu volumen y geografía no exigen distribución real (ver "Qué tener en cuenta al decidir cuánto distribuir" en la Capa 2), NewSQL solo agrega complejidad operativa sin necesidad — el mismo error que adoptar Cassandra antes de tiempo.

---

## 6. Bases de datos de documento {#documento}

### El problema que resuelven

El modelo relacional requiere definir el esquema antes de insertar datos. Todas las filas de una tabla tienen exactamente las mismas columnas. Esto es un problema cuando:

1. Los datos tienen estructura variable: un producto de ropa tiene `{talla, color}`, un producto electrónico tiene `{voltaje, frecuencia, watts}`, un producto digital tiene `{url_descarga, licencia}`
2. El esquema evoluciona frecuentemente: en startups, el schema cambia semana a semana
3. Los datos naturalmente anidados: una orden con sus items es más natural como un documento que como dos tablas con JOIN

### Modelo de datos

```javascript
// PostgreSQL requeriría:
//   tabla productos
//   tabla atributos_ropa (FK → productos)
//   tabla atributos_electronicos (FK → productos)
//   tabla atributos_digitales (FK → productos)

// MongoDB: un solo documento por producto
{
  "_id": ObjectId("507f1f77bcf86cd799439011"),
  "nombre": "Camiseta Polo",
  "precio": 450.00,
  "categoria": "ropa",
  "atributos": {
    "tallas_disponibles": ["S", "M", "L", "XL"],
    "colores": ["rojo", "azul", "negro"],
    "material": "100% algodón"
  },
  "imagenes": [
    { "url": "/img/polo-rojo.jpg", "color": "rojo" },
    { "url": "/img/polo-azul.jpg", "color": "azul" }
  ],
  "metadata": {
    "creado": ISODate("2024-01-15"),
    "actualizado": ISODate("2024-03-20"),
    "activo": true
  }
}
```

### 6.1 MongoDB

**Motor de almacenamiento:** WiredTiger (B-tree por default, también soporta LSM-tree)  
**Formato de datos:** BSON (Binary JSON — más eficiente que JSON texto)

#### Estructura interna

```
MongoDB organiza datos en:
  Base de datos → Colecciones → Documentos

  Base de datos: "tienda"
    Colección: "productos"     ← equivalente a tabla
      Documento: { _id, nombre, precio, ... }   ← equivalente a fila
      Documento: { _id, nombre, precio, ... }
    Colección: "ordenes"
      Documento: { _id, usuario_id, items: [...], total }
```

#### Indexing en MongoDB

MongoDB soporta múltiples tipos de índices sobre campos del documento, incluyendo campos anidados:

```javascript
// Índice sobre campo de documento anidado
db.productos.createIndex({ "atributos.material": 1 });

// Índice sobre campo dentro de un array
db.productos.createIndex({ "imagenes.color": 1 });

// Índice compuesto
db.productos.createIndex({ categoria: 1, precio: -1 });

// Índice de texto para full-text search
db.productos.createIndex({ nombre: "text", descripcion: "text" });

// Índice 2dsphere para datos geoespaciales
db.locales.createIndex({ ubicacion: "2dsphere" });
```

#### Aggregation Pipeline

El equivalente a SQL con GROUP BY, JOINs y transformaciones:

```javascript
db.ordenes.aggregate([
  // Etapa 1: filtrar
  { $match: { estado: "completada", fecha: { $gte: ISODate("2024-01-01") } } },

  // Etapa 2: "join" con colección usuarios
  {
    $lookup: {
      from: "usuarios",
      localField: "usuario_id",
      foreignField: "_id",
      as: "usuario",
    },
  },

  // Etapa 3: agrupar y calcular
  {
    $group: {
      _id: "$usuario.pais",
      total_ventas: { $sum: "$total" },
      num_ordenes: { $count: {} },
    },
  },

  // Etapa 4: ordenar
  { $sort: { total_ventas: -1 } },

  // Etapa 5: limitar
  { $limit: 10 },
]);
```

#### Sharding en MongoDB

MongoDB tiene sharding nativo con un componente llamado `mongos` (router):

```
  App → mongos (router) → Shard-1, Shard-2, Shard-3
                         (cada shard es un replica set)

Configuración del shard key:
  sh.shardCollection("tienda.ordenes", { "usuario_id": "hashed" })

  → MongoDB calcula hash(usuario_id) y determina qué shard almacena cada documento
  → El router mongos dirige cada query al shard correcto
```

#### Transacciones ACID en MongoDB (v4.0+)

```javascript
const session = client.startSession();
session.startTransaction();

try {
  await db.cuentas.updateOne(
    { _id: "cuenta_origen" },
    { $inc: { saldo: -100 } },
    { session },
  );
  await db.cuentas.updateOne(
    { _id: "cuenta_destino" },
    { $inc: { saldo: +100 } },
    { session },
  );
  await session.commitTransaction();
} catch (error) {
  await session.abortTransaction();
}
```

**Costo:** las transacciones multi-documento en MongoDB usan un protocolo de two-phase locking y tienen overhead significativo comparado con las operaciones de documento único (que son atómicas por default). La latencia puede ser 3x–10x mayor.

#### Cuándo usar MongoDB

- Catálogos de productos con atributos heterogéneos
- Content management systems
- Aplicaciones donde el esquema evoluciona rápido (startups en fases tempranas)
- Datos que naturalmente son jerárquicos y se consultan como unidad

#### Cuándo NO usar MongoDB

- Cuando necesitas joins complejos entre colecciones frecuentemente (el aggregation pipeline con `$lookup` es lento comparado con SQL JOIN)
- Cuando el esquema es estable y relacional — no hay ventaja sobre PostgreSQL
- Para datos financieros donde la consistencia es crítica — usa PostgreSQL

---

### 6.2 Firestore (Google)

**Diferencia clave vs MongoDB:** serverless, escala automática, modelo de precios por operación  
**Diseñado para:** apps móviles y web con real-time sync

```javascript
// Firestore: colecciones y documentos, pero con sub-colecciones
// usuarios/{uid}/pedidos/{pedidoId}

const pedidosRef = db.collection("usuarios").doc(uid).collection("pedidos");

// Listener en tiempo real — la app recibe updates automáticamente
pedidosRef.onSnapshot((snapshot) => {
  snapshot.docChanges().forEach((change) => {
    if (change.type === "added") actualizarUI(change.doc.data());
  });
});
```

**Limitaciones importantes:**

- Sin joins entre colecciones top-level (debe duplicar datos o usar referencias manuales)
- Queries limitadas: no hay `OR` con diferentes campos, no hay `!=` en múltiples campos
- Costo puede escalar rápido si hay muchas lecturas de documentos

---

## 7. Bases de datos clave-valor {#kv}

### El problema que resuelven

A veces el acceso a un dato es siempre por una clave exacta y no necesitas nada más. Guardar una sesión de usuario, una configuración, un resultado cacheado. El modelo relacional agrega overhead innecesario para esto.

Un key-value store es esencialmente un hash map distribuido y persistente.

### 7.1 Redis

**Diseñado para:** velocidad extrema en memoria, estructuras de datos en servidor  
**Fortaleza principal:** no es solo key-value — ofrece 8+ tipos de datos con operaciones atómicas

#### Las estructuras de datos de Redis

**String** — el tipo básico:

```
SET usuario:123:nombre "Ana García"
GET usuario:123:nombre   → "Ana García"
INCR contador:paginas    → incremento atómico
SETEX sesion:abc 3600 "{token:...}"  → con TTL de 1 hora
```

**List** — lista doblemente enlazada:

```
LPUSH cola:emails "email1" "email2"   → inserta al inicio
RPUSH cola:emails "email3"            → inserta al final
LPOP cola:emails                      → extrae del inicio (queue FIFO)
LRANGE cola:emails 0 -1              → ver todos los elementos

Uso típico: colas de trabajo, activity feeds
```

**Hash** — objeto con campos:

```
HSET usuario:123 nombre "Ana" edad 30 email "ana@example.com"
HGET usuario:123 nombre        → "Ana"
HGETALL usuario:123            → todos los campos
HINCRBY usuario:123 puntos 10  → incrementar campo numérico

Uso típico: objetos de usuario, configuración por entidad
Ventaja vs String JSON: puedes leer/actualizar un campo sin deserializar todo el objeto
```

**Set** — conjunto sin duplicados con operaciones de conjuntos:

```
SADD amigos:ana "luis" "sol" "carlos"
SADD amigos:luis "ana" "pedro" "sol"
SINTER amigos:ana amigos:luis    → ["sol"]  (amigos en común)
SUNION amigos:ana amigos:luis    → todos los amigos de ambos
SISMEMBER amigos:ana "sol"       → 1 (true)

Uso típico: tags de un artículo, usuarios que vieron un video
```

**Sorted Set (ZSet)** — conjunto con puntaje flotante, ordenado:

```
ZADD ranking:jugadores 1500 "ana" 2300 "luis" 1800 "sol"
ZRANGE ranking:jugadores 0 -1 WITHSCORES  → ordenado por score
ZREVRANK ranking:jugadores "luis"          → posición de "luis" (0 = primero)
ZINCRBY ranking:jugadores 100 "ana"        → sumar 100 puntos a "ana"

Uso típico: leaderboards, rate limiting por ventana deslizante,
           índices geoespaciales (con score = latitud-longitud codificada)
```

**Stream** — log de eventos con consumer groups:

```
XADD eventos:pagos * usuario_id 123 monto 500 tipo "compra"
XREAD COUNT 10 STREAMS eventos:pagos 0   → leer eventos

Consumer groups (procesamiento distribuido):
XGROUP CREATE eventos:pagos workers 0
XREADGROUP GROUP workers worker-1 COUNT 10 STREAMS eventos:pagos >
XACK eventos:pagos workers <message-id>  → confirmar procesamiento

Uso típico: event sourcing, procesamiento de eventos estilo Kafka (a menor escala)
```

**HyperLogLog** — conteo aproximado de cardinalidad:

```
PFADD visitantes:dia1 "ip1" "ip2" "ip3" ... (1 millón de IPs)
PFCOUNT visitantes:dia1   → ~1,000,000 (±0.81% error)
                            usando solo 12KB de memoria

Uso típico: contar usuarios únicos sin guardar todos los IDs
```

**Geospatial** — coordenadas con búsqueda por radio:

```
GEOADD restaurantes -99.1332 19.4326 "Restaurante-A"
GEOADD restaurantes -99.1270 19.4285 "Restaurante-B"
GEOSEARCH restaurantes FROMLONLAT -99.1300 19.4300 BYRADIUS 1 km ASC
→ ["Restaurante-B", "Restaurante-A"]  ordenados por distancia

Uso típico: búsqueda de servicios cercanos, geofencing
```

#### Persistencia en Redis

Redis es in-memory pero ofrece dos mecanismos de persistencia:

**RDB (Redis Database Backup)** — snapshot periódico:

```
save 900 1    # si hubo 1 cambio en 15 min, hacer snapshot
save 300 10   # si hubo 10 cambios en 5 min, hacer snapshot
save 60 10000 # si hubo 10000 cambios en 1 min, hacer snapshot

→ Redis hace fork() del proceso, el hijo escribe el snapshot a disco
→ Ventaja: snapshots compactos, recovery rápido
→ Desventaja: puede perder hasta N minutos de datos si falla entre snapshots
```

**AOF (Append-Only File)** — log de cada operación:

```
appendonly yes
appendfsync everysec  # fsync cada segundo (balance velocidad/durabilidad)

→ Cada escritura se append al archivo AOF
→ En recovery, se repiten todas las operaciones del AOF
→ Ventaja: pérdida máxima de 1 segundo de datos
→ Desventaja: archivos grandes, recovery más lento
```

**Recomendación:** usar ambos. RDB para backups rápidos, AOF para durabilidad.

#### Redis Cluster

```
Redis Cluster divide el keyspace en 16384 slots (hash slots):
  hash_slot = CRC16(key) % 16384

  Slots 0-5460    → Master-1 (+ 1-2 réplicas)
  Slots 5461-10922 → Master-2 (+ 1-2 réplicas)
  Slots 10923-16383 → Master-3 (+ 1-2 réplicas)

  SET usuario:123 "Ana"
  → CRC16("usuario:123") % 16384 = 8743
  → 8743 está en rango 5461-10922 → va a Master-2
```

#### Cuándo usar Redis

- Caché de resultados de queries costosas (cache-aside pattern)
- Sesiones de usuario (con TTL automático)
- Rate limiting (con Sorted Sets o scripts Lua)
- Pub/Sub para comunicación entre microservicios
- Leaderboards y contadores en tiempo real
- Colas de trabajo simples (con Streams para mayor confiabilidad)

#### Cuándo NO usar Redis

- Como base de datos principal si los datos no caben en RAM
- Cuando necesitas queries complejas sobre los datos
- Cuando la durabilidad de cada operación es crítica (usar AOF + WAIT, pero tiene costo)

---

### 7.2 DynamoDB

**Diseñado para:** key-value + documento a escala serverless sin operaciones  
**Fortaleza principal:** escala automática, sin servidor que administrar, latencia predecible de milisegundos

#### Modelo de datos

DynamoDB organiza los datos en **tablas** pero con un modelo muy diferente al SQL:

```
Tabla: Pedidos
  Partition Key: usuario_id   (determina en qué nodo físico vive el dato)
  Sort Key: pedido_id         (ordena los items dentro de la misma partition)

Ítem (equivalente a fila/documento):
{
  "usuario_id": "user_123",        ← Partition Key
  "pedido_id": "order_2024_001",   ← Sort Key
  "total": 1500,
  "items": [                       ← atributos arbitrarios (como documento)
    {"producto": "laptop", "qty": 1},
    {"producto": "mouse", "qty": 2}
  ],
  "estado": "enviado",
  "fecha": "2024-03-15"
}
```

#### Acceso por partition key — el patrón fundamental

La regla de oro de DynamoDB: **el acceso más eficiente siempre es por partition key exacta**.

```
Lectura eficiente:
  GetItem: usuario_id = "user_123" AND pedido_id = "order_2024_001"
  → O(1), siempre en milisegundos

Query eficiente:
  Query: usuario_id = "user_123" AND pedido_id BEGINS_WITH "order_2024"
  → todos los pedidos de 2024 de ese usuario → eficiente (misma partition)

Scan (evitar en producción):
  Scan todos los ítems donde estado = "pendiente"
  → recorre TODA la tabla → costoso, lento, caro
```

#### Índices secundarios

Para acceder por atributos que no son la PK:

```
GSI (Global Secondary Index):
  Tabla: Pedidos (PK: usuario_id, SK: pedido_id)

  GSI-Estado:
    PK: estado
    SK: fecha

  → Permite query: "todos los pedidos con estado='enviado' ordenados por fecha"
  → El GSI es básicamente una copia proyectada de la tabla con diferente PK
  → Tiene su propio throughput, sus propias unidades de capacidad

LSI (Local Secondary Index):
  → Misma partition key, diferente sort key
  → Debe crearse al crear la tabla (no se puede agregar después)
```

#### Modelo de capacidad y precios

DynamoDB cobra por unidades de capacidad de lectura (RCU) y escritura (WCU):

- 1 RCU = 1 lectura strongly consistent de hasta 4KB, o 2 lecturas eventually consistent
- 1 WCU = 1 escritura de hasta 1KB

**On-demand mode:** paga por lo que usas, escala automático.  
**Provisioned mode:** reservas capacidad, más barato si el tráfico es predecible.

#### Cuándo usar DynamoDB

- Aplicaciones serverless en AWS (Lambda + DynamoDB = el stack más simple de operar)
- Patrones de acceso bien definidos y predecibles (sabes de antemano cómo queries)
- Escala masiva sin querer administrar infraestructura
- Sesiones, perfiles de usuario, configuraciones por tenant

#### Cuándo NO usar DynamoDB

- Queries ad hoc complejas (necesitarías scan o múltiples GSIs)
- Relaciones complejas entre entidades
- Si no estás en AWS o no quieres vendor lock-in

---

### 7.3 Memcached

**Diseñado para:** caché pura en RAM, tan simple que no hay casi nada que configurar mal  
**Fortaleza principal:** el modelo más simple y predecible de los tres — precisamente por lo que le falta

#### Qué le falta a propósito

Memcached es anterior a Redis y resuelve un problema más angosto: acelerar lecturas repetidas quitándole trabajo a la base de datos de verdad. Todo lo que Redis agregó después (persistencia opcional, estructuras de datos ricas, replicación, pub/sub) Memcached lo dejó fuera a propósito:

```
GET usuario:123          → obtiene el valor (string u objeto serializado)
SET usuario:123 "..." 3600  → guarda con TTL de 1 hora
DELETE usuario:123       → borra
INCR contador 1          → único tipo de operación atómica más allá de string
```

Sin listas, sin sets, sin streams, sin persistencia en disco bajo ninguna configuración. Si el proceso muere o se reinicia, todo el contenido desaparece — y eso no es una falla, es el diseño: es una caché, no un registro de verdad. La aplicación siempre debe poder reconstruir el dato desde la base de datos real si Memcached lo pierde.

#### Cómo distribuye sin que los servidores se hablen entre sí

A diferencia de Redis Cluster o de Cassandra, Memcached no tiene noción de cluster del lado del servidor. Cada instancia es completamente independiente y ni siquiera sabe que existen las demás. La distribución la resuelve la librería cliente (`libmemcached` y equivalentes) con consistent hashing:

```
Cliente decide, antes de cualquier request:
  hash("usuario:123") → servidor B
  hash("sesion:abc")  → servidor A

  Servidor A y Servidor B nunca se coordinan entre sí.
  Si el cliente cambia de librería o de función de hash,
  puede terminar preguntándole al servidor equivocado —
  eso simplemente produce un cache miss, no un dato corrupto.
```

Esto es más simple de operar que un ring de Cassandra (no hay gossip, no hay rebalanceo automático, no hay reparación) pero también más frágil: agregar o quitar un servidor sin un hashing consistente bien implementado invalida buena parte de la caché de golpe.

#### Memcached vs. Redis

|               | Memcached                              | Redis                                       |
| ------------- | -------------------------------------- | ------------------------------------------- |
| Tipos de dato | Solo strings/bytes opacos              | 8+ tipos (listas, sets, hashes, streams...) |
| Persistencia  | Ninguna, nunca                         | Opcional (RDB, AOF)                         |
| Distribución  | Sharding client-side, sin coordinación | Redis Cluster con coordinación entre nodos  |
| Multi-hilo    | Sí, nativo                             | Principalmente single-thread (I/O)          |
| Modelo mental | Diccionario efímero                    | Estructuras de datos en red                 |

#### Cuándo usar Memcached

- Caché pura donde perder todo el contenido en un reinicio es aceptable y esperado
- Objetos grandes y simples (HTML renderizado, resultados de queries costosas) sin necesidad de estructura interna
- Cuando la simplicidad operativa (sin persistencia que configurar, sin replicación que vigilar) importa más que las funciones extra de Redis
- Cachés multi-hilo de muy alto throughput donde aprovechar varios cores en un solo proceso es una ventaja real

#### Cuándo NO usar Memcached

- Si necesitas que el dato sobreviva un reinicio, aunque sea parcialmente (usa Redis con persistencia)
- Si necesitas estructuras de datos (colas, sets, contadores con operaciones complejas) o pub/sub
- Si necesitas que el propio motor sepa distribuirse y rebalancearse (Redis Cluster, o directamente un motor pensado para eso)

---

## 8. Wide-column stores {#widecolumn}

### El problema que resuelven

El modelo wide-column resuelve un caso específico: escribir y leer datos a escala masiva (millones de operaciones por segundo) con disponibilidad alta, tolerando consistencia eventual.

El nombre "wide-column" es confuso — no es lo mismo que almacenamiento columnar. La confusión es histórica.

```
Wide-column: organiza datos como tabla con filas y columnas,
             pero cada fila puede tener columnas completamente diferentes,
             y las filas se distribuyen en múltiples nodos por partition key.

     Row Key    │ col:email          │ col:nombre │ col:plan  │
     ───────────┼────────────────────┼────────────┼───────────┤
     user_001   │ ana@example.com    │ Ana        │ premium   │
     user_002   │ luis@example.com   │ Luis       │ (ausente) │ ← columnas opcionales
     user_003   │ (ausente)          │ Sol        │ gratis    │
```

### 8.1 Cassandra

**Diseñado para:** disponibilidad alta, escrituras masivas, tolerancia a fallas de datacenter  
**Fortaleza principal:** ningún single point of failure, escala lineal, always-on

#### Arquitectura peer-to-peer

Cassandra no tiene primary/replica ni maestro. Todos los nodos son iguales.

```
Ring topology de Cassandra (6 nodos):

         Nodo-A (0-60)
       ╱               ╲
  Nodo-F           Nodo-B
  (300-360)        (60-120)
      │                 │
  Nodo-E           Nodo-C
  (240-300)        (120-180)
       ╲               ╱
         Nodo-D (180-240)

  Cada nodo es responsable de un rango del token ring
  Con RF=3, cada dato se guarda en 3 nodos consecutivos del ring
```

No hay un nodo coordinador central. Cualquier nodo puede recibir cualquier query (actúa como coordinador temporal solo para esa query).

#### Modelo de datos de Cassandra

**Keyspace** → **Tabla** → **Filas** particionadas

La diferencia crítica con SQL: **el modelo de datos se diseña alrededor de los access patterns, no de la normalización**.

```cql
-- Diseño orientado a queries en Cassandra:
-- "Quiero obtener todos los mensajes de un chat, ordenados por tiempo"

CREATE TABLE mensajes_por_chat (
  chat_id    UUID,          -- Partition key → determina nodo
  timestamp  TIMESTAMP,     -- Clustering key → ordena dentro de la partition
  usuario_id UUID,
  contenido  TEXT,
  PRIMARY KEY (chat_id, timestamp)
) WITH CLUSTERING ORDER BY (timestamp DESC);

-- Query eficiente:
SELECT * FROM mensajes_por_chat
WHERE chat_id = ? AND timestamp > ? LIMIT 50;

-- La partición (chat_id) vive en un nodo específico.
-- Los mensajes dentro de la partición están ordenados por timestamp en disco.
-- Esta query nunca va a más de 1 nodo (o 3 con RF=3).
```

**Reglas de diseño en Cassandra:**

1. La partition key determina en qué nodo vive el dato
2. La clustering key determina el orden dentro de la partition
3. Solo puedes filtrar por columnas que son parte de la PRIMARY KEY (sin índice adicional)
4. Nunca hagas `SELECT * FROM tabla` sin WHERE — recorrerá todos los nodos
5. Desnormaliza agresivamente — duplicar datos es esperado y correcto en Cassandra

#### LSM-tree en Cassandra

Cassandra usa LSM-tree como estructura de almacenamiento. Esto explica su alta velocidad de escritura: toda escritura es un append al CommitLog + insert en MemTable.

```
Escritura en Cassandra:
  1. Append al CommitLog (WAL) → durabilidad
  2. Insert en MemTable (en RAM) → respuesta inmediata
  3. Cuando MemTable llega a su límite → flush a SSTable en disco
  4. Compactación periódica de SSTables

Lectura en Cassandra:
  1. Buscar en MemTable (si está ahí, listo)
  2. Consultar Bloom filters de cada SSTable para filtrar las relevantes
  3. Leer Row cache (si está habilitada)
  4. Leer SSTables relevantes
  → Más costosa que la escritura por las múltiples SSTables
```

#### Tombstones — el problema de los deletes

En LSM-tree, borrar un registro no borra los bytes del disco (las SSTables son inmutables). En su lugar, se escribe un **tombstone** (lápida): una marca que dice "este registro fue borrado".

```
SSTable-001: { key: "user:123", nombre: "Ana", ... }
SSTable-002: { key: "user:123", TOMBSTONE }  ← delete = nueva entrada

Durante una lectura: si se encuentra un tombstone, se ignora el valor de SSTable-001
Durante compactación: se elimina tanto el tombstone como el dato original
```

El problema: si acumulas muchos tombstones sin compactar (o si lees antes de que se compacten), las lecturas se vuelven lentas porque deben leer y descartar muchos tombstones.

**Patrón anti: delete y re-insert masivos en Cassandra** → genera tombstone storm → degradación de performance.

#### Cuándo usar Cassandra

- IoT: millones de sensores escribiendo datos constantemente
- Logs y eventos de alta frecuencia
- Aplicaciones que necesitan funcionar en múltiples datacenters simultáneamente
- Time-series de alta escritura (con TWCS)
- Cuando la disponibilidad (no importa si hay un lag de lectura) es más crítica que la consistencia fuerte

#### Cuándo NO usar Cassandra

- Cuando necesitas joins entre tablas (Cassandra no tiene joins)
- Cuando el modelo de datos cambia frecuentemente (cada cambio requiere rediseñar las tablas)
- Cuando necesitas transacciones multi-fila con ACID fuerte (LWT de Cassandra es limitado y lento)
- Para aplicaciones pequeñas — la complejidad operacional no vale la pena

#### ScyllaDB: el mismo motor, reimplementado

ScyllaDB no necesita su propio apartado porque su fingerprint es idéntico al de Cassandra en las ocho capas (LSM-tree, peer-to-peer, eventual/tunable, wide-column, CQL, sin FK, sin RLS) — ya se estableció en la Sección 1 como el ejemplo de referencia para "cuándo dos motores son el mismo diseño y no una alternativa real". La única diferencia es la implementación: C++ en vez de JVM, sin garbage collector, con I/O propio afinado por core — lo que en la práctica se traduce en latencias de cola (p99) más bajas y predecibles bajo la misma carga. Todo lo de "cuándo usar/no usar Cassandra" de arriba aplica igual a ScyllaDB; la decisión entre ambos es operativa (madurez del ecosistema Cassandra vs. rendimiento por nodo de ScyllaDB), no de diseño.

---

## 9. Motores de búsqueda {#busqueda}

### El problema que resuelven

Buscar `WHERE nombre LIKE '%garcia%'` en PostgreSQL requiere un sequential scan de toda la tabla si el patrón empieza con `%`. No escala para búsqueda de texto libre.

Los motores de búsqueda resuelven tres problemas que los motores SQL no pueden:

1. **Relevancia**: "muéstrame los resultados más relevantes para 'base datos'", no solo los que contienen exactamente esas palabras
2. **Análisis lingüístico**: "datos" y "dato" son la misma raíz; "PostgreSQL" y "postgresql" son lo mismo
3. **Búsqueda en documentos**: buscar en múltiples campos con pesos distintos

### 9.1 Elasticsearch

**Diseñado para:** búsqueda de texto complejo, analítica de logs, búsqueda en tiempo real  
**Basado en:** Apache Lucene (biblioteca de inverted index subyacente)

#### Arquitectura

```
Cluster de Elasticsearch:

  ┌─────────────────────────────────────────────────┐
  │                   Cluster                       │
  │  ┌─────────┐   ┌─────────┐   ┌─────────┐       │
  │  │ Nodo-1  │   │ Nodo-2  │   │ Nodo-3  │       │
  │  │         │   │         │   │         │       │
  │  │ Shard-0 │   │ Shard-1 │   │ Shard-2 │       │
  │  │ Replica1│   │ Replica2│   │ Replica0│       │
  │  └─────────┘   └─────────┘   └─────────┘       │
  └─────────────────────────────────────────────────┘

  Índice "productos" dividido en 3 shards primarios + 1 réplica cada uno
```

**Índice**: equivalente a una tabla SQL, con un mapping (esquema)  
**Shard**: una instancia de Lucene — un inverted index completo para un subconjunto de documentos  
**Réplica**: copia de un shard primario para alta disponibilidad y escala de lecturas

#### Pipeline de análisis de texto

Cuando indexas un texto, Elasticsearch lo transforma antes de guardarlo:

```
Texto original: "PostgreSQL es una Base-de-Datos de código abierto"

Tokenizer (Standard):
  ["PostgreSQL", "es", "una", "Base", "de", "Datos", "de", "código", "abierto"]

Token filters:
  Lowercase: ["postgresql", "es", "una", "base", "de", "datos", "de", "código", "abierto"]
  Stop words: ["postgresql", "una", "base", "datos", "código", "abierto"]
                ↑ removió "es", "de"
  Stemmer (Spanish): ["postgresql", "un", "bas", "dat", "codíg", "abiert"]
                       ↑ reduce palabras a su raíz morfológica

Tokens indexados: ["postgresql", "un", "bas", "dat", "codíg", "abiert"]
```

Esto significa que una búsqueda por "bases de datos" también encuentra documentos con "base", "dato", "datos".

#### Tipos de queries

```json
// Match query — búsqueda de texto analizada
{
  "query": {
    "match": {
      "descripcion": "base de datos relacional"
    }
  }
}

// Multi-match — buscar en varios campos con pesos
{
  "query": {
    "multi_match": {
      "query": "postgresql",
      "fields": ["titulo^3", "descripcion^1", "tags^2"]
    }
  }
}

// Bool query — combinar condiciones
{
  "query": {
    "bool": {
      "must": [{ "match": { "titulo": "postgresql" } }],
      "filter": [{ "term": { "activo": true } }],
      "should": [{ "match": { "descripcion": "MVCC" } }],
      "must_not": [{ "term": { "categoria": "deprecated" } }]
    }
  }
}
```

#### Aggregations — analítica sobre búsquedas

```json
// "Cuántos productos hay por categoría, con precio promedio"
{
  "aggs": {
    "por_categoria": {
      "terms": { "field": "categoria.keyword" },
      "aggs": {
        "precio_promedio": { "avg": { "field": "precio" } }
      }
    }
  }
}
```

Esto hace que Elasticsearch no sea solo un motor de búsqueda sino también una herramienta de analítica para logs (ELK stack: Elasticsearch + Logstash + Kibana).

#### Cuándo usar Elasticsearch

- Búsqueda de texto libre en aplicaciones (e-commerce, portales de contenido)
- Análisis y búsqueda en logs (combinado con Kibana)
- Autocompletar con relevancia
- Búsqueda geoespacial + texto combinados
- Facets y filtros dinámicos en catálogos

#### Cuándo NO usar Elasticsearch

- Como base de datos principal (consistencia eventual, no es fuente de verdad)
- Para transacciones — no existe el concepto
- Para queries relacionales — no hay joins
- Cuando los documentos cambian frecuentemente (los updates son costosos: delete + reindex)

#### El patrón correcto: Elasticsearch como índice secundario

```
Escritura:
  App → PostgreSQL (fuente de verdad, ACID) → evento → Elasticsearch (índice de búsqueda)

Lectura de búsqueda:
  App → Elasticsearch → IDs de documentos → PostgreSQL (para datos completos y actualizados)

Lectura simple:
  App → PostgreSQL directamente
```

---

## 10. Bases de datos de serie de tiempo {#timeseries}

### El problema que resuelven

Los datos de serie de tiempo tienen características únicas:

1. **Siempre append**: nuevas mediciones se agregan, rara vez se actualizan datos históricos
2. **Alto volumen**: un servidor generando métricas cada segundo = 86,400 filas/día/servidor. Con 1000 servidores = 86.4 millones de filas/día
3. **Queries por rango temporal**: casi siempre "dame los datos entre T1 y T2"
4. **Retención automática**: datos de hace 2 años generalmente se pueden comprimir o eliminar
5. **Downsampling**: los datos de hace 1 año no necesitan resolución de 1 segundo; 1 hora es suficiente

Una base de datos SQL normal puede manejar esto, pero las bases de datos de serie de tiempo están optimizadas para estos patrones específicos.

### 10.1 InfluxDB

**Modelo de datos:** measurement, tags, fields, timestamp

```
Analogía con SQL:
  Measurement ≈ tabla
  Tag         ≈ columna indexada (para filtrar y agrupar)
  Field       ≈ columna de valor (lo que mides)
  Timestamp   ≈ columna de tiempo obligatoria

Ejemplo — métricas de un servidor:
  Measurement: "cpu_usage"
  Tags: { host: "servidor-1", datacenter: "mx-1", app: "backend" }
  Fields: { usage_user: 45.2, usage_system: 12.1, usage_idle: 42.7 }
  Timestamp: 2024-03-15T10:30:00Z
```

#### Retention policies y continuous queries

```sql
-- Política de retención: datos crudos por 30 días
CREATE RETENTION POLICY "raw_30d" ON "metricas"
  DURATION 30d REPLICATION 1 DEFAULT

-- Continuous query: downsampling automático
-- Cada hora, calcular el promedio por minuto de la última hora
-- y guardar en una retención de 1 año
CREATE CONTINUOUS QUERY "cq_cpu_1m" ON "metricas"
  BEGIN
    SELECT mean("usage_user") INTO "yearly_1y"."cpu_usage_1m"
    FROM "cpu_usage"
    GROUP BY time(1m), host
  END

-- Resultado: datos crudos (1 segundo) → se borran a los 30 días
--            datos por minuto (60x compresión) → se guardan 1 año
--            datos por hora (3600x compresión) → se guardan 5 años
```

#### TSM (Time-Structured Merge Tree)

InfluxDB usa su propia variante del LSM-tree llamada TSM, optimizada para series de tiempo:

- Los datos se organizan por (measurement, tag_set, timestamp)
- La compresión explota el hecho de que timestamps son secuenciales y fields son valores numéricos similares consecutivamente
- Compresión típica de 10x–50x sobre los datos crudos

#### Consistencia: eventual, y sin la perilla tunable de Cassandra

InfluxDB en cluster es eventual — pero a diferencia de Cassandra o ScyllaDB, no expone `ONE`/`QUORUM`/`ALL` por operación. No hay perilla que girar: el nivel de consistencia no se negocia por query. Esto es coherente con el caso de uso — una métrica de CPU que llega con un segundo de retraso no rompe ningún dashboard, así que no hace falta pagar la complejidad de una consistencia ajustable que nadie va a necesitar ajustar.

---

### 10.2 TimescaleDB

**Diseñado para:** series de tiempo con SQL completo, integración perfecta con PostgreSQL  
**Fortaleza principal:** extensión de PostgreSQL — tienes series de tiempo Y joins relacionales

#### Hypertables

TimescaleDB extiende PostgreSQL con el concepto de **hypertable**: una tabla que automáticamente se particiona en **chunks** por rango de tiempo.

```sql
-- Crear hypertable (tabla + particionamiento automático por tiempo)
SELECT create_hypertable('metricas', 'timestamp');

-- Configurar tamaño de chunk
SELECT set_chunk_time_interval('metricas', INTERVAL '1 day');

-- Internamente, TimescaleDB crea automáticamente:
-- _timescaledb_internal._hyper_1_1_chunk (datos de día 1)
-- _timescaledb_internal._hyper_1_2_chunk (datos de día 2)
-- etc.

-- Para el desarrollador, es transparente:
INSERT INTO metricas (timestamp, host, cpu_usage)
VALUES (NOW(), 'servidor-1', 45.2);

SELECT time_bucket('1 hour', timestamp) AS hora,
       host,
       avg(cpu_usage) as cpu_promedio
FROM metricas
WHERE timestamp > NOW() - INTERVAL '7 days'
GROUP BY hora, host
ORDER BY hora DESC;
```

**Por qué los chunks son eficientes:**

- Queries que filtran por tiempo reciente solo tocan los chunks recientes (los que están en RAM)
- Los chunks viejos pueden comprimirse automáticamente: `add_compression_policy('metricas', INTERVAL '7 days')`
- Chunks viejos pueden moverse a almacenamiento más barato (tiering)

**La ventaja sobre InfluxDB:** como es una extensión de PostgreSQL, puedes hacer JOIN entre tu hypertable de métricas y tus tablas relacionales normales:

```sql
-- "¿Cuándo hubo CPU alto en servidores de clientes premium?"
SELECT m.timestamp, m.host, m.cpu_usage, c.plan
FROM metricas m
JOIN servidores s ON m.host = s.hostname
JOIN clientes c ON s.cliente_id = c.id
WHERE m.cpu_usage > 90
  AND c.plan = 'premium'
  AND m.timestamp > NOW() - INTERVAL '24 hours';
```

Esto es imposible en InfluxDB o Prometheus.

---

### 10.3 Prometheus

**Diseñado para:** monitoreo de infraestructura, alertas  
**Diferencia clave:** no es una base de datos general — es un sistema de monitoreo con almacenamiento integrado

```yaml
# Prometheus scrapes targets (pull model)
scrape_configs:
  - job_name: "backend"
    static_configs:
      - targets: ["app1:9090", "app2:9090"]
    scrape_interval: 15s
```

Prometheus va activamente a pedir métricas a cada servicio cada N segundos. Esto es opuesto a InfluxDB/TimescaleDB donde los servicios escriben activamente sus métricas.

**PromQL** — el lenguaje de query:

```
# CPU promedio de los últimos 5 minutos
rate(cpu_seconds_total{job="backend"}[5m])

# Alertar si el error rate > 1%
rate(http_requests_total{status=~"5.."}[5m]) /
rate(http_requests_total[5m]) > 0.01
```

**Cuándo usar Prometheus:** monitoreo de infraestructura y aplicaciones (con Grafana para dashboards). Para retención larga de datos, se complementa con Thanos o Cortex que extienden su almacenamiento a S3.

---

## 11. Bases de datos de grafo {#grafo}

### El problema que resuelven

Algunos problemas son inherentemente sobre **relaciones entre entidades**, y la relación misma es el dato importante:

- ¿Qué amigos de Ana también siguen a Luis? (red social)
- ¿Existe un camino de menos de 3 saltos entre Empresa-A y Empresa-B a través de directores compartidos? (detección de fraude)
- ¿Cuál es la ruta más corta entre Ciudad-A y Ciudad-B con costo < $500? (logística)
- ¿Qué productos compran las personas que compran X? (recomendaciones)

En SQL, estas queries requieren JOINs recursivos o CTEs recursivas que se vuelven lentos con datos grandes:

```sql
-- SQL: amigos de amigos (3 niveles de profundidad)
-- Funciona pero se vuelve exponencialmente lento con más niveles
WITH RECURSIVE amigos_nivel AS (
  SELECT amigo_id, 1 as nivel FROM relaciones WHERE usuario_id = 123
  UNION ALL
  SELECT r.amigo_id, an.nivel + 1
  FROM relaciones r
  JOIN amigos_nivel an ON r.usuario_id = an.amigo_id
  WHERE an.nivel < 3
)
SELECT DISTINCT amigo_id FROM amigos_nivel;
```

En Neo4j, el mismo query con Cypher:

```cypher
MATCH (ana:Usuario {id: 123})-[:AMIGO*1..3]-(persona)
RETURN DISTINCT persona
```

### 11.1 Neo4j

**Modelo de datos:** nodos, relaciones (aristas), propiedades en ambos

```
Nodo:         (Ana:Usuario {id:1, nombre:"Ana", ciudad:"CDMX"})
              ↑ label      ↑ propiedades

Relación:     (Ana)-[:SIGUE {desde:"2023-01-01"}]->(Luis)
              ↑ tipo de relación con propiedades

Patrón:       (Ana)-[:AMIGO]->(Luis)-[:TRABAJA_EN]->(Google)
```

#### Index-free adjacency — la ventaja técnica clave

En una base de datos SQL, para hacer un JOIN debes buscar en un índice (B-tree) para encontrar las filas relacionadas. Si tienes 1M de usuarios con 500M de relaciones, cada salto de JOIN es una búsqueda en ese índice enorme.

En Neo4j, **cada nodo tiene punteros directos a sus relaciones**. No hay índice que buscar:

```
Nodo "Ana" en disco:
  {
    id: 1,
    propiedades: { nombre: "Ana", ciudad: "CDMX" },
    primera_relacion: → puntero directo a la primera relación de Ana
  }

Relación "Ana SIGUE Luis":
  {
    tipo: SIGUE,
    desde: nodo Ana,
    hasta: nodo Luis,
    siguiente_relacion_desde_Ana: → puntero a la siguiente relación de Ana
    siguiente_relacion_de_Luis_donde_es_destino: → puntero al siguiente
  }
```

Para traversar "¿a quién sigue Ana?", Neo4j sigue el puntero de Ana a su primera relación, luego salta por la lista enlazada de relaciones. Sin buscar índices. Esto es O(1) por salto, independiente del tamaño total del grafo.

Implicación: en SQL, agregar usuarios al grafo hace más lentas las queries de relaciones (índice más grande). En Neo4j, agregar usuarios no hace más lenta la traversal de las relaciones de Ana — ella solo tiene sus relaciones.

#### Consistencia: ACID completo, no eventual

Vale la pena decirlo explícitamente porque suele sorprender: Neo4j es transaccional con garantías ACID completas, igual que un motor relacional — `BEGIN`, la transacción, `COMMIT` o rollback si algo falla. No hay ninguna presión hacia consistencia eventual solo por tener un modelo de grafo; el modelo de datos (nodos y relaciones en vez de tablas) y el nivel de consistencia son decisiones independientes entre sí. En cluster, la replicación de escritura se coordina vía Raft, no vía gossip sin coordinador como en Cassandra — otra señal de que "no relacional" y "consistencia relajada" no son la misma cosa.

#### Cuándo usar Neo4j

- Cuando las traversals de múltiples saltos son queries frecuentes
- Detección de fraude (¿comparte Ana directores con una empresa en lista negra a través de 3 saltos?)
- Recomendaciones basadas en grafo ("personas que compraron X también compraron Y")
- Gestión de conocimiento, ontologías, knowledge graphs
- Redes de dependencias (¿qué servicios dependen de este componente?)

#### Cuándo NO usar Neo4j

- Cuando tus entidades tienen pocas relaciones simples (un JOIN de 2 tablas no necesita grafo)
- Para datos tabulares con queries de agregación — SQL es más eficiente
- Cuando necesitas escala horizontal masiva — Neo4j es difícil de shardear

---

## 12. Bases de datos columnares / OLAP {#olap}

### El problema que resuelven

Existen dos patrones de carga completamente diferentes:

**OLTP (Online Transaction Processing):**

- Muchas transacciones pequeñas y concurrentes
- Acceso a filas individuales: "dame el pedido número 12345"
- Escrituras frecuentes
- Optimizado para: PostgreSQL, MySQL

**OLAP (Online Analytical Processing):**

- Pocas queries grandes y complejas
- Agrega muchas filas: "dame las ventas totales por región del último año"
- Datos históricos que raramente se modifican
- Optimizado para: ClickHouse, BigQuery, Redshift (distribuidos) — o DuckDB (embebido, nodo único, sin nada que operar) cuando el volumen no exige distribuir

### 12.1 ClickHouse

**Diseñado para:** analítica en tiempo real a escala de petabytes con SQL  
**Fortaleza principal:** la base de datos columnar más rápida del mercado para OLAP

#### MergeTree — la familia de motores de tabla

```sql
-- La tabla más básica en ClickHouse
CREATE TABLE ventas (
  fecha        Date,
  tienda_id    UInt32,
  producto_id  UInt32,
  cantidad     UInt32,
  precio       Float32
) ENGINE = MergeTree()
  PARTITION BY toYYYYMM(fecha)    -- partición por mes
  ORDER BY (tienda_id, fecha);    -- sorting key (index primario implícito)
```

**Cómo MergeTree almacena datos:**

1. Datos insertados se ordenan por la sorting key
2. Se guardan en "parts" (fragmentos de datos ordenados, columnares)
3. En background, los parts se fusionan (merge) → de ahí el nombre MergeTree
4. Cada columna es un archivo separado en disco

```
ventas/
  fecha.bin        ← valores de fecha comprimidos
  tienda_id.bin    ← valores de tienda_id comprimidos
  producto_id.bin  ← ...
  cantidad.bin     ← ...
  precio.bin       ← ...
  primary.idx      ← índice primario esparcido (sparse index)
```

#### Índice primario esparcido (sparse index)

A diferencia de un B-tree que indexa cada fila, ClickHouse usa un **índice esparcido**: guarda solo 1 entrada de índice cada 8192 filas (granule).

```
Index granule = 8192 filas

primary.idx:
  Mark 0: tienda_id=1, fecha=2024-01-01  → granule 0 (filas 0-8191)
  Mark 1: tienda_id=1, fecha=2024-03-15  → granule 1 (filas 8192-16383)
  Mark 2: tienda_id=2, fecha=2024-01-01  → granule 2 (filas 16384-24575)
  ...

Query: WHERE tienda_id = 1 AND fecha BETWEEN '2024-01-01' AND '2024-02-28'
  → Índice primario identifica marks relevantes (granules 0 y posiblemente 1)
  → Solo se leen esos granules
  → 8192 filas son mucho menores que la tabla completa
```

#### Velocidad de inserción

ClickHouse no escribe fila por fila. Escribe en **batches** (grupos):

```
Recomendación: insertar en bloques de 10,000-100,000 filas
  → ClickHouse ordena el bloque, comprime cada columna, escribe un part

Problema: insertar una fila a la vez → crea un part por fila → muchos parts pequeños
  → Queries lentas hasta que el background merge los compacte
  → Regla: mínimo 1000 filas por INSERT, ideal 10,000+
```

#### Queries de ejemplo y velocidad típica

```sql
-- "Ventas totales por mes del último año" sobre 1 billón de filas
SELECT
  toYYYYMM(fecha) as mes,
  sum(cantidad * precio) as ingresos
FROM ventas
WHERE fecha >= '2023-01-01'
GROUP BY mes
ORDER BY mes;

-- ClickHouse típico: 0.1 - 2 segundos procesando 50-100GB de datos
-- PostgreSQL equivalente: 10-30 minutos
-- La diferencia: ClickHouse lee solo las columnas fecha, cantidad, precio
--               y usa SIMD/vectorization para sumar millones de valores
```

#### Cuándo usar ClickHouse

- Dashboards analíticos en tiempo real
- Análisis de logs y eventos
- Data warehouses que necesitan query latency baja (segundos, no minutos)
- Cuando tus datos son append-only o raramente se modifican

#### Cuándo NO usar ClickHouse

- OLTP — updates de filas individuales son muy lentos
- Cuando necesitas transacciones ACID
- Para volúmenes pequeños — la complejidad no vale la pena bajo 10M de filas

---

### 12.2 BigQuery y Redshift (Data Warehouses en la nube)

**BigQuery (Google)** y **Amazon Redshift** resuelven el mismo problema que ClickHouse pero como servicios completamente administrados:

| Característica             | ClickHouse               | BigQuery                                         | Redshift         |
| -------------------------- | ------------------------ | ------------------------------------------------ | ---------------- |
| Hosting                    | Self-hosted o cloud      | Solo Google Cloud                                | Solo AWS         |
| Modelo de precios          | Por hardware             | Por TB escaneado (on-demand) o slots (flat-rate) | Por nodo/hora    |
| Escala máxima              | Múltiples PB con cluster | Virtualmente ilimitado                           | PB con RA3 nodes |
| Velocidad query            | Más rápido en general    | Muy rápido                                       | Rápido           |
| Separación compute/storage | ClickHouse Cloud sí      | Sí                                               | Sí (RA3)         |

**BigQuery** tiene la característica de cobrar solo por datos escaneados — si tu query lee 100GB, pagas por 100GB aunque la tabla tenga 10TB. Esto incentiva a usar particionamiento y clustering para reducir el scaneado.

```sql
-- BigQuery: tabla particionada por fecha y clustered por categoria
CREATE TABLE tienda.ventas
PARTITION BY DATE(fecha)
CLUSTER BY categoria
AS SELECT ...;

-- Query que aprovecha partición y clustering:
SELECT SUM(total) FROM tienda.ventas
WHERE DATE(fecha) = '2024-03-15'  -- usa partición → solo lee 1 partición
  AND categoria = 'electronica';  -- usa clustering → solo lee ese cluster
-- Costo: mucho menor que escanear toda la tabla
```

---

### 12.3 DuckDB — columnar sin distribuir

ClickHouse, BigQuery y Redshift asumen que vas a necesitar distribuir — es el acoplamiento natural "columnar + sharding + eventual" que domina esta categoría. DuckDB responde la pregunta de "para volúmenes pequeños, la complejidad no vale la pena" (arriba, en "Cuándo NO usar ClickHouse") con una estructura columnar completa que vive **embebida en un solo proceso**, igual que SQLite lo hace para OLTP.

**Diseñado para:** analítica sobre datasets que caben en un solo servidor, sin levantar ningún servicio  
**Fortaleza principal:** es "SQLite para OLAP" — cero infraestructura, ejecución vectorizada, SQL estándar

```sql
-- Se usa como una librería, no como un servidor:
import duckdb
con = duckdb.connect("analitica.duckdb")
con.sql("SELECT categoria, SUM(total) FROM ventas GROUP BY categoria").show()

-- Puede consultar directamente archivos Parquet o CSV sin cargarlos antes:
con.sql("SELECT * FROM 'ventas_2024.parquet' WHERE categoria = 'electronica'")
```

La diferencia con ClickHouse no es la estructura de almacenamiento (ambos son columnares con ejecución vectorizada) — es la Capa 2. DuckDB no reparte nada entre nodos ni asume que alguna vez lo necesitarás; ClickHouse asume que sí desde el diseño. Por eso DuckDB no compite con ClickHouse en volumen máximo, compite con "levantar un ClickHouse de un solo nodo para un análisis que dura una tarde".

#### Cuándo usar DuckDB

- Análisis exploratorio o notebooks (pandas/Python, R) sobre datasets de hasta decenas de GB
- Consultar archivos Parquet/CSV directamente sin un pipeline de carga
- Analítica embebida dentro de una aplicación, sin operar un servicio de base de datos aparte

#### Cuándo NO usar DuckDB

- Cuando el dataset ya no cabe cómodo en un solo servidor — ahí es cuando ClickHouse, BigQuery o Redshift empiezan a justificarse
- Cuando muchos procesos o usuarios necesitan escribir concurrentemente al mismo archivo

---

## 13. Bases de datos vectoriales {#vectorial}

### El problema que resuelven

Ningún motor visto hasta ahora responde bien la pregunta "¿qué es lo más parecido a esto?", cuando "esto" es el significado de un texto, una imagen o un audio, no un valor exacto de una columna. Un índice B-tree encuentra `WHERE precio = 100` en tiempo logarítmico. Un inverted index (sección 9) encuentra documentos que contienen ciertas palabras exactas. Ninguno de los dos entiende que "perro" y "cachorro" están relacionados semánticamente, o que dos fotos de playas distintas se parecen entre sí aunque no compartan un solo píxel.

La solución de la industria fue representar el significado como un **vector**: una lista de algunos cientos o miles de números (típicamente 384 a 1536 dimensiones) generada por un modelo de embeddings (OpenAI `text-embedding-3`, Cohere, modelos open-source como BERT o sentence-transformers), de forma que dos vectores "cercanos" en ese espacio representan cosas semánticamente parecidas. El problema técnico que queda es encontrar, dado un vector de consulta, los k vectores más cercanos entre millones o miles de millones, sin compararlo contra todos uno por uno — eso sería O(n) por consulta, inviable a esa escala.

### 13.1 Mecánica: cómo se busca "lo más parecido" sin comparar contra todo

La respuesta de la industria es sacrificar exactitud a cambio de velocidad: en vez de garantizar el vecino más cercano exacto, se busca _approximate nearest neighbor_ (ANN), aceptando que el resultado sea correcto casi siempre (medido como _recall_, el porcentaje de veces que el resultado aproximado coincide con el exacto) a cambio de responder en milisegundos en vez de segundos. Dos estructuras dominan cómo se logra esto:

**HNSW (Hierarchical Navigable Small World):** un grafo de navegación organizado en capas, donde las capas superiores tienen pocos nodos y conexiones largas (para saltar rápido entre zonas lejanas del espacio) y la capa inferior tiene todos los nodos con conexiones cortas (para refinar la búsqueda localmente).

```
Capa 2 (pocos nodos, saltos largos):      A ─────────────── F
                                           │                 │
Capa 1 (más nodos, saltos medios):    A ── C ────── D ────── F
                                       │    │        │        │
Capa 0 (todos los nodos, saltos cortos): A──B──C──D──E──F──G──H

Búsqueda de un vector query:
  1. Empieza en un punto de entrada de la capa más alta (A)
  2. Se mueve al vecino más cercano al query en esa capa
  3. Baja una capa cuando ningún vecino mejora la distancia actual
  4. Repite hasta llegar a la capa 0 y devolver los k más cercanos
```

**IVF (Inverted File Index):** en vez de un grafo, se agrupan los vectores en clusters mediante k-means, y una consulta solo compara contra los clusters más prometedores, no contra el índice completo.

```
1. Se entrena k-means sobre una muestra de vectores → N centroides
2. Cada vector del dataset se asigna a su centroide más cercano
3. Query: se identifican los `nprobe` centroides más cercanos al vector
   de consulta, y solo se hace la comparación exacta dentro de esos
   clusters, ignorando el resto

   Centroide-1 (300 vectores)     Centroide-2 (280 vectores)   ...
          ↑
   el query cae cerca de Centroide-1 → solo se buscan esos 300
   vectores, no los millones del índice completo
```

HNSW suele dar mejor recall a la misma latencia, a cambio de más memoria (el grafo completo debe vivir en RAM). IVF es más económico en memoria y más simple de actualizar, pero típicamente necesita un `nprobe` más alto (revisar más clusters) para igualar el recall de HNSW, lo que sacrifica parte de la velocidad que se buscaba ganar.

**Métricas de distancia:** ninguna de las dos estructuras define qué significa "cercano" — eso lo decide la métrica elegida.

| Métrica                      | Qué mide                                         | Cuándo se usa                                                                                  |
| ---------------------------- | ------------------------------------------------ | ---------------------------------------------------------------------------------------------- |
| Coseno (cosine similarity)   | El ángulo entre dos vectores, ignora su magnitud | La más común para embeddings de texto, donde la dirección importa más que el tamaño del vector |
| Producto punto (dot product) | Como coseno, pero sensible a la magnitud         | Cuando la magnitud del vector codifica algo relevante (ej. popularidad de un ítem)             |
| Euclidiana (L2)              | Distancia en línea recta entre los dos puntos    | Común en embeddings de imágenes                                                                |

### 13.2 Pinecone — el extremo gestionado

**Diseñado para:** búsqueda semántica en producción sin operar infraestructura
**Fortaleza principal:** completamente serverless — escala, sharding y el índice HNSW subyacente los gestiona el proveedor, no tú

```python
from pinecone import Pinecone

pc = Pinecone(api_key="...")
index = pc.Index("productos")

# Insertar vectores con metadata
index.upsert(vectors=[
    {"id": "prod-1", "values": [0.12, 0.03, "..."], "metadata": {"categoria": "electronica", "precio": 599}}
])

# Buscar los 10 más parecidos, filtrando por metadata (hybrid search)
index.query(
    vector=[0.15, 0.02, "..."],
    top_k=10,
    filter={"categoria": {"$eq": "electronica"}}
)
```

#### Cuándo usar Pinecone

- RAG o búsqueda semántica en producción, sin querer operar infraestructura de vectores
- Necesitas escalar a cientos de millones de vectores sin diseñar tú mismo el sharding
- El presupuesto para un servicio gestionado tiene sentido frente al costo de operarlo tú mismo

#### Cuándo NO usar Pinecone

- Dataset pequeño (cientos de miles de vectores o menos), donde correrlo embebido es más simple y más barato
- Ya tienes PostgreSQL y prefieres evitar sincronizar un motor más (ver pgvector, abajo)
- Necesitas mantener todos los datos on-premise por cumplimiento normativo

### 13.3 pgvector — vectorial dentro de PostgreSQL

**Diseñado para:** agregar búsqueda semántica a una aplicación que ya vive en PostgreSQL, sin sumar un motor nuevo
**Fortaleza principal:** hybrid search real — combinar similitud vectorial con SQL normal (joins, filtros, transacciones ACID) en la misma consulta

Este es el mismo patrón que ya viste con MyRocks en la Capa 1: un motor relacional al que se le agrega una capacidad de otra categoría, en vez de migrar a un motor nuevo. pgvector no reemplaza el modelo relacional de PostgreSQL, le agrega un tipo de dato (`vector`) y operadores de distancia.

```sql
CREATE EXTENSION vector;

CREATE TABLE productos (
  id SERIAL PRIMARY KEY,
  nombre TEXT,
  categoria TEXT,
  precio NUMERIC,
  embedding VECTOR(1536)
);

CREATE INDEX ON productos USING hnsw (embedding vector_cosine_ops);

-- Los 10 productos más parecidos semánticamente, pero solo de una
-- categoría y bajo cierto precio: hybrid search real, con ACID incluido
SELECT nombre, precio
FROM productos
WHERE categoria = 'electronica' AND precio < 1000
ORDER BY embedding <=> '[0.12, 0.03, "..."]'
LIMIT 10;
```

El costo de esta comodidad: pgvector corre sobre la misma Capa 2 que el resto de PostgreSQL (nodo único / primary-replica, sin sharding nativo — la misma limitación que ya viste en la sección 1 y en la tabla de fingerprints). Para cientos de millones de vectores con necesidad de escritura distribuida, un motor vectorial nativo se vuelve más apropiado.

#### Cuándo usar pgvector

- Ya tienes datos relacionales y necesitas combinar filtros SQL con similitud semántica en la misma consulta
- El volumen de vectores cabe cómodo en un solo servidor (hasta varios millones, con buen hardware)
- Prefieres una sola fuente de verdad con ACID, en vez de sincronizar dos motores distintos

#### Cuándo NO usar pgvector

- Cientos de millones de vectores con necesidad de escritura distribuida sin coordinador
- Necesitas optimizaciones de un motor vectorial nativo (cuantización agresiva, aceleración por GPU) que pgvector no ofrece

### 13.4 Milvus, Weaviate y Qdrant — vectoriales nativos, open-source

Los tres resuelven el mismo problema que Pinecone (motor dedicado, nativo, con su propio sharding) pero como software que tú mismo operas, no como servicio gestionado.

| Motor    | Diferenciador principal                                                                                                                                                   |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Milvus   | El más orientado a escala masiva: arquitectura con cómputo y almacenamiento separados, pensada desde el diseño para miles de millones de vectores                         |
| Weaviate | Incluye un módulo de generación de embeddings integrado (no necesitas llamar a un modelo externo por tu cuenta) y un modelo de datos más cercano a documentos, con schema |
| Qdrant   | Escrito en Rust, el más ligero de operar de los tres, popular para self-hosting en proyectos medianos                                                                     |

Los tres comparten fingerprint en L1 (vector index, HNSW en la práctica) y L4 (vectorial + metadata); la diferencia real está en L2 (qué tan preparados están para escala masiva) y en la capa de producto (Weaviate con embeddings integrados, Qdrant con simplicidad operativa).

### Comparación general

|                      | Pinecone                                        | pgvector                                        | Milvus / Weaviate / Qdrant                                      |
| -------------------- | ----------------------------------------------- | ----------------------------------------------- | --------------------------------------------------------------- |
| Operación            | Gestionado (serverless)                         | Extensión sobre tu PostgreSQL                   | Tú operas la infraestructura                                    |
| Escala               | Cientos de millones en adelante                 | Hasta varios millones, limitado a un nodo       | Cientos de millones en adelante (self-hosted)                   |
| Hybrid search        | Filtros por metadata, no SQL completo           | SQL completo, con joins y ACID                  | Filtros por metadata, no SQL completo                           |
| Cuándo tiene sentido | Producción a escala, sin operar infraestructura | Ya usas PostgreSQL y el volumen cabe en un nodo | Necesitas escala masiva sin depender de un proveedor gestionado |

### El patrón de uso real: RAG (Retrieval-Augmented Generation)

La razón por la que estos motores se volvieron populares no es la búsqueda semántica en abstracto, es un patrón de arquitectura concreto detrás de casi todas las aplicaciones de IA generativa que "conocen tus datos":

```
Indexación (una vez, o cada vez que cambian los datos):
  Documento → modelo de embeddings → vector → se guarda en el motor
  vectorial junto con el texto original y su metadata

Consulta (en cada pregunta del usuario):
  Pregunta del usuario → modelo de embeddings → vector de consulta
       → motor vectorial: los k documentos más parecidos
       → esos documentos se insertan como contexto en el prompt
       → el LLM genera la respuesta usando ese contexto real,
         en vez de solo lo que memorizó durante su entrenamiento
```

Esto explica por qué casi ningún motor vectorial se usa solo: siempre aparece junto a un modelo de embeddings (para generar los vectores) y un LLM (para generar la respuesta final), nunca como la única pieza del sistema.

#### Cuándo usar una base de datos vectorial

- Búsqueda semántica: encontrar por significado, no por coincidencia exacta de texto
- RAG: dar a un LLM acceso a información específica y actualizada sin reentrenarlo
- Sistemas de recomendación basados en similitud de contenido, no en reglas escritas a mano
- Detección de duplicados o de contenido similar (imágenes, documentos)

#### Cuándo NO usar una base de datos vectorial

- Cuando la búsqueda es por coincidencia exacta o por rango: ahí un B-tree normal es más simple y más rápido
- Datasets pequeños (unos pocos miles de vectores): una búsqueda por fuerza bruta en memoria, sin ningún motor especializado, es suficientemente rápida
- Cuando lo que necesitas es full-text search clásico con boosting de relevancia: ahí Elasticsearch (sección 9) sigue siendo la herramienta correcta

---

# PARTE 3 — DECISIONES DE ARQUITECTURA

---

## 14. El fenómeno de convergencia {#convergencia}

### Qué está pasando

Desde ~2015, casi todos los motores que ya viste en este documento agregaron al menos una feature que originalmente pertenecía a otra categoría. Ninguno cambió de categoría — todos ampliaron su superficie sin dejar de ser lo que eran:

| Motor      | Categoría original (L4 nativo) | Features de otra categoría agregadas                                                       |
| ---------- | ------------------------------ | ------------------------------------------------------------------------------------------ |
| PostgreSQL | Relacional                     | JSONB (documento), pg_trgm (búsqueda), TimescaleDB (serie de tiempo), pgvector (vectorial) |
| MySQL      | Relacional                     | JSON, Document Store (documento)                                                           |
| MongoDB    | Documento                      | Transacciones ACID multi-documento (relacional, desde v4.0)                                |
| DynamoDB   | Clave-valor / documento        | Transacciones, streams, búsqueda                                                           |
| Redis      | Clave-valor                    | Streams, búsqueda, JSON, grafos (vía módulos)                                              |
| Cassandra  | Wide-column                    | Lightweight transactions / LWT (relacional)                                                |
| ClickHouse | Columnar / OLAP                | SQL estándar, updates (lentos pero posibles), JSON                                         |

### Por qué pasa: la causa de negocio

Es presión de mercado, en el sentido más literal: los equipos que ya invirtieron años operando un motor tienen casos de uso puntuales que necesitan una capacidad de otra categoría, y prefieren pedírsela a su motor actual antes que operar uno nuevo solo para eso. Cada fila de la tabla de arriba es la respuesta de un fabricante a esa presión.

### Por qué esto no borra las diferencias: el motivo arquitectónico real

Aquí conviene usar lo que ya se construyó en la sección 1, en vez de una intuición nueva. Cada fila de la tabla de arriba es, técnicamente, un **acoplamiento roto** (el mismo concepto de "Acoplamientos rotos", sección 1): un motor cuya Capa 1 y Capa 2 se diseñaron para un patrón de acceso específico, al que ahora se le pide una capacidad que pertenece a un patrón de acceso distinto, sin haber rediseñado esa base.

- Cuando MongoDB le agrega transacciones ACID multi-documento a un motor pensado para leer y escribir un documento entero a la vez, necesita el mismo tipo de trabajo extra que MyRocks necesitó para tener ACID sobre una LSM-tree: coordinar un snapshot consistente entre varios documentos que, en su diseño original, ni siquiera están relacionados entre sí.
- Cuando Cassandra le agrega lightweight transactions a un motor peer-to-peer sin coordinador, no puede dar ACID completo — seguiría necesitando un protocolo de consenso real, que es justo lo que su arquitectura evita a propósito. Por eso son "ligeras" (usan una variante de Paxos acotada a una sola fila) y no transacciones completas.
- Cuando ClickHouse le agrega `UPDATE`/`DELETE` a un motor columnar pensado para insertar en lote y nunca tocar lo ya escrito, la operación termina reescribiendo partes enteras de las columnas afectadas, porque su almacenamiento nunca fue diseñado para modificar una fila puntual.

La regla que se sigue de esto, sin necesitar una analogía nueva: **una feature agregada nunca cambia la arquitectura de fondo del motor, solo construye algo por encima de ella.** El costo de esa construcción (rendimiento, alcance limitado, o ambos) es la huella que deja el acoplamiento roto, y se predice con la misma pregunta de siempre: ¿qué tuvo que romperse en L1 o L2 para que esto fuera posible?

### El costo real, en números

| Motor      | Operación nativa                      | Operación "convergida"                    | Por qué cuesta más                                                                                                  |
| ---------- | ------------------------------------- | ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| MongoDB    | Escritura de un solo documento (<1ms) | Transacción ACID multi-documento (5-50ms) | Exige coordinar un snapshot entre documentos que el motor no relaciona de forma nativa                              |
| Cassandra  | Escritura normal, sin coordinación    | Lightweight transaction (LWT)             | Varias rondas de Paxos por escritura, justo el costo que su arquitectura peer-to-peer existe para evitar            |
| ClickHouse | `INSERT` (columnar, append-only)      | `UPDATE` / `DELETE`                       | Reescribe partes enteras de las columnas afectadas; nunca tan rápido como un motor pensado para updates fila a fila |

No todas las features convergidas cuestan en rendimiento — algunas cuestan en alcance. PostgreSQL con JSONB es rápido (tiene índice GIN real), pero no iguala la flexibilidad operacional de MongoDB para reestructurar documentos anidados sobre la marcha; a cambio, sí puede unir ese JSONB con el resto de las tablas relacionales en la misma query, algo que MongoDB no hace de forma nativa. Ninguno de los dos "ganó": cada uno conservó la forma de su arquitectura original.

### Cómo distinguir una feature real de una superficial

Es la misma sospecha sistemática de "Por qué L5 engaña" (sección 1), aplicada al caso inverso: allá se trataba de no asumir que una palabra _ausente_ en el lenguaje fuera una limitación real; aquí se trata de no asumir que una palabra _presente_ — porque el motor "ahora soporta X" tras converger — signifique la misma capacidad que en un motor nativo de esa categoría. Tres preguntas lo confirman o lo desmienten:

| Pregunta                                                                         | Qué revela                                                                                                            |
| -------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- |
| ¿Cuál es el costo de rendimiento comparado con un motor nativo de esa categoría? | Si el costo es alto (MongoDB con transacciones), la feature existe para casos puntuales, no para uso constante        |
| ¿La feature es completa o limitada?                                              | Las LWT de Cassandra no son ACID completo como en PostgreSQL; siguen atadas a las restricciones de su L2 peer-to-peer |
| ¿Requiere cambiar el modelo de datos o la forma de acceder a él?                 | Usar transacciones en MongoDB exige reescribir el código para abrir una sesión explícita; no es transparente          |

---

## 15. Polyglot persistence {#polyglot}

### Qué es, en términos de lo ya visto

Ya viste el principio en la sección 1, en "Un mismo dominio, ocho formas distintas": una aplicación real casi nunca tiene un solo patrón de acceso, tiene varios, y cada uno tiene un modelo de datos que le queda mejor. Polyglot persistence es la traducción de ese principio a producción: en vez de forzar todos esos patrones dentro de un solo motor, cada uno vive en el motor diseñado para él, y la aplicación los combina.

```
┌────────────────────────────────────────────────────────────────┐
│                          Aplicación                             │
│  ┌───────────┐   ┌──────────┐   ┌──────────────┐  ┌──────────┐ │
│  │ PostgreSQL│   │  Redis   │   │Elasticsearch │  │ClickHouse│ │
│  │ Fuente de │   │ Caché de │   │  Búsqueda    │  │ Analítica│ │
│  │ verdad,   │   │ sesiones,│   │  de texto,   │  │ y        │ │
│  │ ACID      │   │ rate     │   │  facets      │  │ reportes │ │
│  │           │   │ limit    │   │              │  │          │ │
│  └───────────┘   └──────────┘   └──────────────┘  └──────────┘ │
└────────────────────────────────────────────────────────────────┘
```

### La parte que casi nunca se explica bien: cómo se mantienen sincronizados

Si PostgreSQL es la fuente de verdad y los demás son copias derivadas, ¿cómo llegan los cambios de uno a los otros sin que se desincronicen en silencio? Hay tres formas. Solo una es la que usan en serio las empresas grandes.

**1. Dual writes — el antipatrón tentador**

```
App ──escribe──> PostgreSQL      ✓ éxito
    └─escribe──> Elasticsearch   ✗ falla (timeout, red, lo que sea)

Resultado: los dos ya no coinciden, y nada en el sistema lo sabe todavía.
```

Es lo primero que se le ocurre a cualquiera porque parece simple, pero no existe un 2PC entre dos motores distintos (sección 4.6): no hay forma de garantizar que ambas escrituras ocurran juntas o ninguna ocurra. La divergencia es silenciosa — nadie se entera hasta que un usuario reporta un resultado que no debería existir, o que no aparece cuando debería.

**2. Change Data Capture (CDC) — la forma profesional más común**

```
App ──escribe (única escritura síncrona, con ACID)──> PostgreSQL
                                                          │ WAL
                                                          ▼
                                              Conector CDC (ej. Debezium)
                                                          │
                                                          ▼
                                                Kafka (log de eventos)
                                              /           │           \
                                             ▼            ▼            ▼
                                     Elasticsearch      Redis      ClickHouse
                                     (cada consumidor avanza a su propio ritmo)
```

La aplicación escribe, de forma síncrona, solo en el motor que es la fuente de verdad. Un proceso aparte lee el WAL de ese motor (el mismo WAL de `guia_transacciones_db.md`) y publica cada cambio como un evento; cada sistema downstream lo consume a su ritmo. Es, literalmente, la razón por la que Kafka existe: LinkedIn lo construyó porque tenía muchos sistemas necesitando enterarse de los cambios de otros muchos sistemas, y una integración punto a punto entre cada par crece como N². Un log central al que cualquiera se suscribe convierte ese problema en N conexiones, no N².

**3. Transactional outbox — cuando no puedes leer el WAL directamente**

```
App ──(una sola transacción ACID)──> PostgreSQL
                                        ├── tabla "pedidos" (el dato real)
                                        └── tabla "outbox" (el evento a publicar)

Proceso aparte: lee "outbox" → publica en Kafka → borra la fila
```

La escritura del dato y el registro del evento ocurren en la misma transacción, así que nunca existe un estado donde el dato se guardó pero el evento no saldrá a publicarse, o viceversa.

### Ventajas reales

| Ventaja                                      | Por qué es real, no solo teórica                                                                                                                                                                   |
| -------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Rendimiento en el punto fuerte de cada motor | Una búsqueda de texto en Elasticsearch es órdenes de magnitud más rápida y relevante que un `LIKE '%...%'`; un dashboard en ClickHouse corre en milisegundos lo que en PostgreSQL tardaría minutos |
| Aislamiento de fallos                        | Si Elasticsearch se cae, la búsqueda se degrada, pero comprar o pagar sigue funcionando porque vive en PostgreSQL, que ni se enteró                                                                |
| Escalado independiente                       | El clúster de búsqueda escala para su propio tráfico sin tocar la capacidad transaccional del motor principal, y viceversa                                                                         |
| Equipos en paralelo                          | Cada equipo avanza sobre su propio store sin bloquearse ni competir por el mismo esquema                                                                                                           |

### Desventajas y costo real

| Costo                                         | En qué se nota                                                                                                                                                                   |
| --------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Complejidad operativa multiplicada, no sumada | No son "4 motores", son 4 expertises distintas, 4 estrategias de backup, 4 superficies que parchear                                                                              |
| Lag de consistencia medible                   | Un pipeline de CDC típico tarda de milisegundos a segundos; bajo carga o con un consumidor caído, puede crecer a minutos — un usuario puede no encontrar algo que acaba de crear |
| Debugging que cruza sistemas                  | La pregunta deja de ser "¿qué hizo mal mi query?" y pasa a ser "¿el dato nació mal, se perdió en el pipeline, o el consumidor lo procesó mal?"                                   |
| Costo de infraestructura literal              | Cada motor adicional es cómputo, almacenamiento y, muchas veces, licencia o soporte, incluso con poco volumen                                                                    |

### Cómo se hace en empresas grandes, en la práctica

- **Netflix** es polyglot casi por definición: Cassandra para historial de reproducción a escala masiva de escritura, MySQL para facturación (donde ACID importa), Elasticsearch para búsqueda de catálogo, y una capa de caché (EVCache, sobre Memcached) delante de casi todo. Cada elección sigue el mismo framework de la sección 1: el patrón de acceso decide el motor, no una preferencia.
- **LinkedIn** inventó Kafka exactamente para este problema: demasiados sistemas necesitando enterarse de los cambios de demasiados otros sistemas. Nació como la tubería central que conecta las bases operacionales con el data warehouse, los índices de búsqueda y las recomendaciones, sin integrar cada par a mano.
- **Uber** mantiene el dato transaccional central (viajes, pagos) en motores relacionales con garantías fuertes, y lo alimenta hacia analítica y machine learning vía streaming (Kafka de nuevo), en vez de que cada sistema analítico le pegue directamente a la base transaccional.

El patrón que se repite en los tres: **la fuente de verdad es siempre un solo motor con garantías fuertes, y todo lo demás se deriva de ella de forma asíncrona, nunca al revés.** Nadie construye un sistema donde varios motores escriben, de forma independiente, el mismo dato "de verdad" — eso es el dual-write antipatrón, a escala de empresa.

### Cuándo vale la pena, en la práctica

No es una decisión de "arquitectura moderna" que se toma por adelantado — es una respuesta a evidencia medida:

| Señal real que ya tienes                                                                      | Motor a agregar                                                                            |
| --------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| La búsqueda de texto (`LIKE`, full-text) ya no alcanza en relevancia o velocidad              | Elasticsearch, como índice derivado, nunca como fuente de verdad                           |
| Las mismas lecturas repetidas están saturando tu base transaccional                           | Redis, como caché delante del motor principal                                              |
| Los reportes o dashboards tardan minutos y compiten por recursos con el tráfico transaccional | ClickHouse (u otro columnar), alimentado de forma asíncrona                                |
| Necesitas búsqueda semántica sobre contenido                                                  | pgvector si el volumen cabe en tu Postgres, un motor vectorial dedicado si no (sección 13) |

**Regla práctica:** no empieces con polyglot persistence desde el día 1. Empieza con un solo motor (casi siempre PostgreSQL) como fuente de verdad, y agrega cada pieza nueva solo cuando tengas el problema específico y medido que esa pieza resuelve, no antes. Cada motor que agregas es una promesa operativa que vas a sostener para siempre, no una capacidad gratis.

---

## 16. Framework de decisión {#decision}

Este framework no es nuevo — es el mismo orden de prioridad de capas ya construido en la Sección 1 (L4 → L3 → L2, el que "SQL vs NoSQL" usa para responder con capas y motores concretos en vez de con una etiqueta), aplicado aquí hacia una lista de nombres de motor ya resueltos. La Sección 1 da el razonamiento completo capa por capa y la tabla de fingerprints para comparar cualquier par de motores línea por línea; esto es la versión de acceso rápido, para cuando ya sabes qué preguntarte y solo te falta el nombre.

### Las 3 preguntas en orden

```
PREGUNTA 1 (Capa 4 — Modelo de datos): ¿Cómo están estructurados tus datos?
────────────────────────────────────────────────────────────────────────────
¿Tienes relaciones entre entidades que necesitas consultar con JOIN?
    SÍ → ir a Pregunta 2
    NO → ¿Qué tipo de dato?
          Documento (esquema variable, JSON anidado) → MongoDB / Firestore
          Clave-valor simple → Redis / DynamoDB
          Caché pura, sin necesidad de sobrevivir un reinicio → Memcached
          Texto para búsqueda → Elasticsearch / Meilisearch
          Nodos y relaciones → Neo4j
          Mediciones con timestamp → InfluxDB / TimescaleDB
          Agrupaciones analíticas masivas → ClickHouse / BigQuery (o DuckDB si es de bajo volumen y no necesitas distribuir)

PREGUNTA 2 (Capa 3 — Consistencia): ¿Qué garantías necesitas cuando algo falla?
─────────────────────────────────────────────────────────────────────────────
¿El costo de perder o duplicar una transacción es inaceptable?
    SÍ (dinero, inventario, datos críticos) → motor ACID → Pregunta 3
    NO (contadores, logs, actividad, caché) → eventual consistency es OK
       → ¿Escala masiva de escrituras? → Cassandra / DynamoDB
       → ¿Velocidad extrema? → Redis

PREGUNTA 3 (Capa 2 — Distribución): ¿Cuánto necesitas escalar y en qué dimensión?
─────────────────────────────────────────────────────────────────────────────────
¿Cuál es tu carga dominante?
    OLTP estándar (<10M filas, <10K QPS) → PostgreSQL (un nodo bien tuneado)
    OLTP con muchas lecturas, réplicas OK → PostgreSQL + réplicas / MySQL
    OLTP distribuido global → CockroachDB / TiDB / YugabyteDB
    OLAP / analítica a gran escala → ClickHouse / BigQuery / Redshift
    OLAP embebido, sin distribuir → DuckDB
    OLTP + OLAP en el mismo motor → PostgreSQL + TimescaleDB / SQL Server (columnstore)
```

### Árbol de decisión completo

```
¿Es el dato principalmente temporal (series de tiempo)?
    SÍ → TimescaleDB (necesitas SQL + joins) / InfluxDB (solo métricas)
    NO ↓

¿Necesitas búsqueda de texto con relevancia?
    SÍ → Elasticsearch (logs + analítica) / Meilisearch (catálogo simple)
         + mantener fuente de verdad en PostgreSQL o MongoDB
    NO ↓

¿Las relaciones entre entidades son el dato central?
    SÍ → Neo4j / Neptune
    NO ↓

¿Necesitas máxima velocidad de escritura a escala masiva sin SQL complejo?
    SÍ → Cassandra (disponibilidad first) / DynamoDB (serverless AWS)
    NO ↓

¿El dato es efímero, de caché, o accedido siempre por clave exacta?
    SÍ → Redis
    NO ↓

¿Las queries son principalmente analíticas (agregaciones sobre muchas filas)?
    SÍ → ¿el dataset cabe en un solo servidor y no necesitas distribuir?
          SÍ → DuckDB (embebido, sin infraestructura que operar)
          NO → ClickHouse (self-hosted) / BigQuery (GCP) / Redshift (AWS)
    NO ↓

¿El esquema es variable o los datos son documentos JSON anidados?
    SÍ → MongoDB
    NO ↓

→ PostgreSQL (default para todo lo demás)
```

### La regla que simplifica todo

**Empieza con PostgreSQL.** Tiene features que cubren el 80% de casos de uso de otros motores (JSONB, arrays, full-text search básico, TimescaleDB como extensión, particionamiento). Solo muévete a otro motor cuando PostgreSQL tenga un cuello de botella demostrado — no anticipado.

---

## 17. Costo, vendor lock-in y cómo validar antes de comprometerte {#costo-lockin}

### Por qué esta sección existe

El framework de decisión anterior y los anti-patrones que siguen resuelven una sola pregunta: ¿qué motor es técnicamente correcto para mi patrón de acceso? Esa pregunta es necesaria, pero no basta. En una empresa real, dos motores pueden ser técnicamente equivalentes para tu caso y aun así uno ser la decisión correcta y el otro un error costoso, por razones que ninguna de las ocho capas captura: cuánto cuesta de verdad, qué tan caro es salir si te equivocas, y si vas a poder contratar gente que lo opere bien.

### Costo total de propiedad (TCO): más allá del precio de la licencia

| Modelo de costo                                     | Cómo funciona                                                                  | Dónde sorprende                                                                                                           |
| --------------------------------------------------- | ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------- |
| Licencia por núcleo (Oracle, SQL Server Enterprise) | Se paga por core de CPU del servidor, sin importar cuánto lo uses              | Un servidor grande "por si acaso" puede costar más en licencia que en hardware                                            |
| Instancia gestionada (RDS, Cloud SQL)               | Pagas por el tamaño de la instancia, esté o no saturada                        | Capacidad ociosa en cargas con picos, si no hay auto-scaling configurado                                                  |
| Por solicitud o unidad (DynamoDB, Firestore)        | El costo escala con el uso real, no con capacidad reservada                    | Una partición caliente o una query mal diseñada puede disparar el costo sin que nadie lo note hasta la factura            |
| Por cómputo de query (BigQuery, Snowflake)          | Pagas por los datos que escanea cada query, no por tener el servicio encendido | Una query analítica sin filtro adecuado puede escanear terabytes y costar más que un mes completo de un motor tradicional |

El costo que casi nunca entra en la hoja de cálculo inicial es el operativo: un motor con curva de aprendizaje pronunciada (Cassandra, Kafka, un clúster de Elasticsearch bien tuneado) necesita gente que sepa operarlo bajo presión, no solo instalarlo. Ese costo no aparece en ninguna factura de proveedor — aparece en la nómina y en las horas de incidentes a las 3am.

### Vendor lock-in: qué tan caro es arrepentirse

No todos los motores dejan salir igual de fácil, y esto rara vez se piensa hasta que ya es tarde.

- **DynamoDB** tiene una API y un modelo de consultas propios de AWS. Migrar fuera significa reescribir la capa de acceso a datos de la aplicación, no solo mover archivos.
- **CockroachDB y YugabyteDB**, en cambio, hablan el protocolo de PostgreSQL. Si algún día hace falta salir, en el caso simple basta con cambiar la cadena de conexión; en el peor caso, ajustar algunas extensiones específicas. El costo de salida es mucho menor.

Es una variable de decisión real y separada de si el motor "es mejor" técnicamente: dos empresas con la misma necesidad pueden preferir motores distintos porque una valora la portabilidad futura más que la otra. Vale la pena preguntarse, antes de firmar: si en tres años esto no funciona, ¿qué tan caro es salir?

### El mercado de talento, no solo el equipo que ya tienes

Aparte de si tu equipo actual sabe operar un motor, hay una pregunta distinta: ¿qué tan fácil es contratar a alguien que sepa hacerlo, cuando el equipo actual rote o el proyecto crezca? Un ingeniero con experiencia real en PostgreSQL es fácil de encontrar y relativamente barato de contratar; alguien que sepa operar Cassandra, HBase o un clúster de Kafka en producción es más escaso, y eso se refleja directamente en el salario necesario para conseguirlo y retenerlo. Elegir un motor de nicho no es solo una apuesta técnica, es una apuesta a que se va a poder seguir contratando para él en el futuro.

### Cómo validar antes de comprometerte: la metodología, no solo la decisión

El anti-patrón 5, en la siguiente sección, ya nombra el error de no definir los access patterns antes de elegir un motor distribuido. Lo que falta ahí es el procedimiento concreto para no cometerlo:

1. **Prueba con volumen real, no con datos de juguete.** El comportamiento de un índice, un plan de query o una estructura de particiones cambia de forma cualitativa entre 10,000 filas y 100 millones — no es solo "más lento", a veces es un perfil de rendimiento completamente distinto.
2. **Prueba tus queries reales, no un CRUD genérico.** Un benchmark estándar mide el motor en abstracto; solo las queries de negocio propias revelan si el patrón de acceso específico encaja o pelea contra la arquitectura del motor.
3. **Prueba fallas a propósito, no solo el camino feliz.** Mata un nodo en medio de una carga de escritura. Simula latencia de red entre regiones. La forma en que un motor se comporta bajo falla es donde más se separan las promesas de marketing de la realidad operativa.
4. **Define el criterio de éxito antes de empezar, no después.** Sin un umbral decidido de antemano ("esto necesita sostener 5,000 QPS con p99 bajo 50ms"), es fácil racionalizar seguir adelante con un motor en el que ya se invirtieron semanas, aunque los resultados sean mediocres — el sesgo de costo hundido aplica tanto a decisiones de infraestructura como a cualquier otra.

---

## 18. Anti-patrones comunes {#antipatrones}

### Anti-patrón 1: Elegir el motor "de moda"

```
Error: "Todos usan MongoDB, voy a usarlo"
Problema: MongoDB es excelente para datos de documento.
          Si tus datos son relacionales, tendrás problemas
          para hacer JOINs y mantener integridad.

Síntoma: te encuentras haciendo múltiples queries a MongoDB
         para lo que sería un solo JOIN en SQL.
Solución: PostgreSQL para datos relacionales.
```

### Anti-patrón 2: Usar Cassandra para casos de uso relacionales

```
Error: "Cassandra escala horizontalmente, lo usaré para todo"
Problema: Cassandra no tiene JOINs. No tiene índices secundarios
          flexibles. Cada access pattern requiere una tabla distinta.

Síntoma: necesitas hacer "SELECT * FROM tabla WHERE campo != primary_key"
         y resulta en full table scan o LSI/GSI costosos.
Solución: Cassandra para write-heavy, high-availability, append-only.
          Para queries ad hoc, PostgreSQL.
```

### Anti-patrón 3: Elasticsearch como base de datos principal

```
Error: "Elasticsearch tiene todo lo que necesito, lo uso como única DB"
Problema: - Consistencia eventual: documentos recién escritos pueden
            no aparecer por hasta 1 segundo
          - No tiene transacciones
          - Updates son lentos (delete + reindex internamente)
          - Puede perder datos en configuraciones incorrectas

Síntoma: pierdes datos, o los dashboards no coinciden con la realidad.
Solución: PostgreSQL/MongoDB como fuente de verdad,
          Elasticsearch como índice de búsqueda sincronizado.
```

### Anti-patrón 4: Sharding prematuro

Es el mismo error que la Capa 2 (Sección 1) explica con el nombre "por qué esta capa se decide después, no primero" — ahí sin cifras, aquí con datos concretos:

```
Error: "Voy a usar Cassandra/DynamoDB desde el día 1 para escalar"
Problema: - Complejidad operacional enorme
          - Restricciones de modelo de datos desde el inicio
          - Para la mayoría de startups, PostgreSQL en un buen servidor
            aguanta años de crecimiento

Dato real: PostgreSQL bien configurado maneja 10,000-50,000 QPS
           y terabytes de datos en un solo servidor.
           Uber empezó con MySQL. Instagram con PostgreSQL.

Solución: optimiza el motor actual (índices, queries, conexiones)
          antes de cambiar de arquitectura.
```

### Anti-patrón 5: Ignorar el modelo de acceso en sistemas distribuidos

```
Error: elegir DynamoDB o Cassandra sin definir primero los access patterns
Problema: el modelo de datos en estos sistemas se diseña alrededor
          de los access patterns. Si cambias los patterns después,
          debes rediseñar las tablas y migrar todos los datos.

Síntoma: tienes que hacer full table scans, o tus tablas de Cassandra
         generan particiones hot (una partition recibe todo el tráfico).

Solución: antes de implementar en Cassandra/DynamoDB, lista exactamente
          todas las queries que necesitas. Diseña el schema alrededor de ellas.
          Si los patterns son inciertos, usa PostgreSQL hasta que se estabilicen.
```

### Anti-patrón 6: Confundir "soporta ACID" con "está optimizado para ACID"

```
Error: "MongoDB ahora tiene ACID, es igual a PostgreSQL"
Diferencia real:
  - En PostgreSQL, TODA operación está en una transacción.
    El overhead de ACID es el default, no un extra.
  - En MongoDB, una operación de documento único es <1ms sin
    transacción explícita, pero una transacción ACID multi-documento
    sube a 5-50ms (ver sección 14, "El fenómeno de convergencia").
    Son un caso especial, no el modo principal.

Regla: pregunta no "¿soporta X?" sino "¿está optimizado para X?"
```
