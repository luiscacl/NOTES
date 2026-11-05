package com.practice.spring.shop.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * shop.jwt.*: quién emite los tokens, cuánto duran y con qué llaves se firman.
 * Las llaves son rutas a archivos PEM (file:/run/secrets/...), nunca el contenido en el repositorio.
 */
@ConfigurationProperties("shop.jwt")
record JwtProperties(String issuer, Duration ttl, Resource publicKey, Resource privateKey, boolean ephemeralKeys) {
}
