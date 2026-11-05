# Proyecto final: la API de una tienda

Ruta de spring/: proyecto 20 (después de `part6_production/.../packaging/PackagingAndDocker`).

Una API REST completa con todo lo de las partes 1 a 6 junto: registro y login con JWT, catálogo, pedidos
que apartan existencias, PostgreSQL con Flyway, errores como ProblemDetail, pruebas contra una base real y
Docker. No es una guía con CAPAS: es el tipo de proyecto que vas a encontrar en el trabajo, y este archivo
explica **por qué** está hecho así. Cada decisión apunta a la guía donde se explicó.

## Qué hace

| Método y ruta | Quién | Qué |
|---|---|---|
| `POST /api/auth/register` | cualquiera | crea un cliente (`{"username","password"}`) |
| `POST /api/auth/token` | usuario y contraseña (HTTP Basic) | devuelve un JWT de 15 minutos |
| `GET /api/products`, `GET /api/products/{id}` | cualquiera | el catálogo, paginado (`?page=0&size=20&sort=name`) |
| `POST /api/products`, `PUT /api/products/{id}` | ADMIN | crear y cambiar productos |
| `POST /api/orders` | con token | comprar: aparta existencias y guarda el pedido |
| `GET /api/orders`, `GET /api/orders/{id}` | con token | el cliente ve los suyos; el administrador, todos |
| `POST /api/orders/{id}/cancel` | dueño o ADMIN | cancela y devuelve las existencias |
| `POST /api/orders/{id}/ship` | ADMIN | marca como enviado |
| `GET /actuator/health`, `/actuator/info` | cualquiera | salud (para Docker/Kubernetes) y versión |

Cualquier otra ruta responde 401 sin token o 403 con token: la seguridad termina en `denyAll()`. Lo que no se
nombró, no existe.

## Correrla

Desde `v15_spring/`:

```sh
./mvnw -pl final_project test       # 26 pruebas; las de integración levantan PostgreSQL en Docker
```

**Para desarrollar**, con el perfil `dev`:

```sh
./mvnw -pl final_project spring-boot:run -Dspring-boot.run.profiles=dev
```

Spring Boot encuentra `compose.yaml`, levanta **solo** el servicio `db` (el de la app tiene `profiles: ["app"]`),
espera a que esté sano y configura la conexión sin escribir ninguna URL. Medido, el log dijo
`Using Docker Compose file ...\final_project\compose.yaml`. La dependencia `spring-boot-docker-compose` es
`optional` y no viaja en el jar (medido: 0 archivos de docker-compose dentro).

**Todo en contenedores**, como en un servidor (desde `final_project/`, con el jar ya construido):

```sh
docker compose --profile app up -d --build
docker compose --profile app down          # agrega -v para borrar también los datos
```

Una compra completa con curl (en Windows, desde Git Bash):

```sh
A=$(curl -s -u admin:admin-local-123 -X POST localhost:8080/api/auth/token | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
curl -s -X POST localhost:8080/api/products -H "Authorization: Bearer $A" -H 'Content-Type: application/json' \
     -d '{"name":"Teclado","priceCents":89900,"stock":5}'
curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
     -d '{"username":"ana","password":"contrasena-larga-123"}'
T=$(curl -s -u ana:contrasena-larga-123 -X POST localhost:8080/api/auth/token | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
curl -s -X POST localhost:8080/api/orders -H "Authorization: Bearer $T" -H 'Content-Type: application/json' \
     -d '{"lines":[{"productId":1,"quantity":2}]}'
```

Respuestas reales:

```
{"id":1,"customer":"ana","status":"PLACED","createdAt":"2026-11-05T03:08:05.659146022Z","totalCents":179800,
 "lines":[{"productId":1,"productName":"Teclado","unitPriceCents":89900,"quantity":2,"subtotalCents":179800}]}
```

y al pedir 9 más:

```
{"detail":"No hay existencias suficientes de 'Teclado'","instance":"/api/orders","status":409,
 "title":"Regla del negocio","code":"INSUFFICIENT_STOCK"}
```

## Cómo está organizada

```
com.practice.spring.shop
├── ShopApplication            el arranque y el Clock
├── shared/                    lo común: ProblemDetail, NotFound, BusinessRule, PageResponse
├── security/                  usuarios, contraseñas, JWT y las reglas por URL
├── catalog/                   productos y existencias
└── orders/                    pedidos y sus reglas
src/main/resources
├── application.yaml           lo igual en todos los ambientes
├── application-dev.yaml       solo en tu máquina
└── db/migration/V1..V3        el esquema, con Flyway
```

