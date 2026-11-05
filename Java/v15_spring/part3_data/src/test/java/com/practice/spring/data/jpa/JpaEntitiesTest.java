package com.practice.spring.data.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.practice.spring.data.jpa.JpaEntities.OrderService;
import com.practice.spring.data.jpa.JpaEntities.OrderSummary;
import com.practice.spring.data.jpa.JpaEntities.PurchaseOrder;
import com.practice.spring.data.jpa.JpaEntities.ShopApplication;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.LazyInitializationException;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Las comprobaciones de JpaEntities contra PostgreSQL 17 real, contando las sentencias SQL
 * que manda Hibernate (sus estadísticas).
 * Correr desde v15_spring/:  ./mvnw -pl part3_data test -Dtest=JpaEntitiesTest
 */
@SpringBootTest(classes = ShopApplication.class, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@Testcontainers
class JpaEntitiesTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Autowired private OrderService orders;
    @Autowired private JdbcClient jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;
    private final List<Long> orderIds = new ArrayList<>();

    @BeforeEach
    void freshData() {
        jdbc.sql("delete from order_lines").update();
        jdbc.sql("delete from orders").update();
        jdbc.sql("delete from customers").update();
        orderIds.clear();
        for (int i = 1; i <= 10; i++) {
            orderIds.add(orders.createOrder("Cliente " + i, 2));   // 10 pedidos, 10 clientes, 2 líneas cada uno
        }
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    /** Cuántas sentencias SQL se prepararon desde el último clear. */
    private long statements() {
        return statistics.getPrepareStatementCount();
    }

    @Test
    void capa2y4_elAgregadoSeGuardaConSusLineasYElEnumComoTexto() {
        assertThat(jdbc.sql("select count(*) from order_lines").query(Long.class).single()).isEqualTo(20);   // cascada
        assertThat(jdbc.sql("select distinct status from orders").query(String.class).list()).containsExactly("OPEN");
        assertThat(jdbc.sql("select amount_cents from order_lines limit 1").query(Long.class).single())
                .isEqualTo(1_000);                                       // el @Embeddable en su columna
    }

    @Test
    void capa3_mismaInstanciaYDirtyCheckingSinSave() {
        Long customerId = jdbc.sql("select id from customers order by id limit 1").query(Long.class).single();
        statistics.clear();
        assertThat(orders.sameInstanceTwice(customerId)).isTrue();
        assertThat(statements()).isEqualTo(1);                           // el segundo find no fue a la base de datos

        orders.renameCustomer(customerId, "Nombre nuevo");               // nadie llamó a save
        assertThat(jdbc.sql("select name from customers where id = :id").param("id", customerId)
                .query(String.class).single()).isEqualTo("Nombre nuevo");
    }

    @Test
    void capa5_recorrerUnaRelacionPerezosaFueraDeLaTransaccionFalla() {
        PurchaseOrder order = orders.findOrder(orderIds.getFirst());
        assertThatThrownBy(() -> order.getLines().size())
                .isInstanceOf(LazyInitializationException.class)
                .hasMessageContaining("PurchaseOrder.lines")
                .hasMessageContaining("no session");
    }

    @Test
    void capa6_elN1Medido() {
        List<OrderSummary> summaries = orders.summariesNaive();
        assertThat(summaries).hasSize(10);
        assertThat(summaries.getFirst().lines()).isEqualTo(2);
        assertThat(statements()).isEqualTo(21);                          // 1 + 10 clientes + 10 colecciones
    }

    @Test
    void capa7_joinFetchLoHaceEnUnaConsulta() {
        List<OrderSummary> summaries = orders.summariesJoinFetch();
        assertThat(summaries).hasSize(10);
        assertThat(summaries).isEqualTo(orders.summariesNaive());        // el mismo resultado...
        statistics.clear();
        orders.summariesJoinFetch();
        assertThat(statements()).isEqualTo(1);                           // ...con 1 consulta en vez de 21
    }
}
