package com.practice.spring.shop.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.practice.spring.shop.shared.BusinessRuleException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Las reglas del pedido, sin Spring ni base de datos: milisegundos (part4_testing/.../UnitAndSliceTests). */
class CustomerOrderTest {

    private static final Instant NOW = Instant.parse("2026-11-04T18:00:00Z");

    private static CustomerOrder twoKeyboardsAndAMouse() {
        return CustomerOrder.place("ana", List.of(
                new OrderLine(1, "Teclado", 89_900, 2),
                new OrderLine(2, "Ratón", 35_000, 1)), NOW);
    }

    @Test
    void elTotalEsLaSumaDeLosRenglones() {
        CustomerOrder order = twoKeyboardsAndAMouse();
        assertThat(order.totalCents()).isEqualTo(2 * 89_900 + 35_000);
        assertThat(order.status()).isEqualTo(CustomerOrder.Status.PLACED);
        assertThat(order.createdAt()).isEqualTo(NOW);
    }

    @Test
    void unPedidoSinRenglonesNoExiste() {
        assertThatThrownBy(() -> CustomerOrder.place("ana", List.of(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void soloUnPedidoColocadoSeCancelaOSeEnvia() {
        CustomerOrder shipped = twoKeyboardsAndAMouse();
        shipped.ship();
        assertThatThrownBy(shipped::cancel)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("No se puede cancelar un pedido SHIPPED")
                .extracting(e -> ((BusinessRuleException) e).code()).isEqualTo("ORDER_NOT_CANCELLABLE");

        CustomerOrder cancelled = twoKeyboardsAndAMouse();
        cancelled.cancel();
        assertThatThrownBy(cancelled::ship).hasMessage("No se puede enviar un pedido CANCELLED");
        assertThatThrownBy(cancelled::cancel).hasMessage("No se puede cancelar un pedido CANCELLED");
    }

    @Test
    void unTotalQueSeDesbordaFallaEnVezDeMentir() {
        assertThatThrownBy(() -> CustomerOrder.place("ana",
                List.of(new OrderLine(1, "Caro", Long.MAX_VALUE / 2, 3)), NOW))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void losRenglonesNoSeCambianPorFuera() {
        CustomerOrder order = twoKeyboardsAndAMouse();
        assertThatThrownBy(() -> order.lines().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
