package com.practice.spring.security.basics;

import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/*
    =====================================================================
    SPRING SECURITY: QUIÉN ERES Y QUÉ PUEDES HACER
    =====================================================================

    Ruta de spring/: guía 15  (part5_security)
    Anterior:  part4_testing/IntegrationTests
    Siguiente: part5_security/.../jwt/JwtAndMethodSecurity

    Cómo leer esta guía:
    - Está hecha para LEERSE de arriba hacia abajo.
    - Las comprobaciones son pruebas: SecurityBasicsTest, una por capa, con
      MockMvc y las utilidades de spring-security-test. Desde v15_spring/:
          ./mvnw -pl part5_security test -Dtest=SecurityBasicsTest
    - Lo de "sin configurar nada" (CAPA 1) y los tiempos de BCrypt (CAPA 5)
      se midieron con una aplicación aparte, en esta máquina.

    Lo que ya se vio: los filtros y /error (part2_web/.../errors/
    ErrorHandling, CAPA 8), ThreadLocal (v13_concurrency/part1_threads/
    ThreadBasics, CAPA 8), secretos (part1_core/.../config/
    ExternalConfiguration, CAPA 8), DTOs y la asignación masiva
    (v11_patterns/part4_modern/DTO).

    Importancia: MUY ALTA. Una API sin seguridad bien puesta es una
    filtración esperando a pasar. Y la seguridad de Spring es lo que más
    confunde a quien empieza: esta guía va despacio.

    Índice:
        CAPA 1)  Lo que pasa al agregar el starter
        CAPA 2)  Autenticación y autorización: 401 y 403
        CAPA 3)  La cadena de filtros
        CAPA 4)  Configurar: un SecurityFilterChain
        CAPA 5)  Usuarios y contraseñas: BCrypt
        CAPA 6)  Roles y authorities
        CAPA 7)  Quién es el usuario actual
        CAPA 8)  CSRF: cuándo sí y cuándo no
        CAPA 9)  CORS: el navegador pregunta antes
        CAPA 10) Probar la seguridad
        CAPA 11) Errores comunes
        RESUMEN FINAL
*/
public class SecurityBasics {

    /*
        =================================================================
        CAPA 1) LO QUE PASA AL AGREGAR EL STARTER
        =================================================================

        Con solo agregar spring-boot-starter-security, sin configurar nada
        (medido con una aplicación de un endpoint):

        - TODO queda bloqueado: GET /hello responde 401 Unauthorized, con el
          encabezado WWW-Authenticate: Basic realm="Realm", charset="UTF-8";
        - Boot crea UN usuario "user" con una contraseña aleatoria, y la
          imprime en el log:
              Using generated security password: 6dedb517-...
              This generated password is for development use only. Your
              security configuration must be updated before running your
              application in production.
        - se pueden autenticar con HTTP Basic y con un formulario de login
          generado;
        - las respuestas traen encabezados de seguridad (CAPA 3):
          X-Content-Type-Options, X-Frame-Options, Cache-Control...
        - y un POST con la contraseña CORRECTA responde... 401 (CAPA 8).

        "Seguro por defecto": mejor un servicio que no responde que uno
        abierto por accidente. Pero nada de eso sirve para producción: hay
        que decir quién puede hacer qué (CAPA 4).
    */
    @SpringBootApplication
    public static class StoreApplication {
    }

    /*
        =================================================================
        CAPA 2) AUTENTICACIÓN Y AUTORIZACIÓN: 401 Y 403
        =================================================================

        Dos preguntas distintas, que se confunden todo el tiempo:

            AUTENTICACIÓN  ¿quién eres?  (usuario y contraseña, un token)
                           Si no lo sé: 401 Unauthorized ("identifícate").
            AUTORIZACIÓN   ¿puedes hacer ESTO?  (eres admin, es tu pedido)
                           Si sé quién eres pero no puedes: 403 Forbidden.

        (Sí: el código 401 se llama "Unauthorized" pero significa "no
        autenticado". Es un nombre histórico mal puesto.)

        En la prueba: sin credenciales a /api/me -> 401; Ana (usuaria normal)
        creando productos -> 403; el admin -> 201.
    */

