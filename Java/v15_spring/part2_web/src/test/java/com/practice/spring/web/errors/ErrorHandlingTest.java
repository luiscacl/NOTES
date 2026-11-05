package com.practice.spring.web.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.practice.spring.web.errors.ErrorHandling.ErrorsApplication;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Las comprobaciones de ErrorHandling, con el servidor real: lo que de verdad recibe un cliente.
 * Correr desde v15_spring/:  ./mvnw -pl part2_web test -Dtest=ErrorHandlingTest
 */
@SpringBootTest(classes = ErrorsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorHandlingTest {

    @LocalServerPort
    private int port;

    private RestTestClient client;

    @BeforeEach
    void connect() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void capa2y4_noEncontradoEsUnProblemDetail404() {
        client.get().uri("/api/products/XXX-999/stock").exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.tienda.example/problems/product-not-found")
                .jsonPath("$.title").isEqualTo("Producto no encontrado")
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.detail").isEqualTo("No existe el producto XXX-999")
                .jsonPath("$.instance").isEqualTo("/api/products/XXX-999/stock");
    }

    @Test
    void capa4_sinExistenciasEs409ConExtensiones() {
        client.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("sku", "CAF-001", "quantity", 5))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Sin existencias")
                .jsonPath("$.detail").isEqualTo("Solo hay 2 piezas de CAF-001")
                .jsonPath("$.available").isEqualTo(2)
                .jsonPath("$.sku").isEqualTo("CAF-001");
    }

    @Test
    void capa5_validacionDelCuerpoConLaListaDeCampos() {
        client.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .header("Accept-Language", "es")
                .body(Map.of("sku", " ", "quantity", 0))
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Datos inválidos")
                .jsonPath("$.detail").isEqualTo("2 dato(s) inválido(s)")
                .jsonPath("$.errors[0].field").isEqualTo("quantity")
                .jsonPath("$.errors[0].code").isEqualTo("Positive")
                .jsonPath("$.errors[0].message").isEqualTo("debe ser mayor que 0")
                .jsonPath("$.errors[1].field").isEqualTo("sku")
                .jsonPath("$.errors[1].code").isEqualTo("NotBlank");
    }

    @Test
    void capa5_laValidacionDelServicioYaEs400() {
        client.post().uri("/api/reserve?sku=CAF-001&quantity=0").exchange()
                .expectStatus().isBadRequest()                                 // antes: 500
                .expectBody()
                .jsonPath("$.type").isEqualTo("https://api.tienda.example/problems/invalid-data")
                .jsonPath("$.errors[0].field").isEqualTo("reserve.quantity")
                .jsonPath("$.errors[0].code").isEqualTo("Min");
    }

    @Test
    void capa6_el500NoFiltraNadaInternoYTraeUnErrorId() {
        String body = client.get().uri("/api/bug").exchange()
                .expectStatus().isEqualTo(500)
                .expectBody(String.class).returnResult().getResponseBody();
        assertThat(body)
                .contains("\"title\":\"Error interno\"", "\"errorId\":\"")
                .doesNotContain("db-prod-07", "app_rw", "IllegalStateException", "at com.");   // nada interno
    }

    @Test
    void capa3_losErroresDeSpringTambienSonProblemDetail() {
        client.get().uri("/api/no-existe").exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
        client.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON).body("{\"sku\":")
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void capa7_elManejadorDelControllerGanaAlAdvice() {
        client.get().uri("/api/legacy/discount?code=PROMO").exchange()
                .expectStatus().isEqualTo(422)                                 // no el 500 del advice
                .expectBody().jsonPath("$.detail").isEqualTo("Cupón inválido: PROMO");
    }
}
