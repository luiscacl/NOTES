package com.practice.spring.shop;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Registro, tokens y quién puede qué. */
class AuthApiTest extends IntegrationTestBase {

    @Test
    void registrarseDosVecesConElMismoNombreEs409() {
        String username = newCustomer();
        client.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .body("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, PASSWORD))
                .exchange().expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("USERNAME_TAKEN");
    }

    @Test
    void datosInvalidosDicenQueCampoFallo() {
        client.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .body("{\"username\":\"Ana Pérez\",\"password\":\"corta\"}")
                .exchange().expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Datos inválidos")
                .jsonPath("$.errors.username").isEqualTo("solo minúsculas, números, punto, guion y guion bajo")
                .jsonPath("$.errors.password").isEqualTo("debe tener entre 12 y 64 caracteres");
    }

    @Test
    void unaContrasenaDe64CaracteresConEnesPuedePasarDe72Bytes() {
        String password = "contraseña".repeat(6) + "ññññ";                    // 64 caracteres, 74 bytes
        client.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .body("{\"username\":\"bytes-largos\",\"password\":\"%s\"}".formatted(password))
                .exchange().expectStatus().isBadRequest()                       // antes: 500 de BCrypt
                .expectBody().jsonPath("$.errors.passwordWithinBcryptLimit")
                .isEqualTo("es demasiado larga: con acentos o ñ no puede pasar de 72 bytes");
    }

    @Test
    void elTokenLlevaElUsuarioYSuRolYNadaMas() {
        String username = newCustomer();
        String token = tokenFor(username, PASSWORD);
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"sub\":\"" + username + "\"", "\"roles\":[\"CUSTOMER\"]",
                "\"iss\":\"https://tienda.example\"").doesNotContain("FACTOR_");
    }

    @Test
    void contrasenaIncorrectaEs401() {
        String username = newCustomer();
        client.post().uri("/api/auth/token").header("Authorization", basic(username, "otra-contrasena-123"))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void sinTokenOConTokenAlteradoEs401() {
        client.get().uri("/api/orders").exchange().expectStatus().isUnauthorized();
        String token = customerToken();
        String tampered = token.substring(0, token.length() - 4) + "AAAA";       // otra firma
        client.get().uri("/api/orders").header("Authorization", bearer(tampered))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void unClienteNoAdministraElCatalogo() {
        client.post().uri("/api/products").header("Authorization", bearer(customerToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"name\":\"Pirata\",\"priceCents\":1,\"stock\":1}")
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void loQueNoSeNombroEnLaSeguridadNoExiste() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();     // público: lo pide Docker
        client.get().uri("/actuator/env").exchange().expectStatus().isUnauthorized();   // sin token: 401
        client.get().uri("/actuator/env").header("Authorization", bearer(adminToken()))
                .exchange().expectStatus().isForbidden();                           // denyAll, aun para admin
    }
}
