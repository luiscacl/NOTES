package com.practice.spring.shop.security;

import com.practice.spring.shop.security.AppUser.Role;
import com.practice.spring.shop.shared.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las cuentas: registrar clientes, crear el administrador al arrancar, y darle a Spring Security
 * los usuarios de la base (UserDetailsService: lo usa el login con usuario y contraseña).
 */
@Service
class UserAccounts implements UserDetailsService, ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(UserAccounts.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AdminProperties admin;

    UserAccounts(AppUserRepository users, PasswordEncoder encoder, AdminProperties admin) {
        this.users = users;
        this.encoder = encoder;
        this.admin = admin;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
        return User.withUsername(user.username())
                .password(user.passwordHash())
                .roles(user.role().name())           // ROLE_CUSTOMER o ROLE_ADMIN
                .build();
    }

    @Transactional
    public void register(String username, String rawPassword) {
        if (users.existsByUsername(username)) {
            throw usernameTaken(username);
        }
        try {
            users.saveAndFlush(new AppUser(username, encoder.encode(rawPassword), Role.CUSTOMER));
        } catch (DataIntegrityViolationException e) {       // dos registros iguales al mismo tiempo:
            throw usernameTaken(username);                  // el UNIQUE de la tabla decide
        }
    }

    private static BusinessRuleException usernameTaken(String username) {
        return new BusinessRuleException("USERNAME_TAKEN", "El usuario '" + username + "' ya existe");
    }

    /** Al arrancar: crea el administrador si hay contraseña configurada y todavía no existe. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (admin.password() == null || admin.password().isBlank()) {
            LOG.warn("Sin SHOP_ADMIN_PASSWORD: no se crea el administrador");
            return;
        }
        if (!users.existsByUsername(admin.username())) {
            users.save(new AppUser(admin.username(), encoder.encode(admin.password()), Role.ADMIN));
            LOG.info("Administrador '{}' creado", admin.username());
        }
    }
}
