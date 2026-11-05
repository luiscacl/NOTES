package com.practice.spring.testing.shop;

import com.practice.spring.testing.shop.ShopApp.ShopOrder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** El repositorio de pedidos, en su propio archivo: Spring Data lo encuentra sin configuración extra. */
public interface ShopOrderRepository extends JpaRepository<ShopOrder, Long> {
    List<ShopOrder> findByCustomerEmailOrderByCreatedAtDesc(String customerEmail);

    @Query("select coalesce(sum(o.totalCents), 0) from ShopOrder o where o.customerEmail = :email and o.status = 'PAID'")
    long totalPaidBy(@Param("email") String email);
}
