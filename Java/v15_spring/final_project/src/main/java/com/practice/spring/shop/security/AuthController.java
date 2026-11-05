package com.practice.spring.shop.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro y emisión de tokens. En una empresa grande esto lo hace un servidor de identidad
 * (Keycloak, el de tu nube) y la API solo verifica tokens (part5_security/.../jwt/JwtAndMethodSecurity,
 * CAPA 8); aquí la tienda emite los suyos para que el proyecto esté completo.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    /**
     * BCrypt solo admite 72 BYTES, y Spring Security rechaza más con IllegalArgumentException (que aquí
     * sería un 500). El límite de @Size cuenta CARACTERES: una ñ o un acento ocupan 2 bytes en UTF-8,
     * así que 64 caracteres pueden pasar de 72 bytes. Por eso se revisan también los bytes.
     */
    record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[a-z0-9._-]+",
                    message = "solo minúsculas, números, punto, guion y guion bajo") String username,
            @NotBlank @Size(min = 12, max = 64, message = "debe tener entre 12 y 64 caracteres") String password) {

        @AssertTrue(message = "es demasiado larga: con acentos o ñ no puede pasar de 72 bytes")
        boolean isPasswordWithinBcryptLimit() {
            return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
        }
    }

    private final UserAccounts accounts;
    private final JwtEncoder encoder;
    private final JwtProperties jwt;
    private final Clock clock;

    AuthController(UserAccounts accounts, JwtEncoder encoder, JwtProperties jwt, Clock clock) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.jwt = jwt;
        this.clock = clock;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, String> register(@Valid @RequestBody RegisterRequest request) {
        accounts.register(request.username(), request.password());
        return Map.of("username", request.username());
    }

    /** Llega aquí ya autenticado con HTTP Basic (la cadena 1 de SecurityConfig). */
    @PostMapping("/token")
    Map<String, Object> token(Authentication authentication) {
        Instant now = clock.instant();
        List<String> roles = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))            // no FACTOR_PASSWORD ni otras internas
                .map(a -> a.substring("ROLE_".length()))
                .toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwt.issuer())
                .subject(authentication.getName())
                .issuedAt(now)
                .expiresAt(now.plus(jwt.ttl()))
                .claim("roles", roles)
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return Map.of("access_token", token, "token_type", "Bearer", "expires_in", jwt.ttl().toSeconds());
    }
}
