package com.practice.spring.data.migrations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/*
    =====================================================================
    MIGRACIONES CON FLYWAY: EL ESQUEMA TAMBIÉN TIENE VERSIONES
    =====================================================================

    Ruta de spring/: guía 12  (part3_data)
    Anterior:  part3_data/.../repositories/SpringDataRepositories
    Siguiente: part4_testing/UnitAndSliceTests

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo.
    - Las comprobaciones son pruebas: MigrationsTest, contra PostgreSQL 17
      real. Algunas usan Flyway desde código, cada una en su propio schema,
      para simular una base de datos con datos viejos, una migración
      editada y una que falla. Desde v15_spring/:
          ./mvnw -pl part3_data test -Dtest=MigrationsTest
    - Las migraciones están en src/main/resources/migrations/shop (y las de
      los experimentos, en migrations/tampered y migrations/failing).
    - Versión: Flyway 12.4 (verificado con dependency:tree). Mensajes
      citados: corridas reales.

    Lo que ya se vio: ddl-auto y por qué no "update" en producción
    (jpa/JpaEntities, CAPA 8), transacciones (jdbc/JdbcAndTransactions).

    Importancia: ALTA. Toda aplicación que dura cambia su esquema, y un
    cambio de esquema mal hecho es de los incidentes más difíciles de
    revertir.

    Índice:
        CAPA 1)  El problema: el código tiene versiones; el esquema también
        CAPA 2)  Flyway: archivos numerados y una tabla de historia
        CAPA 3)  Cambiar una tabla que ya tiene datos
        CAPA 4)  Nunca editar una migración aplicada
        CAPA 5)  Cuando una migración falla a la mitad
        CAPA 6)  Hibernate en validate: el código y el esquema deben coincidir
        CAPA 7)  Cambios sin detener el servicio: expandir y contraer
        CAPA 8)  Lo demás que hay que saber
        CAPA 9)  Errores comunes
        RESUMEN FINAL
*/
public class Migrations {

    /*
        =================================================================
        CAPA 1) EL PROBLEMA: EL CÓDIGO TIENE VERSIONES; EL ESQUEMA TAMBIÉN
        =================================================================

        La versión 2.3 de la aplicación necesita una columna country_code que
        la 2.2 no tenía. ¿Quién la crea? ¿En tu máquina, en pruebas y en
        producción, igual y en el mismo orden? ¿Y las filas que ya existen?

        Opciones malas:
        - un script que alguien corre a mano "antes de desplegar": se
          olvida, se corre dos veces, o solo en un ambiente;
        - ddl-auto=update (jpa/JpaEntities, CAPA 8): no migra datos, no borra
          ni renombra, y nadie revisa lo que hizo.

        La solución: MIGRACIONES. Cada cambio del esquema es un archivo SQL
        numerado, guardado en el repositorio JUNTO al código que lo
        necesita, revisado en el pull request como cualquier código, y
        aplicado AUTOMÁTICAMENTE al arrancar, en orden, una sola vez, en
        todos los ambientes. El esquema de cualquier base de datos se puede
        reconstruir desde cero repitiendo la historia.

        Las dos herramientas estándar en Java son Flyway (SQL plano, la más
        simple; la de esta guía) y Liquibase (cambios en XML/YAML, más
        opciones). Spring Boot configura cualquiera de las dos sola.
    */

    /*
        =================================================================
        CAPA 2) FLYWAY: ARCHIVOS NUMERADOS Y UNA TABLA DE HISTORIA
        =================================================================

        Con spring-boot-starter-flyway (y, para PostgreSQL, el módulo
        flyway-database-postgresql), al arrancar Flyway busca archivos en
        classpath:db/migration (aquí: spring.flyway.locations=
        classpath:migrations/shop) con este nombre:

            V1__create_customers.sql
            V2__create_countries.sql
            V3__add_customer_country.sql
            | |  |
            | |  descripción (solo para humanos)
            | dos guiones bajos
            versión (1, 2, 3... o 1.1, 2026.11.04...)

        y lleva la cuenta en una tabla que crea él mismo,
        flyway_schema_history: qué versión se aplicó, cuándo, cuánto tardó,
        si funcionó, y un CHECKSUM del contenido del archivo (CAPA 4).

        Al arrancar: compara los archivos con la historia, y aplica SOLO los
        que faltan, en orden. Corre ANTES que JPA, así Hibernate encuentra
        las tablas ya listas (CAPA 6). La prueba comprueba que en una base
        de datos vacía se aplicaron V1, V2 y V3, y que la historia los
        registra con éxito.
    */
    @SpringBootApplication
    public static class MigrationsApplication {
    }

