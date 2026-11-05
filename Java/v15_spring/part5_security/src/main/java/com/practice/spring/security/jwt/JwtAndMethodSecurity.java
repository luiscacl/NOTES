package com.practice.spring.security.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/*
    =====================================================================
    JWT Y SEGURIDAD POR MÉTODO: CÓMO SE PROTEGEN LAS APIS MODERNAS
    =====================================================================

    Ruta de spring/: guía 16  (part5_security)
    Anterior:  part5_security/.../basics/SecurityBasics
    Siguiente: part6_production/...  (la primera de producción)

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo.
    - Las comprobaciones son pruebas: JwtAndMethodSecurityTest. Pide tokens
      reales (firmados con RSA), los usa, los decodifica, los altera y los
      deja vencer. Desde v15_spring/:
          ./mvnw -pl part5_security test -Dtest=JwtAndMethodSecurityTest
    - Versiones: Spring Security 7.1, Nimbus JOSE 10.9 (dependency:tree).
      Mensajes citados: corridas reales.
    - Aquí la MISMA aplicación emite los tokens (POST /auth/token) para
      poder practicar sin servicios externos. En producción, los emite un
      SERVIDOR DE IDENTIDAD aparte (CAPA 7); tu API solo los verifica.

    Lo que ya se vio: la cadena de filtros, 401/403, roles y authorities
    (basics/SecurityBasics), los proxies y la auto-invocación (part1_core/
    .../container/IoCContainer, CAPA 8).

    Importancia: MUY ALTA. Casi toda API moderna (apps móviles, frontends,
    servicios entre sí) se protege con tokens JWT.

    Índice:
        CAPA 1)  Sesiones o tokens
        CAPA 2)  Qué es un JWT (y qué NO es)
        CAPA 3)  La firma: por qué no se puede alterar
        CAPA 4)  Expiración: tokens que mueren solos
        CAPA 5)  Configurar la API como resource server
        CAPA 6)  Seguridad por método: @PreAuthorize y @PostAuthorize
        CAPA 7)  De dónde salen los tokens en la vida real
        CAPA 8)  Errores comunes
        RESUMEN FINAL
*/
public class JwtAndMethodSecurity {

    /*
        =================================================================
        CAPA 1) SESIONES O TOKENS
        =================================================================

        Con SESIONES (la forma clásica): el usuario hace login, el servidor
        guarda "la sesión 7f3a es de Ana" EN SU MEMORIA, y le da al navegador
        una cookie con 7f3a. Cada petición trae la cookie, y el servidor busca
        la sesión. Problemas al crecer: con 5 copias del servicio, la sesión
        está en UNA (hay que compartirla en Redis o una base de datos), y las
        apps móviles y otros servicios no manejan cookies bien.

        Con TOKENS: al hacer login, el usuario recibe un token FIRMADO que
        dice "soy Ana, puedo leer pedidos, válido hasta las 10:15". Lo manda
        en cada petición:

            Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhbmEi...

        El servidor no guarda NADA: verifica la firma y lee lo que el token
        dice. Cualquier copia del servicio puede verificarlo, sin consultar a
        nadie. Por eso es la forma de las APIs modernas ("sin estado").

        "Bearer" significa "portador": QUIEN TENGA el token, es Ana. Un token
        robado es una sesión robada; por eso viajan solo por HTTPS y duran
        poco (CAPA 4).
    */
    @SpringBootApplication
    @EnableMethodSecurity                                   // activa @PreAuthorize y @PostAuthorize (CAPA 6)
    public static class OrdersApiApplication {
    }

    /*
        =================================================================
        CAPA 2) QUÉ ES UN JWT (Y QUÉ NO ES)
        =================================================================

        Un JWT (JSON Web Token, RFC 7519) son tres partes en Base64URL,
        separadas por puntos:

            eyJhbGciOiJSUzI1NiJ9 . eyJzdWIiOiJhbmEiLCJzY29wZSI6Li4ufQ . kq8...
            CABECERA               CONTENIDO (claims)                    FIRMA
            {"alg":"RS256"}        {"sub":"ana","scope":"orders:read",
                                    "iss":"https://auth.tienda.example",
                                    "iat":..., "exp":...}

        Los CLAIMS estándar:
            iss   quién lo emitió (el servidor de identidad)
            sub   de quién es (el usuario)
            exp   cuándo vence; iat, cuándo se emitió
            aud   para qué API es
            scope los permisos (y cualquier claim propio: roles, tenant...)

        Lo que NO es: SECRETO. El contenido es Base64, no está cifrado:
        cualquiera que tenga el token lo lee. La prueba lo decodifica con
        java.util.Base64 y lee "sub":"ana". Nunca pongas en un JWT nada que el
        usuario no deba ver (ni contraseñas, ni datos personales de más).
    */

