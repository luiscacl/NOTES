package com.practice.spring.shop.orders;

import com.practice.spring.shop.orders.OrderDtos.OrderResponse;
import com.practice.spring.shop.orders.OrderDtos.OrderSummary;
import com.practice.spring.shop.orders.OrderDtos.PlaceOrderRequest;
import com.practice.spring.shop.security.CurrentUser;
import com.practice.spring.shop.shared.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Los pedidos por HTTP: traduce la petición y el usuario, y delega TODO a OrderService. */
@RestController
@RequestMapping("/api/orders")
class OrderController {

    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    ResponseEntity<OrderResponse> place(Authentication auth, @Valid @RequestBody PlaceOrderRequest request) {
        OrderResponse placed = orders.place(CurrentUser.from(auth), request);
        return ResponseEntity.created(URI.create("/api/orders/" + placed.id())).body(placed);
    }

    @GetMapping
    PageResponse<OrderSummary> list(Authentication auth,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orders.list(CurrentUser.from(auth), pageable);
    }

    @GetMapping("/{id}")
    OrderResponse find(Authentication auth, @PathVariable long id) {
        return orders.find(CurrentUser.from(auth), id);
    }

    @PostMapping("/{id}/cancel")
    OrderResponse cancel(Authentication auth, @PathVariable long id) {
        return orders.cancel(CurrentUser.from(auth), id);
    }

    @PostMapping("/{id}/ship")
    OrderResponse ship(@PathVariable long id) {
        return orders.ship(id);
    }
}