**Por funcionalidad afuera** (`v14_architecture/part1_layers/LayeredArchitecture`, CAPA 7): lo que cambia junto
vive junto. **Por capa adentro** de cada paquete: controller → service → repository → entidad. Casi todo es
package-private: de `catalog/` solo son públicos `CatalogService` y sus records, así que `orders/` no puede tocar
`Product` ni `ProductRepository`; pide existencias con `catalog.reserve(...)`. El compilador cuida la frontera.

## Decisiones (y por qué)

**Sin puertos ni adaptadores (hexagonal).** La tienda no habla con ningún sistema externo: un puerto por tabla
sería ceremonia (`v14_architecture/part2_boundaries/Hexagonal`, CAPA 10). El primer sistema externo de verdad
(un proveedor de pagos) entraría como un puerto `Payments` en `orders/` con su adaptador; ver "Para seguir
practicando".

**Las reglas viven en el pedido.** `CustomerOrder` calcula su total, solo se cancela o envía si está `PLACED`, y
no tiene `setStatus` (`v14_architecture/part1_layers/DDDBasics`). Es una entidad JPA: separar "dominio puro" y
"entidad" duplicaría cada clase y su conversión, y las anotaciones no estorban para probar las reglas sin base
(`CustomerOrderTest`: 5 pruebas en 0.04 s).

**Nunca vender de más.** Leer el stock, revisarlo en Java y guardarlo es un *check-then-act*
(`v13_concurrency/part1_threads/ConcurrencyProblems`): dos compras simultáneas leen "queda 1" y pasan las dos.
`ProductRepository.takeStock` lo hace en **un** `UPDATE ... WHERE stock >= :quantity` y revisa cuántas filas
cambió. Medido en `StockConcurrencyTest`: 10 clientes compran al mismo tiempo un producto con 3 piezas →
exactamente 3 respuestas 201, 7 respuestas 409 y stock 0. Además:
- los productos de un pedido se apartan **en orden de id**, para que dos pedidos (A,B) y (B,A) no se esperen
  mutuamente (deadlock);
- un `CHECK (stock >= 0)` en la tabla es la última defensa;
- todo el pedido es una transacción: si el segundo renglón no alcanza, el primero se devuelve (probado: los 3
  cables apartados regresaron). `CatalogService.reserve` es `Propagation.MANDATORY`: falla si alguien lo llama
  sin transacción.

**El pedido copia nombre y precio.** El renglón guarda lo que el cliente pagó; probado: el administrador sube
el precio y el pedido sigue diciendo 89 900.

**Un pedido ajeno es 404, no 403.** Un 403 confirmaría que ese número de pedido existe
(`part5_security/.../jwt/JwtAndMethodSecurity`). La regla "cada quien lo suyo" está en `OrderService`, no en
la seguridad por URL, porque depende de los datos.

**`spring.jpa.open-in-view=false`.** Encendido (el valor por defecto, con una advertencia en el log), una relación
perezosa se puede cargar al armar el JSON, fuera de la transacción y sin que nadie lo note. Apagado, los
servicios devuelven DTOs ya armados; la lista de pedidos no carga renglones (el total está guardado en
`orders.total_cents`) y el detalle los trae en la misma consulta (`@EntityGraph`).

**Errores con un `code`.** Todo error es ProblemDetail (`part2_web/.../errors/ErrorHandling`); los de negocio
llevan un código estable (`INSUFFICIENT_STOCK`, `ORDER_NOT_CANCELLABLE`, `USERNAME_TAKEN`) para que el
cliente decida sin leer el texto, y los de validación dicen qué campo falló. Las pruebas encontraron un error
real: `GET /api/products?sort=password` respondía **500** (`PropertyReferenceException` de Spring Data). Ahora
es 400 con `"No se puede ordenar por 'password'"`. Un error del cliente no debe contar como falla nuestra en
las alertas.

**Las llaves del JWT vienen de afuera, o la app no arranca.** `shop.jwt.public-key` y `private-key` son rutas a
archivos PEM (`file:/run/secrets/...`), montados por el orquestador. Medido:
- con llaves PEM montadas en el contenedor, los tokens funcionaron (200);
- sin llaves, la app terminó con código 1 y
  `IllegalStateException: Faltan las llaves JWT: configura shop.jwt.public-key y shop.jwt.private-key`;
- con `shop.jwt.ephemeral-keys=true` (solo desarrollo y pruebas) se generan al arrancar, con una advertencia.
  Medido: un token emitido por otra instancia se rechazó con 401. Por eso nunca en producción con más de una
  instancia.

**El administrador se crea al arrancar** si existe `SHOP_ADMIN_PASSWORD` y todavía no hay uno. Ninguna
contraseña real vive en el código; la de `application-dev.yaml` es solo local.