    /*
        =================================================================
        CAPA 3) LA CADENA DE FILTROS
        =================================================================

        Spring Security NO vive en tus controllers: es una cadena de FILTROS
        de servlet que corre ANTES de que la petición llegue a Spring MVC
        (rest/RestControllers, CAPA 1, paso 1 y medio). Medido, la cadena por
        defecto tiene 16 filtros, en este orden:

            DisableEncodeUrlFilter, WebAsyncManagerIntegrationFilter,
            SecurityContextHolderFilter, HeaderWriterFilter, CsrfFilter,
            LogoutFilter, UsernamePasswordAuthenticationFilter,
            DefaultResourcesFilter, DefaultLoginPageGeneratingFilter,
            DefaultLogoutPageGeneratingFilter, BasicAuthenticationFilter,
            RequestCacheAwareFilter, SecurityContextHolderAwareRequestFilter,
            AnonymousAuthenticationFilter, ExceptionTranslationFilter,
            AuthorizationFilter

        Los que importan entender:
        - HeaderWriterFilter: los encabezados de seguridad;
        - CsrfFilter: CAPA 8;
        - BasicAuthenticationFilter (o el de JWT, en la siguiente guía): lee
          las credenciales, las verifica, y si son buenas guarda QUIÉN ERES
          en el SecurityContext (CAPA 7);
        - ExceptionTranslationFilter: convierte "no autenticado" en 401 y
          "no autorizado" en 403;
        - AuthorizationFilter, el ÚLTIMO: decide si pasas, con las reglas de
          la CAPA 4.

        Consecuencia práctica: un error de seguridad NO pasa por tus
        @RestControllerAdvice (ocurre antes de los controllers). Para darle
        formato se configuran el AuthenticationEntryPoint (401) y el
        AccessDeniedHandler (403) de la cadena.

        Para ver qué filtro hizo qué con una petición:
        logging.level.org.springframework.security=TRACE.
    */

    /*
        =================================================================
        CAPA 4) CONFIGURAR: UN SECURITYFILTERCHAIN
        =================================================================

        Se declara un bean SecurityFilterChain con HttpSecurity (cada
        sección con una lambda). Las reglas se leen DE ARRIBA ABAJO y gana la
        PRIMERA que coincide: de lo más específico a lo más general, y al
        final "todo lo demás".

        Aquí hay DOS cadenas, cada una para un tipo de cliente:
        - apiChain (@Order 1, rutas /api/**): una API REST para apps y otros
          servicios. Sin sesión (cada petición trae sus credenciales), sin
          CSRF (CAPA 8), con CORS (CAPA 9);
        - browserChain (@Order 2, rutas /web/**): páginas para el navegador,
          con sesión, y por eso CON CSRF.

        Con securityMatcher, cada cadena atiende solo sus rutas; la primera
        que coincide, por @Order, se queda con la petición.

        (Si ves código con "extends WebSecurityConfigurerAdapter" y
        configure(HttpSecurity), es la forma vieja: se eliminó en Spring
        Security 6.)
    */
    @Configuration
    public static class SecurityConfig {

        @Bean
        @Order(1)
        public SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
            return http
                    .securityMatcher("/api/**")                                        // esta cadena: solo /api/**
                    .authorizeHttpRequests(rules -> rules
                            .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()   // catálogo: público
                            .requestMatchers("/api/admin/**").hasRole("ADMIN")                 // admin: solo ADMIN
                            .anyRequest().authenticated())                                      // lo demás: con usuario
                    .httpBasic(Customizer.withDefaults())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .csrf(csrf -> csrf.disable())                                      // API sin cookies: CAPA 8
                    .cors(Customizer.withDefaults())                                   // usa el bean de la CAPA 9
                    .build();
        }

        @Bean
        @Order(2)
        public SecurityFilterChain browserChain(HttpSecurity http) throws Exception {
            return http
                    .securityMatcher("/web/**")
                    .authorizeHttpRequests(rules -> rules.anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults())
                    .build();                                                          // CSRF activado (por defecto)
        }

