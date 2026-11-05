package com.practice.spring.shop.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Un producto del catálogo. El precio en centavos: nunca double para dinero (v1_fundamentals). */
@Entity
@Table(name = "products")
class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(name = "price_cents")
    private long priceCents;

    private int stock;

    protected Product() {                        // para JPA
    }

    Product(String name, long priceCents, int stock) {
        change(name, priceCents, stock);
    }

    final void change(String name, long priceCents, int stock) {
        this.name = name;
        this.priceCents = priceCents;
        this.stock = stock;
    }

    Long id() {
        return id;
    }

    String name() {
        return name;
    }

    long priceCents() {
        return priceCents;
    }

    int stock() {
        return stock;
    }
}
