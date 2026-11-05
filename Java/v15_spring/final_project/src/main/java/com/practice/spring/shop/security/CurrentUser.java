package com.practice.spring.shop.security;

import org.springframework.security.core.Authentication;

/** Quién hace la petición, en términos de la tienda: los servicios no dependen de Spring Security. */
public record CurrentUser(String username, boolean admin) {

    public static CurrentUser from(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return new CurrentUser(authentication.getName(), admin);
    }
}
