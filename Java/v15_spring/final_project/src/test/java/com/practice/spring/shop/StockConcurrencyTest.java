package com.practice.spring.shop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Diez clientes compran AL MISMO TIEMPO un producto con 3 piezas. Con "leer, revisar y guardar"
 * se venderían de más; con el UPDATE condicional de ProductRepository, exactamente 3 compras pasan.
 */
class StockConcurrencyTest extends IntegrationTestBase {

    @Test
    void nuncaSeVendeMasDeLoQueHay() throws Exception {
        long product = newProduct("Consola edición limitada", 999_900, 3);
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tokens.add(customerToken());                     // antes: el BCrypt de cada login no entra en la carrera
        }

        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            for (String token : tokens) {
                results.add(pool.submit(() -> {
                    start.await();                           // todos salen juntos
                    return client.post().uri("/api/orders").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"lines\":[{\"productId\":%d,\"quantity\":1}]}".formatted(product))
                            .exchange().returnResult().getStatus().value();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).filteredOn(s -> s == 201).hasSize(3);
            assertThat(statuses).filteredOn(s -> s == 409).hasSize(7);
        }
        assertThat(stockOf(product)).isZero();
    }
}
