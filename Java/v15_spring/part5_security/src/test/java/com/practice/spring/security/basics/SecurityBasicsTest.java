package com.practice.spring.security.basics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

import com.practice.spring.security.basics.SecurityBasics.StoreApplication;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Las comprobaciones de SecurityBasics, una por capa.
 * Correr desde v15_spring/:  ./mvnw -pl part5_security test -Dtest=SecurityBasicsTest
 */
@SpringBootTest(classes = StoreApplication.class)
@AutoConfigureMockMvc
class SecurityBasicsTest {

    private static final String NEW_PRODUCT = "{\"sku\":\"FIL-001\",\"name\":\"Filtro\"}";

    @Autowired private MockMvcTester mvc;
    @Autowired private PasswordEncoder encoder;

    @Test
    void capa2_sinCredencialesEs401YPideAutenticarse() {
        assertThat(mvc.get().uri("/api/me"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .headers().hasValue("WWW-Authenticate", "Basic realm=\"Realm\", charset=\"UTF-8\"");
        assertThat(mvc.get().uri("/api/me").with(httpBasic("ana", "contraseña-mala")))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void capa2y4_publicoAutenticadoYSoloAdmin() {
        assertThat(mvc.get().uri("/api/products")).hasStatusOk();                          // público
        assertThat(mvc.post().uri("/api/admin/products").with(httpBasic("ana", "ana-secreta-123"))
                .contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
                .hasStatus(HttpStatus.FORBIDDEN);                                           // sé quién eres; no puedes
        assertThat(mvc.post().uri("/api/admin/products").with(httpBasic("admin", "admin-secreta-456"))
                .contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void capa5_bcryptConPrefijoYSal() {
        String first = encoder.encode("mi-contraseña");
        String second = encoder.encode("mi-contraseña");
        assertThat(first).startsWith("{bcrypt}$2a$10$");
        assertThat(first).isNotEqualTo(second);                                             // la sal: distinto cada vez
        assertThat(encoder.matches("mi-contraseña", first)).isTrue();                       // y los dos se verifican
        assertThat(encoder.matches("mi-contraseña", second)).isTrue();
        assertThat(encoder.matches("otra", first)).isFalse();
    }

    @Test
    void capa6y7_lasAuthoritiesYElUsuarioActual() {
        assertThat(mvc.get().uri("/api/me").with(httpBasic("ana", "ana-secreta-123")))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.username", v -> v.assertThat().isEqualTo("ana"))
                .hasPathSatisfying("$.authorities", v -> v.assertThat().asInstanceOf(InstanceOfAssertFactories.LIST).containsExactly("ROLE_USER"))
                .hasPathSatisfying("$.sameAsContext", v -> v.assertThat().isEqualTo(true));
    }

    @Test
    void capa8_csrfRechazaElPostSinTokenAunqueLaContrasenaSeaCorrecta() {
        assertThat(mvc.post().uri("/web/profile").with(httpBasic("ana", "ana-secreta-123")))
                .hasStatus(HttpStatus.FORBIDDEN);                                           // sin token CSRF
        assertThat(mvc.post().uri("/web/profile").with(httpBasic("ana", "ana-secreta-123")).with(csrf()))
                .hasStatusOk().hasBodyTextEqualTo("perfil actualizado");                    // con token
    }

    @Test
    void capa9_corsPermiteSoloElOrigenConfigurado() {
        assertThat(mvc.options().uri("/api/products")
                .header("Origin", "https://tienda.example")
                .header("Access-Control-Request-Method", "GET"))
                .hasStatusOk()
                .headers().hasValue("Access-Control-Allow-Origin", "https://tienda.example");
        assertThat(mvc.options().uri("/api/products")
                .header("Origin", "https://malo.example")
                .header("Access-Control-Request-Method", "GET"))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void capa3_encabezadosDeSeguridadEnCadaRespuesta() {
        assertThat(mvc.get().uri("/api/products"))
                .headers()
                .hasValue("X-Content-Type-Options", "nosniff")
                .hasValue("X-Frame-Options", "DENY");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void capa10_withMockUserCorreComoAdminSinLogin() {
        assertThat(mvc.post().uri("/api/admin/products")
                .contentType(MediaType.APPLICATION_JSON).content(NEW_PRODUCT))
                .hasStatus(HttpStatus.CREATED);
    }
}
