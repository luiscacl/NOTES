package com.practice.spring.production.async;

import static org.assertj.core.api.Assertions.assertThat;

import com.practice.spring.production.async.AsyncSchedulingCaching.Delivery;
import com.practice.spring.production.async.AsyncSchedulingCaching.ExpiredCartsCleaner;
import com.practice.spring.production.async.AsyncSchedulingCaching.Product;
import com.practice.spring.production.async.AsyncSchedulingCaching.ProductCatalog;
import com.practice.spring.production.async.AsyncSchedulingCaching.ReceiptService;
import com.practice.spring.production.async.AsyncSchedulingCaching.TasksApplication;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.support.CronExpression;

/**
 * Las comprobaciones de AsyncSchedulingCaching, con el pool normal de Spring Boot.
 * Correr desde v15_spring/:  ./mvnw -pl part6_production test -Dtest=AsyncSchedulingCachingTest
 */
@SpringBootTest(classes = TasksApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "shop.carts.cleanup-delay=PT0.05S")                // la prueba no espera 10 minutos
@ExtendWith(OutputCaptureExtension.class)
class AsyncSchedulingCachingTest {

    @Autowired private ReceiptService receipts;
    @Autowired private ExpiredCartsCleaner cleaner;
    @Autowired private ProductCatalog catalog;
    @Autowired private ThreadPoolTaskExecutor applicationTaskExecutor;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void capa2_elMetodoAsyncCorreEnUnHiloDelPool() {
        String caller = Thread.currentThread().getName();
        Delivery delivery = receipts.sendReceipt("A-1").join();
        assertThat(delivery.thread()).isNotEqualTo(caller).startsWith("task-");
        assertThat(delivery.virtualThread()).isFalse();                  // un hilo de plataforma del pool
    }

    @Test
    void capa3_llamarloConThisNoEsAsincrono() {
        Delivery delivery = receipts.sendReceiptViaThis("A-2");
        assertThat(delivery.thread()).isEqualTo(Thread.currentThread().getName());   // el mismo hilo
    }

    @Test
    void capa4_elErrorDeUnVoidSoloLlegaAlLogYElMdcViajaConElDecorador(CapturedOutput output) throws Exception {
        receipts.notifyWarehouse("A-3");                                 // quien llama no ve nada
        Thread.sleep(500);
        assertThat(output.getOut()).contains("El almacén no responde (pedido A-3)");

        MDC.put("requestId", "req-7");
        assertThat(receipts.sendReceipt("A-4").join().requestId()).isEqualTo("req-7");          // con TaskDecorator
        assertThat(CompletableFuture.supplyAsync(() -> MDC.get("requestId")).join()).isNull();  // sin él
    }

    @Test
    void capa5_elPoolDeBootTieneColaSinLimite() {
        assertThat(applicationTaskExecutor.getCorePoolSize()).isEqualTo(8);
        assertThat(applicationTaskExecutor.getMaxPoolSize()).isEqualTo(Integer.MAX_VALUE);
        assertThat(applicationTaskExecutor.getQueueCapacity()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void capa7_laTareaCorreSolaYElCronTieneSeisCampos() throws Exception {
        long deadline = System.currentTimeMillis() + 2_000;
        while (cleaner.runs() < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(cleaner.runs()).isGreaterThanOrEqualTo(3);
        assertThat(cleaner.lastThread()).startsWith("scheduling-");

        LocalDateTime fridayAt4 = LocalDateTime.of(2026, 11, 6, 4, 0);  // viernes
        assertThat(CronExpression.parse("0 0 3 * * MON-FRI").next(fridayAt4))
                .isEqualTo(LocalDateTime.of(2026, 11, 9, 3, 0));         // el lunes a las 3:00
    }

    @Test
    void capa8_laCacheEvitaLaConsultaYSeBorraAlActualizar() {
        int before = catalog.queries();
        long t0 = System.nanoTime();
        catalog.find(1);
        long t1 = System.nanoTime();
        catalog.find(1);
        long t2 = System.nanoTime();
        assertThat(catalog.queries() - before).isEqualTo(1);           // la segunda no fue a la base
        assertThat(t2 - t1).isLessThan(t1 - t0);                         // medido: 82 ms vs 0 ms

        catalog.update(new Product(1, "Teclado", 79_900));
        assertThat(catalog.find(1).priceCents()).isEqualTo(79_900);     // @CacheEvict: precio nuevo

        int beforeMissing = catalog.queries();
        catalog.find(99);
        catalog.find(99);
        assertThat(catalog.queries() - beforeMissing).isEqualTo(2);     // unless: el null no se guarda

        int beforeThis = catalog.queries();
        catalog.findViaThis(1);
        assertThat(catalog.queries() - beforeThis).isEqualTo(1);        // con this, sin caché
    }
}
