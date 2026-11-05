package com.practice.spring.testing.shop;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las piezas de la aplicación que prueban las guías de part4_testing (UnitAndSliceTests e
 * IntegrationTests): reglas puras, un servicio con una dependencia externa y un reloj, un
 * controller REST. La clase principal está en ShopApplication y el repositorio en
 * ShopOrderRepository, cada uno en su archivo (UnitAndSliceTests, CAPA 8 explica por qué).
 */
public final class ShopApp {

    private ShopApp() {
    }

    // ---- Reglas puras: sin Spring, sin base de datos ----

    public enum Tier { REGULAR, GOLD }

    public record Line(String sku, int quantity, long unitCents) {
    }

    /** Las reglas de precio de la tienda. */
    public static final class Pricing {
        public static final long FREE_SHIPPING_FROM = 100_000;   // 1,000 pesos
        public static final long SHIPPING = 9_900;

        private Pricing() {
        }

        public static long subtotal(List<Line> lines) {
            return lines.stream().mapToLong(line -> line.quantity() * line.unitCents()).sum();
        }

        public static long total(List<Line> lines, Tier tier) {
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("Un pedido necesita al menos una línea");
            }
            long subtotal = subtotal(lines);
            long discounted = tier == Tier.GOLD ? subtotal - subtotal / 10 : subtotal;   // GOLD: 10%
            long shipping = discounted >= FREE_SHIPPING_FROM ? 0 : SHIPPING;
            return discounted + shipping;
        }
    }

    // ---- Persistencia ----

    public enum OrderStatus { PAID, REJECTED }

    @Entity(name = "ShopOrder")
    @Table(name = "shop_orders")
    public static class ShopOrder {
        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE)
        private Long id;

        @Column(nullable = false)
        private String customerEmail;

        private long totalCents;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 20)
        private OrderStatus status;

        @Column(nullable = false)
        private Instant createdAt;

        private String paymentId;

        protected ShopOrder() {
        }

        public ShopOrder(String customerEmail, long totalCents, OrderStatus status, Instant createdAt, String paymentId) {
            this.customerEmail = customerEmail;
            this.totalCents = totalCents;
            this.status = status;
            this.createdAt = createdAt;
            this.paymentId = paymentId;
        }

        public Long getId() {
            return id;
        }

        public String getCustomerEmail() {
            return customerEmail;
        }

        public long getTotalCents() {
            return totalCents;
        }

        public OrderStatus getStatus() {
            return status;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public String getPaymentId() {
            return paymentId;
        }
    }

    // ---- Un sistema externo (en producción, un cliente HTTP: part2_web/.../clients/HttpClients) ----

    public interface PaymentClient {
        /** Devuelve el id del cobro; lanza PaymentDeclinedException si el banco lo rechaza. */
        String charge(String customerEmail, long cents);
    }

    public static class PaymentDeclinedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public PaymentDeclinedException(String reason) {
            super("Pago rechazado: " + reason);
        }
    }

    @Component
    public static class LocalPaymentClient implements PaymentClient {   // para correr en local; las pruebas lo reemplazan
        @Override
        public String charge(String customerEmail, long cents) {
            return "local-" + cents;
        }
    }

    // ---- El caso de uso ----

    public record PlaceOrderRequest(@Email @NotNull String customerEmail, @NotNull Tier tier,
                                    @NotEmpty List<@Valid LineRequest> lines) {
    }

    public record LineRequest(@NotNull String sku, @Positive int quantity, @Positive long unitCents) {
    }

    public record OrderPlaced(long orderId, long totalCents, String paymentId) {
    }

    @Service
    public static class OrderService {
        private final ShopOrderRepository orders;
        private final PaymentClient payments;
        private final Clock clock;

        public OrderService(ShopOrderRepository orders, PaymentClient payments, Clock clock) {
            this.orders = orders;
            this.payments = payments;
            this.clock = clock;
        }

        // noRollbackFor: el pedido RECHAZADO debe quedar guardado aunque se relance la excepción.
        // Sin esto, el rollback lo borraba; la prueba unitaria con mocks no lo vio y la de integración
        // sí (IntegrationTests, CAPA 4).
        @Transactional(noRollbackFor = PaymentDeclinedException.class)
        public OrderPlaced placeOrder(PlaceOrderRequest request) {
            List<Line> lines = request.lines().stream()
                    .map(line -> new Line(line.sku(), line.quantity(), line.unitCents())).toList();
            long total = Pricing.total(lines, request.tier());
            try {
                String paymentId = payments.charge(request.customerEmail(), total);
                ShopOrder saved = orders.save(new ShopOrder(request.customerEmail(), total, OrderStatus.PAID,
                        clock.instant(), paymentId));
                return new OrderPlaced(saved.getId(), total, paymentId);
            } catch (PaymentDeclinedException declined) {
                orders.save(new ShopOrder(request.customerEmail(), total, OrderStatus.REJECTED, clock.instant(), null));
                throw declined;
            }
        }

        @Transactional(readOnly = true)
        public List<ShopOrder> history(String customerEmail) {
            return orders.findByCustomerEmailOrderByCreatedAtDesc(customerEmail);
        }
    }

    // ---- La API ----

    public record OrderView(long id, long totalCents, String status) {
    }

    @RestController
    @RequestMapping("/api/orders")
    public static class OrderController {
        private final OrderService service;

        public OrderController(OrderService service) {
            this.service = service;
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        public OrderPlaced place(@Valid @RequestBody PlaceOrderRequest request) {
            return service.placeOrder(request);
        }

        @GetMapping
        public List<OrderView> history(@RequestParam String email) {
            return service.history(email).stream()
                    .map(order -> new OrderView(order.getId(), order.getTotalCents(), order.getStatus().name()))
                    .toList();
        }

        @ExceptionHandler(PaymentDeclinedException.class)
        public ProblemDetail declined(PaymentDeclinedException error) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, error.getMessage());
        }
    }
}
