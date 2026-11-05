# Guía completa de sintaxis SQL: de fundamentos a nivel profesional
## Dialecto principal: PostgreSQL 15+ · con notas para MySQL, SQL Server y Oracle

> Patrón de cada sección: **por qué existe → cómo funciona → ejemplo mínimo → variante avanzada → diferencias por motor**

---

## Tabla de contenido

### Preámbulo
- [¿Qué dialecto SQL usa esta guía?](#dialecto)
- [Estándares SQL](#estandares)
- [SQL frente a un lenguaje de programación de propósito general](#sql-vs-programacion)
- [Más allá de SQL: el mismo análisis en cualquier motor de base de datos](#mas-alla-de-sql)

### Bloque I — Fundamentos (§1–§5)
- [§1. ¿Qué es SQL? Categorías del lenguaje](#1-que-es-sql)
- [§2. Tipos de datos en PostgreSQL](#2-tipos-datos)
- [§3. DDL básico: CREATE TABLE, ALTER TABLE, DROP](#3-ddl-basico)
- [§4. DML básico: INSERT, SELECT, UPDATE, DELETE](#4-dml-basico)
- [§5. Filtros y condiciones: WHERE](#5-where)

### Bloque II — SELECT a fondo (§6–§13)
- [§6. SELECT completo: alias, ORDER BY, LIMIT, FETCH, DISTINCT ON](#6-select)
- [§7. Funciones de agregación: FILTER, ORDER BY en agregados](#7-agregacion)
- [§8. Agregados de conjunto ordenado: WITHIN GROUP, PERCENTILE](#8-within-group)
- [§9. GROUP BY, HAVING, ROLLUP, CUBE, GROUPING SETS](#9-group-by)
- [§10. JOINs: INNER, LEFT, RIGHT, FULL, CROSS, SELF, NATURAL, USING](#10-joins)
- [§11. Subconsultas y EXISTS vs IN](#11-subconsultas)
- [§12. CTEs (WITH): básicas, modificables, recursivas](#12-cte)
- [§13. Window Functions (funciones de ventana)](#13-window)

### Bloque III — NULL y expresiones condicionales (§14–§16)
- [§14. NULL: semántica, lógica de tres valores, IS DISTINCT FROM](#14-null)
- [§15. CASE, COALESCE, NULLIF, GREATEST, LEAST](#15-case)
- [§16. Casting y conversión de tipos: CAST, ::](#16-casting)

### Bloque IV — Tipos de datos avanzados (§17–§23)
- [§17. Texto: VARCHAR, TEXT y funciones de string](#17-texto)
- [§18. Numéricos: INTEGER, NUMERIC, FLOAT y funciones](#18-numericos)
- [§19. Fecha y tiempo: DATE, TIMESTAMP, TIMESTAMPTZ, INTERVAL](#19-fecha)
- [§20. JSON y JSONB: operadores, funciones, índices](#20-json)
- [§21. Arrays: declaración, operadores, funciones](#21-arrays)
- [§22. Tipos especiales: UUID, BOOLEAN, ENUM, BYTEA, INET, RANGE](#22-especiales)
- [§23. Dominios y tipos compuestos: CREATE DOMAIN, CREATE TYPE](#23-dominios)

### Bloque V — DDL avanzado (§24–§28)
- [§24. Constraints: PK, FK, UNIQUE, CHECK, EXCLUDE, DEFERRABLE](#24-constraints)
- [§25. Columnas generadas: GENERATED ALWAYS AS](#25-generated)
- [§26. Índices: B-tree, Hash, GiST, GIN, BRIN; parciales, de expresión](#26-indices)
- [§27. Vistas: CREATE VIEW, actualizables, MATERIALIZED VIEW](#27-vistas)
- [§28. Secuencias y auto-incremento: SERIAL, IDENTITY, CREATE SEQUENCE](#28-secuencias)

### Bloque VI — DML avanzado (§29–§33)
- [§29. INSERT avanzado: ON CONFLICT (UPSERT), RETURNING, multi-row](#29-insert)
- [§30. UPDATE avanzado: UPDATE FROM, RETURNING, UPDATE con CTE](#30-update)
- [§31. DELETE avanzado: DELETE USING, RETURNING, TRUNCATE vs DELETE](#31-delete)
- [§32. MERGE: WHEN MATCHED / WHEN NOT MATCHED](#32-merge)
- [§33. Locking reads: SELECT FOR UPDATE, FOR SHARE, NOWAIT, SKIP LOCKED](#33-locking)

### Bloque VII — Operadores de conjunto y construcciones adicionales (§34–§37)
- [§34. UNION, UNION ALL, INTERSECT, EXCEPT](#34-set-ops)
- [§35. Expresiones regulares: LIKE, ILIKE, SIMILAR TO, ~, regexp_*](#35-regex)
- [§36. Full-text search: to_tsvector, to_tsquery, @@, ts_rank](#36-fts)
- [§37. LATERAL joins y funciones de tabla](#37-lateral)

### Bloque VIII — Programación SQL / PL·pgSQL (§38–§45)
- [§38. Tablas temporales y COPY: carga masiva de datos](#38-temp-copy)
- [§39. PREPARE / EXECUTE / DEALLOCATE: prepared statements](#39-prepare)
- [§40. Funciones: CREATE FUNCTION, PL/pgSQL, volatilidad, dollar-quoting](#40-funciones)
- [§41. Procedimientos almacenados: CREATE PROCEDURE](#41-procedimientos)
- [§42. DO: bloques anónimos PL/pgSQL](#42-do)
- [§43. Triggers: BEFORE, AFTER, INSTEAD OF](#43-triggers)
- [§44. Cursores: DECLARE, FETCH, MOVE, CLOSE](#44-cursores)
- [§45. Manejo de errores en PL/pgSQL: EXCEPTION, RAISE, SQLSTATE](#45-errores)

### Bloque IX — Estructuras avanzadas (§46–§47)
- [§46. Particionamiento declarativo: RANGE, LIST, HASH](#46-particiones)
- [§47. Herencia de tablas: INHERITS, ONLY](#47-herencia)

### Bloque X — Seguridad, análisis y referencia (§48–§54)
- [§48. DCL: GRANT, REVOKE, roles, esquemas, search_path](#48-dcl)
- [§49. Row-Level Security (RLS): CREATE POLICY](#49-rls)
- [§50. EXPLAIN y EXPLAIN ANALYZE: leer query plans](#50-explain)
- [§51. information_schema y catálogos del sistema (pg_*)](#51-catalogo)
- [§52. Patrones de consulta comunes: paginación, top-N, pivot](#52-patrones)
- [§53. Anti-patrones SQL](#53-antipatrones)
- [§54. Diferencias entre motores: PostgreSQL vs MySQL vs SQL Server vs Oracle](#54-motores)

---

╔══════════════════════════════════════════════════════════════════════╗
║  PREÁMBULO — Sobre el lenguaje SQL                                   ║
╚══════════════════════════════════════════════════════════════════════╝

## ¿Qué dialecto SQL usa esta guía? {#dialecto}

SQL no es un lenguaje único: es un *estándar internacional* del que cada motor implementa
su propio dialecto. Esta guía usa **PostgreSQL 15+** como motor de referencia por varias razones:

| Criterio | Por qué PostgreSQL |
|----------|-------------------|
| Cumplimiento del estándar | El más cercano a SQL:2023 entre los motores open source |
| Características avanzadas | JSONB nativo, arrays, window functions, CTEs modificables |
| Open source | Sin licencia, corre en cualquier entorno |
| Ecosistema | AWS RDS/Aurora, Google Cloud SQL, Azure, Supabase, Neon |

Donde la sintaxis difiere entre motores, encontrarás notas como:
```
⚠ MySQL:       sintaxis alternativa
⚠ SQL Server:  sintaxis alternativa
⚠ Oracle:      sintaxis alternativa
```

## Estándares SQL: historia y evolución {#estandares}

| Estándar | Año | Qué aportó |
|----------|-----|------------|
| SQL-89 | 1989 | SELECT, INSERT, UPDATE, DELETE, WHERE básico |
| SQL-92 | 1992 | JOINs explícitos, subconsultas, CASE, tipos básicos |
| SQL:1999 | 1999 | CTEs (WITH), triggers, tipos compuestos |
| SQL:2003 | 2003 | Window Functions, MERGE, IDENTITY, XML |
| SQL:2008 | 2008 | TRUNCATE estándar, FETCH FIRST |
| SQL:2011 | 2011 | Tablas temporales con versiones (bitemporal) |
| SQL:2016 | 2016 | JSON nativo, MATCH_RECOGNIZE |
| SQL:2023 | 2023 | Property Graph Queries (SQL/PGQ), mejoras JSON |

PostgreSQL implementa la mayoría de SQL:2011 y partes de SQL:2016/2023.

---

## SQL frente a un lenguaje de programación de propósito general {#sql-vs-programacion}

Antes de entrar a la sintaxis conviene resolver una pregunta que casi nunca se explica con precisión, ¿qué tan "lenguaje de programación" es realmente SQL, comparado con algo como JavaScript, Python o Java? La respuesta correcta tiene dos partes, porque bajo el nombre "SQL" en realidad conviven dos cosas distintas.

### Dos lenguajes bajo un mismo nombre

El estándar SQL, el que define `SELECT`, `INSERT`, `WHERE`, `JOIN`, es un lenguaje **declarativo**. Describe qué resultado quieres, no los pasos para obtenerlo, y por diseño no tiene variables, ciclos ni control de flujo imperativo. Nunca los tuvo, porque no fue pensado para eso, fue pensado para expresar operaciones sobre conjuntos de datos.

Pero cada motor añade, por encima de ese núcleo declarativo, un **lenguaje procedural propio**. PostgreSQL tiene PL/pgSQL, SQL Server tiene T-SQL, Oracle tiene PL/SQL, y MySQL tiene su propio lenguaje de stored procedures. Estos no son variantes de un mismo lenguaje con distinto nombre, cada motor lo diseñó desde cero, con su propia sintaxis, pensado única y exclusivamente para vivir dentro de ese motor. Y a diferencia de lo que se ve más abajo con Python o Java, no son un componente aparte que alguien instala encima, vienen integrados en el propio binario del motor, sin paso de instalación adicional. T-SQL ni siquiera es opcional en SQL Server, es el lenguaje con el que el motor procesa cualquier consulta, declarativa o procedural; PL/SQL está compilado dentro del kernel de Oracle desde su diseño original; PL/pgSQL viene disponible por defecto en cualquier instalación moderna de PostgreSQL. Aun así son lenguajes imperativos completos, con variables, ciclos, condicionales y manejo de errores (ya cubiertos en el §40 al §45 de esta guía). La confusión habitual viene de mezclar ambas capas, cuando alguien pregunta "¿SQL tiene variables?", la respuesta depende de si habla del `SELECT` que escribe todos los días o del PL/pgSQL que vive dentro de una función.

```
NIVEL 2 — CAPA PROCEDURAL (imperativa, distinta en cada motor, NO intercambiable)

┌───────────────────────┐   ┌─────────────────────┐   ┌───────────────────────┐
│ PL/pgSQL              │   │ T-SQL               │   │ PL/SQL                │
│ (PostgreSQL)          │   │ (SQL Server)        │   │ (Oracle)              │
│ FOR … LOOP … END LOOP │   │ WHILE … BEGIN … END │   │ FOR … LOOP … END LOOP │
└───────────────────────┘   └─────────────────────┘   └───────────────────────┘
            │                          │                          │
            ▼                          ▼                          ▼
                  cada uno SE CONSTRUYE SOBRE el mismo núcleo

NIVEL 1 — NÚCLEO SQL (declarativo, universal, el mismo para los cuatro motores)

┌─────────────────────────────────────────────────────────────────────────────┐
│                           NÚCLEO SQL DECLARATIVO                            │
│         SELECT · INSERT · UPDATE · DELETE · JOIN · WHERE · GROUP BY         │
│           Igual en los cuatro motores. Sin variables, sin ciclos.           │
└─────────────────────────────────────────────────────────────────────────────┘
```

Las flechas apuntan hacia abajo a propósito, no es que el núcleo "llame" a la capa procedural, es la capa procedural la que depende del núcleo y lo necesita para existir. Sin el `SELECT`/`INSERT`/`WHERE` de abajo no hay nada sobre lo que ejecutar un `FOR` o un `WHILE`. Por eso el Nivel 1 es una sola caja compartida y el Nivel 2 son tres cajas separadas y sin relación entre sí, PL/pgSQL no entiende T-SQL, ni T-SQL entiende PL/SQL, aunque las tres se apoyen en exactamente el mismo SQL declarativo.

MySQL sigue el mismo patrón con su propio lenguaje de stored procedures, más limitado que los tres anteriores en tipos y manejo de errores, pero construido igual encima del mismo Nivel 1.

Existe todavía una tercera cosa, distinta de las dos anteriores y fácil de confundir con ellas. Consiste en instalar un intérprete real de un lenguaje de propósito general dentro del motor, en vez de escribir en el lenguaje procedural propio de ese motor. A diferencia de PL/pgSQL o T-SQL, esto sí requiere un paso explícito de instalación, porque el intérprete no forma parte del binario base del motor. PostgreSQL lo permite vía extensión, con `PL/Python` (`CREATE EXTENSION plpython3u`), `PL/Perl`, `PL/Tcl`, y `PL/V8` como extensión comunitaria para JavaScript. Una función declarada `LANGUAGE plpython3u` no imita la sintaxis de Python, corre Python de verdad, con sus propias librerías instaladas en el sistema, dentro de la misma transacción y el mismo proceso que los datos. Esto no es un cuarto lenguaje procedural nuevo como PL/pgSQL o T-SQL, es el intérprete de un lenguaje que ya existía fuera de la base de datos, traído hacia adentro.

Oracle y SQL Server tienen su propio equivalente a esta tercera cosa, aunque con otro lenguaje embebido. Oracle puede cargar y ejecutar clases Java reales dentro de su JVM interna (`CREATE JAVA CLASS`, expuesta después como función SQL), y SQL Server puede ejecutar ensamblados .NET compilados vía CLR integration (`CREATE ASSEMBLY`, con funciones o procedimientos marcados `EXTERNAL NAME`). MySQL no tiene equivalente, su único lenguaje procedural sigue siendo el suyo propio, sin posibilidad de embeber un intérprete externo.

```sql
CREATE EXTENSION IF NOT EXISTS plpython3u;

CREATE FUNCTION validar_formato(texto TEXT) RETURNS BOOLEAN AS $$
    import re
    return bool(re.match(r'^[A-Z]{3}-\d{4}$', texto))
$$ LANGUAGE plpython3u;
```

Esto no contradice nada de lo explicado arriba, lo confirma con un ejemplo más filoso. Nada impide, dentro de esa misma función en `plpython3u`, importar `requests` o `urllib` y hacer una llamada HTTP bloqueante, exactamente el error mecánico descrito más abajo (Sección "Por qué SQL no debería hacer `fetch`"), solo que ahora con las herramientas completas de Python disponibles para cometerlo. El motor no lo impide, la extensión existe para casos legítimos (validaciones complejas, procesamiento de texto, modelos ligeros de machine learning aplicados fila por fila), pero la responsabilidad de no usarla para orquestar servicios externos recae enteramente en quien escribe la función. Cambia el lenguaje y cambia si es "un lenguaje nuevo diseñado para el motor" o "un intérprete externo traído hacia adentro", pero el riesgo estructural de ejecutar dentro del mismo proceso que los datos es idéntico en las tres capas, la declarativa, la procedural propia del motor y la del lenguaje embebido.

### ¿Entonces es cierto que las bases de datos son "torpes"?

No, al menos no en el sentido en que la mayoría de la gente repite esa idea, como si una base de datos fuera incapaz de hacer lógica real. PL/pgSQL, T-SQL y PL/SQL son lenguajes Turing-completos desde hace décadas: tienen variables, ciclos, condicionales, recursión y manejo de errores, y con eso alcanza para expresar cualquier algoritmo que se le ocurra a un programador, en el mismo sentido de poder computacional puro que Python o JavaScript. La tabla de la siguiente sección lo confirma casilla por casilla. Si "torpe" significara "no puede calcular ni decidir nada", la afirmación sería simplemente falsa.

La fama viene de otro lado, y separar sus causas reales evita sacar la conclusión equivocada:

1. **Portabilidad cero.** Una función en PL/pgSQL solo corre en PostgreSQL, palabra por palabra. Migrar de motor implica reescribir toda esa lógica desde cero, mientras que un backend bien diseñado, en el peor caso, cambia un driver o un ORM y conserva casi toda su lógica intacta. Esta asimetría no depende de qué tan buena sea la lógica escrita en PL/pgSQL, depende de que cada motor definió su propio lenguaje procedural sin coordinarse con los demás.

2. **Un ecosistema de ingeniería de software mucho más pobre alrededor.** No existe un debugger visual maduro y universal para funciones y triggers, el control de versiones de esa lógica vive fuera de git salvo que el equipo discipline sus migraciones, no hay un framework de testing tan extendido como Jest o pytest (existe `pgTAP`, pero es minoritario), y un cambio en un trigger no aparece en ningún diff de aplicación a menos que alguien lo automatice aparte. La lógica ahí no es difícil de escribir, es difícil de mantener con la misma disciplina que el resto del código.

3. **Una historia real de límites técnicos, hoy superados pero no olvidados.** Buena parte de la reputación de "torpe" viene específicamente de MySQL, no de SQL en general: MySQL no tuvo CTEs ni window functions hasta la versión 8.0, en 2018, y su lenguaje de stored procedures siempre fue más limitado que PL/pgSQL. Quien aprendió con una versión vieja de MySQL, o con Oracle mal documentado, se llevó la impresión de que "las bases de datos no pueden hacer nada elegante", una impresión que ya no es cierta ni siquiera para ese mismo motor actualizado.

A esas tres causas técnicas se suma una razón de cultura de la industria, no de capacidad del lenguaje: durante los años 2000 y 2010 se documentó ampliamente que mover lógica de negocio a stored procedures generaba código escondido, difícil de testear y acoplado a un solo motor, y ese aprendizaje se volvió un anti-patrón de arquitectura bien establecido. Gran parte de la fama de "torpe" en realidad describe esa mala práctica evitada, no una limitación real del lenguaje. La sección "Por qué sigue haciendo falta un backend", más abajo, explica las razones técnicas por las que conviene mantener esa separación, y ninguna de ellas es que la base de datos "no pueda" hacer lógica, sino que hacerla ahí sale más caro, más frágil y más difícil de mantener de lo que parece a primera vista.

### Tabla comparativa: capacidad por capacidad

| Capacidad | SQL declarativo puro | Capa procedural (PL/pgSQL, T-SQL, PL/SQL) | Lenguaje de propósito general (JS, Python, Java) |
| --- | --- | --- | --- |
| Variables | No | Sí, con `DECLARE` | Sí, nativo |
| Funciones definidas por el usuario (*user-defined functions*) | No, solo funciones del motor | Sí, `CREATE FUNCTION` (§40) | Sí, nativo |
| Bucles / ciclos (*loops*, `for`, `while`) | No | Sí, `LOOP`, `WHILE`, `FOR` | Sí, nativo |
| Condicionales | Sí, pero solo como expresión (`CASE`, §15) | Sí, como control de flujo (`IF`/`ELSE`) | Sí, nativo |
| Recursión | Sí, vía CTEs recursivas (§12), acotada y declarativa | Sí, una función puede llamarse a sí misma | Sí, nativo |
| Clases, objetos, herencia (POO) | No | No, existen tipos compuestos (§23) pero sin métodos ni polimorfismo | Sí, nativo en los lenguajes orientados a objetos |
| Manejo de errores/excepciones | No | Sí, `EXCEPTION`, `RAISE` (§45) | Sí, `try/catch` |
| Asincronía, promesas, `async`/`await` | No | No | Sí, nativo en JS; Python y otros lo añaden como librería |
| Validaciones de datos | Sí, `CHECK`, `NOT NULL`, `UNIQUE`, dominios (§24, §23) | Sí, con lógica arbitraria en triggers (§43) | Sí, con cualquier librería de validación |
| Operaciones aritméticas y lógicas | Sí, completas | Sí, completas | Sí, completas |
| Manipulación de strings, fechas, JSON | Sí, funciones nativas extensas (§17, §19, §20) | Sí, las mismas funciones disponibles | Sí, con librerías propias |
| Módulos, imports, gestión de dependencias | No, existen esquemas para namespacing (§48) pero no un sistema de paquetes | Parcial, extensiones instalables (`CREATE EXTENSION`) | Sí, ecosistemas maduros (npm, pip, Maven) |
| Multi-threading o paralelismo explícito | No, el paralelismo lo decide el motor internamente (workers paralelos en el query planner) | No | Sí, hilos, procesos, workers, según el lenguaje |
| Fetch / llamar una API HTTP externa | No | Rara vez, y solo con extensiones no estándar (ver más abajo) | Sí, nativo o con una librería estándar |
| Estructuras de datos (arreglos, mapas, sets) | Solo PostgreSQL tiene arreglo como tipo de columna nativo (`INTEGER[]`); los otros tres no tienen arreglo ni mapa como tipo del núcleo declarativo | Muy distinto por motor. PL/SQL tiene arreglos (`VARRAY`, nested tables) y mapas nativos (associative arrays, index-by tables); PL/pgSQL tiene arreglos heredados del tipo de columna pero ningún mapa propio del lenguaje, salvo usar una variable `jsonb`/`hstore`; T-SQL no tiene arreglo ni mapa nativo, solo table variables (colecciones de filas) | Sí, nativo. Array/Map/Set en JS, list/dict/set/tuple en Python, List/Map/Set de la biblioteca estándar en Java |

Como puede verse, salvo casos deliberadamente diseñados como escotillas de escape, las capacidades más parecidas a un lenguaje de propósito general viven todas en la capa procedural, nunca en el SQL declarativo que se usa a diario.

## Más allá de SQL: el mismo análisis en cualquier motor de base de datos {#mas-alla-de-sql}

Todo lo anterior describe una familia concreta, la relacional. Pero la pregunta de fondo, qué tan "programable" es una base de datos, no es exclusiva de SQL: se puede hacer exactamente la misma pregunta sobre MongoDB, Redis, Cassandra, Elasticsearch, InfluxDB, Neo4j, Snowflake o Pinecone, y en cada caso la respuesta depende de las mismas tres decisiones de diseño, no de si el motor "es SQL o no lo es". Esas tres preguntas son el marco que ordena el resto de esta sección:

1. **¿Dónde ejecuta su lenguaje de consulta?** ¿En un servidor propio y separado del backend, al que se le manda la consulta por red, o embebido dentro del mismo proceso de la aplicación?
2. **¿Tiene, además de su lenguaje de consulta, una capa procedural imperativa y Turing-completa** (variables, ciclos, condicionales) para expresar lógica que el lenguaje de consulta por sí solo no puede?
3. **¿Esa capa procedural puede llamar servicios externos por red, y el motor lo permite a propósito, lo bloquea a propósito, o ni siquiera se plantea la pregunta** porque no tiene capa procedural?

Las siguientes secciones responden la Pregunta 1 de forma transversal (aplica casi igual a ocho de las nueve familias), y después recorren, familia por familia, las Preguntas 2 y 3, que sí varían radicalmente de un motor a otro, terminando en una tabla maestra con las nueve respuestas completas.

### Pregunta 1 — ¿Dónde vive y se ejecuta el lenguaje de consulta? Cliente contra servidor

Todo lo anterior describe qué puede hacer el lenguaje, pero queda pendiente una pregunta distinta y igual de importante, ¿en qué máquina y en qué proceso corre quien realmente ejecuta ese lenguaje? La respuesta es la misma para SQL y para la inmensa mayoría de bases de datos NoSQL, aunque la forma de la consulta cambie por completo de una a otra.

**El modelo cliente-servidor de SQL.** El motor (PostgreSQL, MySQL, SQL Server, Oracle) corre como su propio proceso, en su propio servidor, físicamente separado del proceso donde vive el backend (en desarrollo puede ser la misma máquina, en producción casi siempre son máquinas distintas). El backend abre una conexión (ya cubierta en `02_conexiones.md`) y manda el `SELECT`/`INSERT`/`UPDATE` como **texto plano**, literalmente la cadena de caracteres que escribiste. El motor la recibe, la parsea, arma un plan de ejecución, y la ejecuta ahí mismo, en su propio proceso, contra sus propios archivos en disco. El backend nunca toca esos archivos directamente, ni falta que le haga: todo lo que sabe hacer es abrir la conexión y mandar el texto.

```sql
-- Esto es literalmente lo que viaja por la red hacia PostgreSQL,
-- carácter por carácter, sin traducir a ningún formato intermedio:
SELECT nombre, precio FROM productos WHERE precio > 100;
```

**MongoDB: el mismo modelo, pero la "consulta" no es texto, es un documento.** `mongod` (o `mongos` en un cluster shardeado, ya cubierto en `06_partitioning_and_sharding.md`) corre como su propio proceso, separado del backend, exactamente igual que PostgreSQL. Lo que cambia es la forma de la petición: en vez de una cadena SQL, el driver arma un **documento BSON** con la operación a ejecutar, y ese documento es lo que viaja por la red.

```js
// Lo que escribes en el backend (driver de Node.js):
db.collection('productos').find({ precio: { $gt: 100 } });
```

```json
// Lo que realmente viaja por la red hacia mongod, como comando BSON:
{
  "find": "productos",
  "filter": { "precio": { "$gt": 100 } },
  "$db": "tienda"
}
```

El motor recibe ese documento, no la llamada de JavaScript. La sintaxis "amigable" del driver (`db.collection('productos').find({...})`) es exactamente lo mismo que una llamada a un driver de PostgreSQL como `psycopg2`, una capa de conveniencia del lado del cliente que arma la petición real y la manda por la red. Nunca ejecuta nada por sí misma.

**Cassandra: texto, muy parecido a SQL, sobre un protocolo binario propio.** CQL (Cassandra Query Language) es texto plano, con una sintaxis deliberadamente cercana a SQL, que viaja hacia los nodos de Cassandra dentro de frames de su protocolo binario nativo.

```sql
SELECT nombre, precio FROM productos WHERE precio > 100 ALLOW FILTERING;
```

**Neo4j: texto también, pero en Cypher, sobre el protocolo Bolt.** El driver serializa el texto Cypher más sus parámetros dentro de un mensaje Bolt, y el servidor de Neo4j lo parsea y ejecuta contra el grafo almacenado en su propio proceso.

```cypher
MATCH (p:Producto) WHERE p.precio > 100 RETURN p.nombre, p.precio;
```

**Redis: ni texto con sintaxis propia, ni documento, son comandos en su protocolo RESP.** Cuando el driver ejecuta `redisClient.set('producto:501', '{"precio":999}')`, lo que realmente cruza la red hacia `redis-server` es esto:

```
*3\r\n
$3\r\nSET\r\n
$13\r\nproducto:501\r\n
$17\r\n{"precio":999}\r\n
```

Un array RESP de tres elementos (`SET`, la llave, el valor), cada uno precedido por su longitud en bytes. No hay parser de SQL de por medio, pero el principio es idéntico: el driver arma la petición, `redis-server` (un proceso separado) la interpreta y la ejecuta.

**DynamoDB: ni siquiera un protocolo binario propio, HTTP con un cuerpo JSON.** No existe un `redis-server` o un `mongod` que administres, es un servicio completamente gestionado por AWS, pero la mecánica es la misma: el SDK arma una petición HTTP `POST` con un cuerpo JSON describiendo la operación, y AWS la ejecuta en su propia infraestructura.

```json
{
  "TableName": "Productos",
  "KeyConditionExpression": "customer_id = :id",
  "ExpressionAttributeValues": { ":id": { "S": "C001" } }
}
```

Desde 2020, DynamoDB también acepta **PartiQL**, un dialecto compatible con SQL, como capa adicional sobre esa misma API (`SELECT * FROM Productos WHERE customer_id = 'C001'`), justo el mismo patrón de convergencia hacia sintaxis SQL-like que ya se mencionó para Cassandra y Neo4j.

**Bases vectoriales (Pinecone, Weaviate, Milvus): la misma idea, con un vector como cuerpo de la petición.** No hay ni texto ni documento con estructura rica, el cuerpo de la petición es directamente el vector a comparar.

```json
{ "vector": [0.12, -0.04, 0.88, 0.55, 0.19], "topK": 5, "includeMetadata": true }
```

Ese JSON (o su equivalente en protobuf si se usa gRPC) viaja hacia el servidor de la base vectorial, que compara el vector contra su índice HNSW o IVF y devuelve los vecinos más cercanos.

### La excepción real, y por qué un ORM no cambia nada de esto

Sobre el punto de comparar esto con un ORM: un ORM (Sequelize, Prisma, SQLAlchemy) sí vive enteramente en el backend, pero no reemplaza al servidor separado, solo genera la petición que se le manda. `Usuario.findAll({ where: { activo: true } })` arma internamente el texto `SELECT * FROM usuarios WHERE activo = true` y lo manda al mismo PostgreSQL de siempre, por la misma conexión de siempre. Es una capa de traducción del lado del cliente, exactamente el mismo rol que cumple el driver de MongoDB, Cassandra o Redis al armar su propia forma de petición. Ninguno de los dos "ejecuta" la consulta, solo la construyen y la envían.

La excepción real está en el último renglón de la tabla. **SQLite y DuckDB no tienen servidor**, el motor es una librería enlazada dentro del propio proceso de la aplicación. Cuando el código Python hace `cursor.execute("SELECT * FROM productos")`, el parser de SQLite corre en el mismo proceso, el mismo hilo, la misma memoria que el intérprete de Python, sin abrir ninguna conexión de red, sin serializar nada a un protocolo de transporte. Ahí sí el lenguaje de consulta "vive dentro" del proceso del backend en el sentido literal que sugiere la pregunta, no como capa de traducción sino como motor completo embebido. Es la excepción, no la regla: todo lo demás en esta sección, relacional o NoSQL, corre en un proceso separado que el backend nunca toca directamente.

### Familia 1 — Relacional (SQL): por qué no debería hacer `fetch` a una API externa

Esta es la limitación más importante de entender, porque no es un descuido de los diseñadores del lenguaje, es una consecuencia directa de todo lo que ya cubre esta guía y las guías de transacciones y almacenamiento del repositorio.

Cuando ejecutas una sentencia SQL dentro de una transacción, esa sentencia corre sobre una conexión física al motor, la misma conexión que ocupa un lugar limitado en el pool (`02_conexiones.md`), y que puede estar reteniendo locks sobre filas o tablas mientras la transacción sigue abierta (Bloque de locks en `03_transacciones_concurrencia_aislamiento.md`). Si esa sentencia tuviera que esperar la respuesta de una API externa a mitad de la transacción, la duración de la espera dejaría de depender del motor de base de datos y pasaría a depender de la latencia de una red externa, completamente fuera de su control, mientras la conexión sigue ocupada y los locks siguen retenidos. Una API lenta, o caída, podría entonces bloquear otras transacciones que necesiten las mismas filas, agotar el pool de conexiones disponibles, y en el peor caso dejar el motor completo respondiendo mal a todo el tráfico por culpa de un servicio externo que ni siquiera es parte de la base de datos.

Por esa razón estructural, ni el estándar SQL ni las extensiones procedurales mainstream ofrecen un `fetch` genérico. Existen escotillas de escape, pero todas deliberadamente limitadas o poco recomendadas para lógica de negocio real:

- **PostgreSQL**: la extensión `pg_net`, no incluida por defecto, hace llamadas HTTP **asíncronas** desde dentro de la base, ejecutadas por un worker en segundo plano, precisamente para no bloquear la transacción que la invocó mientras espera la respuesta.
- **PostgreSQL** también permite `dblink` y `postgres_fdw`, pero esas conectan con **otra base de datos**, no con una API HTTP arbitraria.
- **SQL Server** puede invocar procedimientos externos vía CLR o `xp_cmdshell`, pero Microsoft mismo advierte contra su uso en producción por razones de seguridad y estabilidad.
- **Supabase**, la plataforma "backend-as-a-service" construida sobre PostgreSQL que esta misma guía menciona como parte del ecosistema del motor de referencia, va un paso más allá de `pg_net`, sus **Database Webhooks** disparan automáticamente una llamada HTTP hacia una **Edge Function** (código serverless que corre en Deno, fuera del proceso de Postgres) cada vez que ocurre un `INSERT`/`UPDATE`/`DELETE` en una tabla marcada para eso.

Ninguno de estos mecanismos está pensado para que la lógica de negocio de una aplicación dependa de ellos de forma rutinaria. Son herramientas de nicho, no un reemplazo del patrón estándar de mantener las llamadas a servicios externos fuera de la base de datos. El caso de Supabase, de hecho, confirma la regla en vez de romperla: la lógica de negocio arbitraria vive en la Edge Function, en Deno, no dentro del motor. Postgres solo dispara un evento asíncrono hacia afuera, nunca espera la respuesta HTTP dentro de su propia transacción.

Las bases de datos analíticas construidas también sobre SQL, como Snowflake y BigQuery, resuelven este mismo problema de un modo distinto y mucho más abierto. Se ven más adelante, en la Familia 8, porque el motivo mecánico por el que sí lo permiten sin correr el mismo riesgo merece explicarse junto al resto de motores analíticos.

---

### Familia 2 — Documento (MongoDB): un historial real de abrir y cerrar la misma puerta

El *aggregation pipeline* de MongoDB es declarativo, parecido en espíritu a una consulta SQL con muchos `JOIN`/`GROUP BY` encadenados. Pero la historia de qué tan programable es MongoDB tiene un antes y un después bien documentado.

Hasta la versión 4.0, MongoDB ofrecía `db.eval()`, un comando que ejecutaba JavaScript arbitrario directamente en el servidor, con acceso de lectura y escritura a toda la base. MongoDB lo eliminó por completo en la versión 4.2 por dos razones de seguridad y rendimiento documentadas oficialmente. El comando tomaba un lock global de escritura mientras corría, bloqueando cualquier otra operación del cluster, y ejecutar JavaScript arbitrario del lado del servidor era, en sí mismo, una superficie de ataque. En su lugar, MongoDB ofrece hoy mecanismos más acotados como los operadores `$function` y `$accumulator` dentro del propio aggregation pipeline (JavaScript, pero confinado a transformar el documento que ya está procesando, no a tocar el resto de la base) y `mapReduce`, con un alcance mucho más limitado que el `db.eval()` original.

MongoDB Atlas, la versión gestionada en la nube, va todavía más lejos que ese JavaScript acotado y ofrece **Atlas Functions** y **Atlas Triggers**, funciones en JavaScript que corren del lado del servidor, se disparan ante eventos de la base de datos (un `INSERT`, un cambio de documento), y sí pueden hacer `fetch` a APIs externas mediante el objeto `context.http`. Esto no es un accidente, es una decisión deliberada de producto, Atlas se posiciona como una plataforma de aplicación completa, no solo como un motor de almacenamiento, así que conscientemente cruza la línea que PostgreSQL o MySQL mantienen cerrada. La contrapartida es la misma advertencia de siempre: una función que espera una respuesta HTTP dentro de un trigger disparado por escritura puede introducir la misma impredecibilidad de latencia ya explicada arriba, solo que ahora Atlas asume ese riesgo como parte de su producto administrado.

---

### Familia 3 — Clave-valor (Redis): programable, pero solo hacia adentro

Redis sí tiene un lenguaje de programación embebido real y Turing-completo, scripts en Lua ejecutados con `EVAL`, y desde Redis 7 también **Redis Functions**, una forma más estructurada de registrar esos mismos scripts. La diferencia crucial frente a MongoDB es que este lenguaje está pensado explícitamente para lógica atómica y rápida sobre los propios datos en memoria (por ejemplo, un `INCR` condicional complejo que de otra forma requeriría varios round-trips), no para llamar servicios externos.

```lua
-- Script Lua atómico: decrementa stock solo si hay suficiente
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock >= tonumber(ARGV[1]) then
    return redis.call('DECRBY', KEYS[1], ARGV[1])
else
    return -1
end
```

Redis, en su configuración por defecto, procesa comandos en un único hilo, así que una llamada HTTP bloqueante dentro de un script Lua congelaría el servidor entero para todos los clientes mientras espera la respuesta. Aquí la limitación no es solo de diseño del lenguaje, es una consecuencia directa del modelo de concurrencia del motor completo, la razón de ser de Redis es la velocidad submilisegundo, y cualquier cosa que introduzca latencia de red impredecible traiciona esa propuesta de valor.

---

### Familia 4 — Wide-column (Cassandra): la puerta cerrada por defecto, con una razón concreta

Cassandra soporta **User-Defined Functions (UDFs)**, escritas en Java o JavaScript (y, agregando el JAR correspondiente al classpath, en cualquier otro lenguaje compatible con JSR 223 como Python o Ruby). A diferencia de MySQL o PostgreSQL, donde la capa procedural viene activa desde el primer momento, Cassandra las trae deshabilitadas por defecto, el parámetro `enable_user_defined_functions` en `cassandra.yaml` vale `false` de fábrica, y hay que activarlo explícitamente para poder crear una sola UDF.

```yaml
# cassandra.yaml
enable_user_defined_functions: false   # valor por defecto, deliberado
```

Incluso activadas, las UDFs corren dentro de un sandbox, no con acceso libre al sistema. La razón de tanta cautela no es teórica, es un caso real y documentado: en 2021 se publicó CVE-2021-44521, una vulnerabilidad que permitía, mediante una UDF en Java cuidadosamente construida, escapar del sandbox y ejecutar código arbitrario en el servidor explotando cómo Java resuelve reflection dentro del propio proceso de Cassandra. El caso confirma exactamente el argumento estructural de esta sección, cuando una capa procedural corre dentro del mismo proceso que los datos, cualquier hueco en su sandbox se convierte en un hueco en la base de datos completa, no solo en la función.

```sql
-- Ejemplo de UDF en Cassandra (requiere enable_user_defined_functions: true)
CREATE FUNCTION descuento(precio double, porcentaje double)
    CALLED ON NULL INPUT
    RETURNS double
    LANGUAGE java
    AS 'return precio - (precio * porcentaje / 100);';
```

---

### Familia 5 — Motores de búsqueda (Elasticsearch / OpenSearch): la puerta bloqueada más explícitamente de todas

La consulta de Elasticsearch, el **Query DSL**, es un documento JSON enviado por HTTP, no muy distinto en espíritu al comando BSON de MongoDB o al vector de una base vectorial, solo que describe filtros de búsqueda, boosting de relevancia y agregaciones en vez de una condición de igualdad simple.

```json
{
  "query": {
    "bool": {
      "must":   { "match": { "descripcion": "laptop gamer" } },
      "filter": { "range": { "precio": { "gt": 100 } } }
    }
  }
}
```

Para lógica que el Query DSL no puede expresar (un cálculo de score personalizado, un campo derivado en tiempo de consulta), Elasticsearch ofrece **Painless**, su propio lenguaje de scripting, con sintaxis parecida a Java, compilado directamente a bytecode de la JVM para minimizar el costo de ejecutarlo sobre cada documento.

```painless
// Script Painless usado como script_score, para reordenar resultados
double base = doc['precio'].value;
return base > 0 ? 100 / base : 0;
```

De las nueve familias de esta sección, Elasticsearch es la más explícita al cerrar la puerta a llamadas externas. Painless corre en un sandbox que documentadamente no puede acceder al sistema de archivos, a la red, ni a ningún otro recurso del sistema operativo, restringido además a una lista blanca de operaciones permitidas y verificada en tiempo de compilación del script. La protección no depende solo del lenguaje, el propio proceso de Elasticsearch usa mecanismos del sistema operativo (seccomp en Linux, Seatbelt en macOS) para impedir que el proceso del motor pueda siquiera lanzar otro proceso. Es, de las nueve familias, el caso donde la respuesta a la Pregunta 3 es más categóricamente negativa, por diseño, sin escotilla de escape documentada.

---

### Familia 6 — Series de tiempo (InfluxDB): el caso donde sí tiene sentido llamar hacia afuera

InfluxDB usa **Flux**, un lenguaje de consulta funcional basado en tuberías, sucesor de InfluxQL (una sintaxis más cercana a SQL, todavía disponible en versiones anteriores). Flux no es solo un lenguaje de consulta, tiene variables, funciones propias, condicionales y una librería estándar organizada en paquetes, lo suficientemente completo como para que buena parte de la comunidad lo describa como un lenguaje de programación de propósito específico, no solo una sintaxis de filtros.

```flux
from(bucket: "sensores")
  |> range(start: -1h)
  |> filter(fn: (r) => r._measurement == "temperatura" and r._value > 80)
```

Y aquí está el contraste real frente a las familias anteriores. Flux sí incluye, de forma nativa y documentada, funciones para llamar servicios externos por HTTP, `http.post()`, y paquetes específicos como `slack.message()` o `pagerduty.sendEvent()`, pensados para el sistema de **Checks & Notifications** de InfluxDB, donde una condición detectada sobre una serie de tiempo (una métrica fuera de rango, un sensor caído) dispara una notificación hacia un sistema externo.

```flux
import "slack"

slack.message(
    url: "https://hooks.slack.com/services/...",
    text: "Temperatura del sensor 12 superó el umbral: 85°C",
    channel: "#alertas"
)
```

Esto no contradice el argumento de las familias anteriores, lo confirma desde el ángulo opuesto. En Redis o Elasticsearch, llamar hacia afuera desde dentro del motor sería un uso indebido, ajeno a lo que esos motores existen para hacer. En InfluxDB, avisarle a un humano o a otro sistema que algo pasó con una métrica es el caso de uso central del producto, no una excepción tolerada. La pregunta correcta nunca es si el motor puede llamar servicios externos, es si ese motor existe, entre otras cosas, para producir ese efecto hacia afuera.

---

### Familia 7 — Grafo (Neo4j): sin lenguaje procedural embebido, con procedimientos compilados aparte

Cypher, el lenguaje de consulta de Neo4j, es declarativo como SQL, describe qué patrón de nodos y relaciones buscar (`MATCH (a:Persona)-[:AMIGO]->(b:Persona)`), no los pasos para recorrer el grafo. A diferencia de PostgreSQL o MySQL, Neo4j no ofrece un lenguaje procedural embebido equivalente a PL/pgSQL que se escriba inline dentro de una consulta Cypher. La lógica imperativa vive en **procedimientos y funciones definidos por el usuario**, escritos en Java, compilados por separado como un `.jar`, e instalados en el servidor como un plugin.

La librería de procedimientos **APOC** (Awesome Procedures On Cypher), casi un estándar de facto en cualquier despliegue real de Neo4j, es exactamente eso, un plugin compilado que agrega cientos de procedimientos reutilizables. Entre ellos, `apoc.load.json` y `apoc.load.jsonParams` sí pueden leer datos de una URL HTTP externa directamente desde una consulta Cypher.

```cypher
CALL apoc.load.json("https://api.ejemplo.com/tasas-cambio") YIELD value
RETURN value.USD, value.EUR;
```

Es, otra vez, una escotilla de escape deliberada y documentada, pensada sobre todo para *cargar* datos externos hacia el grafo (ETL), no para que la lógica de negocio de una aplicación dependa de ella en cada request.

---

### Familia 8 — Columnar / OLAP (Snowflake, BigQuery, ClickHouse): llamar hacia afuera, sin los riesgos de un OLTP

Esta familia retoma la pregunta que quedó pendiente en la Familia 1. Snowflake y BigQuery, ambos con SQL como lenguaje de consulta, sí ofrecen una forma directa y soportada de invocar un servicio externo desde dentro de una consulta, algo que PostgreSQL o MySQL evitan a propósito.

**Snowflake** llama a esto **external functions**. La consulta invoca una función SQL que, en vez de ejecutar lógica interna, reenvía los datos hacia un servicio en la nube (por ejemplo AWS API Gateway, que a su vez invoca una función Lambda) y devuelve la respuesta como si fuera el resultado de cualquier otra función.

**BigQuery** llama a esto **remote functions**. Una consulta SQL puede invocar directamente una Cloud Function o un servicio de Cloud Run, empaquetando por lotes las filas de entrada en peticiones HTTP `POST` hacia ese servicio.

```sql
-- BigQuery: remote function que llama a un servicio externo
SELECT producto, clasificar_sentimiento(reseña) AS sentimiento
FROM resenas_productos;
```

**ClickHouse** va todavía más lejos con sus **executable UDFs**, que no se limitan a invocar servicios en la nube del mismo proveedor, ejecutan literalmente cualquier programa o script externo (Python, Bash, o un binario propio), pasándole los datos por `STDIN` y leyendo el resultado de `STDOUT`, configurado en un archivo XML aparte.

```xml
<!-- Configuración de una executable UDF en ClickHouse -->
<functions>
    <function>
        <type>executable</type>
        <name>enriquecer_con_ia</name>
        <return_type>String</return_type>
        <argument><type>String</type></argument>
        <format>TabSeparated</format>
        <command>python3 enriquecer.py</command>
    </function>
</functions>
```

Lo que hace que esto sea un riesgo de una naturaleza distinta al de un OLTP es la mecánica que ya se explicó en la Familia 1. El problema real de un `fetch` dentro de PostgreSQL o MySQL no es la llamada de red en sí, es que ocurre en medio de una transacción con locks retenidos y una conexión ocupada de un pool limitado, un recurso escaso que otras transacciones concurrentes necesitan. Snowflake, BigQuery y ClickHouse son motores analíticos, sus consultas típicas son de solo lectura, no retienen locks de fila esperando una respuesta HTTP, y una latencia de varios segundos en un `SELECT` analítico no es un evento anómalo como sí lo sería en un OLTP diseñado para responder en milisegundos. La misma acción, llamar afuera desde dentro del motor, tiene un costo estructural completamente distinto según el tipo de carga de trabajo para el que el motor fue diseñado.

---

### Familia 9 — Vectorial (Pinecone, Weaviate, Milvus): la única familia que no se plantea la pregunta

Salvo por `pgvector`, la extensión que agrega tipos e índices vectoriales a PostgreSQL y que, al vivir dentro de PostgreSQL, hereda automáticamente todo PL/pgSQL, las bases de datos vectoriales nativas deliberadamente no ofrecen ningún lenguaje de programación embebido, ni declarativo más allá de su API de consulta por similitud, ni procedural. Su propuesta de valor es una estructura de índice extremadamente especializada (HNSW, IVF) para resolver un solo problema, encontrar los vectores más cercanos a uno dado, tan rápido como sea posible. Agregar un lenguaje procedural completo no aportaría nada a ese objetivo y sí ampliaría innecesariamente la superficie de ataque y la complejidad operativa. Por eso interactúan con el resto del sistema exclusivamente vía una API REST o gRPC externa, exactamente el patrón de "backend orquesta, base de datos ejecuta" que ya se explicó para SQL, solo que en este caso ni siquiera existe la opción de saltárselo con una extensión.

---

### Tabla maestra: las nueve familias frente a las tres preguntas

| Familia | P1: ¿Dónde ejecuta su consulta? | P2: ¿Capa procedural Turing-completa? | P3: ¿Puede llamar servicios externos? |
| --- | --- | --- | --- |
| 1. Relacional (PostgreSQL, MySQL, SQL Server, Oracle) | Servidor propio, separado del backend; texto SQL | Sí (PL/pgSQL, T-SQL, PL/SQL) | Solo con extensiones de nicho (`pg_net`, CLR), no recomendado para lógica de negocio |
| 2. Documento (MongoDB) | Servidor propio (`mongod`/`mongos`); documento BSON | `$function`/`$accumulator` acotados en el pipeline; Atlas Functions en JS (gestionado) | Sí, deliberadamente, vía `context.http` en Atlas Functions |
| 3. Clave-valor (Redis) | Servidor propio (`redis-server`); comandos RESP | Sí, Lua vía `EVAL` y Redis Functions | No recomendado, bloquearía el hilo único del motor |
| 4. Wide-column (Cassandra) | Servidor propio (nodos); texto CQL sobre protocolo binario | Sí, UDFs en Java/JavaScript, deshabilitadas por defecto | No es el caso de uso previsto; CVE-2021-44521 mostró que abrir esa puerta puede llevar a ejecución remota de código |
| 5. Motores de búsqueda (Elasticsearch/OpenSearch) | Servidor propio; Query DSL en JSON sobre HTTP | Sí, Painless (compilado a bytecode JVM) | No, bloqueado explícitamente por sandbox (sin red ni sistema de archivos) |
| 6. Series de tiempo (InfluxDB) | Servidor propio; texto Flux sobre API HTTP | Sí, Flux es un lenguaje funcional completo | Sí, deliberadamente (`http.post`, `slack.message`, `pagerduty.sendEvent`, para alertas) |
| 7. Grafo (Neo4j) | Servidor propio; texto Cypher sobre Bolt | No embebido; procedimientos Java compilados aparte (APOC) | Sí vía APOC (`apoc.load.json`), pensado para ETL, no lógica de negocio recurrente |
| 8. Columnar/OLAP (Snowflake, BigQuery, ClickHouse) | Servicio gestionado o servidor propio; SQL extendido | Sí, UDFs en JavaScript/Python o scripts ejecutables externos | Sí, deliberadamente (external/remote functions, executable UDFs); consultas de solo lectura sin locks retenidos |
| 9. Vectorial (Pinecone, Weaviate, Milvus) | Servidor propio; vector en JSON/protobuf sobre REST o gRPC | No, salvo `pgvector` que hereda PL/pgSQL de PostgreSQL | No, ninguna razón para tenerlo dado su único propósito |

El patrón que se repite en las nueve filas es el mismo. La respuesta a la Pregunta 3 nunca depende de si el motor puede técnicamente hacerlo, casi cualquier motor con un lenguaje procedural podría llamar a una API si nadie se lo impidiera. Depende de si hacerlo pone en riesgo la razón de ser específica de ese motor (bloquear el hilo único de Redis, abrir una superficie de ataque en Cassandra, introducir latencia donde se prometió velocidad submilisegundo o un sandbox cerrado) o si, al contrario, coincide con ella (alertar en InfluxDB, enriquecer datos en un `SELECT` analítico de solo lectura en BigQuery). Cuanto más se posiciona un producto como plataforma en vez de motor de almacenamiento (MongoDB Atlas, Supabase), o cuanto más tolerante es su modelo de ejecución a la latencia de red (los tres motores de la Familia 8), más dispuesto está a cruzar esa línea de forma deliberada y documentada.

---

### Tabla comparativa: capacidad por capacidad, en las nueve familias

La columna P2 de la tabla anterior responde con una sola frase por familia, pero esa frase esconde una pregunta más fina que vale la pena abrir del todo, ¿de todo lo que se puede hacer en Python, JavaScript o Java, cuánto sobrevive dentro de cada motor? La Tabla comparativa de la primera parte de esta sección respondió esto mismo, capacidad por capacidad, para la familia relacional. Con las nueve familias ya explicadas, esa misma tabla se puede extender a los ocho motores restantes, comparando en un solo lugar qué tan cerca o lejos está cada capa procedural de un lenguaje de propósito general real. Cada columna describe la mejor capa procedural o embebida disponible en esa familia, no su lenguaje de consulta declarativo, que ya se cubrió en la Pregunta 1.

| Capacidad | 1. Relacional | 2. Documento¹ | 3. Clave-valor | 4. Wide-column | 5. Búsqueda | 6. Series de tiempo | 7. Grafo | 8. OLAP² | 9. Vectorial |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Variables | Sí | Sí | Sí | Sí, dentro de la función | Sí | Sí | Solo bindings de solo lectura | Sí | No |
| Funciones definidas por el usuario (*user-defined functions*) | Sí | Sí | Sí | Una función por UDF, sin auxiliares | Limitada al script | Sí, funciones de primera clase | No embebido, solo vía plugin Java | Sí | No |
| Bucles / ciclos (*loops*, `for`, `while`) | Sí | Sí | Sí | Sí | Sí | No, se usan `map`/`reduce`/`filter` | No, solo `FOREACH`/`UNWIND` sobre listas | Sí | No |
| Condicionales | Sí, control de flujo | Sí | Sí | Sí | Sí, salvo `switch` | Sí, como expresión `if/then/else` | Sí, solo como expresión (`CASE WHEN`) | Sí | No |
| Recursión | Sí | Sí | Sí | No práctica (una sola función) | Sí, poco común | No es el patrón habitual | No dentro de Cypher | Sí | No |
| Clases, objetos, herencia (POO) | No | Sí, clases de JS | No nativo (tablas/*metatables*) | No | No, solo clases de la lista blanca | No, paradigma funcional | Sí, pero fuera de Cypher (Java/APOC) | Sí (Python/JS reales) | No |
| Manejo de errores/excepciones | Sí | Sí (`try`/`catch`) | Sí (`pcall`) | Limitado al sandbox | Sí (`try`/`catch`) | Limitado | No dentro de Cypher | Sí | No |
| Asincronía, promesas, `async`/`await` | No | Sí, real (`async`/`await`) | No, bloqueante por diseño | No | No | No aplica igual, lo maneja el pipeline | No | Depende del lenguaje externo | No |
| Módulos, imports, gestión de dependencias | Parcial (`CREATE EXTENSION`) | Parcial (dependencias declaradas) | No | No, solo lista blanca | No, solo lista blanca | Sí (`import "paquete"`) | Sí, vía plugins Java | Sí (PyPI, npm, o el intérprete externo) | No |
| Multi-threading o paralelismo explícito | No, lo decide el motor (workers paralelos del query planner) | No, Node.js es de un solo hilo por invocación | No, un único hilo por diseño | No, sandbox sin acceso a hilos propios | No, la lista blanca no incluye clases de concurrencia | No, el motor paraleliza internamente las etapas del pipeline | No lo controla el usuario; hay un *parallel runtime* propio y el procedimiento debe declararse compatible con él | No de forma explícita en SQL/UDFs normales, aunque el motor paraleliza masivamente por arquitectura (las executable UDFs de ClickHouse sí heredan el paralelismo del lenguaje externo) | No |
| Fetch / llamar una API HTTP externa | Solo con extensiones de nicho | Sí, deliberadamente | No recomendado | No, riesgo de seguridad | No, bloqueado por sandbox | Sí, deliberadamente | Sí, vía APOC, para ETL | Sí, deliberadamente | No |
| Estructuras de datos (arreglos, mapas, sets) | Depende del motor, ver la fila equivalente de la tabla anterior | Sí, real, Array/Object/Map/Set de JavaScript | Solo un tipo, la tabla de Lua, que unifica arreglo y mapa en una misma estructura; un set se emula usando los valores como claves | Limitado a lo que el sandbox expone, colecciones básicas del lenguaje permitido (Java o JS), sin estructuras propias de Cassandra dentro de la UDF | Sí, colecciones al estilo Java dentro del sandbox, `ArrayList` y `HashMap` disponibles como cualquier otra clase de la lista blanca | Sí, records, arrays y maps son tipos propios del lenguaje Flux, no de una librería externa | Sí, pero como parte del propio Cypher, no de una capa procedural aparte, las listas y los mapas son literales del lenguaje de consulta (`[1,2,3]`, `{clave: valor}`); fuera de Cypher, solo vía APOC/Java | Sí, heredadas del intérprete real, list/dict/set de Python en Snowpark, Array/Object/Map de JavaScript en BigQuery, lo que tenga el lenguaje externo en las executable UDFs de ClickHouse | No, no hay lenguaje embebido donde tener esto, salvo `pgvector`, que hereda la columna 1 |

**¹ Documento (MongoDB)** en realidad tiene dos contextos distintos, comprimidos en una sola columna por simplicidad: dentro de una Atlas Function la fila anterior es literalmente cierta (es JavaScript/Node.js real), pero además viene con un límite de tiempo de ejecución por invocación que no aplica a las demás familias de esta tabla. Dentro del propio aggregation pipeline, en cambio, el JavaScript de `$function`/`$accumulator` es mucho más limitado, confinado a transformar el documento que ya se está procesando, sin los ciclos explícitos que sí tiene una Atlas Function completa.

**² Columnar/OLAP** también agrupa tres mecanismos distintos bajo una sola columna: Snowpark (Snowflake) permite Python real con paquetes de PyPI desde 2025, BigQuery ofrece UDFs en JavaScript más *remote functions* hacia cualquier lenguaje, y ClickHouse, con sus *executable UDFs*, ni siquiera reimplementa nada, ejecuta el intérprete externo tal cual. Las tres llegan a una paridad alta, pero por caminos distintos, no por el mismo mecanismo.

**Vectorial (Pinecone, Weaviate, Milvus)** es "No" en toda la fila porque no hay ningún lenguaje embebido donde intentar nada de esto, con la única excepción de `pgvector`, que al ser una extensión de PostgreSQL hereda automáticamente toda la columna 1.

Esta tabla deja fuera, a propósito, tres filas que sí aparecían en la Tabla comparativa de la Parte 1, y conviene decir por qué en vez de dejarlo como una omisión silenciosa. **Validaciones de datos** (`CHECK`, `NOT NULL`, esquemas de documento) no está porque es una pregunta distinta, sobre integridad de datos, no sobre lógica de programación, y ya la cubre en detalle el §24 de esta guía y las secciones de constraints de cada familia. **Operaciones aritméticas y lógicas** y **manipulación de strings, fechas y JSON** tampoco están porque, a diferencia de las diez filas que sí quedaron, no diferencian a las familias entre sí, las nueve responden "Sí, completas" sin excepción, incluso las capas más restringidas como Painless o las UDFs de Cassandra, así que agregarlas no aportaría ninguna comparación real, solo repetiría la misma palabra nueve veces.

El patrón que revela esta tabla, leída por columnas en vez de por filas, no es casualidad. Las familias más recientes y menos sensibles a la latencia de cada consulta individual, los motores analíticos de la Familia 8 (que ya toleran consultas de varios segundos) y el documento gestionado de la Familia 2, son las que más columnas tienen en "Sí", porque abrir la puerta a un lenguaje completo no compromete la razón de ser del producto. Las familias diseñadas para un único trabajo hiperespecializado y extremadamente sensible al tiempo de respuesta (búsqueda de texto, clave-valor en memoria, similitud vectorial) son las que más "No" acumulan, precisamente porque una clase mal escrita, un ciclo que no termina, o una llamada de red bloqueante ahí dentro pondría en riesgo la única razón por la que esa base de datos existe. Y el grafo es el caso intermedio y peculiar, el lenguaje de consulta declarativo (Cypher) y el lenguaje procedural completo (Java vía APOC) no conviven en el mismo espacio, viven en dos capas completamente separadas, una dentro de la consulta y otra fuera de ella por completo.

---

### Conclusión: por qué sigue haciendo falta un backend

Con las nueve familias completas ya se puede responder con precisión una pregunta que hasta aquí solo se había respondido a medias, ¿por qué, incluso con procedimientos almacenados, triggers y UDFs tan potentes como los que ofrece cada motor, prácticamente ninguna empresa real prescinde de una capa de backend? La respuesta no es un solo motivo, es una cadena de cinco, y cada uno mezcla en la misma idea el mecanismo técnico y la consecuencia real y medible que ese mecanismo tiene sobre cómo trabaja un equipo de ingeniería.

1. **La seguridad falla en el peor lugar posible.** El backend existe, entre otras cosas, para ser la frontera que valida entradas, aplica autenticación y autorización, y decide qué credenciales de base de datos usar antes de que cualquier dato llegue al motor. Meter esa lógica dentro del motor no solo amplía la superficie de ataque, la coloca justo donde nadie la vigila con la misma disciplina que a un servicio con dueño claro, repositorio propio y revisión de código en cada cambio. CVE-2021-44521, la vulnerabilidad de 2021 que permitió escapar del sandbox de una UDF de Cassandra para ejecutar código arbitrario en el servidor, es exactamente ese escenario, un bug que vivía en un lugar donde ningún proceso normal de revisión lo habría atrapado a tiempo.

2. **El costo de cómputo y el costo de contratación son la misma curva.** Un servidor de aplicación sin estado se replica horizontalmente agregando instancias detrás de un balanceador, casi sin límite práctico. Un motor de base de datos, en cambio, casi siempre tiene un límite físico real, el WAL y el lock manager únicos de un motor relacional (Bloque 1 y 7 de `06_partitioning_and_sharding.md`), el hilo único de Redis, o la capacidad de un nodo de Cassandra, así que cargarlo con trabajo que un servidor de aplicación barato podría hacer desperdicia la capa más cara de escalar. Y la misma escasez se repite en las personas, no solo en el hardware: según la Encuesta de Desarrolladores de Stack Overflow 2025, JavaScript lo usa el 66% de los más de 49,000 desarrolladores encuestados en 177 países, mientras que PL/pgSQL, T-SQL y PL/SQL ni siquiera aparecen entre los lenguajes más usados de esa misma encuesta, son lenguajes de nicho que casi nadie aprende en su formación general, sino ya trabajando, en la empresa que los necesita. Contratar y reemplazar a quien sepa escribir esa lógica con calidad de producción es estructuralmente más caro que hacerlo para cualquier lenguaje de propósito general.

3. **La red externa es impredecible, y revertir un error ahí es lento.** Como se explicó en las Familias 1, 3 y 5, una llamada HTTP bloqueante dentro de una transacción, un hilo único o un proceso de búsqueda convierte esa espera en una variable fuera del control del motor, con locks retenidos y conexiones ocupadas mientras dura. Las Familias 6 y 8 son la excepción que confirma la regla, ahí esa misma impredecibilidad sí es tolerable, porque el motor no retiene locks transaccionales ni depende de responder en milisegundos. Lo que agrava el riesgo donde sí bloquea no es solo el bug en sí, es que revertirlo es mucho más lento que revertir un backend: un pipeline de CI/CD moderno despliega un cambio de backend en minutos, con rollback casi instantáneo, mientras que un cambio en un trigger o una función almacenada es una migración de esquema que corre contra datos ya existentes.

4. **Esa lógica, dentro del motor, se vuelve invisible en dos sentidos a la vez.** Colas de trabajo con reintentos, websockets, notificaciones push o la coordinación entre varios servicios no tienen un modelo nativo en ninguno de los lenguajes de esta guía, `async`/`await` y las arquitecturas orientadas a eventos existen en los lenguajes de propósito general precisamente porque resuelven problemas para los que ninguna de las nueve familias fue diseñada. Y lo que sí llega a escribirse dentro del motor se vuelve invisible para las herramientas que ya vigilan el resto del sistema: el contexto de traza que usan plataformas de APM como Datadog o New Relic para reconstruir una request no se propaga dentro de procedimientos almacenados del lado del servidor. Un trigger que falla aparece en la traza como una sola línea, "ejecutó una query", justo cuando más falta hace ver el detalle, en medio de un incidente real.

5. **Esa lógica no viaja con la empresa cuando la empresa cambia.** Existen casos documentados con cifras reales, no un riesgo hipotético: en una migración de Oracle a PostgreSQL, reescribir procedimientos almacenados, funciones y triggers en PL/SQL es, según guías públicas de migración, la parte más lenta y costosa del proceso completo, con un estimado de entre 40 y 80 horas de trabajo por cada 1,000 líneas de ese código. Una empresa de servicios financieros con 250,000 líneas de PL/SQL gastó aproximadamente 850,000 dólares solo en reescribirlas, el 45% de todo el presupuesto de su migración. El código de un backend en un lenguaje de propósito general cambia de proveedor de base de datos con mucha menos fricción, casi siempre basta con cambiar el driver o el ORM.

| Razón | Qué falla si la lógica vive en el motor | Evidencia real |
| --- | --- | --- |
| Seguridad | Nadie la revisa con la disciplina de un servicio con dueño | CVE-2021-44521, escape de sandbox en una UDF de Cassandra |
| Costo | Escalar el motor y contratar quien programe ahí son ambos caros | JS 66% de uso vs. PL/pgSQL/T-SQL/PL/SQL fuera del top (Stack Overflow 2025) |
| Red impredecible | Bloquea locks y conexiones, y es lento de revertir | Familias 1, 3 y 5 de esta guía |
| Visibilidad | Invisible para el tracing distribuido | El contexto de traza no se propaga a procedimientos almacenados (APM) |
| Portabilidad | No se puede migrar de motor sin reescribirla | Migración Oracle → PostgreSQL, ~850,000 USD, 45% del presupuesto |

Estas cinco razones no son dos listas separadas, una técnica y otra de negocio, son la misma historia contada desde ángulos distintos, y por eso casi cualquier empresa que ha operado un sistema el tiempo suficiente termina llegando a la misma conclusión sin habérselo propuesto de antemano. Cada motor fue diseñado con un alcance deliberadamente distinto al de un lenguaje de propósito general, ya sea para administrar filas de forma durable, responder en submilisegundos, indexar vectores, o notificar cuando una métrica se sale de rango, y las capas procedurales que cada uno añade amplían ese alcance solo hasta donde tiene sentido hacerlo sin comprometer esa razón de ser. Cuando una organización decide, de todas formas, meter ahí más lógica de la que el motor fue pensado para sostener, no es el motor quien paga esa decisión, el motor simplemente ejecuta lo que se le pide, es el equipo completo, quien contrata, quien despliega, quien investiga un incidente a las tres de la mañana, quien migra el sistema tres años después, quien termina pagando el precio real de esa lógica que nunca debió salir del backend.

---

╔══════════════════════════════════════════════════════════════════════╗
║  BLOQUE I — Fundamentos del lenguaje                                 ║
║  §1 al §5                                                            ║
╚══════════════════════════════════════════════════════════════════════╝

## 1. ¿Qué es SQL? Categorías del lenguaje {#1-que-es-sql}

SQL (Structured Query Language) es un lenguaje **declarativo**: describes *qué* quieres,
no *cómo* obtenerlo. El motor decide el plan de ejecución óptimo.

```
┌─────────────────────────────────────────────────────────────────────┐
│  DDL — Data Definition Language    (define estructura)              │
│  CREATE · ALTER · DROP · TRUNCATE · RENAME                          │
├─────────────────────────────────────────────────────────────────────┤
│  DML — Data Manipulation Language  (manipula datos)                 │
│  INSERT · UPDATE · DELETE · MERGE                                   │
├─────────────────────────────────────────────────────────────────────┤
│  DQL — Data Query Language         (consulta datos)                 │
│  SELECT                                                             │
├─────────────────────────────────────────────────────────────────────┤
│  DCL — Data Control Language       (permisos)                       │
│  GRANT · REVOKE                                                     │
├─────────────────────────────────────────────────────────────────────┤
│  TCL — Transaction Control Language                                 │
│  BEGIN · COMMIT · ROLLBACK · SAVEPOINT                              │
└─────────────────────────────────────────────────────────────────────┘
```

### Orden de evaluación de un SELECT

Aunque escribes las cláusulas en este orden:
`SELECT … FROM … JOIN … WHERE … GROUP BY … HAVING … ORDER BY … LIMIT`

El motor las evalúa en este otro:
`FROM → JOIN → WHERE → GROUP BY → HAVING → SELECT → ORDER BY → LIMIT`

Esto explica por qué **no puedes usar un alias de SELECT dentro de WHERE**: WHERE se evalúa antes de que SELECT defina los alias.

```sql
-- ERROR: alias calculado no disponible en WHERE
SELECT precio * 1.16 AS precio_iva FROM productos WHERE precio_iva > 500;

-- CORRECTO: repite la expresión o usa subquery
SELECT precio * 1.16 AS precio_iva FROM productos WHERE precio * 1.16 > 500;
```

### Convenciones de esta guía

- Palabras clave en `MAYÚSCULAS` — convención, no obligatorio
- Identificadores en `minúsculas`
- Strings con comillas simples: `'texto'`
- Identificadores con caracteres especiales o palabras reservadas con comillas dobles: `"User"`
- Comentarios: `-- una línea` o `/* bloque */`

---

## 2. Tipos de datos en PostgreSQL {#2-tipos-datos}

### Tipos numéricos

| Tipo | Bytes | Rango / Precisión | Cuándo usar |
|------|-------|-------------------|-------------|
| `SMALLINT` | 2 | ±32,767 | Contadores pequeños |
| `INTEGER` / `INT` | 4 | ±2.1 mil millones | IDs, contadores estándar |
| `BIGINT` | 8 | ±9.2 × 10¹⁸ | IDs en sistemas de gran escala |
| `NUMERIC(p,s)` | variable | exacto | **Dinero**, cálculos financieros |
| `REAL` | 4 | ~6 decimales significativos | Científico (acepta inexactitud) |
| `DOUBLE PRECISION` | 8 | ~15 decimales | Científico con más precisión |

```sql
-- NUMERIC(precisión_total, decimales)
precio  NUMERIC(10, 2)   -- hasta 99,999,999.99
tasa    NUMERIC(5, 4)    -- hasta 9.9999

-- NUNCA uses FLOAT para dinero
SELECT 0.1::FLOAT + 0.2::FLOAT;    -- 0.30000000000000004  ← incorrecto
SELECT 0.1::NUMERIC + 0.2::NUMERIC; -- 0.3                 ← correcto
```

### Tipos de texto

| Tipo | Descripción |
|------|-------------|
| `TEXT` | Longitud ilimitada — **preferido en PostgreSQL** |
| `VARCHAR(n)` | Máximo n caracteres; error si excede |
| `CHAR(n)` | Longitud fija, rellena con espacios — evitar |

En PostgreSQL `TEXT` y `VARCHAR` tienen el mismo rendimiento. Usa `VARCHAR(n)` solo cuando quieras la restricción de longitud como constraint de negocio.

### Tipos de fecha y tiempo

| Tipo | Bytes | Descripción |
|------|-------|-------------|
| `DATE` | 4 | Solo fecha: `2024-01-15` |
| `TIME` | 8 | Solo hora: `14:30:00` |
| `TIMESTAMP` | 8 | Fecha + hora, **sin** zona horaria |
| `TIMESTAMPTZ` | 8 | Fecha + hora **con** zona horaria — **preferido** |
| `INTERVAL` | 16 | Duración: `'3 days'`, `'2 hours 30 minutes'` |

### Otros tipos importantes

| Tipo | Descripción |
|------|-------------|
| `BOOLEAN` | `TRUE`, `FALSE`, `NULL` |
| `UUID` | `a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11` |
| `JSONB` | JSON binario, indexable — preferido sobre `JSON` |
| `JSON` | Texto JSON sin índices ni operadores avanzados |
| `BYTEA` | Datos binarios arbitrarios |
| `INET` | Dirección IP (IPv4 o IPv6) |
| `CIDR` | Bloque de red IP |
| `INTEGER[]` / `TEXT[]` | Arrays de cualquier tipo |

---

## 3. DDL básico: CREATE TABLE, ALTER TABLE, DROP {#3-ddl-basico}

### CREATE TABLE

```sql
CREATE TABLE productos (
    id          BIGSERIAL PRIMARY KEY,
    nombre      TEXT          NOT NULL,
    precio      NUMERIC(10,2) NOT NULL CHECK (precio >= 0),
    stock       INTEGER       NOT NULL DEFAULT 0,
    categoria   TEXT,
    activo      BOOLEAN       NOT NULL DEFAULT TRUE,
    creado_en   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    modificado  TIMESTAMPTZ
);

-- Solo si no existe (no falla si ya existe)
CREATE TABLE IF NOT EXISTS productos (...);

-- Copiar estructura completa de otra tabla (sin datos)
CREATE TABLE productos_backup (LIKE productos INCLUDING ALL);

-- Crear tabla a partir de una consulta (copia estructura Y datos)
CREATE TABLE top_productos AS
    SELECT * FROM productos WHERE stock > 100;
```

### ALTER TABLE

```sql
-- Agregar columna
ALTER TABLE productos ADD COLUMN descripcion TEXT;
ALTER TABLE productos ADD COLUMN peso NUMERIC(8,3) NOT NULL DEFAULT 0;

-- Eliminar columna
ALTER TABLE productos DROP COLUMN descripcion;
ALTER TABLE productos DROP COLUMN IF EXISTS descripcion;

-- Renombrar columna
ALTER TABLE productos RENAME COLUMN nombre TO nombre_producto;

-- Cambiar tipo de dato
ALTER TABLE productos ALTER COLUMN precio TYPE NUMERIC(12, 2);

-- Cambiar default
ALTER TABLE productos ALTER COLUMN stock SET DEFAULT 10;
ALTER TABLE productos ALTER COLUMN stock DROP DEFAULT;

-- NOT NULL
ALTER TABLE productos ALTER COLUMN categoria SET NOT NULL;
ALTER TABLE productos ALTER COLUMN categoria DROP NOT NULL;

-- Agregar / quitar constraint
ALTER TABLE productos ADD CONSTRAINT chk_precio CHECK (precio >= 0);
ALTER TABLE productos DROP CONSTRAINT chk_precio;

-- Renombrar tabla
ALTER TABLE productos RENAME TO articulos;
```

### DROP y TRUNCATE

```sql
-- DROP: elimina la tabla y todos sus datos permanentemente
DROP TABLE productos;
DROP TABLE IF EXISTS productos;
DROP TABLE productos CASCADE;   -- elimina también vistas, FKs dependientes

-- TRUNCATE: vacía la tabla, mantiene la estructura
-- Mucho más rápido que DELETE sin WHERE
TRUNCATE TABLE productos;
TRUNCATE TABLE productos RESTART IDENTITY;          -- resetea secuencias
TRUNCATE TABLE productos, pedidos CASCADE;           -- respeta FKs
```

⚠ **MySQL**: `TRUNCATE` no puede hacerse dentro de una transacción activa en algunos engines.

### COMMENT ON: documentar el esquema dentro del catálogo

`COMMENT ON` guarda una descripción en el catálogo del propio motor, no en un archivo aparte que se desactualiza con el tiempo. Herramientas de documentación automática de esquema (por ejemplo generadores de diagramas o portales de datos internos) leen estos comentarios directamente de `pg_description`, así que quedan visibles para cualquiera que inspeccione la base, no solo para quien lea el código fuente.

```sql
COMMENT ON TABLE productos IS 'Catálogo de productos vendibles, sincronizado desde el ERP cada noche';
COMMENT ON COLUMN productos.stock IS 'Unidades disponibles en el almacén central, no incluye tránsito';
COMMENT ON FUNCTION calcular_descuento(NUMERIC) IS 'Aplica la regla de descuento vigente por volumen';

-- Ver los comentarios de una tabla
SELECT c.relname, d.description
FROM pg_class c JOIN pg_description d ON d.objoid = c.oid
WHERE c.relname = 'productos';

-- Quitar un comentario (se establece como NULL)
COMMENT ON TABLE productos IS NULL;
```

⚠ **MySQL**: el comentario se declara inline al crear la tabla o columna (`stock INT COMMENT 'unidades disponibles'`), no con una sentencia `COMMENT ON` separada. **SQL Server**: no tiene `COMMENT ON`; usa el procedimiento `sp_addextendedproperty`.

---

## 4. DML básico: INSERT, SELECT, UPDATE, DELETE {#4-dml-basico}

### INSERT

```sql
-- Fila única
INSERT INTO productos (nombre, precio, stock)
VALUES ('Laptop', 999.99, 50);

-- Múltiples filas (más eficiente que inserts individuales)
INSERT INTO productos (nombre, precio, stock) VALUES
    ('Mouse',    25.00, 200),
    ('Teclado',  75.50, 150),
    ('Monitor', 350.00,  30);

-- Desde una consulta
INSERT INTO productos_archivo
SELECT * FROM productos WHERE activo = FALSE;

-- Ver qué se insertó con RETURNING
INSERT INTO productos (nombre, precio)
VALUES ('Tablet', 299.00)
RETURNING id, creado_en;
```

### SELECT básico

```sql
SELECT * FROM productos;                        -- todas las columnas (evitar en prod)
SELECT id, nombre, precio FROM productos;       -- columnas específicas

-- Con expresión calculada y alias
SELECT
    nombre,
    precio,
    precio * 1.16 AS precio_con_iva
FROM productos;

SELECT DISTINCT categoria FROM productos;       -- sin duplicados
```

### UPDATE

```sql
UPDATE productos SET stock = stock - 1 WHERE id = 42;

-- Múltiples columnas
UPDATE productos
SET precio = precio * 1.05, modificado = NOW()
WHERE categoria = 'Electrónica';

-- Ver qué se actualizó
UPDATE productos SET activo = FALSE WHERE stock = 0
RETURNING id, nombre;
```

### DELETE

```sql
DELETE FROM productos WHERE id = 42;
DELETE FROM productos WHERE activo = FALSE AND stock = 0;

-- Ver qué se eliminó
DELETE FROM productos WHERE creado_en < NOW() - INTERVAL '1 year'
RETURNING id, nombre;
```

⚠ Siempre prueba el `WHERE` con un `SELECT` antes de ejecutar `UPDATE` o `DELETE`.
Un `UPDATE`/`DELETE` sin `WHERE` afecta **todas** las filas.

---

## 5. Filtros y condiciones: WHERE {#5-where}

### Operadores de comparación

```sql
WHERE precio = 100        -- igual
WHERE precio != 100       -- distinto (también: <>)
WHERE precio > 100
WHERE precio >= 100
WHERE precio < 100
WHERE precio <= 100
```

### Operadores lógicos

```sql
WHERE categoria = 'Electrónica' AND precio < 500
WHERE categoria = 'Libros'      OR categoria = 'Audio'
WHERE NOT activo

-- AND tiene mayor precedencia que OR; usa paréntesis para claridad
WHERE (categoria = 'A' OR categoria = 'B') AND precio < 100
```

### IN, BETWEEN, LIKE, ILIKE

```sql
-- IN
WHERE categoria IN ('Electrónica', 'Informática', 'Audio')
WHERE id NOT IN (1, 2, 3)

-- BETWEEN (inclusivo en ambos extremos)
WHERE precio BETWEEN 100 AND 500
WHERE creado_en BETWEEN '2024-01-01' AND '2024-12-31'

-- LIKE: % = cualquier secuencia, _ = un carácter
WHERE nombre LIKE 'Lap%'       -- empieza con Lap
WHERE nombre LIKE '%book'      -- termina con book
WHERE nombre LIKE '%pro%'      -- contiene pro
WHERE nombre LIKE 'A_ton'      -- Aton, Anton, Aiton…

-- ILIKE: insensible a mayúsculas (PostgreSQL)
WHERE nombre ILIKE '%laptop%'  -- encuentra Laptop, LAPTOP, laptop
```

⚠ **MySQL**: `LIKE` es case-insensitive por defecto según collation. No tiene `ILIKE`.

### IS NULL / IS NOT NULL

```sql
WHERE descripcion IS NULL
WHERE descripcion IS NOT NULL

-- NUNCA: = NULL o != NULL siempre devuelven cero filas
WHERE descripcion = NULL    -- INCORRECTO
WHERE descripcion IS NULL   -- CORRECTO
```

### EXISTS

```sql
-- Más eficiente que IN para subconsultas grandes
WHERE EXISTS (
    SELECT 1 FROM pedidos WHERE pedidos.producto_id = productos.id
)
WHERE NOT EXISTS (
    SELECT 1 FROM pedidos WHERE pedidos.producto_id = productos.id
)
```

---

╔══════════════════════════════════════════════════════════════════════╗
║  BLOQUE II — SELECT a fondo                                          ║
║  §6 al §13                                                           ║
╚══════════════════════════════════════════════════════════════════════╝

## 6. SELECT completo: alias, ORDER BY, LIMIT, FETCH, DISTINCT ON {#6-select}

### Alias de columna y tabla

```sql
SELECT nombre AS nombre_producto, precio AS "Precio Final"
FROM productos p            -- alias de tabla (sin AS)
WHERE p.stock > 0;
```

### ORDER BY

```sql
SELECT * FROM productos ORDER BY precio;                     -- ASC por defecto
SELECT * FROM productos ORDER BY precio DESC;
SELECT * FROM productos ORDER BY categoria ASC, precio DESC; -- múltiples columnas
SELECT * FROM productos ORDER BY precio * 1.16;              -- expresión

-- NULLs: por defecto van al final en ASC y al inicio en DESC
SELECT * FROM productos ORDER BY descripcion ASC NULLS LAST;
SELECT * FROM productos ORDER BY descripcion DESC NULLS FIRST;
```

### LIMIT, OFFSET y FETCH FIRST

```sql
-- Primeros 10 resultados
SELECT * FROM productos ORDER BY id LIMIT 10;

-- Página 3 (registros 21-30)
SELECT * FROM productos ORDER BY id LIMIT 10 OFFSET 20;

-- Sintaxis SQL estándar (SQL:2008) — equivalente a LIMIT/OFFSET
SELECT * FROM productos ORDER BY id FETCH FIRST 10 ROWS ONLY;
SELECT * FROM productos ORDER BY id
    OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY;
```

⚠ `OFFSET` grande es ineficiente: el motor lee y descarta las filas anteriores.
Para paginación eficiente en tablas grandes usa keyset pagination (ver §52).

⚠ **SQL Server**: usa `OFFSET m ROWS FETCH NEXT n ROWS ONLY` (requiere `ORDER BY`). No tiene `LIMIT`.

### DISTINCT y DISTINCT ON (PostgreSQL exclusivo)

```sql
-- DISTINCT: elimina filas completamente duplicadas
SELECT DISTINCT categoria FROM productos;

-- DISTINCT ON (col): retorna una fila por cada valor único de (col),
-- eligiendo la primera según el ORDER BY
SELECT DISTINCT ON (categoria) id, categoria, nombre, precio
FROM productos
ORDER BY categoria, precio DESC;   -- de cada categoría, el más caro
```

`DISTINCT ON` es la forma más limpia de obtener "el último/primer registro de cada grupo"
sin window functions. La columna en `DISTINCT ON` debe aparecer primero en `ORDER BY`.

### TABLESAMPLE: leer una muestra sin escanear la tabla completa

Cuando el objetivo es una estimación rápida sobre una tabla enorme (un promedio aproximado, una vista previa de datos), `TABLESAMPLE` le pide al motor una fracción de las filas sin leer la tabla completa, a diferencia de `ORDER BY RANDOM() LIMIT n`, que sí escanea todo antes de elegir.

```sql
-- SYSTEM: muestrea por página física, muy rápido, menos aleatorio
-- (puede devolver filas correlacionadas si la tabla no está bien distribuida en disco)
SELECT * FROM productos TABLESAMPLE SYSTEM (10);   -- ~10% de las filas

-- BERNOULLI: muestrea fila por fila, más lento, estadísticamente más uniforme
SELECT * FROM productos TABLESAMPLE BERNOULLI (10);

-- Con semilla fija, para que la muestra sea reproducible entre ejecuciones
SELECT * FROM productos TABLESAMPLE SYSTEM (10) REPEATABLE (42);
```

⚠ El porcentaje es aproximado, no exacto: sobre una tabla de un millón de filas, `TABLESAMPLE SYSTEM (10)` no garantiza devolver exactamente 100,000 filas. **MySQL** no tiene `TABLESAMPLE` nativo; la alternativa habitual es `ORDER BY RAND() LIMIT n`, con el costo de escaneo completo ya mencionado. **SQL Server** sí lo soporta, como `TABLESAMPLE (10 PERCENT)`.

---

## 7. Funciones de agregación {#7-agregacion}

Las funciones de agregación calculan un valor a partir de un conjunto de filas e ignoran NULLs (excepto `COUNT(*)`).

### Funciones básicas

```sql
SELECT
    COUNT(*)                  AS total_filas,        -- cuenta todas las filas
    COUNT(descripcion)        AS con_descripcion,    -- solo cuenta NOT NULL
    COUNT(DISTINCT categoria) AS categorias_unicas,
    SUM(precio)               AS suma,
    AVG(precio)               AS promedio,
    MIN(precio)               AS minimo,
    MAX(precio)               AS maximo
FROM productos;
```

### Funciones de agregación de texto y array

```sql
-- Concatenar valores en un string
SELECT STRING_AGG(nombre, ', ' ORDER BY nombre) AS lista
FROM productos;

-- Agrupar valores en un array
SELECT ARRAY_AGG(id ORDER BY precio) AS ids_por_precio
FROM productos;

-- Agrupar como JSON
SELECT JSON_AGG(nombre ORDER BY nombre) FROM productos;
SELECT JSONB_AGG(row_to_json(p.*)) FROM productos p;
```

### FILTER: agregación condicional

```sql
SELECT
    COUNT(*) FILTER (WHERE activo = TRUE)          AS activos,
    COUNT(*) FILTER (WHERE activo = FALSE)         AS inactivos,
    SUM(precio) FILTER (WHERE categoria = 'Electrónica') AS suma_elec,
    AVG(precio) FILTER (WHERE stock > 0)           AS prom_disponibles
FROM productos;
```

Equivalente sin FILTER (más verboso):
```sql
COUNT(CASE WHEN activo = TRUE THEN 1 END)
```

### ORDER BY dentro de funciones de agregación

```sql
SELECT STRING_AGG(nombre, ', ' ORDER BY nombre ASC) FROM productos;
SELECT ARRAY_AGG(id ORDER BY creado_en DESC)         FROM productos;
```

⚠ `FILTER` es SQL estándar (SQL:2003). MySQL no lo soporta.

---

## 8. Agregados de conjunto ordenado (WITHIN GROUP) {#8-within-group}

Funciones que requieren un `ORDER BY` explícito dentro de la agregación.

```sql
-- PERCENTILE_CONT: interpola el valor (puede no estar en los datos)
SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY precio) AS mediana
FROM productos;

-- PERCENTILE_DISC: retorna el valor real más cercano del conjunto
SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY precio) AS mediana_disc
FROM productos;

-- Múltiples percentiles de una vez
SELECT
    PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY precio) AS p25,
    PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY precio) AS p50,
    PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY precio) AS p75,
    PERCENTILE_CONT(0.90) WITHIN GROUP (ORDER BY precio) AS p90
FROM productos;

-- MODE: valor más frecuente
SELECT MODE() WITHIN GROUP (ORDER BY categoria) AS categoria_mas_comun
FROM productos;

-- Hipotéticos: ¿en qué posición quedaría un valor hipotético?
SELECT RANK(500) WITHIN GROUP (ORDER BY precio) FROM productos;
SELECT DENSE_RANK(500) WITHIN GROUP (ORDER BY precio) FROM productos;
SELECT PERCENT_RANK(500) WITHIN GROUP (ORDER BY precio) FROM productos;
```

⚠ MySQL no soporta `WITHIN GROUP`. SQL Server tiene equivalentes parciales (`PERCENTILE_CONT` con `OVER`).

---

## 9. GROUP BY, HAVING, ROLLUP, CUBE, GROUPING SETS {#9-group-by}

### GROUP BY y HAVING

```sql
-- Una fila por combinación única de columnas agrupadas
SELECT categoria, COUNT(*) AS cantidad, AVG(precio) AS precio_prom
FROM productos
GROUP BY categoria;

-- Regla: toda columna en SELECT que no sea agregación debe estar en GROUP BY

-- HAVING filtra grupos (post-GROUP BY). WHERE filtra filas (pre-GROUP BY)
SELECT categoria, COUNT(*) AS cantidad, AVG(precio) AS precio_prom
FROM productos
WHERE activo = TRUE               -- filtra filas antes de agrupar
GROUP BY categoria
HAVING COUNT(*) >= 5              -- filtra grupos con menos de 5 productos
ORDER BY precio_prom DESC;
```

### ROLLUP: subtotales jerárquicos

```sql
SELECT
    COALESCE(categoria::TEXT, 'TOTAL GENERAL') AS categoria,
    COALESCE(subcategoria::TEXT, 'SUBTOTAL')    AS subcategoria,
    SUM(precio) AS suma
FROM productos
GROUP BY ROLLUP (categoria, subcategoria);
-- Genera filas: (cat, subcat) + (cat, NULL) + (NULL, NULL)
```

### CUBE: todas las combinaciones posibles

```sql
SELECT region, categoria, SUM(ventas)
FROM ventas
GROUP BY CUBE (region, categoria);
-- Genera: (region,cat), (region,NULL), (NULL,cat), (NULL,NULL)
```

### GROUPING SETS: control exacto

```sql
SELECT region, categoria, SUM(ventas)
FROM ventas
GROUP BY GROUPING SETS (
    (region, categoria),
    (region),
    (categoria),
    ()
);
```

⚠ **MySQL**: soporta `ROLLUP`, no `CUBE` ni `GROUPING SETS`. **SQL Server**: los tres.

---

## 10. JOINs {#10-joins}

```
Tabla A (izquierda)           Tabla B (derecha)
┌────────────────┐            ┌──────────────────┐
│ id │ nombre    │            │ a_id │ valor      │
│  1 │ Ana       │──────────► │  1   │ X          │
│  2 │ Bob       │──────────► │  1   │ Y          │
│  3 │ Carlos    │            │  4   │ Z (sin par)│
└────────────────┘            └──────────────────┘
```

### INNER JOIN (solo coincidencias)

```sql
SELECT p.nombre, c.nombre AS categoria
FROM productos p
INNER JOIN categorias c ON p.categoria_id = c.id;

-- USING: cuando las columnas tienen el mismo nombre en ambas tablas
SELECT p.nombre, c.nombre
FROM productos p JOIN categorias c USING (categoria_id);
```

### LEFT JOIN (todas las filas de la izquierda)

```sql
-- Todos los productos, incluyendo los sin pedidos
SELECT p.nombre, COUNT(pe.id) AS num_pedidos
FROM productos p
LEFT JOIN pedidos pe ON pe.producto_id = p.id
GROUP BY p.id, p.nombre;

-- Truco: encontrar huérfanos (filas sin match a la derecha)
SELECT p.nombre FROM productos p
LEFT JOIN pedidos pe ON pe.producto_id = p.id
WHERE pe.id IS NULL;    -- productos que nunca se han pedido
```

### RIGHT JOIN, FULL OUTER JOIN

```sql
-- RIGHT JOIN: todas las filas de la derecha (raramente necesario; invierte las tablas y usa LEFT)
SELECT a.nombre, b.nombre FROM tabla_a a RIGHT JOIN tabla_b b ON a.id = b.a_id;

-- FULL OUTER JOIN: todas las filas de ambas tablas, NULLs donde no hay match
SELECT a.nombre AS a, b.nombre AS b
FROM tabla_a a FULL OUTER JOIN tabla_b b ON a.id = b.a_id;
```

### CROSS JOIN y SELF JOIN

```sql
-- CROSS JOIN: producto cartesiano (n × m filas)
SELECT c.nombre AS color, t.nombre AS talla
FROM colores c CROSS JOIN tallas t;

-- SELF JOIN: una tabla unida consigo misma
SELECT e.nombre AS empleado, m.nombre AS manager
FROM empleados e LEFT JOIN empleados m ON e.manager_id = m.id;
```

### NATURAL JOIN (evitar en producción)

```sql
-- Hace JOIN automático por todas las columnas del mismo nombre.
-- Frágil: el comportamiento cambia si agregas columnas.
SELECT * FROM productos NATURAL JOIN categorias;  -- EVITAR
-- Preferir siempre columnas explícitas:
SELECT * FROM productos JOIN categorias USING (categoria_id);
```

### JOINs múltiples

```sql
SELECT c.nombre AS cliente, p.id, pr.nombre AS producto, d.cantidad
FROM clientes c
JOIN pedidos p         ON p.cliente_id   = c.id
JOIN detalle_pedido d  ON d.pedido_id    = p.id
JOIN productos pr      ON pr.id          = d.producto_id
WHERE c.pais = 'México';
```

---

## 11. Subconsultas y EXISTS vs IN {#11-subconsultas}

### Subconsulta en WHERE

```sql
-- Escalar: retorna un valor
SELECT nombre FROM productos
WHERE precio > (SELECT AVG(precio) FROM productos);

-- Con IN: retorna una lista
SELECT nombre FROM clientes
WHERE id IN (SELECT DISTINCT cliente_id FROM pedidos);
```

### Subconsulta en FROM (tabla derivada)

```sql
SELECT categoria, precio_prom
FROM (
    SELECT categoria, AVG(precio) AS precio_prom
    FROM productos GROUP BY categoria
) AS stats                      -- alias obligatorio en PostgreSQL
WHERE precio_prom > 200;
```

### Subconsulta correlacionada en SELECT

```sql
-- Se ejecuta una vez por cada fila del SELECT exterior
SELECT nombre,
    (SELECT COUNT(*) FROM pedidos WHERE producto_id = p.id) AS num_pedidos
FROM productos p;
-- ⚠ Puede ser lento en tablas grandes; considera LEFT JOIN + GROUP BY
```

### EXISTS vs IN: diferencias críticas

```sql
-- EXISTS: eficiente para subconsultas grandes; para al primer match
SELECT nombre FROM productos p
WHERE EXISTS (SELECT 1 FROM pedidos WHERE producto_id = p.id);

-- IN: puede ser problemático con NULLs
SELECT nombre FROM productos
WHERE id IN (SELECT producto_id FROM pedidos);
```

**El problema de NOT IN con NULLs** — trampa clásica:

```sql
-- Si pedidos contiene algún producto_id NULL:
WHERE id NOT IN (SELECT producto_id FROM pedidos);
-- ← Devuelve 0 filas aunque existan productos no pedidos
-- Porque: x NOT IN (1, 2, NULL) evalúa x<>1 AND x<>2 AND x<>NULL
-- x<>NULL es UNKNOWN, y TRUE AND UNKNOWN = UNKNOWN (no TRUE)

-- Solución 1: NOT EXISTS (siempre correcto)
WHERE NOT EXISTS (SELECT 1 FROM pedidos WHERE producto_id = p.id);

-- Solución 2: filtrar NULLs explícitamente
WHERE id NOT IN (SELECT producto_id FROM pedidos WHERE producto_id IS NOT NULL);
```

### Subconsultas correlacionadas

```sql
-- Productos cuyo precio supera el promedio de SU categoría
SELECT nombre, categoria, precio FROM productos p
WHERE precio > (
    SELECT AVG(precio) FROM productos p2
    WHERE p2.categoria = p.categoria    -- correlación: referencia columna exterior
);
```

---

## 12. CTEs (WITH): básicas, modificables, recursivas {#12-cte}

### CTE básica

```sql
WITH clientes_activos AS (
    SELECT id, nombre FROM clientes
    WHERE activo = TRUE AND ultimo_login > NOW() - INTERVAL '90 days'
)
SELECT c.nombre, COUNT(p.id) AS pedidos_recientes
FROM clientes_activos c
JOIN pedidos p ON p.cliente_id = c.id
WHERE p.fecha >= NOW() - INTERVAL '30 days'
GROUP BY c.id, c.nombre;
```

### Múltiples CTEs

```sql
WITH
    ventas_mes AS (
        SELECT DATE_TRUNC('month', fecha) AS mes, SUM(total) AS total_mes
        FROM pedidos GROUP BY 1
    ),
    prom_global AS (
        SELECT AVG(total_mes) AS prom FROM ventas_mes
    )
SELECT vm.mes, vm.total_mes, pg.prom, vm.total_mes - pg.prom AS diferencia
FROM ventas_mes vm, prom_global pg
ORDER BY vm.mes;
```

### CTEs modificables (Writable CTEs)

```sql
-- Mover filas de una tabla a otra atómicamente
WITH filas_archivadas AS (
    DELETE FROM pedidos_activos
    WHERE fecha < NOW() - INTERVAL '1 year'
    RETURNING *
)
INSERT INTO pedidos_historico SELECT * FROM filas_archivadas;
```

### CTEs recursivas

```sql
-- Árbol organizacional: todos los subordinados de un manager
WITH RECURSIVE subordinados AS (
    -- Caso base
    SELECT id, nombre, manager_id, 0 AS nivel
    FROM empleados WHERE id = 5

    UNION ALL

    -- Caso recursivo
    SELECT e.id, e.nombre, e.manager_id, s.nivel + 1
    FROM empleados e
    JOIN subordinados s ON e.manager_id = s.id
)
SELECT nivel, REPEAT('  ', nivel) || nombre AS nombre_indentado
FROM subordinados ORDER BY nivel, nombre;

-- Recorrer árbol de categorías con ruta completa
WITH RECURSIVE ruta AS (
    SELECT id, nombre, padre_id, nombre::TEXT AS camino
    FROM categorias WHERE padre_id IS NULL
    UNION ALL
    SELECT c.id, c.nombre, c.padre_id, r.camino || ' > ' || c.nombre
    FROM categorias c JOIN ruta r ON c.padre_id = r.id
)
SELECT camino FROM ruta ORDER BY camino;
```

⚠ Una CTE recursiva sin condición de terminación correcta puede ejecutarse indefinidamente.
PostgreSQL tiene `max_recursion_depth` (por defecto 10,000 iteraciones).

---

## 13. Window Functions (funciones de ventana) {#13-window}

Las window functions calculan valores sobre un conjunto de filas relacionadas **sin colapsar filas** como `GROUP BY`. Permiten rankings, running totals y comparaciones entre filas vecinas.

### Anatomía de OVER()

```sql
funcion() OVER (
    [PARTITION BY columnas]   -- divide en grupos independientes
    [ORDER BY columnas]       -- ordena dentro de cada partición
    [frame_clause]            -- define el "marco" de filas visibles
)
```

### Funciones de ranking

```sql
SELECT nombre, categoria, precio,
    ROW_NUMBER()   OVER (PARTITION BY categoria ORDER BY precio DESC) AS rn,
    RANK()         OVER (PARTITION BY categoria ORDER BY precio DESC) AS rnk,
    DENSE_RANK()   OVER (PARTITION BY categoria ORDER BY precio DESC) AS drnk,
    NTILE(4)       OVER (PARTITION BY categoria ORDER BY precio DESC) AS cuartil,
    PERCENT_RANK() OVER (PARTITION BY categoria ORDER BY precio DESC) AS pct_rank
FROM productos;

-- ROW_NUMBER:  1,2,3,4,5   (siempre único)
-- RANK:        1,2,2,4,5   (empates saltan posiciones)
-- DENSE_RANK:  1,2,2,3,4   (empates NO saltan posiciones)
-- NTILE(4):    divide en 4 grupos del mismo tamaño
```

### Top-N por grupo (patrón común)

```sql
SELECT nombre, categoria, precio
FROM (
    SELECT nombre, categoria, precio,
           ROW_NUMBER() OVER (PARTITION BY categoria ORDER BY precio DESC) AS rn
    FROM productos
) ranked
WHERE rn = 1;      -- el más caro de cada categoría
```

### Funciones de desplazamiento

```sql
SELECT fecha, ventas,
    LAG(ventas, 1)  OVER (ORDER BY fecha)  AS ventas_ayer,
    LEAD(ventas, 1) OVER (ORDER BY fecha)  AS ventas_manana,
    FIRST_VALUE(ventas) OVER (ORDER BY fecha
        ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS primera,
    LAST_VALUE(ventas) OVER (ORDER BY fecha
        ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS ultima
FROM ventas_diarias;
```

### Agregados como ventana

```sql
SELECT nombre, precio, categoria,
    SUM(precio)  OVER ()                        AS total_global,
    SUM(precio)  OVER (PARTITION BY categoria)  AS total_categoria,
    AVG(precio)  OVER (PARTITION BY categoria)  AS avg_categoria,
    precio - AVG(precio) OVER (PARTITION BY categoria) AS desviacion
FROM productos;
```

### Frame clause: control del marco

```sql
-- Running total (acumulado)
SUM(ventas) OVER (ORDER BY fecha ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)

-- Media móvil de 7 días
AVG(ventas) OVER (ORDER BY fecha ROWS BETWEEN 6 PRECEDING AND CURRENT ROW)

-- Ventana deslizante ±3 días
AVG(ventas) OVER (ORDER BY fecha ROWS BETWEEN 3 PRECEDING AND 3 FOLLOWING)
```

### WINDOW: reusar definición de ventana

```sql
SELECT nombre, precio,
    ROW_NUMBER() OVER w AS rn,
    RANK()       OVER w AS rnk,
    AVG(precio)  OVER w AS avg_cat
FROM productos
WINDOW w AS (PARTITION BY categoria ORDER BY precio DESC);
```

⚠ **MySQL**: soporta window functions desde MySQL 8.0.

---

╔══════════════════════════════════════════════════════════════════════╗
║  BLOQUE III — NULL y expresiones condicionales                       ║
║  §14 al §16                                                          ║
╚══════════════════════════════════════════════════════════════════════╝

## 14. NULL: semántica, lógica de tres valores, IS DISTINCT FROM {#14-null}

NULL significa "valor desconocido". No es cero, no es cadena vacía, no es FALSE.

### Lógica de tres valores (TRUE / FALSE / UNKNOWN)

```
TRUE  AND UNKNOWN = UNKNOWN
FALSE AND UNKNOWN = FALSE      ← FALSE prevalece
TRUE  OR  UNKNOWN = TRUE       ← TRUE prevalece
FALSE OR  UNKNOWN = UNKNOWN
NOT UNKNOWN       = UNKNOWN
```

Cualquier operación aritmética o comparación con NULL produce UNKNOWN.
`WHERE` solo incluye filas donde la condición es TRUE; UNKNOWN se comporta como FALSE.

```sql
SELECT * FROM t WHERE NULL = NULL;     -- 0 filas (UNKNOWN)
SELECT * FROM t WHERE NULL IS NULL;    -- todas las filas (TRUE)
SELECT * FROM t WHERE 1 = NULL;        -- 0 filas
SELECT * FROM t WHERE 1 != NULL;       -- 0 filas también
```

### NULL en agregaciones

```sql
SELECT
    COUNT(*)           AS total_filas,   -- 100 (cuenta todas)
    COUNT(descripcion) AS con_desc,      -- 67  (ignora NULLs)
    AVG(precio)        AS prom_precio    -- ignora NULLs automáticamente
FROM productos;
```

### IS DISTINCT FROM: comparación NULL-safe

```sql
-- Problema: = no distingue correctamente con NULLs
WHERE a = b       -- NULL = NULL → UNKNOWN (no TRUE)

-- Solución: IS DISTINCT FROM
WHERE a IS DISTINCT FROM b       -- NULL IS DISTINCT FROM NULL → FALSE (son iguales)
WHERE a IS NOT DISTINCT FROM b   -- NULL IS NOT DISTINCT FROM NULL → TRUE

-- Caso de uso: actualizar solo si el valor cambió (incluyendo NULL → valor o valor → NULL)
UPDATE productos SET nota = nueva_nota
WHERE nota IS DISTINCT FROM nueva_nota;
```

⚠ **MySQL 8.0+**: usa `<=>` (NULL-safe equals). `a <=> b` equivale a `a IS NOT DISTINCT FROM b`.  
⚠ **SQL Server**: no tiene esta sintaxis; hay que usar `CASE WHEN a IS NULL AND b IS NULL THEN...`.

### NULLs en concatenación y aritmética

```sql
SELECT 'hola' || NULL || 'mundo';    -- NULL (contamina)
SELECT 'hola' || COALESCE(NULL, '') || 'mundo';  -- 'holamundo'

SELECT NULL + 5;     -- NULL
SELECT NULL * 0;     -- NULL (no es 0)
```

---

## 15. CASE, COALESCE, NULLIF, GREATEST, LEAST {#15-case}

### CASE

```sql
-- CASE simple (igualdad)
SELECT nombre,
    CASE categoria
        WHEN 'Electrónica' THEN 'Tecnología'
        WHEN 'Libros'      THEN 'Educación'
        ELSE 'Otros'
    END AS categoria_agrupada
FROM productos;

-- CASE buscado (condiciones booleanas)
SELECT nombre, precio,
    CASE
        WHEN precio < 100  THEN 'Económico'
        WHEN precio < 500  THEN 'Medio'
        WHEN precio < 1000 THEN 'Premium'
        ELSE 'Lujo'
    END AS segmento
FROM productos;

-- CASE en ORDER BY: primero los agotados al final
ORDER BY CASE WHEN stock = 0 THEN 1 ELSE 0 END, precio;

-- CASE en UPDATE
UPDATE productos SET descuento =
    CASE
        WHEN stock > 100 THEN 0.20
        WHEN stock > 50  THEN 0.10
        ELSE 0
    END;
```

### COALESCE: primer valor no-NULL

```sql
SELECT COALESCE(descripcion, 'Sin descripción') FROM productos;
SELECT COALESCE(precio_oferta, precio_normal, 0) AS precio_efectivo FROM productos;
-- Devuelve el primer argumento NOT NULL; el resto no se evalúa
```

### NULLIF: devuelve NULL si dos valores son iguales

```sql
SELECT NULLIF(stock, 0);    -- NULL si stock=0, de lo contrario el stock real

-- Caso clásico: evitar división por cero
SELECT ventas / NULLIF(unidades, 0) AS precio_unitario FROM datos;
-- Si unidades=0, el divisor es NULL y el resultado es NULL (no error)
```

### GREATEST y LEAST

```sql
SELECT GREATEST(precio_a, precio_b, precio_c);  -- máximo de varios valores
SELECT LEAST(100, NULL, 50, 75);               -- 50 (ignora NULLs)

UPDATE comisiones SET porcentaje = LEAST(porcentaje, 0.30);      -- máximo 30%
UPDATE precios   SET precio = GREATEST(precio, costo * 1.10);   -- mínimo +10%
```

---

## 16. Casting y conversión de tipos: CAST, :: {#16-casting}

### Sintaxis

```sql
CAST(valor AS tipo)    -- SQL estándar
valor::tipo            -- PostgreSQL nativo (preferido)
tipo(valor)            -- notación funcional (limitada)

-- Ejemplos
SELECT '42'::INTEGER;
SELECT '2024-01-15'::DATE;
SELECT 3.14::TEXT;
SELECT '{"a":1}'::JSONB;
SELECT ARRAY['1','2','3']::INTEGER[];
```

### Conversiones comunes

```sql
-- Texto → número
SELECT '123.45'::NUMERIC;
SELECT '42'::INT;

-- Número → texto
SELECT 'ID: ' || id::TEXT FROM tabla;

-- Fechas
SELECT '2024-01-15'::DATE;
SELECT '14:30:00'::TIME;
SELECT '2024-01-15 14:30:00+00'::TIMESTAMPTZ;
SELECT '3 days'::INTERVAL;

-- Booleanos
SELECT 'true'::BOOLEAN;   -- también: 't', 'yes', 'on', '1', 'y'
SELECT 'false'::BOOLEAN;  -- también: 'f', 'no', 'off', '0', 'n'
```

### TO_CHAR, TO_NUMBER, TO_DATE: formato controlado

```sql
SELECT TO_CHAR(precio, 'FM$999,999.00');              -- $1,234.50
SELECT TO_CHAR(NOW(), 'DD/MM/YYYY HH24:MI');          -- 15/01/2024 14:30
SELECT TO_CHAR(NOW(), 'Day, DD "de" Month YYYY');     -- Lunes, 15 de Enero 2024

SELECT TO_NUMBER('1.234,50', '9G999D99');             -- 1234.50
SELECT TO_DATE('15/01/2024', 'DD/MM/YYYY');           -- 2024-01-15
SELECT TO_TIMESTAMP('15/01/2024 14:30', 'DD/MM/YYYY HH24:MI');
```

### Conversión implícita vs explícita

```sql
-- Implícita: PostgreSQL puede convertir automáticamente
SELECT * FROM productos WHERE id = '42';   -- '42' se convierte a INTEGER

-- Problema: la conversión implícita puede impedir el uso de índices
SELECT * FROM tabla WHERE columna_text = 123;  -- cast en cada fila, no usa índice

-- Siempre mejor: cast explícito en condiciones de búsqueda
SELECT * FROM tabla WHERE columna_text = '123';   -- usa índice
SELECT * FROM tabla WHERE columna_int = '123'::INTEGER;
```

---

╔══════════════════════════════════════════════════════════════════════╗
║  BLOQUE IV — Tipos de datos avanzados                                ║
║  §17 al §23                                                          ║
╚══════════════════════════════════════════════════════════════════════╝

## 17. Texto: VARCHAR, TEXT y funciones de string {#17-texto}

```sql
-- Longitud
SELECT LENGTH('café');           -- 4 (caracteres Unicode)
SELECT OCTET_LENGTH('café');     -- 5 (bytes en UTF-8: é ocupa 2 bytes)

-- Mayúsculas / minúsculas / título
SELECT UPPER('hola mundo');      -- HOLA MUNDO
SELECT LOWER('HOLA MUNDO');      -- hola mundo
SELECT INITCAP('hola mundo');    -- Hola Mundo

-- Espacios
SELECT TRIM('  hola  ');         -- 'hola'
SELECT LTRIM('  hola  ');        -- 'hola  '
SELECT RTRIM('  hola  ');        -- '  hola'
SELECT TRIM(BOTH 'x' FROM 'xxxholaxxx'); -- 'hola'

-- Padding
SELECT LPAD('42', 6, '0');       -- '000042'
SELECT RPAD('hola', 8, '.');     -- 'hola....'

-- Extracción
SELECT SUBSTRING('hola mundo', 6);           -- 'mundo'
SELECT SUBSTRING('hola mundo', 1, 4);        -- 'hola'
SELECT LEFT('hola mundo', 4);                -- 'hola'
SELECT RIGHT('hola mundo', 5);               -- 'mundo'

-- Búsqueda
SELECT POSITION('mundo' IN 'hola mundo');    -- 6
SELECT STRPOS('hola mundo', 'mundo');        -- 6

-- Reemplazo
SELECT REPLACE('hola mundo', 'mundo', 'earth'); -- 'hola earth'
SELECT TRANSLATE('aeiou', 'aeiou', '12345');    -- reemplaza char por char

-- Concatenación
SELECT CONCAT('hola', ' ', 'mundo');             -- 'hola mundo' (ignora NULLs)
SELECT 'hola' || ' ' || 'mundo';                 -- 'hola mundo' (NULL contamina)
SELECT CONCAT_WS(', ', 'Ana', 'Bob', NULL, 'Carlos'); -- 'Ana, Bob, Carlos'

-- División
SELECT SPLIT_PART('a,b,c,d', ',', 2);       -- 'b'
SELECT STRING_TO_ARRAY('a,b,c', ',');        -- '{a,b,c}'

-- Repetición y formato
SELECT REPEAT('ab', 3);                      -- 'ababab'
SELECT FORMAT('Hola %s, tienes %s años', nombre, edad) FROM personas;

-- ASCII / CHR
SELECT ASCII('A');    -- 65
SELECT CHR(65);       -- 'A'

-- Codificación
SELECT ENCODE('datos'::BYTEA, 'base64');
SELECT DECODE('ZGF0b3M=', 'base64')::TEXT;
```

### Collations: cómo se comparan y ordenan los strings

Una collation define, para un tipo de texto, dos cosas a la vez: qué orden siguen los caracteres (`ORDER BY`) y qué cuenta como "igual" en una comparación o un índice. No es un detalle cosmético, es la causa real detrás de dos problemas frecuentes en producción: un `ORDER BY` que no ordena como se espera con acentos o mayúsculas, y un `JOIN` entre columnas con distinta collation que falla o se vuelve inusualmente lento porque no puede usar el índice.

```sql
-- Ver la collation por defecto de la base de datos
SHOW LC_COLLATE;

-- Comparar el mismo ORDER BY con dos collations distintas
SELECT nombre FROM clientes ORDER BY nombre COLLATE "C";        -- orden binario (por byte)
SELECT nombre FROM clientes ORDER BY nombre COLLATE "es-x-icu"; -- orden "natural" en español

-- Definir la collation al crear una columna
CREATE TABLE clientes (
    nombre TEXT COLLATE "es-x-icu"
);

-- Índice sensible a la collation de la columna
CREATE INDEX idx_clientes_nombre ON clientes (nombre);
```

`COLLATE "C"` ordena por el valor numérico de cada byte, rápido y predecible, pero no coincide con el orden alfabético real de ningún idioma ("Álvarez" puede quedar después de "Zamora"). Una collation de locale, como `"es-x-icu"` (basada en ICU) o `"es_ES.utf8"` (basada en la biblioteca C del sistema operativo), ordena como esperaría un hablante del idioma, a costa de ser más lenta de calcular.

⚠ Dos columnas con distinta collation no pueden compararse ni unirse directamente sin un `COLLATE` explícito en al menos una de ellas (`ERROR: collation mismatch`), y un índice creado con una collation no acelera una consulta que compara con otra distinta.
⚠ **MySQL**: usa `CHARACTER SET` + `COLLATE` combinados (por ejemplo `utf8mb4_unicode_ci` para insensible a mayúsculas, `utf8mb4_bin` para sensible). **SQL Server**: collations a nivel de columna con `COLLATE`, por ejemplo `Latin1_General_CI_AS` (case-insensitive, accent-sensitive).

---

## 18. Numéricos: INTEGER, NUMERIC, FLOAT y funciones {#18-numericos}

```sql
-- Redondeo
SELECT ROUND(3.14159, 2);   -- 3.14
SELECT CEIL(3.1);           -- 4  (hacia arriba)
SELECT FLOOR(3.9);          -- 3  (hacia abajo)
SELECT TRUNC(3.9);          -- 3  (trunca hacia cero)
SELECT TRUNC(3.14159, 2);   -- 3.14

-- Valor absoluto y signo
SELECT ABS(-42);            -- 42
SELECT SIGN(-42);           -- -1 | SIGN(0) = 0 | SIGN(42) = 1

-- Potencia y raíz
SELECT POWER(2, 10);        -- 1024.0
SELECT SQRT(144);           -- 12.0
SELECT CBRT(27);            -- 3.0 (raíz cúbica)
SELECT 2 ^ 10;              -- 1024 (operador PostgreSQL)

-- Módulo
SELECT MOD(17, 5);          -- 2
SELECT 17 % 5;              -- 2

-- Logaritmos
SELECT LOG(100);            -- 2.0   (base 10)
SELECT LOG(2, 8);           -- 3.0   (log base 2 de 8)
SELECT LN(2.71828);         -- ~1.0  (logaritmo natural)
SELECT EXP(1);              -- e ≈ 2.71828

-- Trigonometría (en radianes)
SELECT SIN(PI()/2);         -- 1.0
SELECT COS(0);              -- 1.0
SELECT DEGREES(PI());       -- 180.0
SELECT RADIANS(180);        -- π

-- Aleatorio
SELECT RANDOM();                              -- 0.0 ≤ x < 1.0
SELECT FLOOR(RANDOM() * 100 + 1)::INTEGER;   -- entero entre 1 y 100
```

---

## 19. Fecha y tiempo: DATE, TIMESTAMP, TIMESTAMPTZ, INTERVAL {#19-fecha}

### Obtener la fecha/hora actual

```sql
SELECT NOW();              -- TIMESTAMPTZ (con zona horaria)
SELECT CURRENT_TIMESTAMP;  -- igual que NOW()
SELECT CURRENT_DATE;       -- solo DATE
SELECT CURRENT_TIME;       -- solo TIME con zona horaria
SELECT LOCALTIMESTAMP;     -- TIMESTAMP sin zona horaria
```

### Extracción de partes

```sql
SELECT EXTRACT(YEAR   FROM NOW());  -- 2024
SELECT EXTRACT(MONTH  FROM NOW());  -- 1
SELECT EXTRACT(DAY    FROM NOW());  -- 15
SELECT EXTRACT(HOUR   FROM NOW());  -- 14
SELECT EXTRACT(DOW    FROM NOW());  -- 1 (0=domingo, 6=sábado)
SELECT EXTRACT(DOY    FROM NOW());  -- día del año
SELECT EXTRACT(WEEK   FROM NOW());  -- número de semana ISO
SELECT EXTRACT(EPOCH  FROM NOW());  -- segundos desde 1970-01-01 UTC

-- DATE_PART: alias de EXTRACT con sintaxis alternativa
SELECT DATE_PART('year', NOW());
```

### DATE_TRUNC: truncar a una unidad

```sql
SELECT DATE_TRUNC('month', NOW());  -- primer día del mes, a las 00:00
SELECT DATE_TRUNC('year',  NOW());  -- 1 de enero del año
SELECT DATE_TRUNC('week',  NOW());  -- lunes de la semana (ISO)
SELECT DATE_TRUNC('hour',  NOW());  -- inicio de la hora actual

-- Agrupar pedidos por mes:
SELECT DATE_TRUNC('month', fecha) AS mes, SUM(total)
FROM pedidos GROUP BY 1 ORDER BY 1;
```

### Aritmética de fechas

```sql
SELECT NOW() + INTERVAL '30 days';
SELECT NOW() - INTERVAL '1 year 2 months 15 days';
SELECT '2024-03-01'::DATE - '2024-01-15'::DATE;  -- 46 días

SELECT AGE(NOW(), '1990-05-20'::DATE);             -- '33 years 7 months 11 days'
SELECT AGE('2024-01-15', '1990-05-20');

-- Construir fechas programáticamente
SELECT MAKE_DATE(2024, 1, 15);
SELECT MAKE_TIMESTAMP(2024, 1, 15, 14, 30, 0);
SELECT MAKE_INTERVAL(years => 1, months => 2, days => 15);
```

### Zona horaria

```sql
-- Convertir a otra zona
SELECT NOW() AT TIME ZONE 'America/Mexico_City';
SELECT NOW() AT TIME ZONE 'UTC';
SELECT NOW() AT TIME ZONE 'America/New_York';

-- Zona de la sesión
SHOW timezone;
SET timezone = 'America/Mexico_City';
```

⚠ Siempre usa `TIMESTAMPTZ` en aplicaciones multiregión. PostgreSQL almacena en UTC internamente.

---

## 20. JSON y JSONB {#20-json}

### JSON vs JSONB

| Aspecto | JSON | JSONB |
|---------|------|-------|
| Almacenamiento | Texto plano | Binario descompuesto |
| Velocidad escritura | Más rápido | Más lento (parsing) |
| Velocidad lectura | Más lento | Más rápido |
| Índices | No | GIN, B-tree parcial |
| Preserva orden claves | Sí | No |
| **Uso recomendado** | Logs/audit | **Todo lo demás** |

### Operadores de acceso

```sql
-- -> retorna JSON (con comillas si es string)
-- ->> retorna TEXT (sin comillas)
SELECT datos -> 'nombre'            -- "Ana"   (tipo JSON)
SELECT datos ->> 'nombre'           -- Ana     (tipo TEXT)

-- Anidado
SELECT datos -> 'direccion' -> 'ciudad'  -- acceso encadenado
SELECT datos #> '{direccion,ciudad}'     -- con path array
SELECT datos #>> '{direccion,ciudad}'    -- como TEXT

-- Elemento de array
SELECT datos -> 'tags' -> 0    -- primer elemento
SELECT datos -> 'tags' ->> 1   -- segundo elemento como TEXT
```

### Operadores de filtrado

```sql
-- @>  el lado derecho está contenido en el izquierdo
SELECT * FROM docs WHERE datos @> '{"nombre": "Ana"}';
SELECT * FROM docs WHERE datos @> '{"tags": ["admin"]}';

-- ?   existe la clave
SELECT * FROM docs WHERE datos ? 'nombre';
SELECT * FROM docs WHERE datos ?| ARRAY['nombre', 'edad'];  -- alguna
SELECT * FROM docs WHERE datos ?& ARRAY['nombre', 'edad'];  -- todas
```

### Funciones de construcción y modificación

```sql
SELECT JSONB_BUILD_OBJECT('nombre', 'Ana', 'edad', 30);
SELECT JSONB_BUILD_ARRAY(1, 'dos', TRUE, NULL);
SELECT ROW_TO_JSON(p.*) FROM productos p LIMIT 1;

-- Modificar
SELECT JSONB_SET(datos, '{direccion,cp}', '"06600"') FROM docs;
SELECT datos - 'edad'                  FROM docs;  -- eliminar clave
SELECT datos - ARRAY['edad', 'tags']   FROM docs;  -- eliminar varias
SELECT datos #- '{direccion,ciudad}'   FROM docs;  -- eliminar anidado
SELECT datos || '{"extra": true}'::JSONB FROM docs; -- merge/concatenar

-- Extraer como filas
SELECT * FROM JSONB_EACH('{"a":1,"b":2}');
SELECT * FROM JSONB_EACH_TEXT('{"a":"x","b":"y"}');
SELECT * FROM JSONB_ARRAY_ELEMENTS('[1,2,3]');
SELECT JSONB_OBJECT_KEYS('{"a":1,"b":2}');
```

### Índices en JSONB

```sql
-- GIN: habilita @>, ?, ?|, ?&
CREATE INDEX idx_datos_gin ON documentos USING GIN (datos);

-- GIN con jsonb_path_ops: más compacto, solo soporta @>
CREATE INDEX idx_datos_path ON documentos USING GIN (datos jsonb_path_ops);

-- Índice de expresión para campo específico
CREATE INDEX idx_email ON documentos ((datos ->> 'email'));
-- Permite: WHERE datos ->> 'email' = 'ana@example.com'
```

---

## 21. Arrays {#21-arrays}

### Declaración y acceso

```sql
CREATE TABLE articulos (
    id     SERIAL PRIMARY KEY,
    tags   TEXT[],
    precios NUMERIC[]
);

-- Insertar
INSERT INTO articulos (tags, precios)
VALUES (ARRAY['electrónica', 'nuevo'], ARRAY[999.99, 899.99]);
-- Con literal de texto:
VALUES ('{electrónica,nuevo}', '{999.99,899.99}');

-- Los arrays en PostgreSQL son 1-indexed
SELECT tags[1]    FROM articulos;   -- 'electrónica'
SELECT precios[2] FROM articulos;   -- 899.99

-- Slice [desde:hasta]
SELECT tags[1:2]  FROM articulos;   -- '{electrónica,nuevo}'

-- Longitud
SELECT ARRAY_LENGTH(tags, 1) FROM articulos;  -- dimensión 1
SELECT CARDINALITY(tags)     FROM articulos;  -- total de elementos
```

### Operadores

```sql
-- ANY: la condición se cumple para algún elemento
SELECT * FROM articulos WHERE 'nuevo' = ANY(tags);

-- @>  contiene el array derecho
SELECT * FROM articulos WHERE tags @> ARRAY['nuevo'];

-- <@  está contenido en
SELECT * FROM articulos WHERE tags <@ ARRAY['electrónica','nuevo','oferta'];

-- &&  tienen al menos un elemento en común
SELECT * FROM articulos WHERE tags && ARRAY['oferta','liquidación'];

-- Concatenar
SELECT ARRAY[1,2] || ARRAY[3,4];   -- {1,2,3,4}
SELECT ARRAY[1,2] || 5;            -- {1,2,5}
```

### Funciones

```sql
SELECT ARRAY_APPEND(tags, 'premium')            FROM articulos;
SELECT ARRAY_PREPEND('urgente', tags)            FROM articulos;
SELECT ARRAY_REMOVE(tags, 'nuevo')               FROM articulos;
SELECT ARRAY_POSITION(tags, 'electrónica')       FROM articulos;  -- índice
SELECT ARRAY_REPLACE(tags, 'nuevo', 'reciente')  FROM articulos;

-- UNNEST: convierte array en filas
SELECT id, UNNEST(tags) AS tag FROM articulos;

-- Con índice de posición
SELECT id, tag, pos
FROM articulos, UNNEST(tags) WITH ORDINALITY AS t(tag, pos);

-- ALL: condición se cumple para todos los elementos
SELECT * FROM tabla WHERE 10 > ALL(numeros);
```

---

## 22. Tipos especiales: UUID, BOOLEAN, ENUM, BYTEA, INET, RANGE {#22-especiales}

### UUID

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";  -- para uuid_generate_v4()

CREATE TABLE pedidos (
    -- PostgreSQL 13+: gen_random_uuid() sin extensión
    id UUID PRIMARY KEY DEFAULT gen_random_uuid()
);

SELECT gen_random_uuid();
```

### BOOLEAN

```sql
activo BOOLEAN NOT NULL DEFAULT TRUE

-- En WHERE no necesitas = TRUE
SELECT * FROM productos WHERE activo;        -- equivale a WHERE activo = TRUE
SELECT * FROM productos WHERE NOT activo;
```

### ENUM

```sql
CREATE TYPE estado_pedido AS ENUM (
    'pendiente', 'procesando', 'enviado', 'entregado', 'cancelado'
);

CREATE TABLE pedidos (
    estado estado_pedido NOT NULL DEFAULT 'pendiente'
);

-- Ver todos los valores
SELECT unnest(enum_range(NULL::estado_pedido));

-- Agregar valor (solo al final, o en posición específica)
ALTER TYPE estado_pedido ADD VALUE 'en_revision' AFTER 'procesando';

-- Los ENUMs se ordenan según el orden de definición
SELECT * FROM pedidos ORDER BY estado;
```

### INET y CIDR

```sql
CREATE TABLE conexiones (
    ip  INET,   -- IPv4 o IPv6, con máscara opcional
    red CIDR    -- solo dirección de red (borra bits de host)
);

-- Operadores de red
SELECT '192.168.1.100'::INET << '192.168.1.0/24'::INET;  -- TRUE: está dentro
SELECT '10.0.0.1'::INET << '192.168.1.0/24'::INET;       -- FALSE
```

### RANGE types

```sql
-- Tipos nativos: INT4RANGE, INT8RANGE, NUMRANGE, TSRANGE, TSTZRANGE, DATERANGE

CREATE TABLE reservas (
    sala_id INTEGER,
    periodo TSTZRANGE NOT NULL,
    EXCLUDE USING GIST (sala_id WITH =, periodo WITH &&)  -- no overlap
);

INSERT INTO reservas VALUES (1, '[2024-01-15 09:00, 2024-01-15 11:00)');
-- [ = extremo inclusivo, ) = exclusivo

SELECT '[1,10)'::INT4RANGE @> 5;           -- TRUE: contiene valor
SELECT '[1,5)'::INT4RANGE && '[3,8)'::INT4RANGE;   -- TRUE: se solapan
SELECT '[1,5)'::INT4RANGE * '[3,8)'::INT4RANGE;    -- [3,5): intersección
SELECT '[1,5)'::INT4RANGE + '[3,8)'::INT4RANGE;    -- [1,8): unión
```

---

## 23. Dominios y tipos compuestos {#23-dominios}

### CREATE DOMAIN: tipo con constraints reutilizables

```sql
CREATE DOMAIN email AS TEXT
    NOT NULL
    CHECK (VALUE ~ '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$');

CREATE DOMAIN precio_positivo AS NUMERIC(12, 2)
    NOT NULL CHECK (VALUE >= 0);

CREATE DOMAIN porcentaje AS NUMERIC(5, 2)
    CHECK (VALUE BETWEEN 0 AND 100);

-- Usar en tabla
CREATE TABLE clientes (
    id     SERIAL PRIMARY KEY,
    correo email,
    precio precio_positivo
);

-- Modificar el dominio (aplica a TODAS las tablas que lo usan)
ALTER DOMAIN email ADD CONSTRAINT chk_len CHECK (LENGTH(VALUE) <= 255);
ALTER DOMAIN email DROP CONSTRAINT chk_len;
DROP DOMAIN email CASCADE;  -- elimina también columnas que lo usan
```

### CREATE TYPE: tipos compuestos

```sql
CREATE TYPE direccion AS (
    calle   TEXT,
    ciudad  TEXT,
    estado  TEXT,
    cp      CHAR(5)
);

CREATE TABLE clientes (
    id        SERIAL PRIMARY KEY,
    nombre    TEXT,
    dir_envio direccion
);

-- Insertar
INSERT INTO clientes (nombre, dir_envio)
VALUES ('Ana', ROW('Av. Reforma 100', 'CDMX', 'CDMX', '06600'));

-- Acceder a campo (paréntesis alrededor del campo requeridos)
SELECT nombre, (dir_envio).ciudad FROM clientes;
SELECT nombre, (dir_envio).cp FROM clientes;
```

---

╔══════════════════════════════════════════════════════════════════════╗
║  BLOQUE V — DDL avanzado                                             ║
║  §24 al §28                                                          ║
╚══════════════════════════════════════════════════════════════════════╝

## 24. Constraints: PK, FK, UNIQUE, CHECK, EXCLUDE, DEFERRABLE {#24-constraints}

### PRIMARY KEY y FOREIGN KEY

```sql
CREATE TABLE pedidos (
    id          SERIAL PRIMARY KEY,
    cliente_id  INTEGER NOT NULL REFERENCES clientes(id),

    -- ON DELETE / ON UPDATE: qué hacer cuando la fila referenciada cambia
    categoria_id INTEGER REFERENCES categorias(id)
        ON DELETE SET NULL      -- pone NULL si se borra la categoría
        ON UPDATE CASCADE,      -- actualiza si cambia el id

    producto_id INTEGER REFERENCES productos(id)
        ON DELETE RESTRICT,     -- error si se intenta borrar el producto

    -- ON DELETE CASCADE: elimina este pedido si se elimina el producto
    -- ON DELETE SET DEFAULT: pone el valor DEFAULT
    -- ON DELETE NO ACTION: como RESTRICT pero más flexible (deferrable)
);
```

### UNIQUE y CHECK

```sql
-- Columna única
email TEXT UNIQUE,

-- Único compuesto
CONSTRAINT uq_pedido_prod UNIQUE (pedido_id, producto_id),

-- Índice UNIQUE condicional
CREATE UNIQUE INDEX idx_email_activo ON clientes(email) WHERE activo = TRUE;

-- NULLS NOT DISTINCT (PostgreSQL 15+): trata varios NULL como duplicados entre sí
-- Por defecto UNIQUE deja pasar múltiples NULL, porque NULL nunca es "igual" a NULL
CREATE TABLE integraciones (
    proveedor_id INTEGER,
    codigo_externo TEXT,
    UNIQUE NULLS NOT DISTINCT (proveedor_id, codigo_externo)
);
-- Sin NULLS NOT DISTINCT, esto insertaría dos filas sin error:
-- INSERT INTO integraciones VALUES (1, NULL); INSERT INTO integraciones VALUES (1, NULL);
-- Con NULLS NOT DISTINCT, la segunda fila viola el UNIQUE.

-- CHECK de columna
precio NUMERIC CHECK (precio > 0),

-- CHECK de tabla (múltiples columnas)
CONSTRAINT chk_precios CHECK (precio_oferta < precio_normal),
CONSTRAINT chk_fechas  CHECK (fecha_fin >= fecha_inicio)
```

### DEFERRABLE: diferir verificación hasta el COMMIT

```sql
-- Por defecto los constraints se verifican en cada sentencia.
-- DEFERRABLE INITIALLY DEFERRED los verifica al COMMIT.

CREATE TABLE nodos (
    id        SERIAL PRIMARY KEY,
    padre_id  INTEGER REFERENCES nodos(id)
        DEFERRABLE INITIALLY DEFERRED  -- permite referencias circulares temporales
);

-- Controlar deferimiento en una transacción
BEGIN;
SET CONSTRAINTS ALL DEFERRED;
INSERT INTO nodos VALUES (1, 2);  -- padre 2 no existe aún → sin error
INSERT INTO nodos VALUES (2, 1);
COMMIT;   -- se verifican los constraints aquí

-- Diferir solo un constraint específico
SET CONSTRAINTS fk_nodos_padre DEFERRED;
```

### EXCLUDE: constraint de exclusión (PostgreSQL)

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE reservas (
    sala_id INTEGER,
    periodo TSTZRANGE,
    EXCLUDE USING GIST (
        sala_id WITH =,    -- misma sala
        periodo WITH &&    -- periodos solapados
    )
    -- Garantiza: no dos reservas para la misma sala en el mismo horario
);
```

---

## 25. Columnas generadas (GENERATED ALWAYS AS) {#25-generated}

Las columnas generadas calculan su valor automáticamente. No se pueden insertar ni actualizar directamente.

```sql
CREATE TABLE productos (
    id             SERIAL PRIMARY KEY,
    nombre         TEXT,
    precio         NUMERIC(10, 2),
    iva_rate       NUMERIC(4, 4) DEFAULT 0.16,
    precio_con_iva NUMERIC(10, 2)
        GENERATED ALWAYS AS (precio * (1 + iva_rate)) STORED,
    busqueda       TSVECTOR
        GENERATED ALWAYS AS (to_tsvector('spanish', nombre)) STORED
);

-- STORED: calculado al INSERT/UPDATE y guardado en disco
-- PostgreSQL solo soporta STORED (no VIRTUAL como MySQL)

-- Se consulta como cualquier columna
SELECT nombre, precio, precio_con_iva FROM productos;

-- ERROR: no se puede asignar manualmente
INSERT INTO productos (nombre, precio, precio_con_iva) VALUES (...);  -- Error

-- Más ejemplos
CREATE TABLE personas (
    nombre           TEXT,
    apellido         TEXT,
    nombre_completo  TEXT GENERATED ALWAYS AS (nombre || ' ' || apellido) STORED,
    inicial          CHAR(1) GENERATED ALWAYS AS (LEFT(nombre, 1)) STORED
);
```

⚠ **MySQL**: soporta tanto `STORED` como `VIRTUAL`. **SQL Server**: "computed columns" con `AS (expr)`.

---

## 26. Índices {#26-indices}

### Tipos de índices

```sql
-- B-TREE: predeterminado; para =, <, >, BETWEEN, IN, ORDER BY, LIKE 'x%'
CREATE INDEX idx_precio ON productos (precio);
CREATE INDEX idx_precio_desc ON productos (precio DESC);
CREATE INDEX idx_multi ON pedidos (cliente_id, fecha DESC);  -- compuesto

-- HASH: solo para igualdad (=). Raramente mejor que B-tree en PostgreSQL moderno
CREATE INDEX idx_hash ON tabla USING HASH (columna);

-- GIN: para arrays, JSONB, full-text search
CREATE INDEX idx_tags   ON articulos  USING GIN (tags);
CREATE INDEX idx_datos  ON documentos USING GIN (datos);
CREATE INDEX idx_fts    ON articulos  USING GIN (to_tsvector('spanish', contenido));

-- GiST: para rangos, geometría, EXCLUDE constraints
CREATE INDEX idx_periodo ON reservas USING GIST (periodo);

-- BRIN: para columnas con correlación física (timestamps de inserción)
-- Muy pequeño, útil solo cuando los datos están físicamente ordenados
CREATE INDEX idx_brin ON eventos USING BRIN (creado_en);
```

### Índices parciales

```sql
-- Solo indexa el subconjunto que cumple la condición
CREATE INDEX idx_activos   ON productos (nombre)  WHERE activo = TRUE;
CREATE INDEX idx_pendientes ON pedidos (cliente_id) WHERE estado = 'pendiente';
-- Más pequeño, más rápido para ese subconjunto
```

### Índices de expresión

```sql
-- Para búsquedas case-insensitive
CREATE INDEX idx_nombre_lower ON clientes (LOWER(nombre));
-- Habilita: WHERE LOWER(nombre) = 'ana'  usando el índice

-- Para partes de fecha
CREATE INDEX idx_año ON pedidos (EXTRACT(YEAR FROM fecha)::INTEGER);

-- Para campo de JSONB
CREATE INDEX idx_json_email ON documentos ((datos ->> 'email'));
-- Habilita: WHERE datos ->> 'email' = 'ana@example.com'
```

### Gestión de índices

```sql
-- Ver índices de una tabla
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'productos';

-- Crear sin bloquear escrituras (lento, pero no bloquea)
CREATE INDEX CONCURRENTLY idx_nuevo ON tabla (columna);

-- Reconstruir índice
REINDEX INDEX idx_productos_precio;
REINDEX TABLE productos;   -- todos los índices de la tabla

-- Ver si el planner usa el índice
EXPLAIN SELECT * FROM productos WHERE precio < 100;
EXPLAIN ANALYZE SELECT * FROM productos WHERE precio < 100;
```

---

## 27. Vistas {#27-vistas}

### CREATE VIEW

```sql
CREATE VIEW productos_disponibles AS
SELECT id, nombre, precio, stock
FROM productos
WHERE activo = TRUE AND stock > 0;

-- Usar la vista como tabla
SELECT * FROM productos_disponibles WHERE precio < 500;

-- Reemplazar una vista existente
CREATE OR REPLACE VIEW productos_disponibles AS ...;

DROP VIEW productos_disponibles;
DROP VIEW IF EXISTS productos_disponibles CASCADE;
```

### Vistas actualizables

Una vista es automáticamente actualizable si: una sola tabla en FROM, sin GROUP BY, DISTINCT, UNION, LIMIT, agregaciones ni columnas calculadas.

```sql
CREATE VIEW productos_electronica AS
SELECT id, nombre, precio FROM productos WHERE categoria = 'Electrónica'
WITH CHECK OPTION;   -- previene INSERT/UPDATE que la vista no vería

-- Se puede modificar a través de la vista
UPDATE productos_electronica SET precio = 999.99 WHERE id = 1;
```

### MATERIALIZED VIEW: datos en caché

```sql
CREATE MATERIALIZED VIEW mv_ventas_mes AS
SELECT DATE_TRUNC('month', fecha) AS mes, SUM(total) AS ventas
FROM pedidos GROUP BY 1;

-- Crear índice sobre la vista materializada
CREATE INDEX idx_mv_mes ON mv_ventas_mes (mes);

-- Actualizar datos (bloquea durante el refresh)
REFRESH MATERIALIZED VIEW mv_ventas_mes;

-- Sin bloqueo (requiere UNIQUE INDEX en la vista)
REFRESH MATERIALIZED VIEW CONCURRENTLY mv_ventas_mes;
```

---

## 28. Secuencias y auto-incremento {#28-secuencias}

### SERIAL vs IDENTITY

```sql
-- Forma antigua: SERIAL (implícitamente crea una secuencia)
id SERIAL      PRIMARY KEY   -- INTEGER + secuencia
id BIGSERIAL   PRIMARY KEY   -- BIGINT  + secuencia
id SMALLSERIAL PRIMARY KEY   -- SMALLINT + secuencia

-- Forma moderna: IDENTITY (SQL estándar, PostgreSQL 10+)
id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY
   -- ALWAYS: no se puede insertar valor manual (más seguro)
id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY
   -- BY DEFAULT: se puede insertar valor manual (útil para migraciones)
```

### CREATE SEQUENCE manual

```sql
CREATE SEQUENCE seq_numero_pedido
    START WITH 1000
    INCREMENT BY 1
    MINVALUE 1000
    MAXVALUE 9999999
    NO CYCLE        -- error si llega al máximo
    CACHE 10;       -- pre-genera 10 valores (más rápido, puede crear huecos)

-- Funciones de secuencia
SELECT NEXTVAL('seq_numero_pedido');   -- siguiente valor (avanza la secuencia)
SELECT CURRVAL('seq_numero_pedido');   -- valor actual en esta sesión
SELECT LASTVAL();                      -- última secuencia usada en esta sesión
SELECT SETVAL('seq_numero_pedido', 5000, true);  -- establecer valor

-- Usar como DEFAULT en columna
numero INTEGER DEFAULT NEXTVAL('seq_numero_pedido')
```

⚠ Las secuencias NO retroceden en caso de ROLLBACK. Los valores "consumidos" en transacciones abortadas se pierden. No uses secuencias si necesitas números consecutivos sin huecos.

---

╔══════════════════════════════════════════════════════════════════════╗
║  BLOQUE VI — DML avanzado                                            ║
║  §29 al §33                                                          ║
╚══════════════════════════════════════════════════════════════════════╝

## 29. INSERT avanzado: ON CONFLICT (UPSERT), RETURNING, multi-row {#29-insert}

### INSERT … ON CONFLICT (UPSERT)

```sql
-- Si el INSERT viola una constraint UNIQUE/PK, ejecuta otra acción
INSERT INTO productos (id, nombre, precio, stock)
VALUES (1, 'Laptop', 999.99, 50)
ON CONFLICT (id) DO UPDATE
    SET nombre = EXCLUDED.nombre,    -- EXCLUDED = la fila que intentaba insertarse
        precio = EXCLUDED.precio,
        stock  = productos.stock + EXCLUDED.stock,  -- combinar valores
        modificado = NOW();

-- DO NOTHING: ignorar silenciosamente el conflicto
INSERT INTO categorias (nombre) VALUES ('Electrónica')
ON CONFLICT (nombre) DO NOTHING;

-- Conflict en constraint nombrado
INSERT INTO tabla VALUES (...)
ON CONFLICT ON CONSTRAINT uq_email DO UPDATE SET ...;
```

### RETURNING: ver qué se modificó

```sql
-- INSERT con RETURNING
INSERT INTO clientes (nombre, email) VALUES ('Ana', 'ana@example.com')
RETURNING id, creado_en;

-- UPDATE con RETURNING
UPDATE pedidos SET estado = 'enviado'
WHERE fecha < NOW() - INTERVAL '2 days' AND estado = 'procesando'
RETURNING id, cliente_id, fecha;

-- DELETE con RETURNING
DELETE FROM sesiones WHERE expira_en < NOW()
RETURNING usuario_id, creado_en;

-- RETURNING en CTEs (insertar y usar el resultado)
WITH nuevo_cliente AS (
    INSERT INTO clientes (nombre) VALUES ('Bob')
    RETURNING id
)
INSERT INTO suscripciones (cliente_id, plan)
SELECT id, 'basic' FROM nuevo_cliente;
```

---

## 30. UPDATE avanzado: UPDATE FROM, RETURNING, UPDATE con CTE {#30-update}

### UPDATE … FROM (JOIN implícito)

```sql
-- Actualizar usando datos de otra tabla
UPDATE productos p
SET precio = p.precio * c.factor_ajuste
FROM ajustes_precio c
WHERE c.categoria = p.categoria;

-- Equivalente con subconsulta (más portable)
UPDATE productos SET precio = precio * (
    SELECT factor_ajuste FROM ajustes_precio
    WHERE categoria = productos.categoria
);

-- UPDATE con JOIN a múltiples tablas
UPDATE detalle_pedido d
SET precio_unitario = pr.precio
FROM pedidos pe
JOIN productos pr ON pr.id = d.producto_id
WHERE d.pedido_id = pe.id
  AND pe.estado = 'pendiente'
  AND pr.precio_actualizado = TRUE;
```

### UPDATE con CTE

```sql
-- Calcular y aplicar en un solo paso
WITH calculo AS (
    SELECT
        id,
        precio * (1 + COALESCE(iva_rate, 0.16)) AS precio_final
    FROM productos
    WHERE activo = TRUE
)
UPDATE productos p
SET precio_iva = c.precio_final
FROM calculo c
WHERE p.id = c.id;
```

---

## 31. DELETE avanzado: DELETE USING, RETURNING, TRUNCATE {#31-delete}

### DELETE … USING (JOIN para condición)

```sql
-- Eliminar filas usando datos de otra tabla
DELETE FROM detalle_pedido d
USING pedidos p
WHERE d.pedido_id = p.id
  AND p.estado = 'cancelado'
  AND p.fecha < NOW() - INTERVAL '1 year';

-- Eliminar duplicados manteniendo el más reciente
DELETE FROM logs l1
USING logs l2
WHERE l1.id < l2.id
  AND l1.evento = l2.evento
  AND l1.usuario_id = l2.usuario_id;
```

### TRUNCATE vs DELETE

| Aspecto | DELETE | TRUNCATE |
|---------|--------|----------|
| WHERE | Sí | No (elimina todo) |
| RETURNING | Sí | No |
| Velocidad | Lenta en tablas grandes | Muy rápida |
| Dispara triggers | Sí | Solo statement-level |
| Resetea secuencias | No | Con RESTART IDENTITY |
| Transaccional | Sí | Sí (en PostgreSQL) |

```sql
TRUNCATE TABLE logs;
TRUNCATE TABLE logs RESTART IDENTITY;
TRUNCATE TABLE pedidos, detalle_pedido CASCADE;   -- en orden correcto con FKs
```

---

## 32. MERGE: WHEN MATCHED / WHEN NOT MATCHED {#32-merge}

MERGE combina INSERT, UPDATE y DELETE según si existe o no una coincidencia.
Es SQL estándar (SQL:2003). PostgreSQL lo soporta desde la versión 15.

```sql
MERGE INTO inventario AS destino
USING nuevas_entradas AS origen
    ON destino.producto_id = origen.producto_id

WHEN MATCHED AND origen.cantidad > 0 THEN
    UPDATE SET
        cantidad   = destino.cantidad + origen.cantidad,
        actualizado = NOW()

WHEN MATCHED AND origen.cantidad = 0 THEN
    DELETE

WHEN NOT MATCHED THEN
    INSERT (producto_id, cantidad, actualizado)
    VALUES (origen.producto_id, origen.cantidad, NOW());

-- Retornar qué hizo MERGE
MERGE INTO inventario AS d USING nuevas_entradas AS o ON d.producto_id = o.producto_id
WHEN MATCHED THEN UPDATE SET cantidad = d.cantidad + o.cantidad
WHEN NOT MATCHED THEN INSERT VALUES (o.producto_id, o.cantidad)
RETURNING merge_action(), d.*;   -- 'INSERT', 'UPDATE', 'DELETE'
```

⚠ **PostgreSQL**: disponible desde v15. **MySQL**: no soporta `MERGE`; su equivalente es `INSERT … ON DUPLICATE KEY UPDATE`, no `ON CONFLICT` (esa sintaxis es de PostgreSQL/SQLite, MySQL nunca la tuvo).
⚠ **SQL Server**: tiene `MERGE` desde SQL Server 2008. **Oracle**: también soporta `MERGE`.

### Equivalente de MySQL: INSERT … ON DUPLICATE KEY UPDATE

```sql
-- Requiere una PRIMARY KEY o UNIQUE sobre la que detectar el duplicado
INSERT INTO inventario (producto_id, cantidad, actualizado)
VALUES (501, 20, NOW())
ON DUPLICATE KEY UPDATE
    cantidad    = cantidad + VALUES(cantidad),   -- VALUES(col) = el valor que intentabas insertar
    actualizado = NOW();

-- Insertar varias filas de una vez, cada una con su propio UPSERT
INSERT INTO inventario (producto_id, cantidad) VALUES
    (501, 20),
    (502, 5)
ON DUPLICATE KEY UPDATE cantidad = cantidad + VALUES(cantidad);
```

`VALUES(columna)` referencia el valor que la fila entrante intentaba insertar antes del conflicto, el mismo papel que cumple `EXCLUDED` en el `ON CONFLICT` de PostgreSQL (§29). A diferencia de `MERGE`, no puede decidir entre `UPDATE`/`INSERT`/`DELETE` en la misma sentencia, solo `INSERT` o `UPDATE`.

---

## 33. Locking reads: SELECT FOR UPDATE, FOR SHARE, NOWAIT, SKIP LOCKED {#33-locking}

Cuando necesitas leer filas y luego modificarlas, el intervalo entre el SELECT y el UPDATE puede
dejar que otra transacción modifique esas filas. `SELECT FOR UPDATE` bloquea las filas leídas.

```sql
-- Bloqueo exclusivo: nadie más puede modificar estas filas hasta tu COMMIT
BEGIN;
SELECT * FROM cuentas WHERE id = 42 FOR UPDATE;
UPDATE cuentas SET saldo = saldo - 100 WHERE id = 42;
COMMIT;

-- FOR SHARE: bloqueo compartido; otras transacciones pueden leer pero no modificar
SELECT * FROM productos WHERE id = 1 FOR SHARE;

-- NOWAIT: falla inmediatamente si las filas ya están bloqueadas (no espera)
SELECT * FROM pedidos WHERE id = 5 FOR UPDATE NOWAIT;
-- Genera error: ERROR: could not obtain lock on row in relation "pedidos"

-- SKIP LOCKED: salta las filas bloqueadas (para colas de trabajo concurrentes)
SELECT * FROM jobs WHERE estado = 'pendiente'
ORDER BY creado_en
LIMIT 1
FOR UPDATE SKIP LOCKED;
-- Usado en workers concurrentes: cada worker toma un job diferente sin esperar
```

### Patrón de cola de trabajo

```sql
-- Worker seguro con SKIP LOCKED
BEGIN;
WITH job AS (
    SELECT id FROM jobs WHERE estado = 'pendiente'
    ORDER BY prioridad DESC, creado_en ASC
    LIMIT 1
    FOR UPDATE SKIP LOCKED
)
UPDATE jobs SET estado = 'procesando', worker_id = $1
WHERE id = (SELECT id FROM job)
RETURNING *;
COMMIT;
```

⚠ **MySQL InnoDB**: tiene `SELECT … FOR UPDATE` y `FOR SHARE`, pero no `SKIP LOCKED` antes de MySQL 8.0.

---

╔══════════════════════════════════════════════════════════════════════╗
║  BLOQUE VII — Operadores de conjunto y construcciones adicionales    ║
║  §34 al §37                                                          ║
╚══════════════════════════════════════════════════════════════════════╝

## 34. UNION, UNION ALL, INTERSECT, EXCEPT {#34-set-ops}

Combinan resultados de múltiples consultas. Las columnas deben ser compatibles en número y tipo.

```sql
-- UNION: combina y elimina duplicados (más lento)
SELECT nombre FROM clientes
UNION
SELECT nombre FROM proveedores;

-- UNION ALL: combina sin eliminar duplicados (más rápido, generalmente preferido)
SELECT nombre, 'cliente'   AS tipo FROM clientes
UNION ALL
SELECT nombre, 'proveedor' AS tipo FROM proveedores;

-- INTERSECT: solo filas presentes en AMBAS consultas
SELECT producto_id FROM pedidos_2023
INTERSECT
SELECT producto_id FROM pedidos_2024;  -- productos pedidos en ambos años

-- EXCEPT: filas de la primera consulta que NO están en la segunda
SELECT producto_id FROM catalogo
EXCEPT
SELECT producto_id FROM pedidos;       -- productos nunca pedidos
```

### ORDER BY y LIMIT con operadores de conjunto

```sql
-- ORDER BY aplica al resultado COMPLETO
SELECT nombre FROM clientes
UNION ALL
SELECT nombre FROM proveedores
ORDER BY nombre;   -- ordena todo el resultado combinado

-- LIMIT también aplica al resultado final
SELECT id FROM tabla_a
UNION ALL
SELECT id FROM tabla_b
ORDER BY id
LIMIT 100;
```

⚠ **MySQL**: soporta `UNION` y `UNION ALL`. Desde MySQL 8.0 soporta `INTERSECT` y `EXCEPT`.

---

## 35. Expresiones regulares: LIKE, ILIKE, SIMILAR TO, ~, regexp_* {#35-regex}

### LIKE e ILIKE

```sql
WHERE nombre LIKE 'Lap%'        -- empieza con Lap (% = cualquier secuencia)
WHERE nombre LIKE '%top'        -- termina con top
WHERE nombre LIKE '%pro%'       -- contiene pro
WHERE nombre LIKE 'La_top'      -- _ = exactamente un carácter
WHERE nombre ILIKE '%laptop%'   -- insensible a mayúsculas
```

### SIMILAR TO: expresiones regulares SQL

```sql
-- Sintaxis SQL estándar (más limitada que regex POSIX)
WHERE nombre SIMILAR TO '(Lap|Not)%'   -- empieza con Lap o Not
WHERE codigo SIMILAR TO '[0-9]{5}'     -- exactamente 5 dígitos
-- Metacaracteres: | * + ? {} [] ()  (igual que regex pero sin \ como escape)
```

### Operadores regex POSIX (PostgreSQL)

```sql
-- ~ y ~*: coincide con regex POSIX
WHERE nombre ~ '^Lap'           -- empieza con Lap (case-sensitive)
WHERE nombre ~* '^lap'          -- empieza con lap (case-insensitive)

-- !~ y !~*: NO coincide con regex
WHERE email !~ '@gmail\.com$'
WHERE nombre !~* 'test'

-- Ejemplos de regex POSIX
WHERE email ~ '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$'
WHERE telefono ~ '^\+?[0-9]{10,15}$'
WHERE nombre ~ '[A-Z][a-z]+'    -- primera letra mayúscula, resto minúsculas
```

### Funciones de expresiones regulares

```sql
-- REGEXP_REPLACE: reemplazar con regex
SELECT REGEXP_REPLACE(telefono, '[^0-9]', '', 'g');    -- eliminar no-dígitos
SELECT REGEXP_REPLACE('hola mundo', '(\w+)', '[\1]', 'g');  -- envolver palabras

-- REGEXP_MATCH: retorna el primer match (array de grupos)
SELECT REGEXP_MATCH('abc123def', '([a-z]+)([0-9]+)');  -- {'abc','123'}

-- REGEXP_MATCHES: retorna todos los matches (una fila por match)
SELECT m[1] FROM REGEXP_MATCHES('one 1 two 2 three 3', '[0-9]+', 'g') AS m;

-- REGEXP_SPLIT_TO_TABLE: split con regex
SELECT REGEXP_SPLIT_TO_TABLE('uno, dos,  tres', '\s*,\s*');  -- una por fila

-- REGEXP_SPLIT_TO_ARRAY
SELECT REGEXP_SPLIT_TO_ARRAY('uno, dos, tres', ',\s*');     -- '{uno,dos,tres}'
```

---

## 36. Full-text search {#36-fts}

Full-text search permite búsqueda semántica en texto: maneja plurales, variantes de palabras
y stopwords (palabras vacías como "el", "la", "de").

### Conceptos base

```sql
-- to_tsvector: convierte texto a "documento" (lista de lexemas)
SELECT to_tsvector('spanish', 'Los perros corren rápido');
-- 'correr':3 'perro':2 'rápid':4  ← sin "Los" (stopword) y con raíces

-- to_tsquery: convierte búsqueda a query
SELECT to_tsquery('spanish', 'perro & correr');
SELECT to_tsquery('spanish', 'laptop | notebook');
SELECT to_tsquery('spanish', '!barato');           -- NOT barato
SELECT to_tsquery('spanish', 'laptop <-> rápido'); -- laptop seguido de rápido

-- plainto_tsquery: texto libre sin operadores
SELECT plainto_tsquery('spanish', 'laptop gamer económico');

-- phraseto_tsquery: búsqueda de frase exacta
SELECT phraseto_tsquery('spanish', 'base de datos');
```

### Buscar: operador @@

```sql
-- Buscar documentos que coincidan con la query
SELECT titulo FROM articulos
WHERE to_tsvector('spanish', contenido) @@ to_tsquery('spanish', 'postgre:*');

-- Con columna tsvector pre-calculada (mucho más eficiente)
ALTER TABLE articulos ADD COLUMN busqueda TSVECTOR
    GENERATED ALWAYS AS (to_tsvector('spanish', titulo || ' ' || contenido)) STORED;

CREATE INDEX idx_fts ON articulos USING GIN (busqueda);

SELECT titulo FROM articulos
WHERE busqueda @@ plainto_tsquery('spanish', 'transacciones ACID');
```

### Ranking de resultados

```sql
SELECT
    titulo,
    ts_rank(busqueda, query)      AS relevancia,
    ts_rank_cd(busqueda, query)   AS relevancia_cd,  -- considera proximidad
    ts_headline('spanish', contenido, query,
        'MaxWords=50, MinWords=20') AS extracto
FROM articulos,
    plainto_tsquery('spanish', 'transacciones ACID') AS query
WHERE busqueda @@ query
ORDER BY relevancia DESC;
```

---

## 37. LATERAL joins y funciones de tabla {#37-lateral}

`LATERAL` permite que una subquery en el `FROM` referencie columnas de tablas a su izquierda.
Sin `LATERAL`, las subqueries en FROM son independientes.

### LATERAL básico

```sql
-- Sin LATERAL: el subquery no puede referenciar 'c' exterior
-- Con LATERAL: sí puede
SELECT c.nombre, ultimos.id, ultimos.fecha
FROM clientes c
JOIN LATERAL (
    SELECT id, fecha FROM pedidos
    WHERE cliente_id = c.id       -- ← referencia a c (requiere LATERAL)
    ORDER BY fecha DESC
    LIMIT 3
) AS ultimos ON TRUE;             -- ON TRUE porque la condición ya está dentro
```

### generate_series: generar secuencias

```sql
-- Secuencia de números
SELECT generate_series(1, 10);              -- 1 al 10
SELECT generate_series(0, 100, 5);         -- 0, 5, 10, ..., 100

-- Secuencia de fechas (muy útil para llenar huecos en datos)
SELECT generate_series(
    '2024-01-01'::DATE,
    '2024-12-31'::DATE,
    '1 month'::INTERVAL
) AS mes;

-- Detectar días sin ventas
SELECT d.dia, COALESCE(v.total, 0) AS total
FROM generate_series('2024-01-01'::DATE, '2024-01-31'::DATE, '1 day') AS d(dia)
LEFT JOIN ventas_diarias v ON v.fecha = d.dia
ORDER BY d.dia;
```

### UNNEST con ORDINALITY

```sql
-- Convertir array en tabla con posición
SELECT tag, posicion
FROM articulos,
     UNNEST(tags) WITH ORDINALITY AS t(tag, posicion)
WHERE id = 1;
```

### ROWS FROM: múltiples funciones en paralelo

```sql
SELECT * FROM ROWS FROM (
    generate_series(1, 3),
    unnest(ARRAY['a', 'b', 'c'])
) AS t(num, letra);
-- Resultado: (1,a), (2,b), (3,c)
```

---

╔══════════════════════════════════════════════════════════════════════╗
║  BLOQUE VIII — Programación SQL / PL·pgSQL                           ║
║  §38 al §45                                                          ║
╚══════════════════════════════════════════════════════════════════════╝

## 38. Tablas temporales y COPY {#38-temp-copy}

### Tablas temporales

Las tablas temporales existen solo durante la sesión (o la transacción). Son privadas para la conexión.

```sql
-- Tabla temporal de sesión (dura hasta que la conexión se cierra)
CREATE TEMP TABLE tmp_resultados (
    id     INTEGER,
    valor  TEXT
);

-- ON COMMIT DROP: se elimina al hacer COMMIT de la transacción
CREATE TEMP TABLE tmp_calculo (
    producto_id INTEGER,
    total       NUMERIC
) ON COMMIT DROP;

-- ON COMMIT DELETE ROWS: se mantiene la estructura pero se vacía al COMMIT
CREATE TEMP TABLE tmp_staging (...) ON COMMIT DELETE ROWS;

-- ON COMMIT PRESERVE ROWS (default): los datos sobreviven el COMMIT
CREATE TEMP TABLE tmp_cache (...) ON COMMIT PRESERVE ROWS;

-- Llenar una tabla temporal con datos
CREATE TEMP TABLE tmp_top AS
SELECT id, nombre, precio FROM productos WHERE precio > 500;
```

### COPY: carga masiva de datos

`COPY` es la forma más rápida de cargar o exportar grandes volúmenes de datos en PostgreSQL.

```sql
-- Exportar tabla a archivo CSV (en el servidor)
COPY productos TO '/tmp/productos.csv' WITH (FORMAT CSV, HEADER TRUE);
COPY (SELECT id, nombre, precio FROM productos WHERE activo = TRUE)
    TO '/tmp/productos_activos.csv' WITH (FORMAT CSV, HEADER);

-- Importar CSV a tabla (en el servidor)
COPY productos (nombre, precio, stock) FROM '/tmp/nuevos.csv'
    WITH (FORMAT CSV, HEADER TRUE, DELIMITER ',');

-- Opciones de COPY
COPY tabla FROM '/ruta/archivo.csv' WITH (
    FORMAT CSV,
    HEADER TRUE,
    DELIMITER ',',
    QUOTE '"',
    ESCAPE '"',
    NULL ''           -- cómo representan los NULLs
);

-- \copy: versión del cliente psql (usa rutas del cliente, no del servidor)
\copy productos FROM '/ruta/local/archivo.csv' WITH (FORMAT CSV, HEADER)
```

⚠ `COPY` requiere privilegio de superusuario para rutas de servidor. `\copy` en psql usa el cliente y no requiere privilegios especiales.

---

## 39. PREPARE / EXECUTE / DEALLOCATE: prepared statements {#39-prepare}

Los prepared statements compilan la consulta una vez y la reutilizan con diferentes parámetros.
Beneficios: rendimiento (evita re-parsear) y seguridad (evita SQL injection).

```sql
-- Preparar: compila la consulta con $1, $2... como placeholders
PREPARE buscar_producto (INTEGER) AS
    SELECT id, nombre, precio FROM productos WHERE id = $1;

PREPARE insertar_pedido (INTEGER, NUMERIC, TEXT) AS
    INSERT INTO pedidos (cliente_id, total, estado)
    VALUES ($1, $2, $3)
    RETURNING id;

-- Ejecutar: provee los valores de los parámetros
EXECUTE buscar_producto(42);
EXECUTE insertar_pedido(5, 99.99, 'pendiente');

-- Liberar (elimina la prepared statement de la sesión)
DEALLOCATE buscar_producto;
DEALLOCATE ALL;   -- libera todas las prepared statements

-- Ver prepared statements de la sesión
SELECT * FROM pg_prepared_statements;
```

⚠ Las prepared statements son por sesión; no se comparten entre conexiones.
En aplicaciones reales, los drivers (psycopg2, pg, JDBC) usan prepared statements automáticamente.

---

## 40. Funciones: CREATE FUNCTION, PL/pgSQL, volatilidad {#40-funciones}

### Funciones SQL (sin lógica procedural)

```sql
-- La forma más simple: una función que ejecuta SQL
CREATE OR REPLACE FUNCTION precio_con_iva(p NUMERIC, tasa NUMERIC DEFAULT 0.16)
RETURNS NUMERIC AS $$
    SELECT p * (1 + tasa);
$$ LANGUAGE SQL IMMUTABLE;

-- Llamar:
SELECT precio_con_iva(100);          -- 116
SELECT precio_con_iva(100, 0.08);    -- 108
```

### Dollar-quoting: delimitar el cuerpo de la función

```sql
-- El cuerpo se encierra entre $$ ... $$ para evitar conflictos con comillas
-- Puedes usar cualquier etiqueta: $body$, $func$, $mi_etiqueta$

CREATE FUNCTION f() RETURNS TEXT AS $cuerpo$
    SELECT 'hola, mundo';
$cuerpo$ LANGUAGE SQL;
```

### Funciones PL/pgSQL

```sql
CREATE OR REPLACE FUNCTION calcular_descuento(
    p_precio    NUMERIC,
    p_categoria TEXT
) RETURNS NUMERIC AS $$
DECLARE
    v_descuento NUMERIC := 0;
BEGIN
    IF p_categoria = 'Electrónica' THEN
        v_descuento := 0.10;
    ELSIF p_categoria = 'Libros' THEN
        v_descuento := 0.20;
    ELSE
        v_descuento := 0.05;
    END IF;

    RETURN p_precio * (1 - v_descuento);
END;
$$ LANGUAGE plpgsql;

-- Usar:
SELECT calcular_descuento(100, 'Libros');   -- 80
```

### RETURNS TABLE: función que retorna filas

```sql
CREATE OR REPLACE FUNCTION productos_por_categoria(p_cat TEXT)
RETURNS TABLE (id INTEGER, nombre TEXT, precio NUMERIC) AS $$
BEGIN
    RETURN QUERY
        SELECT p.id, p.nombre, p.precio
        FROM productos p
        WHERE p.categoria = p_cat AND p.activo = TRUE
        ORDER BY p.precio;
END;
$$ LANGUAGE plpgsql;

-- Usar como tabla
SELECT * FROM productos_por_categoria('Electrónica');
```

### Parámetros IN, OUT, INOUT

```sql
CREATE FUNCTION dividir(
    IN  numerador   NUMERIC,
    IN  denominador NUMERIC,
    OUT cociente    NUMERIC,
    OUT residuo     NUMERIC
) AS $$
BEGIN
    cociente := numerador / denominador;
    residuo  := numerador % denominador;
END;
$$ LANGUAGE plpgsql;

-- Llamar: retorna fila con múltiples valores
SELECT * FROM dividir(17, 5);   -- cociente=3.4, residuo=2
```

### Volatilidad

La volatilidad le dice al planner cómo optimizar las llamadas a la función:

| Volatilidad | Descripción | Uso |
|-------------|-------------|-----|
| `IMMUTABLE` | Mismo input → siempre mismo output; puede cachearse | Funciones matemáticas puras |
| `STABLE` | Mismo input → mismo output en la misma consulta; no cacheado entre consultas | NOW(), funciones que leen tablas |
| `VOLATILE` | Puede retornar diferente resultado cada vez (default) | RANDOM(), INSERT, UPDATE |

---

## 41. Procedimientos almacenados: CREATE PROCEDURE {#41-procedimientos}

Disponibles desde PostgreSQL 11. La diferencia con funciones: los procedimientos pueden hacer `COMMIT`/`ROLLBACK` internamente.

```sql
CREATE OR REPLACE PROCEDURE transferir_saldo(
    p_origen  INTEGER,
    p_destino INTEGER,
    p_monto   NUMERIC
)
LANGUAGE plpgsql AS $$
BEGIN
    -- Verificar saldo disponible
    IF (SELECT saldo FROM cuentas WHERE id = p_origen) < p_monto THEN
        RAISE EXCEPTION 'Saldo insuficiente en cuenta %', p_origen;
    END IF;

    UPDATE cuentas SET saldo = saldo - p_monto WHERE id = p_origen;
    UPDATE cuentas SET saldo = saldo + p_monto WHERE id = p_destino;

    -- Los procedures pueden hacer COMMIT parcial
    -- (las funciones no pueden)
COMMIT;
END;
$$;

-- Llamar con CALL (no con SELECT)
CALL transferir_saldo(1, 2, 500.00);
```

---

## 42. DO: bloques anónimos PL/pgSQL {#42-do}

`DO` ejecuta código PL/pgSQL sin necesidad de crear una función. Útil para migraciones de datos y operaciones puntuales.

```sql
-- Bloque básico
DO $$
BEGIN
    RAISE NOTICE 'Hora actual: %', NOW();
END;
$$;

-- Con variables y lógica
DO $$
DECLARE
    v_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM productos WHERE activo = FALSE;
    RAISE NOTICE 'Productos inactivos a archivar: %', v_count;

    INSERT INTO productos_archivados
    SELECT *, NOW() AS archivado_en
    FROM productos WHERE activo = FALSE;

    DELETE FROM productos WHERE activo = FALSE;

    RAISE NOTICE 'Archivado completado: % productos', v_count;
END;
$$;

-- Migración de datos con loop
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT id, email FROM clientes WHERE email IS NOT NULL LOOP
        UPDATE usuarios SET email = r.email WHERE cliente_id = r.id;
    END LOOP;
END;
$$;
```

---

## 43. Triggers: BEFORE, AFTER, INSTEAD OF {#43-triggers}

Un trigger ejecuta una función automáticamente cuando ocurre un evento DML en una tabla.

### Función de trigger

```sql
-- La función de trigger siempre retorna TRIGGER y no toma parámetros
CREATE OR REPLACE FUNCTION set_modificado() RETURNS TRIGGER AS $$
BEGIN
    NEW.modificado = NOW();     -- NEW: nueva fila (en INSERT y UPDATE)
    RETURN NEW;                  -- retornar NULL cancela la operación
END;
$$ LANGUAGE plpgsql;
```

### CREATE TRIGGER

```sql
-- BEFORE UPDATE: modifica NEW antes de que se escriba
CREATE TRIGGER trg_productos_modificado
    BEFORE UPDATE ON productos
    FOR EACH ROW                 -- una vez por cada fila afectada
    EXECUTE FUNCTION set_modificado();

-- AFTER INSERT: ejecuta después de la inserción
CREATE TRIGGER trg_auditoria
    AFTER INSERT OR UPDATE OR DELETE ON productos
    FOR EACH ROW
    EXECUTE FUNCTION registrar_auditoria();

-- INSTEAD OF: solo en vistas (reemplaza la operación)
CREATE TRIGGER trg_vista_insert
    INSTEAD OF INSERT ON vista_compleja
    FOR EACH ROW
    EXECUTE FUNCTION insertar_en_vista();

-- FOR EACH STATEMENT: una vez por sentencia, no por fila
CREATE TRIGGER trg_notificar
    AFTER INSERT ON pedidos
    FOR EACH STATEMENT
    EXECUTE FUNCTION notificar_nuevos_pedidos();
```

### Variables en la función de trigger

```sql
CREATE OR REPLACE FUNCTION registrar_auditoria() RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO auditoria (tabla, operacion, fila_antes, fila_despues, usuario, cuando)
    VALUES (
        TG_TABLE_NAME,    -- nombre de la tabla
        TG_OP,            -- 'INSERT', 'UPDATE', 'DELETE'
        row_to_json(OLD), -- fila anterior (NULL en INSERT)
        row_to_json(NEW), -- fila nueva (NULL en DELETE)
        current_user,
        NOW()
    );
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;
```

⚠ Los triggers afectan el rendimiento. Úsalos con criterio, especialmente en tablas de alta escritura.

---

## 44. Cursores: DECLARE, FETCH, MOVE, CLOSE {#44-cursores}

Los cursores permiten procesar filas de un SELECT una a la vez, útil cuando el resultado es demasiado grande para cargarlo en memoria.

### Cursores en PL/pgSQL

```sql
CREATE OR REPLACE PROCEDURE procesar_pedidos_grandes() AS $$
DECLARE
    cur_pedidos CURSOR FOR
        SELECT id, total FROM pedidos WHERE total > 10000 ORDER BY total DESC;
    v_pedido RECORD;
BEGIN
    OPEN cur_pedidos;

    LOOP
        FETCH cur_pedidos INTO v_pedido;
        EXIT WHEN NOT FOUND;    -- sale cuando no hay más filas

        -- Procesar cada fila
        RAISE NOTICE 'Procesando pedido %: total = %', v_pedido.id, v_pedido.total;
        UPDATE pedidos SET procesado = TRUE WHERE id = v_pedido.id;
    END LOOP;

    CLOSE cur_pedidos;
END;
$$ LANGUAGE plpgsql;
```

### Cursores con parámetros

```sql
DECLARE
    cur_cat CURSOR (p_cat TEXT) FOR
        SELECT id, nombre FROM productos WHERE categoria = p_cat;
BEGIN
    OPEN cur_cat('Electrónica');
    FETCH cur_cat INTO v_prod;
    -- ...
    CLOSE cur_cat;
```

### FETCH con dirección

```sql
FETCH NEXT FROM cur;           -- siguiente fila (default)
FETCH PRIOR FROM cur;          -- fila anterior
FETCH FIRST FROM cur;          -- primera fila
FETCH LAST FROM cur;           -- última fila
FETCH ABSOLUTE 5 FROM cur;     -- fila en posición absoluta 5
FETCH RELATIVE -2 FROM cur;    -- 2 filas hacia atrás
FETCH 10 FROM cur;             -- los siguientes 10 registros

MOVE NEXT FROM cur;            -- mover sin retornar datos
MOVE 100 FROM cur;             -- saltar 100 filas
```

### Cursor refcursor (retornable)

```sql
CREATE OR REPLACE FUNCTION get_cursor() RETURNS REFCURSOR AS $$
DECLARE
    ref REFCURSOR := 'mi_cursor';
BEGIN
    OPEN ref FOR SELECT * FROM productos;
    RETURN ref;
END;
$$ LANGUAGE plpgsql;

-- Usar desde el cliente:
BEGIN;
SELECT get_cursor();
FETCH ALL IN mi_cursor;
COMMIT;
```

---

## 45. Manejo de errores en PL/pgSQL: EXCEPTION, RAISE, SQLSTATE {#45-errores}

### RAISE: emitir mensajes y errores

```sql
-- Niveles: DEBUG, LOG, INFO, NOTICE, WARNING, EXCEPTION (detiene ejecución)
RAISE DEBUG   'Valor de x: %', x;
RAISE NOTICE  'Procesando registro %', id;
RAISE WARNING 'Stock bajo para producto %', nombre;
RAISE EXCEPTION 'Error: valor inválido %', valor;

-- Con código de error SQLSTATE
RAISE EXCEPTION 'Saldo insuficiente'
    USING ERRCODE = 'P0001',    -- código de error personalizado
          HINT    = 'Revisa el saldo disponible antes de transferir',
          DETAIL  = 'Saldo actual: %, monto solicitado: %';
```

### EXCEPTION: capturar errores

```sql
BEGIN
    -- código que puede fallar
    INSERT INTO tabla VALUES (...);

EXCEPTION
    WHEN unique_violation THEN
        -- ORA-00001 equivalente: constraint UNIQUE violada
        RAISE NOTICE 'Registro duplicado, omitiendo';

    WHEN foreign_key_violation THEN
        RAISE EXCEPTION 'FK violada: el registro padre no existe';

    WHEN division_by_zero THEN
        RETURN NULL;

    WHEN OTHERS THEN
        -- Captura cualquier otro error
        RAISE NOTICE 'Error inesperado: % %', SQLSTATE, SQLERRM;
        RAISE;   -- relanza el mismo error
END;
```

### Condiciones de error comunes

| Condición | SQLSTATE | Descripción |
|-----------|----------|-------------|
| `unique_violation` | 23505 | UNIQUE o PK violada |
| `foreign_key_violation` | 23503 | FK violada |
| `not_null_violation` | 23502 | NOT NULL violada |
| `check_violation` | 23514 | CHECK violada |
| `division_by_zero` | 22012 | División por cero |
| `no_data_found` | P0002 | SELECT INTO sin resultados |
| `too_many_rows` | P0003 | SELECT INTO devolvió múltiples filas |
| `raise_exception` | P0001 | RAISE EXCEPTION genérico |

```sql
-- Verificar SQLSTATE y SQLERRM en el handler
EXCEPTION WHEN OTHERS THEN
    GET STACKED DIAGNOSTICS
        v_estado  = RETURNED_SQLSTATE,
        v_mensaje = MESSAGE_TEXT,
        v_detalle = PG_EXCEPTION_DETAIL,
        v_context = PG_EXCEPTION_CONTEXT;
    RAISE NOTICE 'Error %: % (detalle: %)', v_estado, v_mensaje, v_detalle;
```

---

╔══════════════════════════════════════════════════════════════════════╗
║  BLOQUE IX — Estructuras de datos avanzadas                          ║
║  §46 al §47                                                          ║
╚══════════════════════════════════════════════════════════════════════╝

## 46. Particionamiento declarativo: RANGE, LIST, HASH {#46-particiones}

El particionamiento divide una tabla lógica en particiones físicas. Permite:
- Eliminar datos rápidamente (DROP PARTITION en lugar de DELETE)
- Mejora de rendimiento por partition pruning (el planner solo lee particiones relevantes)
- Mantenimiento de índices por partición

### RANGE: rango de valores (ej: por fecha)

```sql
CREATE TABLE pedidos (
    id         BIGSERIAL,
    fecha      DATE NOT NULL,
    total      NUMERIC,
    cliente_id INTEGER
) PARTITION BY RANGE (fecha);

-- Crear particiones
CREATE TABLE pedidos_2023 PARTITION OF pedidos
    FOR VALUES FROM ('2023-01-01') TO ('2024-01-01');  -- [inicio, fin)

CREATE TABLE pedidos_2024 PARTITION OF pedidos
    FOR VALUES FROM ('2024-01-01') TO ('2025-01-01');

CREATE TABLE pedidos_default PARTITION OF pedidos DEFAULT;  -- resto

-- Eliminar año completo instantáneamente (mucho más rápido que DELETE)
DROP TABLE pedidos_2023;
-- o desconectar sin borrar:
ALTER TABLE pedidos DETACH PARTITION pedidos_2023;
```

### LIST: lista de valores (ej: por región)

```sql
CREATE TABLE ventas (
    id     BIGSERIAL,
    region TEXT NOT NULL,
    total  NUMERIC
) PARTITION BY LIST (region);

CREATE TABLE ventas_norte PARTITION OF ventas
    FOR VALUES IN ('Monterrey', 'Saltillo', 'Tijuana');

CREATE TABLE ventas_centro PARTITION OF ventas
    FOR VALUES IN ('CDMX', 'Puebla', 'Querétaro');

CREATE TABLE ventas_otro PARTITION OF ventas DEFAULT;
```

### HASH: distribución uniforme

```sql
CREATE TABLE logs (
    id      BIGSERIAL,
    usuario TEXT
) PARTITION BY HASH (usuario);

-- Dividir en N particiones con MODULUS y REMAINDER
CREATE TABLE logs_0 PARTITION OF logs FOR VALUES WITH (MODULUS 4, REMAINDER 0);
CREATE TABLE logs_1 PARTITION OF logs FOR VALUES WITH (MODULUS 4, REMAINDER 1);
CREATE TABLE logs_2 PARTITION OF logs FOR VALUES WITH (MODULUS 4, REMAINDER 2);
CREATE TABLE logs_3 PARTITION OF logs FOR VALUES WITH (MODULUS 4, REMAINDER 3);
```

### Índices y constraints en particiones

```sql
-- Un índice en la tabla padre crea índices en todas las particiones
CREATE INDEX idx_pedidos_cliente ON pedidos (cliente_id);

-- PRIMARY KEY debe incluir la clave de partición
ALTER TABLE pedidos ADD PRIMARY KEY (id, fecha);  -- fecha es la clave de partición
```

---

## 47. Herencia de tablas: INHERITS, ONLY {#47-herencia}

La herencia permite que una tabla "hija" herede columnas de una tabla "padre".
En PostgreSQL moderno, el particionamiento declarativo (§46) es preferible para la mayoría de casos.

```sql
-- Tabla padre
CREATE TABLE animales (
    id     SERIAL PRIMARY KEY,
    nombre TEXT NOT NULL,
    peso   NUMERIC
);

-- Tablas hijas: heredan todas las columnas de animales
CREATE TABLE perros (
    raza TEXT
) INHERITS (animales);

CREATE TABLE gatos (
    es_indoor BOOLEAN DEFAULT TRUE
) INHERITS (animales);

-- INSERT en tabla hija
INSERT INTO perros (nombre, peso, raza) VALUES ('Rex', 25, 'Labrador');

-- SELECT en tabla padre incluye filas de todas las hijas
SELECT * FROM animales;           -- incluye perros y gatos

-- ONLY: solo la tabla padre, sin hijas
SELECT * FROM ONLY animales;

-- Verificar qué tabla contiene cada fila
SELECT tableoid::regclass AS tabla, * FROM animales;
```

---

╔══════════════════════════════════════════════════════════════════════╗
║  BLOQUE X — Seguridad, análisis y referencia                         ║
║  §48 al §54                                                          ║
╚══════════════════════════════════════════════════════════════════════╝

## 48. DCL: GRANT, REVOKE, roles, esquemas, search_path {#48-dcl}

### Roles y usuarios

En PostgreSQL, usuarios y roles son lo mismo (los usuarios son roles con capacidad de login).

```sql
-- Crear rol
CREATE ROLE app_readonly;                         -- rol sin login
CREATE ROLE ana LOGIN PASSWORD 'secreto';         -- usuario con login
CREATE ROLE admin_user LOGIN PASSWORD 'clave' SUPERUSER;

-- Eliminar
DROP ROLE IF EXISTS app_readonly;
```

### GRANT y REVOKE

```sql
-- Privilegios sobre tabla
GRANT SELECT ON productos TO app_readonly;
GRANT SELECT, INSERT, UPDATE ON productos TO ana;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO admin_user;

-- Privilegios sobre columnas específicas
GRANT SELECT (id, nombre, precio) ON productos TO app_readonly;
GRANT UPDATE (stock) ON productos TO app_readonly;

-- Revocar
REVOKE INSERT ON productos FROM ana;
REVOKE ALL ON productos FROM ana;

-- Asignar rol a otro rol o usuario
GRANT app_readonly TO ana;   -- ana hereda todos los permisos de app_readonly
REVOKE app_readonly FROM ana;

-- Privilegios sobre esquema
GRANT USAGE ON SCHEMA tienda TO ana;
GRANT CREATE ON SCHEMA tienda TO ana;

-- Privilegios sobre funciones y secuencias (no solo tablas)
GRANT EXECUTE ON FUNCTION calcular_descuento(NUMERIC) TO app_readonly;
GRANT USAGE, SELECT ON SEQUENCE productos_id_seq TO ana;   -- SELECT para leer currval/nextval
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO ana;
```

### ALTER DEFAULT PRIVILEGES: permisos que se aplican solos a lo que aún no existe

`GRANT` solo afecta objetos que ya existen. Sin `ALTER DEFAULT PRIVILEGES`, cada tabla nueva que alguien cree obligaría a repetir el `GRANT` manualmente, un problema real en pipelines de migración donde las tablas se crean todo el tiempo desde un ORM o una herramienta de migraciones.

```sql
-- A partir de ahora, toda tabla nueva creada por "ana" en el esquema "tienda"
-- le da SELECT automáticamente a app_readonly, sin GRANT manual
ALTER DEFAULT PRIVILEGES FOR ROLE ana IN SCHEMA tienda
    GRANT SELECT ON TABLES TO app_readonly;

-- Lo mismo para funciones y secuencias futuras
ALTER DEFAULT PRIVILEGES FOR ROLE ana IN SCHEMA tienda
    GRANT EXECUTE ON FUNCTIONS TO app_readonly;
ALTER DEFAULT PRIVILEGES FOR ROLE ana IN SCHEMA tienda
    GRANT USAGE ON SEQUENCES TO app_readonly;

-- Ver las reglas de default privileges configuradas
SELECT * FROM pg_default_acl;
```

⚠ `ALTER DEFAULT PRIVILEGES` no es retroactivo: solo aplica a objetos creados después de definirla, nunca a los que ya existían. Los objetos existentes siguen necesitando un `GRANT` explícito una sola vez.

### Esquemas: namespaces

```sql
-- Crear esquema
CREATE SCHEMA tienda;
CREATE SCHEMA AUTHORIZATION ana;   -- ana es el owner

-- Objeto calificado con esquema
CREATE TABLE tienda.productos (...);
SELECT * FROM tienda.productos;

-- search_path: lista de esquemas donde buscar objetos sin cualificar
SHOW search_path;
SET search_path = tienda, public;   -- busca primero en tienda, luego en public

-- Para la sesión del usuario:
ALTER ROLE ana SET search_path = tienda, public;

-- Mover tabla a otro esquema
ALTER TABLE productos SET SCHEMA tienda;
```

---

## 49. Row-Level Security (RLS) {#49-rls}

RLS permite que diferentes usuarios vean o modifiquen solo las filas a las que tienen acceso,
directamente a nivel de la base de datos.

```sql
-- Habilitar RLS en la tabla
ALTER TABLE pedidos ENABLE ROW LEVEL SECURITY;

-- Crear política: los usuarios solo ven sus propios pedidos
CREATE POLICY pedidos_propios ON pedidos
    FOR ALL
    TO PUBLIC
    USING (cliente_id = current_setting('app.cliente_id')::INTEGER);

-- Política separada por operación
CREATE POLICY ver_pedidos ON pedidos
    FOR SELECT
    USING (cliente_id = current_setting('app.cliente_id')::INTEGER);

CREATE POLICY crear_pedido ON pedidos
    FOR INSERT
    WITH CHECK (cliente_id = current_setting('app.cliente_id')::INTEGER);

-- Deshabilitar RLS para superusuarios
ALTER TABLE pedidos FORCE ROW LEVEL SECURITY;  -- aplica incluso a owners

-- Los superusuarios pueden bypass RLS con BYPASSRLS
CREATE ROLE admin_full BYPASSRLS;

-- En la aplicación: establecer la variable de sesión
SET LOCAL app.cliente_id = '42';
```

### Patrón multi-tenant con RLS

```sql
-- Tabla con columna tenant
CREATE TABLE datos (tenant_id TEXT, id SERIAL, valor TEXT);
ALTER TABLE datos ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON datos
    USING (tenant_id = current_setting('app.tenant_id'));

-- Cada conexión solo ve su tenant
SET app.tenant_id = 'empresa_a';
SELECT * FROM datos;   -- solo filas donde tenant_id = 'empresa_a'
```

---

## 50. EXPLAIN y EXPLAIN ANALYZE: leer query plans {#50-explain}

`EXPLAIN` muestra el plan que el motor usará para ejecutar la consulta.
`EXPLAIN ANALYZE` además ejecuta la consulta y muestra tiempos reales.

```sql
-- Solo el plan (no ejecuta la consulta)
EXPLAIN SELECT * FROM productos WHERE precio < 100;

-- Plan + ejecución real (más útil)
EXPLAIN ANALYZE SELECT * FROM productos WHERE precio < 100;

-- Formato completo con buffers de caché
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) SELECT ...;

-- Formato JSON para herramientas como explain.dalibo.com
EXPLAIN (ANALYZE, FORMAT JSON) SELECT ...;
```

### Nodos más importantes

```
Seq Scan        → escaneo secuencial (sin índice) — malo en tablas grandes
Index Scan      → usa el índice para encontrar filas
Index Only Scan → toda la info está en el índice (no accede a la tabla)
Bitmap Scan     → combina múltiples índices
Hash Join       → join vía tabla hash (eficiente para tablas medianas)
Merge Join      → join de dos conjuntos ordenados
Nested Loop     → join via loop (eficiente cuando un lado es pequeño)
Sort            → ordenamiento en memoria o disco
Hash Aggregate  → GROUP BY via hash
Limit           → aplica LIMIT/OFFSET
```

### Leer el plan

```
Hash Join  (cost=35.50..72.25 rows=500 width=64)
           (actual time=0.813..1.523 rows=427 loops=1)
   Hash Cond: (p.categoria_id = c.id)
   →  Seq Scan on productos p  (cost=0..25.00 rows=1000 width=48)
      (actual time=0.012..0.312 rows=1000 loops=1)
   →  Hash  (cost=18.00..18.00 rows=600 width=20)
      (actual time=0.456..0.456 rows=600 loops=1)
         →  Seq Scan on categorias c  (cost=0..18.00 rows=600 width=20)
```

- `cost=inicio..total`: costo estimado (unidades relativas)
- `rows`: filas estimadas que retorna el nodo
- `actual time`: tiempo real en ms (inicio..total)
- `loops`: cuántas veces se ejecutó el nodo

### Cuándo forzar o desactivar índices

```sql
-- Temporalmente desactivar seq scan para forzar uso de índice
SET enable_seqscan = OFF;
EXPLAIN ANALYZE SELECT ...;
RESET enable_seqscan;

-- Actualizar estadísticas si el planner toma malas decisiones
ANALYZE productos;             -- actualiza estadísticas de la tabla
ANALYZE;                       -- actualiza toda la base de datos
```

---

## 51. information_schema y catálogos del sistema (pg_*) {#51-catalogo}

PostgreSQL expone metadatos de la base de datos a través de dos interfaces:

1. **`information_schema`**: estándar SQL, portable entre motores
2. **`pg_catalog`**: específico de PostgreSQL, más completo

### information_schema: consultas portables

```sql
-- Listar todas las tablas del esquema actual
SELECT table_name, table_type
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY table_name;

-- Columnas de una tabla
SELECT column_name, data_type, character_maximum_length,
       is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'productos'
ORDER BY ordinal_position;

-- Constraints
SELECT constraint_name, constraint_type, table_name
FROM information_schema.table_constraints
WHERE table_schema = 'public';

-- Columnas involucradas en una FK
SELECT
    kcu.column_name,
    ccu.table_name  AS tabla_referenciada,
    ccu.column_name AS columna_referenciada
FROM information_schema.key_column_usage kcu
JOIN information_schema.referential_constraints rc
    USING (constraint_name, constraint_schema)
JOIN information_schema.constraint_column_usage ccu
    ON ccu.constraint_name = rc.unique_constraint_name
WHERE kcu.table_name = 'pedidos';
```

### pg_catalog: metadatos avanzados de PostgreSQL

```sql
-- Tablas y tamaños
SELECT
    relname                    AS tabla,
    pg_size_pretty(pg_total_relation_size(oid)) AS tamaño_total,
    pg_size_pretty(pg_relation_size(oid))        AS tamaño_datos,
    pg_size_pretty(pg_indexes_size(oid))         AS tamaño_indices
FROM pg_class
WHERE relkind = 'r' AND relnamespace = 'public'::regnamespace
ORDER BY pg_total_relation_size(oid) DESC;

-- Índices
SELECT indexname, tablename, indexdef
FROM pg_indexes
WHERE schemaname = 'public';

-- Conexiones activas
SELECT pid, usename, application_name, state, query, query_start
FROM pg_stat_activity
WHERE state != 'idle';

-- Locks activos
SELECT pid, relation::regclass, mode, granted
FROM pg_locks WHERE relation IS NOT NULL;

-- Estadísticas de acceso (cuántas veces se usa cada tabla)
SELECT relname, seq_scan, idx_scan, n_live_tup, n_dead_tup
FROM pg_stat_user_tables
ORDER BY seq_scan DESC;

-- Prepared transactions (2PC)
SELECT gid, prepared, owner FROM pg_prepared_xacts;

-- Sequences
SELECT sequence_name, last_value, increment_by, max_value
FROM information_schema.sequences;

-- Tipos de enumeración
SELECT t.typname, e.enumlabel, e.enumsortorder
FROM pg_type t JOIN pg_enum e ON e.enumtypid = t.oid
ORDER BY t.typname, e.enumsortorder;
```

---

## 52. Patrones de consulta comunes {#52-patrones}

### Paginación: OFFSET vs Keyset

```sql
-- OFFSET (simple pero lento para páginas grandes)
SELECT id, nombre FROM productos ORDER BY id LIMIT 20 OFFSET 980;
-- Problema: el motor lee y descarta 980 filas antes de devolver 20

-- Keyset pagination (eficiente para todas las páginas)
SELECT id, nombre FROM productos
WHERE id > :ultimo_id_de_pagina_anterior   -- el cliente guarda este valor
ORDER BY id LIMIT 20;
-- El motor usa el índice directamente, sin descartar filas
```

### Top-N por grupo

```sql
-- Los 3 productos más caros de cada categoría
SELECT categoria, nombre, precio
FROM (
    SELECT categoria, nombre, precio,
           ROW_NUMBER() OVER (PARTITION BY categoria ORDER BY precio DESC) AS rn
    FROM productos
) t WHERE rn <= 3;

-- Alternativa con DISTINCT ON para top-1
SELECT DISTINCT ON (categoria) categoria, nombre, precio
FROM productos ORDER BY categoria, precio DESC;
```

### Deduplicación

```sql
-- Eliminar duplicados manteniendo el de menor id
DELETE FROM tabla WHERE id NOT IN (
    SELECT MIN(id) FROM tabla GROUP BY columna_que_debe_ser_unica
);

-- Más eficiente con CTE
WITH duplicados AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY email ORDER BY id) AS rn
    FROM clientes
)
DELETE FROM clientes WHERE id IN (SELECT id FROM duplicados WHERE rn > 1);
```

### Pivot (filas → columnas)

```sql
-- Con FILTER (preferido en PostgreSQL)
SELECT
    mes,
    SUM(ventas) FILTER (WHERE region = 'Norte')  AS norte,
    SUM(ventas) FILTER (WHERE region = 'Sur')    AS sur,
    SUM(ventas) FILTER (WHERE region = 'Centro') AS centro
FROM ventas_regionales
GROUP BY mes ORDER BY mes;

-- Con CASE
SELECT
    mes,
    SUM(CASE WHEN region = 'Norte'  THEN ventas ELSE 0 END) AS norte,
    SUM(CASE WHEN region = 'Sur'    THEN ventas ELSE 0 END) AS sur
FROM ventas_regionales GROUP BY mes;
```

### Unpivot (columnas → filas)

```sql
-- Convertir columnas en filas con UNION ALL
SELECT mes, 'Norte' AS region, norte AS ventas FROM tabla_pivot
UNION ALL
SELECT mes, 'Sur',    sur   FROM tabla_pivot
UNION ALL
SELECT mes, 'Centro', centro FROM tabla_pivot;
```

### Running total y media móvil

```sql
SELECT fecha, ventas,
    SUM(ventas) OVER (ORDER BY fecha
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS acumulado,
    AVG(ventas) OVER (ORDER BY fecha
        ROWS BETWEEN 6 PRECEDING AND CURRENT ROW)         AS media_7_dias
FROM ventas_diarias ORDER BY fecha;
```

### Gaps en series

```sql
-- Encontrar IDs faltantes en una secuencia
SELECT id + 1 AS inicio_gap
FROM tabla t1
WHERE NOT EXISTS (SELECT 1 FROM tabla t2 WHERE t2.id = t1.id + 1)
  AND id < (SELECT MAX(id) FROM tabla);

-- Con generate_series
SELECT s.id AS id_faltante
FROM generate_series(1, (SELECT MAX(id) FROM tabla)) AS s(id)
LEFT JOIN tabla t ON t.id = s.id
WHERE t.id IS NULL;
```

---

## 53. Anti-patrones SQL {#53-antipatrones}

### SELECT * en producción

```sql
-- MAL: trae todas las columnas, incluso las que no necesitas
SELECT * FROM pedidos p JOIN clientes c ON p.cliente_id = c.id;

-- BIEN: especifica las columnas necesarias
SELECT p.id, p.fecha, p.total, c.nombre AS cliente FROM pedidos p
JOIN clientes c ON p.cliente_id = c.id;
-- Beneficios: menos datos transferidos, permite Index Only Scan
```

### Funciones en WHERE que impiden el uso de índices

```sql
-- MAL: función aplicada a la columna indexada → el índice no se usa
WHERE EXTRACT(YEAR FROM fecha) = 2024
WHERE LOWER(nombre) = 'ana'
WHERE fecha::DATE = '2024-01-15'

-- BIEN: transformar el valor constante, no la columna
WHERE fecha >= '2024-01-01' AND fecha < '2025-01-01'
WHERE nombre ILIKE 'ana'             -- con índice GIN/trigram
-- O crear un índice de expresión (ver §26):
CREATE INDEX idx_lower_nombre ON tabla (LOWER(nombre));
WHERE LOWER(nombre) = 'ana'          -- ahora usa el índice
```

### N+1 queries

```sql
-- MAL: una query por cada cliente (N+1 = 1 query para clientes + N queries para pedidos)
-- En código de aplicación: for each cliente: SELECT * FROM pedidos WHERE cliente_id = ?

-- BIEN: una sola query con JOIN
SELECT c.nombre, COUNT(p.id) AS total_pedidos, SUM(p.total) AS suma
FROM clientes c LEFT JOIN pedidos p ON p.cliente_id = c.id
GROUP BY c.id, c.nombre;
```

### NOT IN con NULLs (ya cubierto en §11, resumido aquí)

```sql
-- MAL: si la subconsulta puede retornar NULLs
WHERE id NOT IN (SELECT producto_id FROM pedidos);

-- BIEN: siempre seguro
WHERE NOT EXISTS (SELECT 1 FROM pedidos WHERE producto_id = p.id);
```

### OR en condiciones de JOIN (impide merge join)

```sql
-- MAL: OR en la condición del JOIN — el planner no puede usar índices eficientemente
JOIN otra ON a.id = b.a_id OR a.id = b.b_id

-- BIEN: separar en dos JOINs con UNION
SELECT * FROM tabla a JOIN otra b ON a.id = b.a_id
UNION
SELECT * FROM tabla a JOIN otra b ON a.id = b.b_id
```

### OFFSET en paginación profunda

```sql
-- MAL: para páginas grandes OFFSET lee y descarta todo lo anterior
SELECT * FROM tabla ORDER BY id LIMIT 20 OFFSET 10000;   -- lento

-- BIEN: keyset pagination
SELECT * FROM tabla WHERE id > :ultimo_id ORDER BY id LIMIT 20;
```

### Subconsulta correlacionada en SELECT para datos que pueden hacerse con JOIN

```sql
-- MAL: subconsulta por cada fila
SELECT nombre,
    (SELECT COUNT(*) FROM pedidos WHERE cliente_id = c.id) AS total
FROM clientes c;

-- BIEN: LEFT JOIN + GROUP BY
SELECT c.nombre, COUNT(p.id) AS total
FROM clientes c LEFT JOIN pedidos p ON p.cliente_id = c.id
GROUP BY c.id, c.nombre;
```

### Tipo incorrecto en condiciones de búsqueda (implicit cast)

```sql
-- MAL: columna es INTEGER pero comparas con texto — puede impedir índice
WHERE id = '42'      -- PostgreSQL convierte, pero potencialmente ineficiente

-- BIEN: mismo tipo
WHERE id = 42

-- MAL: columna es TEXT pero comparas con INTEGER
WHERE codigo = 42    -- fuerza cast en cada fila

-- BIEN:
WHERE codigo = '42'
```

---

## 54. Diferencias entre motores: PostgreSQL vs MySQL vs SQL Server vs Oracle {#54-motores}

| Característica | PostgreSQL | MySQL | SQL Server | Oracle |
|---------------|------------|-------|------------|--------|
| **Auto-incremento** | `BIGSERIAL` o `GENERATED ALWAYS AS IDENTITY` | `AUTO_INCREMENT` | `IDENTITY(1,1)` | `GENERATED ALWAYS AS IDENTITY` o sequence+trigger |
| **String insensible** | `ILIKE` | `LIKE` (default CI) | `LIKE` (según collation) | `UPPER() = UPPER()` |
| **LIMIT/OFFSET** | `LIMIT n OFFSET m` | `LIMIT n OFFSET m` | `OFFSET m ROWS FETCH NEXT n ROWS ONLY` | `FETCH FIRST n ROWS ONLY` (12c+) |
| **UPSERT** | `INSERT … ON CONFLICT` | `INSERT … ON DUPLICATE KEY UPDATE` | `MERGE` | `MERGE` |
| **JSON nativo** | `JSON`, `JSONB` (indexable) | `JSON` (no indexable como JSONB) | `NVARCHAR(MAX)` + JSON functions | `JSON` (21c+) |
| **Window Functions** | Sí (completo) | Sí desde 8.0 | Sí | Sí |
| **CTEs modificables** | Sí | No | Sí | Sí |
| **RETURNING** | Sí | No | `OUTPUT` clause | `RETURNING INTO` |
| **Tablas temporales** | `CREATE TEMP TABLE` | `CREATE TEMPORARY TABLE` | `#tabla` (local) o `##tabla` (global) | `CREATE GLOBAL TEMPORARY TABLE` |
| **Full-text search** | `tsvector`/`tsquery` nativo | `MATCH…AGAINST` | `CONTAINS`, `FREETEXT` | `Oracle Text` |
| **Arrays** | Tipo nativo `TEXT[]` | No nativo | No nativo | `VARRAY`, `TABLE OF` |
| **MERGE** | Desde v15 | No | Sí | Sí |
| **RLS** | `CREATE POLICY` | No nativo | Row-Level Security (Enterprise) | Virtual Private Database |
| **Particionamiento** | Declarativo (Range/List/Hash) | Range/List/Hash/Columns | Range/List/Hash | Range/List/Hash/Composite |
| **Schemas** | Sí (namespace real) | Alias de base de datos | Sí | Sí (owner-based) |
| **DISTINCT ON** | Sí | No | No | No |
| **Tipos compuestos** | `CREATE TYPE … AS (…)` | No | No | `CREATE TYPE … AS OBJECT` |
| **Dominios** | `CREATE DOMAIN` | No | No | No |
| **Dollar-quoting** | Sí (`$$`) | No (usa `DELIMITER`) | No | No |
| **LATERAL** | Sí | Sí desde 8.0 | Sí (`CROSS APPLY`) | Sí (`CROSS APPLY`) |
| **Índices parciales** | Sí | No | Sí (filtered index) | No nativo |
| **Índice de expresión** | Sí | Sí desde 5.7 | Sí (computed column) | Sí (function-based index) |
| **Herencia de tablas** | Sí (único) | No | No | No |
| **GENERATED columns** | Sí (`STORED`) | Sí (`STORED`/`VIRTUAL`) | Sí (computed) | Sí desde 11g |
| **FILTER en agregados** | Sí | No | No | No |
| **IS DISTINCT FROM** | Sí | `<=>` (NULL-safe =) | No nativo | No nativo |

### Diferencias de sintaxis más frecuentes

```sql
-- Obtener el primer N registros
PostgreSQL:   SELECT … LIMIT 10
MySQL:        SELECT … LIMIT 10
SQL Server:   SELECT TOP 10 …   (también soporta FETCH FIRST desde 2012)
Oracle:       SELECT … FETCH FIRST 10 ROWS ONLY  (también ROWNUM < 11)

-- Concatenar strings
PostgreSQL:   'hola' || ' mundo'  o  CONCAT('hola', ' mundo')
MySQL:        CONCAT('hola', ' mundo')  (|| solo en modo ANSI)
SQL Server:   'hola' + ' mundo'   o  CONCAT('hola', ' mundo')
Oracle:       'hola' || ' mundo'

-- Fecha actual
PostgreSQL:   NOW()  o  CURRENT_TIMESTAMP
MySQL:        NOW()  o  SYSDATE()
SQL Server:   GETDATE()  o  SYSDATETIME()
Oracle:       SYSDATE  o  SYSTIMESTAMP

-- Truncar fecha a mes
PostgreSQL:   DATE_TRUNC('month', fecha)
MySQL:        DATE_FORMAT(fecha, '%Y-%m-01')
SQL Server:   DATEADD(month, DATEDIFF(month, 0, fecha), 0)
Oracle:       TRUNC(fecha, 'MM')

-- Verificar existencia de tabla antes de crear
PostgreSQL:   CREATE TABLE IF NOT EXISTS …
MySQL:        CREATE TABLE IF NOT EXISTS …
SQL Server:   IF OBJECT_ID('tabla', 'U') IS NULL CREATE TABLE …
Oracle:       BEGIN EXECUTE IMMEDIATE 'CREATE TABLE …'; EXCEPTION WHEN OTHERS THEN NULL; END;

-- Generar serie de números
PostgreSQL:   SELECT generate_series(1, 10)
MySQL:        (no nativo; usar tabla de números auxiliar)
SQL Server:   SELECT … FROM master.dbo.spt_values WHERE type='P' AND number BETWEEN 1 AND 10
Oracle:       SELECT LEVEL FROM DUAL CONNECT BY LEVEL <= 10
```

