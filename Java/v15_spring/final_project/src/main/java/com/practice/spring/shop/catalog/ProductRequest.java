package com.practice.spring.shop.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Lo que el administrador manda para crear o cambiar un producto. */
public record ProductRequest(
        @NotBlank(message = "es obligatorio") @Size(max = 120, message = "máximo 120 caracteres") String name,
        @Positive(message = "debe ser mayor que cero") long priceCents,
        @PositiveOrZero(message = "no puede ser negativo") int stock) {
}
