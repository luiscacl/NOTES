# Cómo estudiar Spring Boot con esta carpeta

Esta carpeta tiene **19 guías y un proyecto final**. Son muchas, y no todas pesan igual. Este archivo te dice
**por dónde empezar, en qué orden seguir, cómo estudiar cada guía y cómo saber que ya la dominas**.

Si solo vas a leer una sección, lee **"La ruta urgente"** y **"Cómo estudiar una guía"**.

---

## 1. La idea en una imagen

Casi todo en Spring Boot pasa en el camino de **una petición**. Cada guía explica un pedazo de ese camino:

```
                         ┌──────────── 17 Observability: logs, métricas, salud ────────────┐
                         │                                                                  │
 cliente ──HTTP──► 15-16 Seguridad ──► 5 Controller ──► 6 Validación ──► Servicio ──► 11 Repositorio ──► 10 JPA ──► PostgreSQL
 (app, otro             (¿quién eres?   (la URL y el     (¿los datos      (9 @Transactional:     (Spring Data)  (entidades)  (12 Flyway crea
  servicio)              ¿puedes?)       JSON)            tienen forma?)   todo o nada)                                     las tablas)
    ▲                                                                         │
    └───────────────── 7 Errores: cualquier falla sale como ProblemDetail ◄───┘     8 HttpClients: cuando TU servicio llama a otro

 Debajo de todo:   1-2 el contenedor (beans e inyección)   3 la autoconfiguración   4 la configuración externa
 Alrededor:        13-14 las pruebas        18 @Async, @Scheduled, @Cacheable        19 el jar y Docker
 Todo junto:       20 final_project
```

Si entiendes este dibujo, sabes **dónde buscar** cuando algo falla: un 401 es seguridad (15-16), un 400 es
validación (6), un 500 con "LazyInitializationException" es JPA (10), una tabla que no existe es Flyway (12).

---

## 2. Antes de empezar: lo de Java que sí necesitas

No hace falta repasar todo Java. Spring usa mucho estas cinco cosas; si alguna te suena confusa, repásala
primero (una sesión basta):

| Necesitas | Por qué | Dónde está |
|---|---|---|
| Interfaces y polimorfismo | Spring inyecta "algo que cumple la interface" | `v2_oop/part3_interfaces_and_relationships/` |
| Anotaciones | Spring se configura con ellas (`@Service`, `@Transactional`...) | `v12_jvm/part2_classes/ReflectionAndAnnotations` |
| Excepciones (checked vs unchecked) | el rollback y el manejo de errores dependen de esto | `v3_exceptions/` |
| Lambdas, streams, `Optional` | están en todo el código moderno | `v7_functional/` |
| Hilos y `ThreadLocal` (lo básico) | cada petición es un hilo; la transacción y el usuario viven en un ThreadLocal | `v13_concurrency/part1_threads/ThreadBasics` |

Y una idea que aparece en TODAS las partes: **el proxy**. Spring envuelve tus beans en otro objeto que hace algo
antes y después de tu método (abrir una transacción, revisar permisos, mandar a otro hilo). Por eso
`@Transactional`, `@PreAuthorize`, `@Async` y `@Cacheable` **no funcionan si llamas al método con `this`**. Se
explica en `part1_core/.../container/IoCContainer` y se mide en `part3_data/.../jdbc/JdbcAndTransactions`
(CAPA 6). Si solo te llevas una cosa técnica de aquí, que sea esta.

---

## 3. Las dos rutas

### La ruta urgente (si en el trabajo ya te toca tocar código)

Estas 9 guías cubren lo que vas a ver **el primer día** en casi cualquier proyecto Spring Boot. Léelas en este
orden y después regresa a la ruta completa por lo demás:

1. `part1_core/.../startup/SpringAndBoot` — qué es cada cosa
2. `part1_core/.../container/IoCContainer` — beans, inyección, proxies
3. `part2_web/.../rest/RestControllers` — endpoints y JSON
4. `part2_web/.../validation/Validation`
5. `part2_web/.../errors/ErrorHandling`
6. `part3_data/.../jdbc/JdbcAndTransactions` — **sobre todo las transacciones**
7. `part3_data/.../jpa/JpaEntities`
8. `part3_data/.../repositories/SpringDataRepositories`
9. `part4_testing/UnitAndSliceTests`

Si tu proyecto del trabajo tiene login, agrega `part5_security/.../basics/SecurityBasics` y
`part5_security/.../jwt/JwtAndMethodSecurity` justo después del 5.

### La ruta completa (para ser muy bueno)

