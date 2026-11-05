package com.practice.spring.web.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.practice.spring.web.clients.HttpClients.Charge;
import com.practice.spring.web.clients.HttpClients.ChargeRequest;
import com.practice.spring.web.clients.HttpClients.PaymentDeclinedException;
import com.practice.spring.web.clients.HttpClients.PaymentProviderUnavailableException;
import com.practice.spring.web.clients.HttpClients.PaymentsApi;
import com.practice.spring.web.clients.HttpClients.PaymentsGateway;
import com.practice.spring.web.clients.HttpClients.ShippingQuotes;
import com.practice.spring.web.clients.HttpClients.ShopApplication;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Las comprobaciones de HttpClients contra un "proveedor de pagos" falso pero real: un servidor
 * HTTP del JDK en un puerto libre (HttpClients, CAPA 7, forma 2).
 * Correr desde v15_spring/:  ./mvnw -pl part2_web test -Dtest=HttpClientsTest
 */
@SpringBootTest(classes = ShopApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class HttpClientsTest {

    private static final FakePaymentsProvider PROVIDER = FakePaymentsProvider.start();

    @DynamicPropertySource
    static void pointToFakeProvider(DynamicPropertyRegistry registry) {
        registry.add("payments.base-url", PROVIDER::baseUrl);
        registry.add("spring.http.serviceclient.payments.base-url", PROVIDER::baseUrl);   // CAPA 5
        registry.add("spring.http.clients.read-timeout", () -> "1s");                     // CAPA 4 (con margen: ver la guía)
    }

    @AfterAll
    static void stop() {
        PROVIDER.server.stop(0);
    }

    @Autowired
    private RestClient.Builder builder;

    @Autowired
    private PaymentsGateway gateway;

    @Autowired
    private PaymentsApi paymentsApi;

    @Autowired
    private ShippingQuotes quotes;

    @Test
    void capa1y2_bootRegistraElBuilderYElClienteFunciona() {
        assertThat(builder).isNotNull();                                     // viene de spring-boot-starter-restclient
        Charge charge = gateway.charge("ana@correo.com", 25_000, "pedido-1");
        assertThat(charge.status()).isEqualTo("approved");
        assertThat(charge.amountCents()).isEqualTo(25_000);
    }

    @Test
    void capa3y6_laClaveDeIdempotenciaEvitaElCobroDoble() {
        int before = PROVIDER.realCharges.get();
        Charge first = gateway.charge("luis@correo.com", 9_900, "pedido-7");
        Charge retry = gateway.charge("luis@correo.com", 9_900, "pedido-7");  // "la respuesta se perdió": reintento
        assertThat(retry).isEqualTo(first);
        assertThat(PROVIDER.realCharges.get() - before).isEqualTo(1);        // se cobró una vez
    }

    @Test
    void capa3_los4xxY5xxSeTraducenAExcepcionesPropias() {
        assertThatThrownBy(() -> gateway.charge("ana@correo.com", 900_000, "pedido-2"))
                .isInstanceOf(PaymentDeclinedException.class)
                .hasMessage("Pago rechazado: Fondos insuficientes");          // el title del ProblemDetail
        assertThatThrownBy(() -> gateway.charge("ana@correo.com", 999, "pedido-3"))
                .isInstanceOf(PaymentProviderUnavailableException.class);
    }

    @Test
    void capa4_elTiempoLimiteCortaLaEspera() {
        long start = System.nanoTime();
        assertThatThrownBy(() -> gateway.slowReport()).isInstanceOf(ResourceAccessException.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs).isBetween(900L, 2_500L);                       // ~1 s, no los 3 s del servidor
    }

    @Test
    void capa5_laInterfaceDeclarativaEsUnProxyQueHablaHttp() {
        assertThat(Proxy.isProxyClass(paymentsApi.getClass())).isTrue();
        Charge created = paymentsApi.create(new ChargeRequest("mia@correo.com", 15_000), "pedido-9");
        assertThat(paymentsApi.find(created.id())).isEqualTo(created);
        assertThat(PROVIDER.lastIdempotencyKey).isEqualTo("pedido-9");        // el encabezado llegó
    }

    @Test
    void capa6_retryableReintentaLasFallasTransitorias() {
        String quote = quotes.quote("64000");                                // 503, 503, 200
        assertThat(quote).isEqualTo("envío a 64000: 99 pesos");
        assertThat(quotes.attempts()).isEqualTo(3);
    }

    /** Un proveedor de pagos de mentira, con comportamientos de verdad. */
    static final class FakePaymentsProvider {
        private static final Pattern AMOUNT = Pattern.compile("\"amountCents\"\\s*:\\s*(\\d+)");

        final HttpServer server;
        final AtomicInteger realCharges = new AtomicInteger();
        final Map<String, String> chargesByKey = new ConcurrentHashMap<>();
        final AtomicInteger quoteCalls = new AtomicInteger();
        volatile String lastIdempotencyKey;

        private FakePaymentsProvider(HttpServer server) {
            this.server = server;
        }

        static FakePaymentsProvider start() {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
                server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
                FakePaymentsProvider provider = new FakePaymentsProvider(server);
                server.createContext("/v1/charges", provider::charges);
                server.createContext("/v1/slow-report", exchange -> {
                    sleep(3_000);
                    send(exchange, 200, "text/plain", "ok");
                });
                server.createContext("/v1/quotes/", exchange -> {
                    if (provider.quoteCalls.incrementAndGet() <= 2) {
                        send(exchange, 503, "text/plain", "ocupado");
                    } else {
                        String cp = exchange.getRequestURI().getPath().substring("/v1/quotes/".length());
                        send(exchange, 200, "text/plain", "envío a " + cp + ": 99 pesos");
                    }
                });
                server.start();
                return provider;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void charges(HttpExchange exchange) throws IOException {
            if (exchange.getRequestMethod().equals("GET")) {
                String id = exchange.getRequestURI().getPath().substring("/v1/charges/".length());
                String json = chargesByKey.values().stream().filter(c -> c.contains("\"" + id + "\"")).findFirst().orElse(null);
                if (json == null) {
                    send(exchange, 404, "application/problem+json", "{\"title\":\"No existe\",\"status\":404}");
                } else {
                    send(exchange, 200, "application/json", json);
                }
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Matcher amount = AMOUNT.matcher(body);
            long cents = amount.find() ? Long.parseLong(amount.group(1)) : 0;
            String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
            lastIdempotencyKey = key;
            if (cents == 999) {
                send(exchange, 500, "text/plain", "falla interna");
            } else if (cents > 500_000) {
                send(exchange, 402, "application/problem+json",
                        "{\"type\":\"https://pagos.example/problems/insufficient-funds\",\"title\":\"Fondos insuficientes\","
                                + "\"status\":402,\"detail\":\"El banco rechazó el cargo\"}");
            } else {
                String json = chargesByKey.computeIfAbsent(key, k ->                 // misma clave: mismo cargo
                        "{\"id\":\"ch_" + realCharges.incrementAndGet() + "\",\"status\":\"approved\",\"amountCents\":" + cents + "}");
                send(exchange, 201, "application/json", json);
            }
        }

        private static void send(HttpExchange exchange, int status, String type, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", type);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }

        private static void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