        /*
            =============================================================
            CAPA 5) USUARIOS Y CONTRASEÑAS: BCRYPT
            =============================================================

            ¿De dónde salen los usuarios? De un UserDetailsService: dado un
            nombre, devuelve el usuario con su contraseña ENCRIPTADA y sus
            permisos. Aquí, en memoria (InMemoryUserDetailsManager); en una
            aplicación real, uno que busca en tu tabla de usuarios con un
            repositorio (part3_data). O, más común hoy, NO guardas
            contraseñas: delegas en un proveedor de identidad (Keycloak,
            Auth0, el de tu nube) que emite tokens (siguiente guía).

            Si guardas contraseñas, NUNCA en texto plano, y NUNCA con un hash
            rápido (MD5, SHA-256): una tarjeta de video prueba miles de
            millones de SHA-256 por segundo, y una base de datos robada se
            descifra en horas. Se usa un algoritmo LENTO A PROPÓSITO y con
            SAL (un valor aleatorio distinto por contraseña): BCrypt (o
            Argon2, scrypt). Medido en esta máquina:

                BCrypt fuerza 10 (la de por defecto)   ~145 ms por contraseña
                BCrypt fuerza 12                       ~581 ms

            Cada +1 de fuerza duplica el tiempo. 145 ms es nada para un login,
            y una eternidad para quien quiere probar miles de millones.

            PasswordEncoderFactories.createDelegatingPasswordEncoder() guarda
            el algoritmo como PREFIJO: {bcrypt}$2a$10$... Así, si mañana
            cambias de algoritmo, las contraseñas viejas se siguen
            verificando con el suyo. La prueba comprueba el prefijo, que dos
            encriptaciones de la misma contraseña son DISTINTAS (la sal), y
            que las dos se verifican.

            (Las contraseñas de este ejemplo están en el código solo porque
            es un ejemplo. En una aplicación, los usuarios están en una base
            de datos o en un proveedor de identidad: config/
            ExternalConfiguration, CAPA 8.)
        */
        @Bean
        public PasswordEncoder passwordEncoder() {
            return PasswordEncoderFactories.createDelegatingPasswordEncoder();   // {bcrypt} por defecto
        }

        @Bean
        public UserDetailsService users(PasswordEncoder encoder) {
            UserDetails ana = User.withUsername("ana")
                    .password(encoder.encode("ana-secreta-123"))   // se guarda ENCRIPTADA
                    .roles("USER")
                    .build();
            UserDetails admin = User.withUsername("admin")
                    .password(encoder.encode("admin-secreta-456"))
                    .roles("USER", "ADMIN")
                    .build();
            return new InMemoryUserDetailsManager(ana, admin);
        }

        /*
            =============================================================
            CAPA 9) CORS: EL NAVEGADOR PREGUNTA ANTES
            =============================================================

            El NAVEGADOR (no el servidor, no curl, no otra aplicación)
            aplica la "política del mismo origen": una página cargada desde
            https://tienda.example no puede leer las respuestas de
            https://api.tienda.example (otro origen) salvo que la API lo
            permita. Es lo que protege a tu banco de que cualquier página
            que visites le haga peticiones con tu sesión.

            Para peticiones "no simples" (con Authorization, JSON, PUT,
            DELETE), el navegador primero manda un OPTIONS (el "preflight")
            preguntando "¿me dejas?", y la API responde con encabezados
            Access-Control-Allow-*. Si no los manda, el navegador bloquea la
            respuesta (y tu frontend ve un error de CORS que no dice mucho).

            Se configura con un bean CorsConfigurationSource y .cors() en la
            cadena (Spring Security lo atiende ANTES de pedir credenciales:
            el preflight no lleva usuario). Lista EXACTA de orígenes
            permitidos; nunca "*" junto con credenciales. La prueba: el
            preflight desde https://tienda.example se permite; desde
            https://malo.example, 403.

            CORS no es seguridad del SERVIDOR: curl o un atacante con un
            script lo ignoran. Protege a los USUARIOS en el navegador; la
            autenticación y la autorización siguen haciendo falta.
        */
        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(List.of("https://tienda.example"));              // exactos, nunca "*"
            config.setAllowedMethods(List.of("GET", "POST"));
            config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/api/**", config);
            return source;
        }
    }

    /*
        =================================================================
        CAPA 6) ROLES Y AUTHORITIES
        =================================================================

        Cada usuario autenticado tiene una lista de AUTHORITIES (permisos):
        textos como "ROLE_ADMIN" o "orders:write". Un ROL es solo una
        authority con el prefijo ROLE_:

            .roles("ADMIN")                 guarda la authority "ROLE_ADMIN"
            .hasRole("ADMIN")               revisa la authority "ROLE_ADMIN"
            .hasAuthority("ROLE_ADMIN")     lo mismo, escrito completo
            .hasAuthority("orders:write")   un permiso fino, sin prefijo

        La confusión clásica: hasRole("ROLE_ADMIN") busca "ROLE_ROLE_ADMIN"
        y nunca coincide. La prueba muestra las authorities reales de Ana.

        Roles para grupos grandes (USER, ADMIN); authorities finas
        (orders:read, orders:write) cuando hay muchos permisos distintos. Con
        tokens JWT, los permisos suelen llegar como "scopes" (siguiente guía).
    */