En orden: cada guía usa lo de las anteriores. La columna "Al terminar debes poder..." es tu prueba: si no
puedes hacerlo sin ver la guía, repásala antes de seguir.

| # | Guía | Importancia | Al terminar debes poder... |
|---|---|---|---|
| | **Parte 1: el núcleo** (`part1_core`) | | |
| 1 | `startup/SpringAndBoot` | MUY ALTA | explicar qué hace Spring, qué agrega Boot, qué es un starter y qué pasa al correr `main` |
| 2 | `container/IoCContainer` | MUY ALTA | explicar por qué inyectar por constructor, qué es un proxy y por qué `this.metodo()` lo salta |
| 3 | `autoconfig/AutoConfigurationUsage` | ALTA | encontrar por qué existe (o no) un bean que no declaraste, con el reporte de condiciones |
| 4 | `config/ExternalConfiguration` | MUY ALTA | configurar el mismo jar para dev y prod con perfiles y variables de entorno, sin secretos en el código |
| | **Parte 2: APIs REST** (`part2_web`) | | |
| 5 | `rest/RestControllers` | MUY ALTA | escribir un CRUD con los códigos HTTP correctos (201 + Location, 204, 404) y DTOs |
| 6 | `validation/Validation` | MUY ALTA | validar un JSON anidado y distinguir "forma" (aquí) de "regla de negocio" (el servicio) |
| 7 | `errors/ErrorHandling` | MUY ALTA | hacer que todo error salga como ProblemDetail, sin filtrar detalles internos en un 500 |
| 8 | `clients/HttpClients` | ALTA | llamar a otro servicio con `RestClient` con timeouts, y explicar qué pasa si el otro tarda |
| | **Parte 3: datos** (`part3_data`, necesita Docker) | | |
| 9 | `jdbc/JdbcAndTransactions` | MUY ALTA | decir dónde poner `@Transactional`, cuándo hace rollback y cuándo NO (checked, `this`) |
| 10 | `jpa/JpaEntities` | MUY ALTA | mapear entidades y relaciones, y detectar un N+1 y un LazyInitializationException |
| 11 | `repositories/SpringDataRepositories` | MUY ALTA | escribir consultas por nombre, `@Query`, paginación y proyecciones |
| 12 | `migrations/Migrations` | ALTA | cambiar el esquema con una migración nueva, y explicar por qué nunca se edita una ya aplicada |
| | **Parte 4: pruebas** (`part4_testing`, necesita Docker) | | |
| 13 | `UnitAndSliceTests` | MUY ALTA | elegir entre unitaria, Mockito, `@WebMvcTest` y `@DataJpaTest` según qué quieres probar |
| 14 | `IntegrationTests` | ALTA | escribir una prueba de punta a punta con PostgreSQL real y que no tarde de más |
| | **Parte 5: seguridad** (`part5_security`) | | |
| 15 | `basics/SecurityBasics` | MUY ALTA | explicar 401 vs 403, la cadena de filtros, BCrypt, CSRF y CORS |
| 16 | `jwt/JwtAndMethodSecurity` | MUY ALTA | proteger una API con JWT y `@PreAuthorize`, y decir qué contiene un token (y qué no debe) |
| | **Parte 6: producción** (`part6_production`) | | |
| 17 | `observability/Observability` | MUY ALTA | exponer health/liveness/readiness, crear una métrica y seguir una petición en los logs |
| 18 | `async/AsyncSchedulingCaching` | ALTA | usar `@Async`, `@Scheduled` y `@Cacheable` sin caer en sus trampas (proxy, pool, varias instancias) |
| 19 | `packaging/PackagingAndDocker` | ALTA | construir una imagen de Docker correcta (capas, sin root, apagado ordenado, memoria) |
| | **Proyecto final** | | |
| 20 | `final_project/` (empieza por su `README.md`) | MUY ALTA | explicar cada decisión del README y hacer los ejercicios de la sección 6 de este archivo |

**Cómo repartir el tiempo:** una guía por sesión, sin prisa. Las de importancia MUY ALTA merecen dos sesiones:
una para leer y correr, otra para romper cosas (ver abajo). Es mejor terminar 3 guías entendidas que 10 leídas.

---

## 4. Cómo estudiar una guía

Cada guía es un archivo `.java` donde **el texto es la explicación** (en comentarios, por CAPAS) y su prueba
(`...Test.java`, en `src/test`) es la **comprobación**: cada método `capaN_...` demuestra lo que dice la CAPA N.

**Paso 1. Ubícate (2 minutos).** Lee el encabezado: `Importancia`, `Lo que ya se vio` y el índice de CAPAS.
Si "lo que ya se vio" menciona algo que no recuerdas, ábrelo primero.

