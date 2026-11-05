package com.practice.spring.testing.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.practice.spring.testing.shop.ShopApp.OrderController;
import com.practice.spring.testing.shop.ShopApp.OrderPlaced;
import com.practice.spring.testing.shop.ShopApp.OrderService;
import com.practice.spring.testing.shop.ShopApp.PaymentDeclinedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * UnitAndSliceTests, CAPA 6: una prueba de UNA CAPA (slice). @WebMvcTest arranca solo Spring MVC
 * y este controller; el servicio se reemplaza por un mock con @MockitoBean.
 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final String VALID = """
            {"customerEmail":"ana@correo.com","tier":"GOLD",
             "lines":[{"sku":"CAF-001","quantity":2,"unitCents":18000}]}""";

    private static final String INVALID = """
            {"customerEmail":"no-es-correo","tier":"GOLD","lines":[]}""";

    @Autowired private MockMvcTester mvc;
    @Autowired private ApplicationContext context;
    @MockitoBean private OrderService service;              // un mock DENTRO del contenedor

    @Test
    void soloLaCapaWebSinRepositoriosNiBaseDeDatos() {
        assertThat(context.getBeanNamesForType(OrderController.class)).hasSize(1);
        assertThat(context.containsBean("dataSource")).isFalse();                     // ni JPA ni PostgreSQL
    }

    @Test
    void unPedidoValidoResponde201ConLoQueDevuelveElServicio() {
        when(service.placeOrder(any())).thenReturn(new OrderPlaced(7, 42_300, "pay-9"));
        assertThat(mvc.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.paymentId").isEqualTo("pay-9");
    }

    @Test
    void unPedidoInvalidoNiSiquieraLlegaAlServicio() {
        assertThat(mvc.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON).content(INVALID))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @Test
    void unPagoRechazadoSeTraduceA402() {
        when(service.placeOrder(any())).thenThrow(new PaymentDeclinedException("fondos insuficientes"));
        assertThat(mvc.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .hasStatus(HttpStatus.PAYMENT_REQUIRED)
                .bodyJson().extractingPath("$.detail").isEqualTo("Pago rechazado: fondos insuficientes");
    }
}