    /*
        =================================================================
        CAPA 7) QUIÉN ES EL USUARIO ACTUAL
        =================================================================

        Cuando el filtro de autenticación (CAPA 3) acepta las credenciales,
        guarda el resultado (un Authentication: nombre, authorities) en el
        SecurityContext, que vive en un THREADLOCAL (v13_concurrency/
        part1_threads/ThreadBasics, CAPA 8): cualquier código en el MISMO
        HILO de esa petición puede preguntarlo.

        - En un controller: un parámetro @AuthenticationPrincipal UserDetails
          (lo recomendado: explícito y fácil de probar);
        - en cualquier lado: SecurityContextHolder.getContext()
          .getAuthentication() (lo hace MeController también, y coincide).

        Y por ser un ThreadLocal: si dentro de la petición mandas trabajo a
        OTRO hilo (@Async, un executor, un CompletableFuture), ese hilo NO
        sabe quién es el usuario, salvo que se propague (Spring Security trae
        envoltorios para executors: DelegatingSecurityContextExecutor). Al
        terminar la petición, el filtro lo limpia: los hilos del pool no
        heredan al usuario anterior.

        Nunca confíes en un id de usuario que llega en el CUERPO de la
        petición ("userId": 7) para decidir de quién son los datos: se toma
        del usuario autenticado. Si no, cualquiera pide los pedidos de otro
        (y el DTO de entrada no debe tener ese campo: v11_patterns/
        part4_modern/DTO, CAPA 4).
    */
    @RestController
    public static class MeController {
        @GetMapping("/api/me")
        public Map<String, Object> me(@AuthenticationPrincipal UserDetails user) {
            String fromContext = SecurityContextHolder.getContext().getAuthentication().getName();   // el ThreadLocal
            return Map.of(
                    "username", user.getUsername(),
                    "authorities", user.getAuthorities().stream().map(GrantedAuthority::getAuthority).sorted().toList(),
                    "sameAsContext", fromContext.equals(user.getUsername()));
        }
    }

    public record ProductRequest(String sku, String name) {
    }

    @RestController
    public static class ProductController {
        @GetMapping("/api/products")
        public List<String> list() {
            return List.of("CAF-001", "TAZ-001");
        }

        @PostMapping("/api/admin/products")
        @ResponseStatus(HttpStatus.CREATED)
        public ProductRequest create(@RequestBody ProductRequest request) {
            return request;
        }
    }

