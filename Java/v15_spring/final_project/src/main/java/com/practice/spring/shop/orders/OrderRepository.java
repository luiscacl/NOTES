package com.practice.spring.shop.orders;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

    /** Las listas NO cargan renglones: el total está en la tabla de pedidos. */
    Page<CustomerOrder> findByCustomer(String customer, Pageable pageable);

    /** El detalle sí, en la MISMA consulta (un JOIN), no una consulta extra por pedido (N+1). */
    @EntityGraph(attributePaths = "lines")
    Optional<CustomerOrder> findWithLinesById(long id);
}
