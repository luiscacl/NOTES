# Java Practice

Guías para aprender Java a fondo, de cero a nivel profesional, y Spring Boot. Cada guía es un archivo con la
explicación (en comentarios, por CAPAS) y código real que comprueba lo que dice.

- **Qué estudiar y en qué orden:** [`ROADMAP.md`](ROADMAP.md).
- **Cómo estudiar Spring Boot** (la prioridad): [`v15_spring/README.md`](v15_spring/README.md).

## Qué necesitas instalar

| Herramienta | Para qué partes | ¿Obligatoria? |
|---|---|---|
| **Java (JDK) 25 o más** | todo | **sí**. Con Java 24 el repositorio no compila (medido: `ScopedValue` y la clase `IO` son de Java 25). Aquí hay Java 26. |
| **Docker Desktop**, abierto | `v15_spring/` partes 3, 4 y 6, y `final_project` | solo para esas pruebas: levantan PostgreSQL en un contenedor |
| **Git** 2.28 o más | `v9_tools/` (la guía de Git) | solo para esa prueba (aquí hay 2.51) |
| Maven | `v9_tools/` y `v15_spring/` | **no**: el wrapper (`mvnw`) descarga la versión correcta solo |
| Un IDE (IntelliJ o VS Code) | todo | no, pero ayuda: correr pruebas con un clic, depurar |

**No hace falta instalar:** Maven, Gradle, PostgreSQL, Spring ni ninguna librería. Maven baja las librerías, y
Docker baja PostgreSQL.

**La primera vez necesitas internet**, y espacio en disco (medido en esta máquina):
- `~/.m2` (Maven y las librerías): ~260 MB;
- imágenes de Docker: PostgreSQL 17, ~650 MB; Java para la imagen de la guía 19 (`PackagingAndDocker`), ~480 MB;
- solo si pruebas los buildpacks (guía 19): ~1.3 GB más.

Después todo funciona sin internet.

## Cómo se corre cada parte

| Parte | Cómo | Ejemplo |
|---|---|---|
| `v1_fundamentals/` a `v14_architecture/` (menos `v9_tools/`) | un archivo, sin compilar aparte, desde la raíz | `java v1_fundamentals/part1_first_steps/HowJavaWorks.java` |
| `v9_tools/` (Maven, Jackson, Git) | pruebas con Maven, desde `v9_tools/` | `./mvnw test -Dtest=JacksonTest` |
| `v15_spring/` (Spring Boot) | pruebas con Maven, desde `v15_spring/` | `./mvnw -pl part2_web test -Dtest=ValidationTest` |

En PowerShell se escribe `.\mvnw.cmd` en lugar de `./mvnw`.

Cada guía de `v1` a `v14` imprime una sola línea si todo se comporta como explica. En los proyectos Maven, la
comprobación son las pruebas: `BUILD SUCCESS`.

## Si algo falla

- **"Could not find a valid Docker environment"** o pruebas de `v15_spring` que fallan sin haber cambiado nada:
  Docker Desktop no está abierto.
- **"release version 25 not supported"** (en Maven), o **"ScopedValue is a preview API"** / no encuentra `IO`
  (en las guías): tu Java es más viejo que 25. Revisa `java -version`.
- **La primera corrida tarda mucho:** está descargando Maven, librerías o imágenes. Es una sola vez.
- **En Windows, las pruebas de `v9_tools` tardan minutos:** corren Maven y Git de verdad muchas veces, y en
  Windows arrancar cada proceso cuesta ~180 ms (medido). Es normal.

## Tus archivos

`Main.java` (tus notas) y `Ejercicios.java` (tu espacio de práctica) son tuyos: las guías no los modifican.