    /*
        =================================================================
        CAPA 8) CSRF: CUÁNDO SÍ Y CUÁNDO NO
        =================================================================

        CSRF (Cross-Site Request Forgery): tú tienes una sesión abierta en
        tu banco (una COOKIE que el navegador manda sola en cada petición a
        ese sitio). Visitas una página maliciosa que tiene un formulario
        oculto que hace POST a tu banco: el navegador manda la cookie, y el
        banco ve una petición "tuya".

        La defensa: un TOKEN CSRF que el servidor pone en sus páginas y que
        cada POST/PUT/DELETE debe traer. La página maliciosa no lo puede leer
        (por la política del mismo origen), así que su POST falla.

        Spring Security lo activa POR DEFECTO, y por eso (medido en la CAPA
        1) un POST con la contraseña CORRECTA y sin token es RECHAZADO. Con
        el servidor real y la configuración por defecto, el rechazo llegó
        como 401, no como 403, y parece un problema de contraseña (lo más
        probable: el error se reenvía a la página /error, que en esa
        configuración también exige usuario). Con MockMvc, que no hace ese
        reenvío, se ve el 403 directo. La prueba lo comprueba en
        browserChain: sin token, 403; con token (.with(csrf())), 200.

        ¿Cuándo desactivarlo? CSRF ataca a las credenciales que el
        NAVEGADOR manda SOLO (cookies). Una API que recibe las credenciales
        en un encabezado (Authorization: Basic o Bearer), que el navegador
        nunca agrega solo, no es vulnerable: por eso apiChain lo desactiva.
        Si tu API usa cookies de sesión (un frontend en el mismo dominio),
        CSRF se queda ACTIVADO.
    */
    @RestController
    public static class ProfileFormController {
        @PostMapping("/web/profile")
        public String update() {
            return "perfil actualizado";
        }
    }

    /*
        =================================================================
        CAPA 10) PROBAR LA SEGURIDAD
        =================================================================

        spring-security-test (con su starter de pruebas) trae:
        - con MockMvc: .with(httpBasic("ana", "...")) para mandar
          credenciales, .with(csrf()) para el token, .with(user("x").roles(..))
          para un usuario sin contraseña;
        - @WithMockUser(roles = "ADMIN") en el método de prueba: corre como
          ese usuario, sin pasar por el login.

        Qué probar SIEMPRE: que lo protegido responde 401 sin credenciales,
        403 con el rol equivocado, y funciona con el correcto. Las reglas de
        seguridad son configuración, y la configuración se rompe en silencio
        (una regla en el orden equivocado abre todo). Una prueba por regla
        importante.
    */

    /*
        =================================================================
        CAPA 11) ERRORES COMUNES
        =================================================================

        1) Ir a producción con el usuario y la contraseña generados (CAPA 1).

        2) Confundir 401 y 403 (CAPA 2).

        3) Esperar que @RestControllerAdvice maneje los errores de seguridad
           (CAPA 3).

        4) Reglas en el orden equivocado (anyRequest().permitAll() antes de
           las específicas); copiar WebSecurityConfigurerAdapter (CAPA 4).

        5) Contraseñas en texto plano o con SHA-256; BCrypt con fuerza 4
           "porque las pruebas son lentas" en producción (CAPA 5).

        6) hasRole("ROLE_ADMIN") (CAPA 6).

        7) Tomar el id del usuario del cuerpo de la petición; perder el
           usuario al cambiar de hilo (CAPA 7).

        8) Desactivar CSRF en una aplicación que usa cookies de sesión
           (CAPA 8).

        9) CORS con "*" y credenciales; creer que CORS protege la API de curl
           (CAPA 9).

        10) Reglas de seguridad sin pruebas (CAPA 10).
    */

    /*
        =================================================================
        RESUMEN FINAL
        =================================================================

        - Con el starter, todo queda cerrado por defecto (401), con un
          usuario temporal y encabezados de seguridad.
        - Autenticación (¿quién eres? 401) vs autorización (¿puedes? 403).
        - Es una cadena de filtros antes de MVC (16 por defecto); el último
          decide.
        - SecurityFilterChain con reglas de lo específico a lo general; una
          cadena por tipo de cliente (API sin estado / navegador con sesión).
        - Contraseñas con BCrypt (lento a propósito: ~145 ms) y prefijo
          {bcrypt}; mejor aún, un proveedor de identidad.
        - Roles = authorities con ROLE_.
        - Usuario actual: @AuthenticationPrincipal (ThreadLocal por debajo).
        - CSRF: sí con cookies, no con tokens en encabezados.
        - CORS: lo aplica el navegador; orígenes exactos.
        - Probar 401, 403 y el caso permitido de cada regla.
    */
}
