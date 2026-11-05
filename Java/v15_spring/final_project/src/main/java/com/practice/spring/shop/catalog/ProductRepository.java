package com.practice.spring.shop.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Descuenta existencias SOLO si alcanzan, en una sola sentencia. PostgreSQL bloquea la fila
     * mientras la actualiza: dos pedidos por la última pieza no pueden pasar los dos. Devuelve
     * cuántas filas cambió: 0 = no alcanzó. (Leer el stock, revisarlo en Java y luego guardarlo
     * permitiría vender de más: v13_concurrency/part1_threads/ConcurrencyProblems, check-then-act.)
     */
    @Modifying
    @Query("update Product p set p.stock = p.stock - :quantity where p.id = :id and p.stock >= :quantity")
    int takeStock(long id, int quantity);

    @Modifying
    @Query("update Product p set p.stock = p.stock + :quantity where p.id = :id")
    int returnStock(long id, int quantity);
}
