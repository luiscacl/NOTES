package com.practice.spring.shop;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** El catálogo: público para leer, del administrador para cambiar. */
class CatalogApiTest extends IntegrationTestBase {

    @Test
    void elAdministradorCreaYCualquieraLee() {
        long id = newProduct("Teclado mecánico", 89_900, 5);
        client.get().uri("/api/products/" + id).exchange()                         // sin token
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Teclado mecánico")
                .jsonPath("$.priceCents").isEqualTo(89_900);
    }

    @Test
    void productoInexistenteEsProblemDetail404() {
        client.get().uri("/api/products/999999").exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.title").isEqualTo("No encontrado")
                .jsonPath("$.detail").isEqualTo("No existe el producto 999999");
    }

    @Test
    void precioYExistenciasSeValidan() {
        client.post().uri("/api/products").header("Authorization", bearer(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"\",\"priceCents\":0,\"stock\":-1}")
                .exchange().expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errors.name").isEqualTo("es obligatorio")
                .jsonPath("$.errors.priceCents").isEqualTo("debe ser mayor que cero")
                .jsonPath("$.errors.stock").isEqualTo("no puede ser negativo");
    }

    @Test
    void laPaginaTieneUnTopeDe50() {
        client.get().uri("/api/products?size=100000").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.size").isEqualTo(50);
    }

    @Test
    void ordenarPorUnCampoQueNoExisteEs400() {
        client.get().uri("/api/products?sort=password").exchange()
                .expectStatus().isBadRequest()                                    // antes: 500
                .expectBody().jsonPath("$.detail").isEqualTo("No se puede ordenar por 'password'");
    }
}
