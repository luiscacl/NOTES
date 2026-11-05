package com.practice.spring.data.migrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.practice.spring.data.migrations.Migrations.Customer;
import com.practice.spring.data.migrations.Migrations.MigrationsApplication;
import com.practice.spring.data.schemacheck.SchemaCheckApplication;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Las comprobaciones de Migrations contra PostgreSQL 17 real.
 * La aplicación arranca con Flyway (migrations/shop) y Hibernate en validate; los experimentos
 * usan Flyway desde código, cada uno en su propio schema.
 * Correr desde v15_spring/:  ./mvnw -pl part3_data test -Dtest=MigrationsTest
 */
@SpringBootTest(classes = MigrationsApplication.class, properties = {
        "spring.flyway.locations=classpath:migrations/shop",
        "spring.jpa.hibernate.ddl-auto=validate"})
@Testcontainers
class MigrationsTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired private Flyway flyway;
    @Autowired private JdbcClient jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactions;

    /** Un Flyway aparte, sobre otro schema de la misma base de datos. */
    private static Flyway flywayOn(String schema, String location, String target) {
        var config = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema)
                .locations("classpath:migrations/" + location);
        return (target == null ? config : config.target(target)).load();
    }

    @Test
    void capa2_alArrancarSeAplicaronLasTresEnOrden() {
        List<String> applied = List.of(flyway.info().applied()).stream()
                .map(MigrationInfo::getVersion).map(Object::toString).toList();
        assertThat(applied).containsExactly("1", "2", "3");
        assertThat(jdbc.sql("select count(*) from flyway_schema_history where success").query(Long.class).single())
                .isEqualTo(3);
    }

    @Test
    void capa3_unaBaseConDatosViejosRecibeLaColumnaObligatoria() {
        flywayOn("upgrade", "shop", "2").migrate();                         // una base que llegó hasta V2...
        jdbc.sql("insert into upgrade.customers (id, name, email) values (1, 'Ana', 'ana@correo.com'), "
                + "(2, 'Luis', 'luis@correo.com')").update();                // ...con clientes dados de alta

        flywayOn("upgrade", "shop", null).migrate();                        // se despliega la versión con V3
        assertThat(jdbc.sql("select country_code from upgrade.customers order by id").query(String.class).list())
                .containsExactly("MX", "MX");                                // las filas viejas, llenadas
        assertThat(jdbc.sql("select is_nullable from information_schema.columns where table_schema = 'upgrade' "
                + "and table_name = 'customers' and column_name = 'country_code'").query(String.class).single())
                .isEqualTo("NO");                                            // y ya es obligatoria
    }

    @Test
    void capa4_editarUnaMigracionAplicadaImpideArrancar() {
        flywayOn("tamper", "shop", "1").migrate();                          // V1 aplicada en "producción"
        assertThatThrownBy(() -> flywayOn("tamper", "tampered", null).validate())   // alguien editó V1
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("Migration checksum mismatch for migration version 1");
    }

    @Test
    void capa5_unaMigracionQueFallaSeDeshaceCompletaEnPostgres() {
        assertThatThrownBy(() -> flywayOn("failing", "failing", null).migrate())
                .isInstanceOf(FlywayException.class);
        List<String> tables = jdbc.sql("select table_name from information_schema.tables where table_schema = 'failing' "
                + "order by table_name").query(String.class).list();
        assertThat(tables).contains("tags")                                  // V1 quedó
                .doesNotContain("tag_aliases");                              // lo que V2 alcanzó a crear: deshecho
        assertThat(jdbc.sql("select version from failing.flyway_schema_history "
                + "where success and version is not null order by installed_rank")   // sin la fila "creé el schema"
                .query(String.class).list()).containsExactly("1");
        assertThat(jdbc.sql("select count(*) from failing.flyway_schema_history where not success")
                .query(Long.class).single()).isZero();                       // la fallida ni siquiera quedó anotada
    }

    @Test
    void capa6_validatePasaConLasEntidadesQueCoincidenYLasUsa() {
        Long id = transactions.execute(status -> {
            Customer customer = new Customer("Mia", "mia@correo.com", "CA");
            entityManager.persist(customer);
            return customer.getId();
        });
        assertThat(jdbc.sql("select country_code from customers where id = :id").param("id", id)
                .query(String.class).single()).isEqualTo("CA");
    }

    @Test
    void capa6_unaEntidadSinSuMigracionImpideArrancar() {
        SpringApplication application = new SpringApplication(SchemaCheckApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        assertThatThrownBy(() -> application.run(
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.flyway.locations=classpath:migrations/shop",
                "--spring.jpa.hibernate.ddl-auto=validate").close())
                .rootCause()
                .hasMessageContaining("missing column [phone] in table [customers]");
    }

    @Test
    void capa8_cleanEstaDesactivadoPorDefecto() {
        assertThat(flyway.getConfiguration().isCleanDisabled()).isTrue();
    }
}
