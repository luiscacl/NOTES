package com.practice.spring.testing.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.practice.spring.testing.shop.ShopApp.Line;
import com.practice.spring.testing.shop.ShopApp.Pricing;
import com.practice.spring.testing.shop.ShopApp.Tier;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * UnitAndSliceTests, CAPAS 2 y 3: una prueba UNITARIA pura. Sin Spring, sin base de datos:
 * se crea lo que se prueba y se llama. Corre en milisegundos.
 */
@DisplayName("Reglas de precio")
class PricingTest {

    @Test
    @DisplayName("suma cantidad por precio de cada línea")
    void subtotal() {
        // Arrange: preparar los datos
        List<Line> lines = List.of(new Line("CAF-001", 2, 18_000), new Line("TAZ-001", 1, 12_000));
        // Act: ejecutar lo que se prueba
        long subtotal = Pricing.subtotal(lines);
        // Assert: comprobar el resultado
        assertThat(subtotal).isEqualTo(48_000);
    }

    @Nested
    @DisplayName("el total")
    class Total {

        @ParameterizedTest(name = "{0} de {1} centavos -> {2}")
        @CsvSource({
                "REGULAR, 50000,  59900",      // menos de 1,000 pesos: paga envío
                "REGULAR, 100000, 100000",     // justo en el límite: envío gratis
                "GOLD,    50000,  54900",      // 10% de descuento, y paga envío
                "GOLD,    110000, 108900",     // ¡el descuento lo deja en 990 y VUELVE a pagar envío!
                                               // Un caso de borde que la prueba encontró: pregunta para el negocio
        })
        void segunNivelYMonto(Tier tier, long unitCents, long expected) {
            long total = Pricing.total(List.of(new Line("X", 1, unitCents)), tier);
            assertThat(total).isEqualTo(expected);
        }

        @Test
        @DisplayName("sin líneas, es un error")
        void sinLineas() {
            assertThatThrownBy(() -> Pricing.total(List.of(), Tier.REGULAR))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Un pedido necesita al menos una línea");
        }
    }
}
