package com.practice.spring.shop.catalog;

/** Un producto como sale por la API: la entidad nunca sale directo (v11_patterns/part4_modern/DTO). */
public record ProductResponse(long id, String name, long priceCents, int stock) {

    static ProductResponse from(Product product) {
        return new ProductResponse(product.id(), product.name(), product.priceCents(), product.stock());
    }
}
