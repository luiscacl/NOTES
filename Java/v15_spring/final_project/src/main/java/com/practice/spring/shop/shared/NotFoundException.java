package com.practice.spring.shop.shared;

/** No existe, o no es tuyo: las dos se responden igual, 404 (README, "Decisiones"). */
public class NotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }
}
