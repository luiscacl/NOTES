package com.practice.spring.production.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.practice.spring.production.observability.Observability.ObservedShopApplication;
import com.practice.spring.production.observability.Observability.PaymentGatewayHealth;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Las comprobaciones de Observability, con el servidor real y Actuator.
 * Correr desde v15_spring/:  ./mvnw -pl part6_production test -Dtest=ObservabilityTest
 */
@SpringBootTest(classes = ObservedShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.endpoints.web.exposure.include=health,info,metrics,prometheus,loggers",
                "management.endpoint.health.show-details=always",
                "management.endpoint.health.probes.enabled=true",
                "management.endpoint.health.group.readiness.include=readinessState,paymentGateway",
                "logging.structured.format.console=ecs",
                "management.info.env.enabled=true",
                "info.app.name=Tienda",
                "info.app.version=1.4.0"})
@ExtendWith(OutputCaptureExtension.class)
@AutoConfigureMetrics                          // en pruebas, Boot APAGA la exportación de métricas (Observability, CAPA 4)
class ObservabilityTest {

    @LocalServerPort private int port;
    @Autowired private PaymentGatewayHealth paymentGateway;
    @Autowired private MeterRegistry metrics;

    private RestTestClient client;

    @BeforeEach
    void connect() {
        paymentGateway.simulateOutage(false);
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void capa2_soloLoExpuestoExiste() {
        client.get().uri("/actuator/env").exchange().expectStatus().isNotFound();      // no se expuso
        client.get().uri("/actuator/info").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.app.version").isEqualTo("1.4.0");
    }

    @Test
    void capa3_unaDependenciaCaidaTumbaReadinessPeroNoLiveness() {
        client.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk();

        paymentGateway.simulateOutage(true);
        client.get().uri("/actuator/health/readiness").exchange()
                .expectStatus().isEqualTo(503)                                         // sin tráfico...
                .expectBody().jsonPath("$.status").isEqualTo("DOWN");
        client.get().uri("/actuator/health/liveness").exchange()
                .expectStatus().isOk()                                                  // ...pero sin reiniciar
                .expectBody().jsonPath("$.status").isEqualTo("UP");
        client.get().uri("/actuator/health").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody().jsonPath("$.components.paymentGateway.details.error").isEqualTo("timeout al conectar");
    }

    /** Cuántos pedidos GOLD lleva el contador (0 si todavía no existe). */
    private double goldOrders() {
        Counter counter = metrics.find("shop.orders.placed").tag("tier", "GOLD").counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void capa4_metricasDelNegocioYDeHttp() {
        double before = goldOrders();                    // las métricas se ACUMULAN mientras vive el contexto
        for (String tier : new String[] {"GOLD", "GOLD", "REGULAR"}) {
            client.post().uri("/api/orders?tier=" + tier).exchange().expectStatus().isCreated();
        }
        assertThat(goldOrders() - before).isEqualTo(2.0); // por eso se compara la DIFERENCIA, no el total
        client.get().uri("/actuator/metrics/shop.orders.placed?tag=tier:GOLD").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.measurements[0].statistic").isEqualTo("COUNT");

        String prometheus = client.get().uri("/actuator/prometheus").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).returnResult().getResponseBody();
        assertThat(prometheus)
                .contains("shop_orders_placed_total{tier=\"GOLD\"}")
                .contains("shop_payment_duration_seconds{quantile=\"0.99\"}")         // el percentil del Timer
                .contains("uri=\"/api/orders\"");                                      // la plantilla, no la URL con ?tier
    }

    @Test
    void capa5_logsJsonConElIdDePeticion(CapturedOutput output) {
        client.post().uri("/api/orders?tier=GOLD").header("X-Request-Id", "req-42").exchange()
                .expectStatus().isCreated()
                .expectHeader().valueEquals("X-Request-Id", "req-42");
        String line = output.getOut().lines()
                .filter(l -> l.contains("Pedido creado para un cliente GOLD"))
                .findFirst().orElseThrow();
        assertThat(line).startsWith("{\"@timestamp\"")
                .contains("\"requestId\":\"req-42\"")
                .contains("\"level\":\"INFO\"");
    }

    @Test
    void capa6_cambiarElNivelDeLogEnCaliente() {
        String logger = "/actuator/loggers/com.practice.spring.production";
        client.post().uri(logger).contentType(MediaType.APPLICATION_JSON)
                .body("{\"configuredLevel\":\"DEBUG\"}")
                .exchange().expectStatus().isNoContent();
        client.get().uri(logger).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.configuredLevel").isEqualTo("DEBUG");
        client.post().uri(logger).contentType(MediaType.APPLICATION_JSON)
                .body("{\"configuredLevel\":null}")
                .exchange().expectStatus().isNoContent();                              // de regreso
    }
}
