package com.practice.spring.shop.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Lo que entra y sale por la API de pedidos. */
final class OrderDtos {

    private OrderDtos() {
    }

    record PlaceOrderRequest(
            @NotEmpty(message = "el pedido necesita al menos un renglón")
            @Size(max = 20, message = "máximo 20 renglones") List<@Valid @NotNull LineRequest> lines) {
    }

    record LineRequest(
            @NotNull(message = "es obligatorio") Long productId,
            @Min(value = 1, message = "mínimo 1") @Max(value = 100, message = "máximo 100") int quantity) {
    }

    record OrderSummary(long id, String status, Instant createdAt, long totalCents) {
        static OrderSummary from(CustomerOrder order) {
            return new OrderSummary(order.id(), order.status().name(), order.createdAt(), order.totalCents());
        }
    }

    record OrderResponse(long id, String customer, String status, Instant createdAt, long totalCents,
            List<LineResponse> lines) {
        static OrderResponse from(CustomerOrder order) {
            return new OrderResponse(order.id(), order.customer(), order.status().name(), order.createdAt(),
                    order.totalCents(), order.lines().stream().map(LineResponse::from).toList());
        }
    }

    record LineResponse(long productId, String productName, long unitPriceCents, int quantity, long subtotalCents) {
        static LineResponse from(OrderLine line) {
            return new LineResponse(line.productId(), line.productName(), line.unitPriceCents(), line.quantity(),
                    line.subtotalCents());
        }
    }
}
