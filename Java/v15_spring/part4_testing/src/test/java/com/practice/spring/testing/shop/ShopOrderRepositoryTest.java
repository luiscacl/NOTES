package com.practice.spring.testing.shop;

import static org.assertj.core.api.Assertions.assertThat;

import com.practice.spring.testing.shop.ShopApp.OrderController;
import com.practice.spring.testing.shop.ShopApp.OrderStatus;
import com.practice.spring.testing.shop.ShopApp.ShopOrder;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.transaction.TestTransaction;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * UnitAndSliceTests, CAPA 7: la capa de datos sola. @DataJpaTest arranca solo JPA y los
 * repositorios, contra PostgreSQL real, y cada prueba corre en una transacción que se deshace.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Testcontainers
class ShopOrderRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired private ShopOrderRepository orders;
    @Autowired private TestEntityManager entityManager;
    @Autowired private ApplicationContext context;

    private void persist(String email, long cents, OrderStatus status, String day) {
        entityManager.persist(new ShopOrder(email, cents, status, Instant.parse(day + "T10:00:00Z"), "p"));
    }

    @Test
    void consultasPropiasContraLaBaseDeDatosReal() {
        persist("ana@correo.com", 10_000, OrderStatus.PAID, "2026-11-01");
        persist("ana@correo.com", 25_000, OrderStatus.PAID, "2026-11-03");
        persist("ana@correo.com", 99_000, OrderStatus.REJECTED, "2026-11-04");
        persist("luis@correo.com", 5_000, OrderStatus.PAID, "2026-11-02");
        entityManager.flush();                                                    // mandar los inserts antes de consultar

        assertThat(orders.findByCustomerEmailOrderByCreatedAtDesc("ana@correo.com"))
                .extracting(ShopOrder::getTotalCents).containsExactly(99_000L, 25_000L, 10_000L);
        assertThat(orders.totalPaidBy("ana@correo.com")).isEqualTo(35_000);      // los rechazados no cuentan
    }

    @Test
    void cadaPruebaCorreEnUnaTransaccionQueSeDeshaceYSinWeb() {
        assertThat(TestTransaction.isActive()).isTrue();
        assertThat(TestTransaction.isFlaggedForRollback()).isTrue();             // al terminar: rollback
        assertThat(context.getBeanNamesForType(OrderController.class)).isEmpty();   // nada de la capa web
    }
}