**Topes.** `max-page-size: 50` (probado: `?size=100000` devuelve 50), máximo 20 renglones y 100 piezas por
renglón, contraseñas de 12 a 64 caracteres y de no más de 72 bytes (el límite de BCrypt; una ñ o un
acento ocupan 2 bytes). Lo encontró una revisión posterior: con una contraseña de 64 caracteres con eñes,
el registro respondía 500; ahora es un 400 con el campo y el motivo (`AuthApiTest`).

## Docker

`Dockerfile` igual al de `PackagingAndDocker` (dos etapas, capas, usuario 1001, forma exec), con un problema
nuevo: la caché AOT se entrena arrancando la app, y esta app necesita PostgreSQL, que no existe durante
`docker build`. Se entrena sin conectarse: Flyway apagado, `ddl-auto=none` e Hibernate con el dialecto fijo y
sin leer metadatos de la base (el pool no abre conexiones hasta que alguien las pide). Medido, con la misma
base:

| | arranque |
|---|---|
| sin caché AOT | 12.45 s, 12.29 s |
| con caché AOT | 5.13 s, 4.93 s |

Otras mediciones: jar de 61.3 MB con 92 dependencias; imagen de 743 MB; en Compose, la app usaba 279.5 MiB de
sus 768 MiB y PostgreSQL 50.9 MiB. `depends_on: condition: service_healthy` hizo que la app esperara a
`pg_isready` (log: `db-1 Healthy`, luego `app-1 Started`). Después de `down` y `up`, los datos seguían (el
volumen `tienda-data`), Flyway dijo `Schema "public" is up to date` y el administrador no se volvió a crear.

## Las pruebas

| Clase | Qué prueba |
|---|---|
| `orders/CustomerOrderTest` | las reglas del pedido, sin Spring (0.04 s) |
| `AuthApiTest` | registro, validación, contenido del token, 401/403, actuator |
| `CatalogApiTest` | CRUD de administrador, 404 como ProblemDetail, validación, tope de página, `sort` inválido |
| `OrderApiTest` | el flujo completo, rollback, precio congelado, pedidos ajenos, cancelar, enviar |
| `StockConcurrencyTest` | 10 compras simultáneas por 3 piezas |

Todas las de integración heredan de `IntegrationTestBase`: un contexto y un PostgreSQL para todas
(`part4_testing/.../IntegrationTests`). La primera clase tarda unos 30 s (arrancar el contenedor y la app); las
demás, segundos. Sin limpiar tablas: cada prueba crea sus usuarios y productos con nombres únicos.

## Boot 3 vs Boot 4 en este proyecto

Si en tu trabajo usan Boot 3, esto es lo que cambia:

- starters: `spring-boot-starter-webmvc` (Boot 3: `-web`), `spring-boot-starter-flyway` (Boot 3: solo
  `flyway-core`), y un starter de pruebas por tecnología (`spring-boot-starter-webmvc-test`; Boot 3:
  `spring-boot-starter-test` para todo);
- `RestTestClient` es de Spring Framework 7; en Boot 3 se usa `TestRestTemplate` o `WebTestClient`;
- `PropertyReferenceException` pasó de `org.springframework.data.mapping` a `org.springframework.data.core`;
- Spring Security 7 agrega authorities `FACTOR_*` al iniciar sesión: por eso `AuthController` copia al token
  solo las que empiezan con `ROLE_`;
- `-Djarmode=tools` (Boot 3.2 y antes: `layertools`).

## Para seguir practicando

Primero, los 6 ejercicios guiados (uno por cada parte de `v15_spring/`): [`EXERCISES.md`](EXERCISES.md). Después,
ideas más grandes:

1. **Pagos**: un puerto `Payments` en `orders/`, un adaptador falso y uno HTTP con `RestClient`
   (`part2_web/.../clients/HttpClients`). Ojo: cobrar dentro de la transacción del pedido la deja abierta
   mientras responde el banco; lo usual es pedido `PENDING_PAYMENT` → cobrar → `PLACED`.
2. **Avisar a otros sistemas** con el patrón outbox (`v14_architecture/part3_systems/CommandsQueriesAndConsistency`).
3. **Caché del catálogo** con `@Cacheable` y su `@CacheEvict` al actualizar (`part6_production/.../async`).
4. **Métricas del negocio** (pedidos por minuto, rechazos por falta de stock) y Prometheus
   (`part6_production/.../observability`).
5. **Un servidor de identidad** (Keycloak) en `compose.yaml`, y que la tienda solo verifique tokens con
   `spring.security.oauth2.resourceserver.jwt.issuer-uri`.
