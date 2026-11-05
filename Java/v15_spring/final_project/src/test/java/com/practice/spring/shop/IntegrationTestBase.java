package com.practice.spring.shop;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * La base de las pruebas de integración: la aplicación completa en un puerto real, con PostgreSQL
 * en Docker y las migraciones de Flyway. Todas las clases heredan la MISMA configuración, así
 * Spring reusa un solo contexto y un solo contenedor (part4_testing/.../IntegrationTests).
 *
 * Sin limpiar tablas entre pruebas: cada prueba crea sus propios usuarios y productos (nombres
 * únicos), así no dependen del orden ni de lo que dejó otra.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"shop.jwt.ephemeral-keys=true", "shop.admin.password=" + IntegrationTestBase.ADMIN_PASSWORD})
@Import(IntegrationTestBase.Containers.class)
public abstract class IntegrationTestBase {

    protected static final String ADMIN_PASSWORD = "admin-de-pruebas-123";
    protected static final String PASSWORD = "contrasena-larga-123";

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:17");
        }
    }

    @LocalServerPort private int port;

    protected RestTestClient client;

    @BeforeEach
    void connect() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    /** Un cliente nuevo, con nombre único. */
    protected String newCustomer() {
        String username = "cliente-" + UUID.randomUUID().toString().substring(0, 8);
        client.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .body("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD))
                .exchange().expectStatus().isCreated();
        return username;
    }

    protected String tokenFor(String username, String password) {
        String body = client.post().uri("/api/auth/token")
                .header("Authorization", basic(username, password))
                .exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        return JsonPath.read(body, "$.access_token");
    }

    protected String customerToken() {
        return tokenFor(newCustomer(), PASSWORD);
    }

    protected String adminToken() {
        return tokenFor("admin", ADMIN_PASSWORD);
    }

    protected static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Crea un producto como administrador y devuelve su id. */
    protected long newProduct(String name, long priceCents, int stock) {
        String body = client.post().uri("/api/products").header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"%s\",\"priceCents\":%d,\"stock\":%d}".formatted(name, priceCents, stock))
                .exchange().expectStatus().isCreated()
                .expectBody(String.class).returnResult().getResponseBody();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected int stockOf(long productId) {
        String body = client.get().uri("/api/products/" + productId).exchange().expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        return JsonPath.read(body, "$.stock");
    }
}
