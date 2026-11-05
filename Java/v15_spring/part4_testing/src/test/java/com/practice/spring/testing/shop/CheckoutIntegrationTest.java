package com.practice.spring.testing.shop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * IntegrationTests, CAPAS 3 y 4: el flujo completo de compra, por HTTP real, contra PostgreSQL real.
 */
class CheckoutIntegrationTest extends IntegrationTestBase {

    private static final String SMALL_ORDER = """
            {"customerEmail":"ana@correo.com","tier":"GOLD",
             "lines":[{"sku":"CAF-001","quantity":2,"unitCents":18000}]}""";

    private static final String HUGE_ORDER = """
            {"customerEmail":"ana@correo.com","tier":"REGULAR",
             "lines":[{"sku":"CAF-001","quantity":100,"unitCents":18000}]}""";

    @Test
    void unaCompraPagadaQuedaGuardadaYApareceEnElHistorial() {
        client.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON).body(SMALL_ORDER)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.totalCents").isEqualTo(42_300)          // 36,000 - 10% + envío 9,900
                .jsonPath("$.paymentId").isEqualTo("fake-1");

        assertThat(payments.charged).containsExactly(42_300L);      // el "banco" cobró lo correcto
        assertThat(jdbc.sql("select status from shop_orders where customer_email = 'ana@correo.com'")
                .query(String.class).list()).containsExactly("PAID"); // y la base de datos lo tiene

        client.get().uri("/api/orders?email=ana@correo.com").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[0].status").isEqualTo("PAID");
    }

    @Test
    void unPagoRechazadoResponde402YDejaElPedidoRechazadoGuardado() {
        client.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON).body(HUGE_ORDER)
                .exchange()
                .expectStatus().isEqualTo(402)
                .expectBody().jsonPath("$.detail").isEqualTo("Pago rechazado: excede el límite de la tarjeta");

        assertThat(payments.charged).isEmpty();
        assertThat(jdbc.sql("select status from shop_orders").query(String.class).list())
                .containsExactly("REJECTED");                        // el registro del intento debe quedar
    }
}
