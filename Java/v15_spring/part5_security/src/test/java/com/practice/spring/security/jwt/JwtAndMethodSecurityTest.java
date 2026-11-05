package com.practice.spring.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

import com.practice.spring.security.jwt.JwtAndMethodSecurity.OrdersApiApplication;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Las comprobaciones de JwtAndMethodSecurity, con tokens reales firmados con RSA.
 * Correr desde v15_spring/:  ./mvnw -pl part5_security test -Dtest=JwtAndMethodSecurityTest
 */
@SpringBootTest(classes = OrdersApiApplication.class)
@AutoConfigureMockMvc
class JwtAndMethodSecurityTest {

    @Autowired private MockMvcTester mvc;
    @Autowired private JwtEncoder encoder;

    /** Pide un token con usuario y contraseña, como lo haría una app. */
    private String tokenFor(String user, String password) throws Exception {
        MvcTestResult result = mvc.post().uri("/auth/token").with(httpBasic(user, password)).exchange();
        String json = result.getResponse().getContentAsString();
        return json.replaceAll(".*\"access_token\":\"([^\"]+)\".*", "$1");
    }

    private MvcTestResult get(String uri, String token) {
        return mvc.get().uri(uri).header("Authorization", "Bearer " + token).exchange();
    }

    @Test
    void capa2_elJwtTieneTresPartesYSuContenidoSeLeeSinLlave() throws Exception {
        String token = tokenFor("ana", "ana-secreta-123");
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);                                            // cabecera.contenido.firma
        String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(header).contains("\"alg\":\"RS256\"");
        assertThat(payload).contains("\"sub\":\"ana\"", "\"scope\":\"orders:read\"",
                "\"iss\":\"https://auth.tienda.example\"");                       // cualquiera lo lee: NO es secreto
    }

    @Test
    void capa3_unTokenAlteradoSeRechaza() throws Exception {
        String[] parts = tokenFor("ana", "ana-secreta-123").split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forged = payload.replace("\"sub\":\"ana\"", "\"sub\":\"admin\"");
        String tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(forged.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        MvcTestResult result = get("/api/orders/2", tampered);
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result.getResponse().getHeader("WWW-Authenticate"))
                .contains("error=\"invalid_token\"").contains("Invalid signature");
    }

    @Test
    void capa4_unTokenVencidoSeRechaza() {
        Instant anHourAgo = Instant.now().minusSeconds(3_600);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://auth.tienda.example").subject("ana")
                .issuedAt(anHourAgo.minusSeconds(900)).expiresAt(anHourAgo)      // venció hace una hora
                .claim("scope", "orders:read").build();
        String expired = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        MvcTestResult result = get("/api/orders/1", expired);
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result.getResponse().getHeader("WWW-Authenticate")).contains("Jwt expired at");
    }

    @Test
    void capa5_sinTokenEs401ConBearer() {
        MvcTestResult result = mvc.get().uri("/api/orders/1").exchange();
        assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(result.getResponse().getHeader("WWW-Authenticate"))
                .startsWith("Bearer")
                .contains("resource_metadata=");                                  // RFC 9728, nuevo en Spring Security 7
    }

    @Test
    void capa6_cadaQuienSusPedidosYElAdminTodos() throws Exception {
        String ana = tokenFor("ana", "ana-secreta-123");
        String admin = tokenFor("admin", "admin-secreta-456");

        assertThat(get("/api/orders/1", ana)).hasStatusOk()
                .bodyJson().extractingPath("$.owner").isEqualTo("ana");          // el suyo
        assertThat(get("/api/orders/2", ana)).hasStatus(HttpStatus.FORBIDDEN);  // el de Luis: no
        assertThat(get("/api/orders", ana)).hasStatus(HttpStatus.FORBIDDEN);    // la lista: solo admin

        assertThat(get("/api/orders/2", admin)).hasStatusOk();
        assertThat(get("/api/orders", admin)).hasStatusOk()
                .bodyJson().extractingPath("$.length()").isEqualTo(3);
    }
}
