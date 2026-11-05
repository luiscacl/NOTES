package com.practice.spring.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.practice.spring.web.rest.RestControllers.CatalogApplication;
import com.practice.spring.web.rest.RestControllers.ProductResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Las comprobaciones de RestControllers CON EL SERVIDOR REAL: Tomcat en un puerto libre y
 * peticiones HTTP de verdad (CAPA 8, forma 2).
 * Correr desde v15_spring/:  ./mvnw -pl part2_web test -Dtest='RestControllers*'
 */
@SpringBootTest(classes = CatalogApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RestControllersTest {

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void connect() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void capa3_filtrosComoParametros() {
        List<ProductResponse> coffee = client.get().uri("/api/products?category=café&maxPriceCents=17000")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<ProductResponse>>() { })
                .returnResult().getResponseBody();
        assertThat(coffee).extracting(ProductResponse::name).containsExactly("Café de Veracruz");
    }

    @Test
    void capa4_crearResponde201ConLocationYLuegoSePuedeLeer() {
        ProductResponse created = client.post().uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-Id", "req-42")
                .body(Map.of("name", "Filtro de tela", "category", "cocina", "priceCents", 9_900))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("X-Request-Id", "req-42")
                .expectHeader().value("Location", location -> assertThat(location).startsWith("/api/products/"))
                .expectBody(ProductResponse.class)
                .returnResult().getResponseBody();

        client.get().uri("/api/products/" + created.id()).exchange()
                .expectStatus().isOk()
                .expectBody(ProductResponse.class).isEqualTo(created);
    }

    @Test
    void capa4_patchDeleteY404() {
        client.patch().uri("/api/products/2/price")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("priceCents", 11_000))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ProductResponse.class)
                .value(product -> assertThat(product.priceCents()).isEqualTo(11_000));

        client.delete().uri("/api/products/2").exchange().expectStatus().isNoContent();
        client.delete().uri("/api/products/2").exchange().expectStatus().isNotFound();   // ya no existe
        client.get().uri("/api/products/999").exchange().expectStatus().isNotFound();
    }

    @Test
    void capa5_fechasIsoYCamposDesconocidosIgnorados() {
        client.get().uri("/api/products/1").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.createdAt").isEqualTo("2026-11-04T10:00:00Z");

        client.post().uri("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"Jarra\",\"category\":\"cocina\",\"priceCents\":20000,\"admin\":true}")
                .exchange()
                .expectStatus().isCreated();                                    // "admin" se ignora
    }

    @Test
    void capa6_erroresQueSpringRespondeSolo() {
        client.get().uri("/api/no-existe").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.error").isEqualTo("Not Found");
        client.put().uri("/api/products").contentType(MediaType.APPLICATION_JSON).body("{}")
                .exchange().expectStatus().isEqualTo(405);
        client.post().uri("/api/products").contentType(MediaType.APPLICATION_JSON).body("{\"name\":")
                .exchange().expectStatus().isBadRequest();
        client.post().uri("/api/products").contentType(MediaType.TEXT_PLAIN).body("hola")
                .exchange().expectStatus().isEqualTo(415);
        client.get().uri("/api/products/1").accept(MediaType.APPLICATION_XML)
                .exchange().expectStatus().isEqualTo(406);
    }

    @Test
    void capa9_cadaPeticionEnUnHiloDeTomcat() {
        client.get().uri("/api/diagnostics/thread").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.virtual").isEqualTo(false)
                .jsonPath("$.name").value(name -> assertThat((String) name).startsWith("http-nio-"));
    }
}
