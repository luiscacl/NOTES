package com.practice.spring.shop.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Un usuario de la tienda: cliente o administrador. */
@Entity
@Table(name = "users")
class AppUser {

    enum Role { CUSTOMER, ADMIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)                 // el nombre, nunca el número (v11_patterns/.../State)
    private Role role;

    protected AppUser() {                        // para JPA
    }

    AppUser(String username, String passwordHash, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    String username() {
        return username;
    }

    String passwordHash() {
        return passwordHash;
    }

    Role role() {
        return role;
    }
}
