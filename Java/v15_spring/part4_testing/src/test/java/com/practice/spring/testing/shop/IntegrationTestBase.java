package com.practice.spring.testing.shop;

import com.practice.spring.testing.shop.ShopApp.PaymentClient;
import com.practice.spring.testing.shop.ShopApp.PaymentDeclinedException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * La base de las pruebas de integración (IntegrationTests, CAPAS 2 a 5).
 * TODAS las clases que la heredan piden exactamente la misma configuración, así Spring reusa UN
 * contexto (y con él, UN PostgreSQL) para todas: la diferencia entre segundos y minutos.
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({IntegrationTestBase.Containers.class, IntegrationTestBase.FakeExternalSystems.class})
abstract class IntegrationTestBase {

    /** El PostgreSQL como un BEAN: vive lo que vive el contexto, y el contexto se reusa. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:17");
        }
    }

    /** Los sistemas externos, reemplazados por falsos que se comportan como los reales. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FakeExternalSystems {
        @Bean
        @Primary                                            // gana sobre LocalPaymentClient
        RecordingPayments fakePayments() {
            return new RecordingPayments();
        }
    }

    /** Un banco de mentira: aprueba hasta 5,000 pesos y anota cada cobro. */
    static final class RecordingPayments implements PaymentClient {
        final List<Long> charged = new CopyOnWriteArrayList<>();

        @Override
        public String charge(String customerEmail, long cents) {
            if (cents > 500_000) {
                throw new PaymentDeclinedException("excede el límite de la tarjeta");
            }
            charged.add(cents);
            return "fake-" + charged.size();
        }
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected RecordingPayments payments;

    protected RestTestClient client;

    @BeforeEach
    void cleanStateAndConnect() {
        jdbc.sql("delete from shop_orders").update();      // sin rollback automático: se limpia a mano
        payments.charged.clear();
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
