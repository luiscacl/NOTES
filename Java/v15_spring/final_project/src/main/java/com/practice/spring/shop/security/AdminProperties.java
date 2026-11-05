package com.practice.spring.shop.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** shop.admin.*: la cuenta de administrador que se crea al arrancar. La contraseña viene de afuera. */
@ConfigurationProperties("shop.admin")
record AdminProperties(String username, String password) {
}
