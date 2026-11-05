# Ejercicios sobre el proyecto final

Seis cambios pequeños a la tienda, uno por cada parte de `v15_spring/`. No traen solución: traen **dónde tocar,
pistas y cómo comprobar** que te quedó bien. Hazlos en orden; cada uno usa justo lo de su parte.

## Antes de empezar

- **Ponlo en Git** (5 minutos): en la carpeta `Java Practice`, `git init` y un primer commit. Así experimentas
  sin miedo y deshaces con `git restore` / `git reset`, o una rama por ejercicio
  (`v9_tools/.../quality/git/Git`).
- **Cómo comprobar cualquier ejercicio**, desde `v15_spring/` y con Docker abierto:

  ```sh
  ./mvnw -pl final_project test          # PowerShell: .\mvnw.cmd -pl final_project test
  ```

  Las 26 pruebas que ya existen deben seguir pasando, más las tuyas.

---

## 1. Configuración: el límite de renglones por pedido  (parte 1)

**Objetivo:** hoy el máximo de 20 renglones está fijo en `OrderDtos` (`@Size(max = 20)`). Hazlo configurable con
`shop.orders.max-lines` en `application.yaml`.

- **Dónde:** un record con `@ConfigurationProperties("shop.orders")` (copia la forma de `security/JwtProperties.java`;
  `ShopApplication` ya tiene `@ConfigurationPropertiesScan`). Úsalo en `OrderService.place(...)`.
- **Pista:** una anotación no puede leer una propiedad, así que la regla pasa al servicio. Lanza una
  `BusinessRuleException` con un código como `TOO_MANY_LINES`: ya sale como 409 con `code`.
- **Comprueba:** en una prueba, pon la propiedad en 2 (`@SpringBootTest(properties = ...)`) y verifica que 3
  renglones se rechacen.
- **Guía:** `part1_core/.../config/ExternalConfiguration`.

## 2. Web: buscar productos por nombre  (parte 2)

**Objetivo:** `GET /api/products?name=tec` devuelve los productos cuyo nombre contiene "tec", sin importar
mayúsculas, paginado. Si `name` tiene menos de 2 letras: 400. Sin `name`, que siga listando todo.

- **Dónde:** `CatalogController.list`, `CatalogService`, `ProductRepository`.
- **Pista:** Spring Data arma la consulta desde el nombre del método:
  `findByNameContainingIgnoreCase(String name, Pageable pageable)`.
- **Guías:** `part3_data/.../repositories/SpringDataRepositories` (métodos por nombre),
  `part2_web/.../validation/Validation`.

## 3. Datos: agregar una descripción  (parte 3)

**Objetivo:** los productos tienen una `description` opcional (hasta 500 caracteres).

- **Dónde:** una migración **nueva** `V4__add_product_description.sql` (nunca edites V2: Flyway detecta el cambio
  de checksum y la app no arranca), y luego `Product`, `ProductRequest`, `ProductResponse`.
- **Pista:** con `ddl-auto: validate`, si la columna y la entidad no coinciden la app no arranca, y el mensaje dice
  exactamente qué falta. Léelo completo (la causa está al final, en "Caused by").
- **Guía:** `part3_data/.../migrations/Migrations`.

## 4. Pruebas: cubrir lo que hiciste  (parte 4)

**Objetivo:**
- en `CatalogApiTest`: que la búsqueda del ejercicio 2 encuentra con mayúsculas distintas, devuelve vacío si no
  hay coincidencias y da 400 con una sola letra;
- en `orders/CustomerOrderTest`: una prueba unitaria nueva (por ejemplo, el total de un pedido con un renglón de
  cantidad 3 es precio × 3).

- **Pista:** usa nombres de producto únicos en cada prueba, como las demás: comparten la base de datos y no se
  limpian entre pruebas.
- **Guías:** `part4_testing/IntegrationTests` y `UnitAndSliceTests`.

## 5. Seguridad: estadísticas solo para el administrador  (parte 5)

**Objetivo:** `GET /api/admin/stats` devuelve `{"orders": N, "salesCents": M}` (sin contar los pedidos
cancelados) y solo lo ve ADMIN.

- **Dónde:** un controller nuevo y un método en `OrderRepository` con `@Query` (count y suma de `totalCents` donde
  el estado no sea `CANCELLED`).
- **Pista importante:** la cadena de seguridad termina en `denyAll()`. Si no agregas la regla en `SecurityConfig`
  **antes** de esa línea, tu endpoint responde 403 incluso al administrador.
- **Comprueba:** sin token → 401, cliente → 403, admin → 200 con los números correctos.
- **Guías:** `part5_security/.../basics/SecurityBasics` y `jwt/JwtAndMethodSecurity`.

## 6. Producción: contar pedidos creados y rechazados  (parte 6)

**Objetivo:** un `Counter` `shop.orders.placed` y otro `shop.orders.rejected` con la etiqueta `reason` (por ejemplo
`INSUFFICIENT_STOCK`).

- **Dónde:** inyecta `MeterRegistry` en `OrderService`.
- **Pista 1:** el rechazo ocurre dentro de `catalog.reserve(...)` como excepción: atrápala, cuenta y vuelve a
  lanzarla, para no cambiar la respuesta.
- **Pista 2 (lección real de la guía):** las métricas se acumulan entre pruebas que comparten contexto. Compara la
  **diferencia** contra el valor de antes, no el total.
- **Pista 3:** la etiqueta lleva el código del error, nunca el nombre del producto ni el usuario (explosión de
  cardinalidad).
- **Guía:** `part6_production/.../observability/Observability`, CAPA 4.

---

Al terminar los 6 habrás tocado configuración, web, datos, pruebas, seguridad y métricas en un proyecto real.
Ideas más grandes (pagos con un puerto, outbox, Keycloak): `README.md`, "Para seguir practicando".