    /*
        =================================================================
        CAPA 3) LA FIRMA: POR QUÉ NO SE PUEDE ALTERAR
        =================================================================

        Si el contenido se lee, ¿qué impide que Ana cambie "sub":"ana" por
        "sub":"admin"? La FIRMA: un cálculo sobre la cabecera y el contenido
        con una llave que solo tiene el emisor. Si cambia un solo carácter,
        la firma ya no coincide.

        Dos formas de firmar:
        - RS256 (asimétrica, la de esta guía): el emisor firma con su llave
          PRIVADA; las APIs verifican con la PÚBLICA. Las APIs no pueden
          crear tokens, solo verificarlos. El emisor publica sus llaves
          públicas en una URL (el JWKS, .well-known/jwks.json) y las APIs las
          descargan solas.
        - HS256 (simétrica): la MISMA llave secreta firma y verifica. Toda API
          que verifica también podría FABRICAR tokens. Solo cuando emisor y
          verificador son el mismo sistema.

        La prueba altera el contenido de un token válido (lo cambia a
        "admin") y la API lo rechaza. Medido:

            401  WWW-Authenticate: Bearer error="invalid_token",
                 error_description="An error occurred while attempting to
                 decode the Jwt: Signed JWT rejected: Invalid signature", ...
    */
    @Configuration
    public static class TokenKeys {
        @Bean
        public KeyPair signingKeys() throws NoSuchAlgorithmException {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();             // la privada firma; la pública verifica
        }

        @Bean
        public JwtEncoder jwtEncoder(KeyPair keys) {         // lo que tendría el SERVIDOR DE IDENTIDAD
            RSAKey jwk = new RSAKey.Builder((RSAPublicKey) keys.getPublic())
                    .privateKey((RSAPrivateKey) keys.getPrivate())
                    .build();
            return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        }

        @Bean
        public JwtDecoder jwtDecoder(KeyPair keys) {         // lo que tiene TU API: solo la llave pública
            return NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
            // En producción, en vez de este bean, una propiedad:
            //   spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.tienda.example
            // y Spring descarga las llaves públicas del emisor solo.
        }

        @Bean
        public Clock clock() {
            return Clock.systemUTC();
        }
    }

    /*
        =================================================================
        CAPA 4) EXPIRACIÓN: TOKENS QUE MUEREN SOLOS
        =================================================================

        Como la API no guarda nada, NO PUEDE "cerrar la sesión" de un token:
        si alguien lo roba, sirve hasta que venza. Por eso los ACCESS TOKENS
        duran poco: 5 a 15 minutos (aquí, 15). Para no pedir la contraseña
        cada 15 minutos, el servidor de identidad entrega también un REFRESH
        TOKEN (de larga duración, guardado con más cuidado), que sirve SOLO
        para pedir un access token nuevo, y que el servidor de identidad SÍ
        puede revocar.

        La prueba crea un token vencido hace una hora y la API lo rechaza.
        Medido:

            401  WWW-Authenticate: Bearer error="invalid_token",
                 error_description="An error occurred while attempting to
                 decode the Jwt: Jwt expired at 2026-11-04T23:25:44Z", ...

        (Spring acepta 60 segundos de diferencia de reloj entre servidores,
        por defecto: los relojes de dos máquinas nunca coinciden exacto.)
    */
    @RestController
    public static class TokenController {
        private final JwtEncoder encoder;
        private final Clock clock;

        public TokenController(JwtEncoder encoder, Clock clock) {
            this.encoder = encoder;
            this.clock = clock;
        }

        /** Solo para practicar: en producción esto lo hace el servidor de identidad (CAPA 7). */
        @PostMapping("/auth/token")
        public Map<String, Object> token(Authentication user) {   // el usuario ya autenticado con Basic
            Instant now = clock.instant();
            String scope = user.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(authority -> !authority.startsWith("ROLE_"))   // los permisos del usuario...
                    .filter(authority -> !authority.startsWith("FACTOR_"))  // ...no CÓMO se autenticó (CAPA 4)
                    .collect(Collectors.joining(" "));
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer("https://auth.tienda.example")
                    .subject(user.getName())
                    .issuedAt(now)
                    .expiresAt(now.plus(Duration.ofMinutes(15)))   // vida corta
                    .claim("scope", scope)
                    .build();
            String token = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
            return Map.of("access_token", token, "token_type", "Bearer", "expires_in", 900);
        }
    }