    @Entity(name = "Customer")
    @Table(name = "customers")
    public static class Customer {
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customers_seq")
        @SequenceGenerator(name = "customers_seq", sequenceName = "customers_seq", allocationSize = 50)   // la de V1
        private Long id;

        @Column(nullable = false, length = 100)
        private String name;

        @Column(nullable = false, length = 200, unique = true)
        private String email;

        @Column(name = "country_code", nullable = false, length = 2)   // la de V3
        private String countryCode;

        protected Customer() {
        }

        public Customer(String name, String email, String countryCode) {
            this.name = name;
            this.email = email;
            this.countryCode = countryCode;
        }

        public Long getId() {
            return id;
        }

        public String getCountryCode() {
            return countryCode;
        }
    }

    /*
        =================================================================
        CAPA 3) CAMBIAR UNA TABLA QUE YA TIENE DATOS
        =================================================================

        En tu máquina, la base de datos está vacía y todo funciona. En
        producción, customers tiene un millón de filas. Agregar una columna
        OBLIGATORIA (not null) a una tabla con datos falla: las filas
        existentes no tendrían valor.

        V3 lo hace en tres pasos, en la misma migración:
            1) alter table ... add column country_code ... (opcional, null)
            2) update customers set country_code = 'MX' where ... is null
               (llenar las filas viejas: una MIGRACIÓN DE DATOS)
            3) alter table ... alter column country_code set not null

        La prueba lo simula: una base de datos que llegó hasta V2, con
        clientes ya dados de alta; luego se aplica V3, y los clientes viejos
        quedan con 'MX' y la columna, obligatoria.

        En tablas MUY grandes, el UPDATE del paso 2 bloquea la tabla mucho
        tiempo: se hace por lotes, o con un valor por defecto (en
        PostgreSQL, add column ... default 'MX' not null es instantáneo
        desde la versión 11).
    */

    /*
        =================================================================
        CAPA 4) NUNCA EDITAR UNA MIGRACIÓN APLICADA
        =================================================================

        Una migración aplicada es HISTORIA: ya se ejecutó en alguna base de
        datos. Si la editas, esa base de datos no se entera (Flyway no la
        vuelve a correr), y las nuevas sí: los ambientes divergen.

        Para evitarlo, Flyway guarda el CHECKSUM de cada archivo aplicado y,
        al arrancar, lo compara. Si alguien editó V1, la aplicación NO
        ARRANCA. Medido, con migrations/tampered (la V1 con una columna
        agregada):

            Validate failed: Migrations have failed validation
            Migration checksum mismatch for migration version 1
            -> Applied to database : -1144454249
            -> Resolved locally    : 1944012139
            Either revert the changes to the migration, or run repair to
            update the schema history.

        La sugerencia del final: "repair" reescribe el checksum guardado
        con el del archivo actual. Solo es correcto si la edición NO cambió
        lo que la migración hace (un comentario, espacios); si cambió el
        esquema, la base de datos y el archivo ya no dicen lo mismo, y repair
        esconde el problema. Lo correcto casi siempre: revertir el archivo.

        La regla: un cambio nuevo es SIEMPRE una migración nueva (V4), aunque
        sea para corregir la anterior. La única excepción: una migración que
        todavía no se aplicó en ningún ambiente compartido (solo en tu
        máquina).
    */

    /*
        =================================================================
        CAPA 5) CUANDO UNA MIGRACIÓN FALLA A LA MITAD
        =================================================================

        migrations/failing/V2__broken.sql crea una tabla y después hace un
        insert en una tabla que no existe. ¿Qué queda?

        En PostgreSQL, los cambios de esquema (create, alter) son
        TRANSACCIONALES: Flyway corre cada migración en una transacción, y si
        falla, se deshace COMPLETA. Medido: la tabla tag_aliases (la primera
        sentencia, que sí funcionó) NO existe; V1 sigue aplicada; y la
        aplicación no arranca hasta que se corrija V2. Se corrige el archivo
        (todavía no se aplicó en ningún lado) y se vuelve a desplegar.

        OJO: en MySQL y Oracle, los cambios de esquema NO son transaccionales:
        cada create/alter se confirma solo. Una migración que falla a la mitad
        deja el esquema A MEDIAS, Flyway la marca como fallida, y hay que
        arreglar la base de datos a mano (y luego flyway repair). Por eso, en
        esas bases, una migración = un cambio.
    */

