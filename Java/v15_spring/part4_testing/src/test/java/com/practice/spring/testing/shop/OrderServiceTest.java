package com.practice.spring.testing.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.practice.spring.testing.shop.ShopApp.LineRequest;
import com.practice.spring.testing.shop.ShopApp.OrderPlaced;
import com.practice.spring.testing.shop.ShopApp.OrderService;
import com.practice.spring.testing.shop.ShopApp.OrderStatus;
import com.practice.spring.testing.shop.ShopApp.PaymentClient;
import com.practice.spring.testing.shop.ShopApp.PaymentDeclinedException;
import com.practice.spring.testing.shop.ShopApp.PlaceOrderRequest;
import com.practice.spring.testing.shop.ShopApp.ShopOrder;
import com.practice.spring.testing.shop.ShopApp.Tier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * UnitAndSliceTests, CAPAS 4 y 5: el servicio probado SIN Spring, con sus dependencias
 * reemplazadas: mocks de Mockito para el repositorio y el cliente de pagos, y un reloj fijo.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");

    @Mock private ShopOrderRepository orders;               // Mockito crea una implementación falsa
    @Mock private PaymentClient payments;
    @Captor private ArgumentCaptor<ShopOrder> saved;        // para revisar QUÉ se guardó

    private OrderService service;

    @BeforeEach
    void createService() {
        service = new OrderService(orders, payments, Clock.fixed(NOW, ZoneOffset.UTC));   // con new: una clase normal
    }

    private static PlaceOrderRequest order(long unitCents) {
        return new PlaceOrderRequest("ana@correo.com", Tier.REGULAR, List.of(new LineRequest("CAF-001", 1, unitCents)));
    }

    /** "La base de datos" asigna un id al guardar. */
    private void saveAssignsId(long id) {
        when(orders.save(any())).thenAnswer(call -> {
            ShopOrder order = call.getArgument(0);
            ReflectionTestUtils.setField(order, "id", id);
            return order;
        });
    }

    @Test
    void unPagoAprobadoGuardaElPedidoPagadoConLaHoraDelReloj() {
        when(payments.charge("ana@correo.com", 59_900)).thenReturn("pay-123");          // stub: qué responder
        saveAssignsId(7);

        OrderPlaced placed = service.placeOrder(order(50_000));

        assertThat(placed).isEqualTo(new OrderPlaced(7, 59_900, "pay-123"));
        verify(orders).save(saved.capture());                                           // se guardó una vez...
        assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.PAID);           // ...y así
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);                     // la hora del reloj fijo
    }

    @Test
    void unPagoRechazadoGuardaElPedidoRechazadoYRelanzaElError() {
        when(payments.charge(eq("ana@correo.com"), anyLong()))
                .thenThrow(new PaymentDeclinedException("fondos insuficientes"));

        assertThatThrownBy(() -> service.placeOrder(order(50_000)))
                .isInstanceOf(PaymentDeclinedException.class);

        verify(orders).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(saved.getValue().getPaymentId()).isNull();
        // OJO: esta prueba pasaba también cuando el rollback BORRABA este pedido de la base de datos.
        // Un mock no tiene transacciones; lo encontró la prueba de integración (IntegrationTests, CAPA 4).
    }

    @Test
    void sinLineasNiSiquieraIntentaCobrar() {
        PlaceOrderRequest empty = new PlaceOrderRequest("ana@correo.com", Tier.REGULAR, List.of());
        assertThatThrownBy(() -> service.placeOrder(empty)).isInstanceOf(IllegalArgumentException.class);
        verify(payments, never()).charge(any(), anyLong());                             // nunca llamó al banco
    }

    @Test
    void conUnFalsoEscritoAManoEnVezDeUnMock() {
        PaymentClient alwaysApproves = (email, cents) -> "fake-" + cents;               // un "fake": una lambda
        saveAssignsId(1);
        OrderService withFake = new OrderService(orders, alwaysApproves, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(withFake.placeOrder(order(200_000)).paymentId()).isEqualTo("fake-200000");   // sin envío: >= 1,000
    }
}
