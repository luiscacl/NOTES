package com.practice.spring.shop.shared;

/**
 * Una regla del negocio impide la operación: sin existencias, pedido que ya no se puede cancelar...
 * Se responde 409 con un {@code code} estable, para que el cliente decida qué hacer sin leer el texto.
 */
public class BusinessRuleException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
