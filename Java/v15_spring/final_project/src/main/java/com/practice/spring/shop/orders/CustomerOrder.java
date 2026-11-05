package com.practice.spring.shop.orders;

import com.practice.spring.shop.shared.BusinessRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Un pedido, con SUS reglas: se crea con al menos un renglón, el total se calcula aquí, y solo un
 * pedido PLACED se puede enviar o cancelar. Nadie más cambia el estado: no hay setStatus
 * (v14_architecture/part1_layers/DDDBasics, el agregado y sus invariantes).
 *
 * Decisión práctica: es una entidad JPA. Separar "dominio puro" y "entidad de persistencia" duplica
 * cada clase y su conversión; aquí las anotaciones no estorban para probar las reglas sin base de
 * datos (CustomerOrderTest). Ver README, "Decisiones".
 */
@Entity
@Table(name = "orders")
class CustomerOrder {

    enum Status { PLACED, SHIPPED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customer;

    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "total_cents")
    private long totalCents;                     // guardado: la lista de pedidos no carga los renglones

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderLine> lines = new ArrayList<>();

    protected CustomerOrder() {                  // para JPA
    }

    static CustomerOrder place(String customer, List<OrderLine> lines, Instant now) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Un pedido necesita al menos un renglón");
        }
        CustomerOrder order = new CustomerOrder();
        order.customer = customer;
        order.status = Status.PLACED;
        order.createdAt = now;
        order.lines.addAll(lines);
        order.totalCents = lines.stream().mapToLong(OrderLine::subtotalCents).sum();
        return order;
    }

    void ship() {
        requirePlaced("enviar", "ORDER_NOT_SHIPPABLE");
        status = Status.SHIPPED;
    }

    void cancel() {
        requirePlaced("cancelar", "ORDER_NOT_CANCELLABLE");
        status = Status.CANCELLED;
    }

    private void requirePlaced(String action, String code) {
        if (status != Status.PLACED) {
            throw new BusinessRuleException(code, "No se puede " + action + " un pedido " + status);
        }
    }

    boolean belongsTo(String username) {
        return customer.equals(username);
    }

    Long id() {
        return id;
    }

    String customer() {
        return customer;
    }

    Status status() {
        return status;
    }

    Instant createdAt() {
        return createdAt;
    }

    long totalCents() {
        return totalCents;
    }

    List<OrderLine> lines() {
        return List.copyOf(lines);               // quien lee no puede agregar renglones por fuera
    }
}
