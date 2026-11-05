package com.practice.spring.data.schemacheck;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Apoyo de MigrationsTest (Migrations, CAPA 6): una aplicación cuya entidad espera una columna
 * "phone" que ninguna migración creó. Con ddl-auto=validate no debe arrancar.
 * Vive en su propio paquete para que su escaneo no se mezcle con el de las guías.
 */
@SpringBootApplication
public class SchemaCheckApplication {

    @Entity(name = "CustomerWithPhone")
    @Table(name = "customers")
    public static class CustomerWithPhone {
        @Id
        private Long id;

        private String name;

        private String email;

        private String phone;                               // agregado al código... sin migración

        protected CustomerWithPhone() {
        }

        public String describe() {
            return id + " " + name + " " + email + " " + phone;
        }
    }
}
