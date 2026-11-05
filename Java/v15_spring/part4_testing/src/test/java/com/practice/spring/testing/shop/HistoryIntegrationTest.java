package com.practice.spring.testing.shop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * IntegrationTests, CAPA 2: otra clase con la MISMA configuración que CheckoutIntegrationTest.
 * Spring reusa el contexto (y el contenedor): esta clase no paga otra vez el arranque.
 */
class HistoryIntegrationTest extends IntegrationTestBase {

    @Test
    void elHistorialVieneDelMasRecienteAlMasViejoYSoloDelCliente() {
        for (String email : new String[] {"luis@correo.com", "luis@correo.com", "mia@correo.com"}) {
            client.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON)
                    .body("{\"customerEmail\":\"" + email + "\",\"tier\":\"REGULAR\","
                            + "\"lines\":[{\"sku\":\"TAZ-001\",\"quantity\":1,\"unitCents\":12000}]}")
                    .exchange()
                    .expectStatus().isCreated();
        }
        client.get().uri("/api/orders?email=luis@correo.com").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.length()").isEqualTo(2);
        assertThat(jdbc.sql("select count(*) from shop_orders").query(Long.class).single()).isEqualTo(3);
    }
}
