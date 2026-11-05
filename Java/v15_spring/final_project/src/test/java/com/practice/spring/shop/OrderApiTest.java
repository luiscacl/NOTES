package com.practice.spring.shop;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** El flujo de un pedido, de punta a punta: HTTP, seguridad, reglas y PostgreSQL. */
class OrderApiTest extends IntegrationTestBase {

    private long place(String token, String linesJson) {
        String body = client.post().uri("/api/orders").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).body("{\"lines\":" + linesJson + "}")
                .exchange().expectStatus().isCreated()
                .expectBody(String.class).returnResult().getResponseBody();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void comprarDescuentaExistenciasYGuardaElPrecioDeEseMomento() {
        long keyboard = newProduct("Teclado", 89_900, 5);
        long mouse = newProduct("Ratón", 35_000, 5);
        String ana = customerToken();

        long order = place(ana, "[{\"productId\":%d,\"quantity\":2},{\"productId\":%d,\"quantity\":1},{\"productId\":%d,\"quantity\":1}]"
                .formatted(keyboard, mouse, keyboard));                      // el teclado dos veces: se suma
        assertThat(stockOf(keyboard)).isEqualTo(2);
        assertThat(stockOf(mouse)).isEqualTo(4);

        client.put().uri("/api/products/" + keyboard).header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"Teclado\",\"priceCents\":99900,\"stock\":2}")
                .exchange().expectStatus().isOk();                           // sube el precio DESPUÉS

        client.get().uri("/api/orders/" + order).header("Authorization", bearer(ana)).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("PLACED")
                .jsonPath("$.totalCents").isEqualTo(3 * 89_900 + 35_000)     // lo que pagó
                .jsonPath("$.lines.length()").isEqualTo(2)
                .jsonPath("$.lines[0].unitPriceCents").isEqualTo(89_900);
    }

    @Test
    void siUnRenglonNoAlcanzaNoSeApartaNada() {
        long plenty = newProduct("Cable", 5_000, 10);
        long scarce = newProduct("Monitor", 400_000, 1);
        client.post().uri("/api/orders").header("Authorization", bearer(customerToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"lines\":[{\"productId\":%d,\"quantity\":3},{\"productId\":%d,\"quantity\":2}]}"
                        .formatted(plenty, scarce))
                .exchange().expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("INSUFFICIENT_STOCK")
                .jsonPath("$.detail").isEqualTo("No hay existencias suficientes de 'Monitor'");
        assertThat(stockOf(plenty)).isEqualTo(10);                            // el rollback devolvió los 3 cables
        assertThat(stockOf(scarce)).isEqualTo(1);
    }

    @Test
    void elPedidoDeOtroEs404YElAdministradorLoVe() {
        long product = newProduct("Audífonos", 120_000, 3);
        long order = place(customerToken(), "[{\"productId\":%d,\"quantity\":1}]".formatted(product));

        client.get().uri("/api/orders/" + order).header("Authorization", bearer(customerToken()))
                .exchange().expectStatus().isNotFound();                     // ni confirma que existe
        client.get().uri("/api/orders/" + order).header("Authorization", bearer(adminToken()))
                .exchange().expectStatus().isOk();
    }

    @Test
    void cadaClienteListaSoloSusPedidos() {
        long product = newProduct("Libreta", 9_000, 10);
        String luis = customerToken();
        place(luis, "[{\"productId\":%d,\"quantity\":1}]".formatted(product));
        place(luis, "[{\"productId\":%d,\"quantity\":2}]".formatted(product));
        place(customerToken(), "[{\"productId\":%d,\"quantity\":1}]".formatted(product));

        client.get().uri("/api/orders").header("Authorization", bearer(luis)).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.content[0].totalCents").isEqualTo(18_000);       // el más nuevo primero
    }

    @Test
    void cancelarDevuelveExistenciasYSoloSePuedeUnaVez() {
        long product = newProduct("Silla", 250_000, 4);
        String ana = customerToken();
        long order = place(ana, "[{\"productId\":%d,\"quantity\":3}]".formatted(product));
        assertThat(stockOf(product)).isEqualTo(1);

        client.post().uri("/api/orders/" + order + "/cancel").header("Authorization", bearer(ana))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("CANCELLED");
        assertThat(stockOf(product)).isEqualTo(4);

        client.post().uri("/api/orders/" + order + "/cancel").header("Authorization", bearer(ana))
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("ORDER_NOT_CANCELLABLE");
        assertThat(stockOf(product)).isEqualTo(4);                            // no devolvió dos veces
    }

    @Test
    void soloElAdministradorEnviaYLoEnviadoYaNoSeCancela() {
        long product = newProduct("Lámpara", 45_000, 2);
        String ana = customerToken();
        long order = place(ana, "[{\"productId\":%d,\"quantity\":1}]".formatted(product));

        client.post().uri("/api/orders/" + order + "/ship").header("Authorization", bearer(ana))
                .exchange().expectStatus().isForbidden();
        client.post().uri("/api/orders/" + order + "/ship").header("Authorization", bearer(adminToken()))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("SHIPPED");
        client.post().uri("/api/orders/" + order + "/cancel").header("Authorization", bearer(ana))
                .exchange().expectStatus().isEqualTo(409);
    }

    @Test
    void unPedidoVacioOConCantidadInvalidaEs400() {
        client.post().uri("/api/orders").header("Authorization", bearer(customerToken()))
                .contentType(MediaType.APPLICATION_JSON).body("{\"lines\":[]}")
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors.lines").isEqualTo("el pedido necesita al menos un renglón");
        client.post().uri("/api/orders").header("Authorization", bearer(customerToken()))
                .contentType(MediaType.APPLICATION_JSON).body("{\"lines\":[{\"productId\":1,\"quantity\":0}]}")
                .exchange().expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors['lines[0].quantity']").isEqualTo("mínimo 1");
    }
}