    /*
        =================================================================
        CAPA 6) HIBERNATE EN VALIDATE: EL CÓDIGO Y EL ESQUEMA DEBEN COINCIDIR
        =================================================================

        Con Flyway creando el esquema, Hibernate ya no debe tocarlo:

            spring.jpa.hibernate.ddl-auto=validate

        Al arrancar, Hibernate revisa que cada entidad tenga su tabla, sus
        columnas y su secuencia, con tipos compatibles. Si alguien agregó un
        campo a la entidad y olvidó la migración, la aplicación NO ARRANCA
        (en vez de fallar en la primera consulta, en producción). Medido,
        con una entidad que espera una columna phone que ninguna migración
        creó:

            Schema validation: missing column [phone] in table [customers]

        La prueba principal arranca con validate y funciona: las entidades y
        las migraciones coinciden.
    */

    /*
        =================================================================
        CAPA 7) CAMBIOS SIN DETENER EL SERVICIO: EXPANDIR Y CONTRAER
        =================================================================

        En producción hay varias copias de la aplicación, y se actualizan una
        por una: durante unos minutos conviven la versión vieja y la nueva,
        sobre la MISMA base de datos ya migrada. Una migración que rompe a la
        versión vieja (renombrar o borrar una columna que la vieja usa)
        tumba el servicio durante el despliegue.

        La técnica, EXPANDIR Y CONTRAER, en varios despliegues. Para renombrar
        email a contact_email:
            1) EXPANDIR: migración que agrega contact_email y la llena; el
               código nuevo escribe en las DOS y lee la nueva. La versión
               vieja sigue funcionando con email.
            2) cuando ya no queda ninguna copia vieja: código que usa solo
               contact_email.
            3) CONTRAER: migración que borra email.
        Más lento, pero en ningún momento hay una versión que se rompa.

        Regla práctica: agregar (columnas opcionales, tablas, índices) es
        seguro; quitar y renombrar se hace en varios pasos.
    */

    /*
        =================================================================
        CAPA 8) LO DEMÁS QUE HAY QUE SABER
        =================================================================

        - Migraciones REPETIBLES: R__vista_ventas.sql (sin versión). Se
          vuelven a aplicar cada vez que su contenido cambia; para vistas y
          funciones, que se redefinen completas.
        - BASELINE: para empezar a usar Flyway en una base de datos que ya
          existía: spring.flyway.baseline-on-migrate=true marca su estado
          actual como versión inicial.
        - flyway clean BORRA todo el esquema. Spring Boot lo deja desactivado
          por defecto (spring.flyway.clean-disabled=true; verificado en la
          prueba). Que siga así en cualquier ambiente con datos reales.
        - Índices en tablas grandes: en PostgreSQL, create index concurrently
          no bloquea las escrituras, pero no puede ir dentro de una
          transacción (Flyway permite marcar esa migración como no
          transaccional).
        - Datos de REFERENCIA (países, estados de un pedido) sí van en
          migraciones (V2); datos de PRUEBA, no: van en las pruebas.
    */

    /*
        =================================================================
        CAPA 9) ERRORES COMUNES
        =================================================================

        1) Scripts a mano antes de desplegar; ddl-auto=update en producción
           (CAPA 1).

        2) Agregar columnas obligatorias sin pensar en las filas existentes
           (CAPA 3).

        3) Editar una migración que ya se aplicó en otro ambiente (CAPA 4).

        4) En MySQL, varias sentencias de esquema en una migración (CAPA 5).

        5) ddl-auto=none en vez de validate: las diferencias aparecen en
           producción (CAPA 6).

        6) Renombrar o borrar columnas en un solo despliegue (CAPA 7).

        7) Datos de prueba en las migraciones; clean habilitado (CAPA 8).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - El esquema se versiona con migraciones: archivos SQL numerados, en
          el repositorio, aplicados al arrancar, en orden, una vez.
        - Flyway: V<versión>__<descripción>.sql y la tabla
          flyway_schema_history (con checksums).
        - Columnas obligatorias en tablas con datos: agregar, llenar,
          obligar.
        - Una migración aplicada no se edita: Flyway lo detecta y la
          aplicación no arranca.
        - En PostgreSQL una migración que falla se deshace completa; en
          MySQL, no.
        - ddl-auto=validate: entidad y esquema deben coincidir al arrancar.
        - Expandir y contraer para no romper durante los despliegues.
    */
}
