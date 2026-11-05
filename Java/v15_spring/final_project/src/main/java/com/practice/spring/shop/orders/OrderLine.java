package com.practice.spring.shop.orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Un renglón: el producto con el nombre y el precio que tenía AL COMPRAR. */
@Entity
@Table(name = "order_lines")
class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id")
    private long productId;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "unit_price_cents")
    private long unitPriceCents;

    private int quantity;

    protected OrderLine() {                      // para JPA
    }

    OrderLine(long productId, String productName, long unitPriceCents, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.unitPriceCents = unitPriceCents;
        this.quantity = quantity;
    }

    long subtotalCents() {
        return Math.multiplyExact(unitPriceCents, quantity);   // si se desborda, excepción; nunca un total falso
    }

    long productId() {
        return productId;
    }

    String productName() {
        return productName;
    }

    long unitPriceCents() {
        return unitPriceCents;
    }

    int quantity() {
        return quantity;
    }
}