    /*
        =================================================================
        CAPA 5) CONFIGURAR LA API COMO RESOURCE SERVER
        =================================================================

        En OAuth2, la API que recibe tokens es un "resource server". Con
        Spring:

            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))

        agrega a la cadena el filtro que lee el encabezado Authorization:
        Bearer, verifica la FIRMA, el VENCIMIENTO (y, si se configura, el
        emisor y la audiencia), y convierte el token en un usuario autenticado.
        Cada claim "scope" se vuelve una authority con prefijo SCOPE_:

            "scope": "orders:read orders:admin"
              -> authorities SCOPE_orders:read, SCOPE_orders:admin

        Sin token: 401. Medido, el encabezado en Spring Security 7:

            WWW-Authenticate: Bearer resource_metadata="http://localhost/
                              .well-known/oauth-protected-resource"

        "Bearer" dice que se espera un token; resource_metadata es nuevo
        (RFC 9728): la dirección donde la API describe qué servidor de
        identidad acepta, para que un cliente sepa dónde conseguir el token.
        (En Spring Security 6 el encabezado era solo "Bearer".)

        Otra novedad de Spring Security 7 que apareció al escribir esta guía:
        el usuario autenticado tiene una authority EXTRA que nadie le dio,
        FACTOR_PASSWORD. Spring Security 7 registra CÓMO se autenticó cada
        quien (con contraseña, con un código de un solo uso...), para poder
        exigir autenticación de varios factores (MFA) en las reglas. La
        primera versión del emisor de práctica la copió al token como si
        fuera un permiso ("scope":"orders:read FACTOR_PASSWORD"); por eso
        TokenController la filtra. Moraleja: al pasar authorities a un token,
        elige explícitamente cuáles.

        Aquí hay dos cadenas: /auth/** con Basic (solo para obtener el token
        de práctica) y /api/** con JWT, sin sesión y sin CSRF (los tokens
        viajan en un encabezado, no en una cookie: basics/SecurityBasics,
        CAPA 8).
    */
    @Configuration
    public static class SecurityConfig {
        @Bean
        @Order(1)
        public SecurityFilterChain tokenChain(HttpSecurity http) throws Exception {
            return http.securityMatcher("/auth/**")
                    .authorizeHttpRequests(rules -> rules.anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults())
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .build();
        }

        @Bean
        @Order(2)
        public SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
            return http.securityMatcher("/api/**")
                    .authorizeHttpRequests(rules -> rules.anyRequest().authenticated())   // el detalle: por método
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .build();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
            return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }

        @Bean
        public UserDetailsService users(PasswordEncoder encoder) {   // en producción: el servidor de identidad
            return new InMemoryUserDetailsManager(
                    User.withUsername("ana").password(encoder.encode("ana-secreta-123"))
                            .authorities("orders:read").build(),
                    User.withUsername("luis").password(encoder.encode("luis-secreta-123"))
                            .authorities("orders:read").build(),
                    User.withUsername("admin").password(encoder.encode("admin-secreta-456"))
                            .authorities("orders:read", "orders:admin").build());
        }
    }

    /*
        =================================================================
        CAPA 6) SEGURIDAD POR MÉTODO: @PREAUTHORIZE Y @POSTAUTHORIZE
        =================================================================

        Las reglas por URL (basics/SecurityBasics, CAPA 4) dicen "quién
        entra a /api/orders". No pueden decir "Ana puede ver SUS pedidos,
        no los de Luis": eso depende del DATO. Para eso, reglas en los
        métodos del servicio (con @EnableMethodSecurity):

            @PreAuthorize("hasAuthority('SCOPE_orders:read')")
                antes de llamar: ¿tiene el permiso?
            @PostAuthorize("returnObject.owner() == authentication.name
                            or hasAuthority('SCOPE_orders:admin')")
                después de llamar: ¿el pedido que se devuelve es SUYO? (o es
                admin)

        Si no se cumple: AccessDeniedException -> 403. Las expresiones son
        SpEL (el lenguaje de expresiones de Spring): authentication es el
        usuario actual, returnObject lo que devolvió el método, y #id un
        parámetro.

        Medido en la prueba: Ana pide su pedido (1): 200; el de Luis (2): 403;
        la lista completa (solo admin): 403; el admin: 200 en todo.

        Dos cuidados:
        - es un PROXY (part1_core/.../container/IoCContainer, CAPA 8): una
          llamada desde la misma clase NO revisa nada;
        - @PostAuthorize revisa DESPUÉS de ejecutar: el método ya corrió. Para
          leer está bien; para algo que CAMBIA datos, la revisión va antes
          (@PreAuthorize con el dueño, o en la consulta: "where owner = :usuario").
          En listas grandes, filtrar en la CONSULTA (no traer los de otros y
          luego descartarlos).
    */
    public record CustomerOrder(long id, String owner, long totalCents) {
    }

    @Service
    public static class OrderService {
        private final Map<Long, CustomerOrder> orders = Map.of(
                1L, new CustomerOrder(1, "ana", 25_000),
                2L, new CustomerOrder(2, "luis", 9_900),
                3L, new CustomerOrder(3, "ana", 4_500));

        @PreAuthorize("hasAuthority('SCOPE_orders:read')")
        @PostAuthorize("returnObject.owner() == authentication.name or hasAuthority('SCOPE_orders:admin')")
        public CustomerOrder find(long id) {
            CustomerOrder order = orders.get(id);
            if (order == null) {
                throw new IllegalArgumentException("No existe el pedido " + id);
            }
            return order;
        }

        @PreAuthorize("hasAuthority('SCOPE_orders:admin')")
        public List<CustomerOrder> all() {
            return orders.values().stream().sorted((a, b) -> Long.compare(a.id(), b.id())).toList();
        }
    }

    @RestController
    public static class OrderController {
        private final OrderService orders;

        public OrderController(OrderService orders) {
            this.orders = orders;
        }

        @GetMapping("/api/orders/{id}")
        public CustomerOrder find(@PathVariable long id) {
            return orders.find(id);
        }

        @GetMapping("/api/orders")
        public List<CustomerOrder> all() {
            return orders.all();
        }
    }

    /*
        =================================================================
        CAPA 7) DE DÓNDE SALEN LOS TOKENS EN LA VIDA REAL
        =================================================================

        Tu API NO debería emitir tokens ni guardar contraseñas. Lo hace un
        SERVIDOR DE IDENTIDAD (un "authorization server" de OAuth2/OpenID
        Connect): Keycloak (libre, muy usado), Okta, Auth0, Microsoft Entra
        ID, el de tu nube, o Spring Authorization Server si lo construyes tú.
        Él maneja el login, las contraseñas, el doble factor, el "olvidé mi
        contraseña", y emite los tokens. Tus APIs solo los verifican (CAPA 5)
        con su llave pública.

        Cómo obtiene el token cada tipo de cliente (los "flujos" de OAuth2):
        - un USUARIO en una web o app móvil: "authorization code + PKCE" (el
          usuario hace login en la página del servidor de identidad, que
          redirige de vuelta con un código que la app cambia por tokens);
        - un SERVICIO llamando a otro, sin usuario: "client credentials" (el
          servicio se identifica con su propio id y secreto).

        El POST /auth/token de esta guía es un atajo de práctica (parecido al
        viejo flujo "password", que OAuth 2.1 ya no recomienda). Sirve para
        entender qué hay dentro de un token, no para producción.
    */

    /*
        =================================================================
        CAPA 8) ERRORES COMUNES
        =================================================================

        1) Tokens por HTTP sin cifrar; tokens de días o semanas (CAPAS 1 y 4).

        2) Datos sensibles dentro del JWT, creyendo que está cifrado
           (CAPA 2).

        3) HS256 con el mismo secreto en varias APIs; no verificar el emisor
           ni la audiencia (CAPA 3).

        4) Esperar poder "cerrar la sesión" de un access token (CAPA 4).

        5) Confundir scope:orders:read con SCOPE_orders:read en las reglas
           (CAPA 5).

        6) Revisar el dueño solo en el frontend; @PostAuthorize en métodos
           que cambian datos; llamadas desde la misma clase (CAPA 6).

        7) Escribir tu propio sistema de login y tokens en vez de usar un
           servidor de identidad (CAPA 7).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Tokens en vez de sesiones: el servidor no guarda nada; Bearer =
          quien lo tenga.
        - JWT = cabecera.contenido.firma en Base64URL: se LEE, no es secreto.
        - Firma RS256: privada para firmar (emisor), pública para verificar
          (APIs). Alterarlo -> 401 "Invalid signature".
        - Vida corta (5-15 min) + refresh token; vencido -> 401 "Jwt
          expired".
        - oauth2ResourceServer().jwt(): scope -> authorities SCOPE_...
        - @PreAuthorize / @PostAuthorize: reglas que dependen del dato (el
          dueño); son proxies.
        - Los tokens los emite un servidor de identidad; tu API los verifica.
    */
}