**Paso 2. Lee una CAPA, luego su prueba.** No leas toda la guía de corrido. Lee la CAPA 3, abre la prueba,
busca `capa3_...` y mira cómo se comprueba lo que acabas de leer. La prueba es el ejemplo más honesto: no
puede mentir porque se ejecuta.

**Paso 3. Córrela.** Desde `v15_spring/`:

```sh
./mvnw -pl part2_web test -Dtest=ValidationTest                 # toda la guía
./mvnw -pl part2_web test -Dtest='ValidationTest#capa3*'        # solo las pruebas de la CAPA 3
```

En PowerShell es `.\mvnw.cmd` en lugar de `./mvnw`. En IntelliJ o VS Code: abre la carpeta `v15_spring` como
proyecto Maven y usa la flecha verde junto a cada prueba; es más cómodo.

**Paso 4. Predice y rompe (lo más importante).** Antes de cambiar algo, **escribe qué crees que va a pasar**.
Luego cámbialo, corre la prueba y compara. Así se aprende de verdad. Ideas:

| Guía | Rompe esto | Deberías ver |
|---|---|---|
| IoCContainer | quita `@Service` de una clase que se inyecta | la app no arranca: falta un bean (lee el mensaje completo) |
| Validation | quita `@Valid` del parámetro del controller | los datos inválidos pasan sin error |
| JdbcAndTransactions | quita `@Transactional` de un método que hace dos escrituras | la primera escritura queda aunque la segunda falle |
| JpaEntities | cambia una relación de `LAZY` a `EAGER` | más consultas en el log, aunque no uses los datos |
| SecurityBasics | quita una regla de `authorizeHttpRequests` | otro código (401/403) en esa ruta |
| AsyncSchedulingCaching | llama a un método `@Cacheable` desde la misma clase | la caché no se usa (la prueba de la CAPA 3 lo muestra) |

Al terminar, deshaz tus cambios (o vuelve a correr la prueba hasta que pase como al principio).

**Paso 5. Explícalo con tus palabras.** Si no lo puedes explicar en tres frases sin mirar, todavía no lo
tienes. Escríbelo en tus notas (`Main.java`, en la raíz, es tuyo). Cierra con la pregunta de la columna
"Al terminar debes poder..." de la tabla de arriba.

**Paso 6. Lee "Errores comunes" y "RESUMEN FINAL".** Los errores comunes son lo que más vas a ver en código
real, y lo que más preguntan en entrevistas.

---

## 5. Lo que necesitas instalado

- **Java 25 o más** (aquí hay 26) y nada más para Maven: `mvnw` lo descarga solo la primera vez.
- **Docker Desktop corriendo** para `part3_data`, `part4_testing`, `part6_production` (la prueba
  `PackagedImageIT`) y `final_project`: sus pruebas levantan PostgreSQL en un contenedor (Testcontainers). La
  primera vez descarga la imagen y tarda más.
- Todo de una vez: `./mvnw clean verify` desde `v15_spring/` (156 pruebas, varios minutos).

Si una prueba falla y no cambiaste nada, lo más común es que Docker no esté abierto.

---

## 6. Practicar: un cambio en el proyecto final por cada parte

Leer no basta: hay que escribir. Cuando termines una parte, haz su ejercicio en `final_project/`.
**La versión completa, con dónde tocar, pistas y cómo comprobar cada uno, está en
[`final_project/EXERCISES.md`](final_project/EXERCISES.md).** Cada uno es pequeño y usa justo lo de esa
parte. Después de cada uno, corre `./mvnw -pl final_project test`: no debe romper nada. El resumen:

| Al terminar | Ejercicio en `final_project/` |
|---|---|
| Parte 1 (núcleo) | agrega una propiedad `shop.orders.max-lines` (hoy el 20 está fijo en `OrderDtos`) con `@ConfigurationProperties` y úsala en `OrderService` |
| Parte 2 (web) | agrega `GET /api/products?name=tec` que busque por nombre; valida que `name` tenga al menos 2 letras |
| Parte 3 (datos) | agrega la columna `description` con una migración `V4__...sql` (nunca editando V2) y súmala a `ProductRequest`/`ProductResponse` |
| Parte 4 (pruebas) | escribe la prueba de tu búsqueda por nombre en `CatalogApiTest`, y una unitaria nueva en `CustomerOrderTest` |
| Parte 5 (seguridad) | agrega `GET /api/admin/stats` (total de pedidos y de ventas) que solo pueda ver ADMIN; prueba el 401 y el 403 |
| Parte 6 (producción) | cuenta los pedidos creados y los rechazados por falta de stock con un `Counter` de Micrometer |

