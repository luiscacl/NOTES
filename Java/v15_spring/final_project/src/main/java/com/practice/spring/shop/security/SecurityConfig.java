package com.practice.spring.shop.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Dos cadenas de filtros (part5_security):
 *   1) POST /api/auth/token: usuario y contraseña (HTTP Basic) a cambio de un JWT;
 *   2) todo lo demás: el JWT en "Authorization: Bearer ...". Sin sesión y sin CSRF: es una API sin
 *      cookies (part5_security/.../basics/SecurityBasics, CAPA 7).
 * Las reglas por URL viven aquí; la regla "cada quien sus pedidos" vive en OrderService, porque
 * depende de los DATOS, no de la URL.
 */
@Configuration
class SecurityConfig {
    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    @Order(1)
    SecurityFilterChain tokenChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/api/auth/token")
                .authorizeHttpRequests(rules -> rules.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(rules -> rules
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/*").permitAll()
                        .requestMatchers("/api/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/ship").hasRole("ADMIN")
                        .requestMatchers("/api/orders/**").authenticated()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().denyAll())                     // lo que no se nombró, no existe
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(rolesFromToken())))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /** El claim "roles": ["ADMIN"] del token se vuelve la authority ROLE_ADMIN. */
    private static JwtAuthenticationConverter rolesFromToken() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();   // {bcrypt}...
    }

    /**
     * Las llaves que firman y verifican los tokens. Si no están configuradas y no se pidieron
     * llaves efímeras, la aplicación NO arranca: mejor fallar al desplegar que firmar con algo
     * inesperado.
     */
    @Bean
    KeyPair jwtKeys(JwtProperties properties) throws IOException, GeneralSecurityException {
        if (properties.publicKey() != null && properties.privateKey() != null) {
            try (InputStream publicPem = properties.publicKey().getInputStream();
                 InputStream privatePem = properties.privateKey().getInputStream()) {
                return new KeyPair(RsaKeyConverters.x509().convert(publicPem),
                        RsaKeyConverters.pkcs8().convert(privatePem));
            }
        }
        if (properties.ephemeralKeys()) {
            LOG.warn("Llaves JWT efímeras: solo para desarrollo. Cada arranque invalida los tokens anteriores, "
                    + "y con varias instancias cada una rechazaría los tokens de las otras");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }
        throw new IllegalStateException("Faltan las llaves JWT: configura shop.jwt.public-key y "
                + "shop.jwt.private-key (o shop.jwt.ephemeral-keys=true, solo en desarrollo)");
    }

    @Bean
    JwtEncoder jwtEncoder(KeyPair keys) {
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) keys.getPublic())
                .privateKey((RSAPrivateKey) keys.getPrivate())
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    /** Verifica la firma, la expiración Y el emisor: un token firmado para otro sistema no sirve aquí. */
    @Bean
    JwtDecoder jwtDecoder(KeyPair keys, JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }
}