Ideas más grandes (pagos con un puerto, outbox, Keycloak) están en `final_project/README.md`, "Para seguir
practicando".

---

## 7. ¿Ya soy bueno? Las preguntas que lo demuestran

Si puedes contestar estas sin ver las guías, dominas lo esencial de Spring Boot. Son también preguntas
típicas de entrevista. Entre paréntesis, dónde está la respuesta.

1. ¿Qué es un bean y por qué la inyección por constructor es la recomendada? (2)
2. ¿Por qué `@Transactional` no funciona si llamas al método con `this`? (2, 9)
3. ¿Cómo sabe Spring Boot que debe crear un `DataSource` sin que lo declares? (3)
4. ¿Cómo cambias la URL de la base entre dev y prod sin recompilar? (4)
5. ¿Qué código HTTP devuelves al crear, y qué encabezado lo acompaña? (5)
6. ¿Dónde va la validación de "el campo es obligatorio" y dónde "no hay existencias"? (6)
7. ¿Por qué un error inesperado nunca debe devolver el mensaje de la excepción al cliente? (7)
8. ¿Qué pasa si el servicio al que llamas nunca responde y no pusiste timeout? (8)
9. ¿Una excepción checked hace rollback por defecto? (9)
10. ¿Qué es el problema N+1 y cómo lo resuelves? (10, 11)
11. ¿Por qué no se usa `ddl-auto=update` en producción? (12)
12. ¿Cuándo usas `@WebMvcTest` y cuándo `@SpringBootTest`? (13, 14)
13. ¿Diferencia entre 401 y 403? ¿Por qué un error de seguridad no llega a tu `@RestControllerAdvice`? (15)
14. ¿Qué contiene un JWT, quién lo puede leer y qué impide que alguien lo modifique? (16)
15. ¿Qué va en liveness y qué en readiness, y qué pasa si lo confundes? (17)
16. ¿Qué pasa con una tarea `@Scheduled` si corres tres instancias de la aplicación? (18)
17. ¿Por qué el `ENTRYPOINT` debe ser `["java", ...]` y no `java ...`? (19)
18. ¿Cómo evitas vender la misma pieza dos veces con pedidos simultáneos? (20)

---

## 8. Boot 3 vs Boot 4 (por si en tu trabajo usan Boot 3)

Estas guías usan Spring Boot 4. Cada guía señala sus diferencias donde aplica; aquí están las que más vas a
notar al abrir un proyecto Boot 3:

| En Boot 4 (estas guías) | En Boot 3 | Guía |
|---|---|---|
| `spring-boot-starter-webmvc` | `spring-boot-starter-web` | 1, 5 |
| un starter de pruebas por tecnología (`...-webmvc-test`, `...-data-jpa-test`) | `spring-boot-starter-test` para todo | 13 |
| `@MockitoBean` | `@MockBean` (eliminado en Boot 4) | 13 |
| `RestTestClient` (nuevo en Spring Framework 7) | `TestRestTemplate` o `WebTestClient` | 5, 14 |
| `spring-boot-starter-restclient` aparte | `RestClient.Builder` incluido en el starter web | 8 |
| Jackson 3 (paquete `tools.jackson`) | Jackson 2 (`com.fasterxml.jackson`) | 5 |
| `spring-boot-starter-flyway` | solo `flyway-core` | 12 |
| `@AutoConfigureMetrics` en pruebas | `@AutoConfigureObservability` | 17 |
| `-Djarmode=tools` | `-Djarmode=layertools` (hasta 3.2) | 19 |

Lo que **no** cambia, y es lo más importante: beans, inyección, proxies, transacciones, JPA, validación,
ProblemDetail, la cadena de seguridad, Actuator. Si dominas esto con Boot 4, un proyecto Boot 3 se lee igual.

---

## 9. Errores comunes al estudiar Spring

1. **Leer sin correr.** Spring tiene mucha "magia"; solo corriendo y rompiendo ves qué hace de verdad.
2. **Saltar directo a seguridad o a microservicios** sin entender beans y proxies: después nada tiene sentido.
3. **Memorizar anotaciones.** Importa saber qué hace cada una por dentro (casi siempre: un proxy o una
   autoconfiguración), no la lista.
4. **Copiar código de tutoriales viejos.** Mucho de internet es de Boot 2 (`WebSecurityConfigurerAdapter`,
   `javax.persistence`): ya no compila. Si ves `javax.` en vez de `jakarta.`, es viejo.
5. **No leer el error completo.** Los errores de arranque de Spring son largos, pero la causa real está al
   final ("Caused by: ..."), y casi siempre dice exactamente qué falta.
